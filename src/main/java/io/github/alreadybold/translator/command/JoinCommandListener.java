package io.github.alreadybold.translator.command;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import io.github.alreadybold.translator.audio.VoiceConnectionRegistry;
import io.github.alreadybold.translator.audio.VoiceConnectionSupervisor;
import io.github.alreadybold.translator.i18n.BotMessage;
import io.github.alreadybold.translator.i18n.Language;
import io.github.alreadybold.translator.settings.UserLanguageRegistry;
import io.github.alreadybold.translator.settings.UserOutputLanguageRegistry;
import io.github.alreadybold.translator.stt.SpeechToTextClient;
import io.github.alreadybold.translator.translation.TranslationClient;

/**
 * "/join" 슬래시 커맨드를 처리하는 리스너.
 *
 * 커맨드를 실행한 유저가 지금 들어가 있는 음성 채널로 봇을 접속시킨다. 커맨드 등록 자체는
 * CommandRegistrationListener가 담당하고, 이 클래스는 실행 로직만 갖는다.
 * 실제 접속과 오디오 수신 핸들러 관리는 VoiceConnectionSupervisor에 위임한다
 * (자동 재접속 로직까지 포함하고 있어서 여기서 직접 다루면 클래스가 비대해진다).
 */
public class JoinCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(JoinCommandListener.class);

	private final UserLanguageRegistry languageRegistry;
	private final Map<Language, SpeechToTextClient> sttClientsByLanguage;
	private final UserOutputLanguageRegistry outputLanguageRegistry;
	private final TranslationClient translationClient;
	private final VoiceConnectionRegistry connectionRegistry;

	public JoinCommandListener(
			UserLanguageRegistry languageRegistry,
			Map<Language, SpeechToTextClient> sttClientsByLanguage,
			UserOutputLanguageRegistry outputLanguageRegistry,
			TranslationClient translationClient,
			VoiceConnectionRegistry connectionRegistry) {
		this.languageRegistry = languageRegistry;
		this.sttClientsByLanguage = sttClientsByLanguage;
		this.outputLanguageRegistry = outputLanguageRegistry;
		this.translationClient = translationClient;
		this.connectionRegistry = connectionRegistry;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!CommandRegistrationListener.JOIN_COMMAND.equals(event.getName())) {
			return;
		}

		// 응답은 이 커맨드를 실행한 유저가 선택한 언어로 보여준다 (선택 전이면 기본값).
		Language language = languageRegistry.get(event.getUser().getIdLong());
		Guild guild = event.getGuild();
		Member member = event.getMember();
		AudioChannelUnion voiceChannel = member == null ? null : getVoiceChannel(member);

		// 유저가 음성 채널에 들어가 있지 않으면 봇이 어디로 들어가야 할지 알 수 없다.
		// 실제로 흔히 발생할 수 있는 사용자 실수라서, 예외를 던지는 대신 안내 메시지로 처리한다.
		if (guild == null || voiceChannel == null) {
			event.reply(BotMessage.NOT_IN_VOICE_CHANNEL.text(language)).setEphemeral(true).queue();
			return;
		}

		AudioManager audioManager = guild.getAudioManager();
		AudioChannel connectedChannel = audioManager.getConnectedChannel();

		// 이미 같은 채널에 접속해있는데 또 접속을 시도하면 디스코드 쪽에서 음성 연결(DAVE
		// 암호화 세션 포함)을 다시 맺는다. 이 재협상 구간에 짧게 겹치는 오디오 패킷은
		// 복호화가 실패할 수 있어서(키 전환 유예 구간), 발화가 짧으면 통째로 유실될 수
		// 있다. 그래서 이미 같은 채널이면 아예 재접속을 시도하지 않는다.
		if (connectedChannel != null && connectedChannel.getIdLong() == voiceChannel.getIdLong()) {
			event.reply(BotMessage.ALREADY_CONNECTED.text(language, voiceChannel.getName())).setEphemeral(true).queue();
			return;
		}

		VoiceConnectionSupervisor supervisor = new VoiceConnectionSupervisor(
				audioManager,
				voiceChannel,
				languageRegistry,
				sttClientsByLanguage,
				outputLanguageRegistry,
				translationClient);

		try {
			// 접속 + 오디오 수신 핸들러 등록 + 이후 자동 재접속 감시까지 전부 supervisor가 담당한다.
			supervisor.connect();
		} catch (InsufficientPermissionException exception) {
			// 봇에게 Connect 권한이 없는 채널일 수 있음 - 서버 설정에 따라 흔히 발생하는 상황이라
			// 명시적으로 잡아서 사용자에게 원인을 알려준다.
			event.reply(BotMessage.NO_CONNECT_PERMISSION.text(language)).setEphemeral(true).queue();
			return;
		} catch (RuntimeException exception) {
			// 위에서 예상한 권한 문제 외의 오류(채널이 그 사이 삭제됨, 디스코드 쪽 일시적
			// 오류 등)까지 여기서 안 잡으면, 응답을 아예 안 보내서 유저 화면에는 3초 뒤
			// "상호작용이 실패했습니다"만 뜨고 원인을 알 방법이 없다.
			LOGGER.error("음성 채널 접속 중 예상치 못한 오류가 발생했습니다: {}", voiceChannel.getName(), exception);
			event.reply(BotMessage.JOIN_FAILED.text(language)).setEphemeral(true).queue();
			return;
		}

		// 길드에 이미 등록된 supervisor가 있으면(다른 채널로 이동하는 경우) register()가
		// 알아서 그 이전 것부터 정리해준다.
		connectionRegistry.register(guild.getIdLong(), supervisor);

		LOGGER.info("음성 채널 접속: {}", voiceChannel.getName());
		event.reply(BotMessage.JOINED_CHANNEL.text(language, voiceChannel.getName())).queue();
	}

	/**
	 * 멤버가 현재 접속해있는 음성 채널을 반환한다. 음성 채널에 없다면 null.
	 *
	 * GuildVoiceState 자체가 null일 수 있고(음성 상태 정보를 아직 못 받은 경우),
	 * 채널에 들어가 있지 않으면 getChannel()도 null을 반환하므로 둘 다 안전하게 처리한다.
	 */
	private AudioChannelUnion getVoiceChannel(Member member) {
		GuildVoiceState voiceState = member.getVoiceState();
		return voiceState == null ? null : voiceState.getChannel();
	}
}
