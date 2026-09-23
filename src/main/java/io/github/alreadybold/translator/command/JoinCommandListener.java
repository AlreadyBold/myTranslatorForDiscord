package io.github.alreadybold.translator.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import io.github.alreadybold.translator.audio.UserAudioReceiveHandler;

/**
 * "/join" 슬래시 커맨드를 처리하는 리스너.
 *
 * 커맨드를 실행한 유저가 지금 들어가 있는 음성 채널로 봇을 접속시킨다. 커맨드 등록 자체는
 * CommandRegistrationListener가 담당하고, 이 클래스는 실행 로직만 갖는다.
 * 이후 STT 연동 단계에서, 이 접속된 음성 채널을 통해 유저별 오디오를 캡처하게 된다.
 */
public class JoinCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(JoinCommandListener.class);

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!"join".equals(event.getName())) {
			return;
		}

		Guild guild = event.getGuild();
		Member member = event.getMember();
		AudioChannelUnion voiceChannel = member == null ? null : getVoiceChannel(member);

		// 유저가 음성 채널에 들어가 있지 않으면 봇이 어디로 들어가야 할지 알 수 없다.
		// 실제로 흔히 발생할 수 있는 사용자 실수라서, 예외를 던지는 대신 안내 메시지로 처리한다.
		if (guild == null || voiceChannel == null) {
			event.reply("먼저 음성 채널에 들어가 있어야 해요.").setEphemeral(true).queue();
			return;
		}

		AudioManager audioManager = guild.getAudioManager();

		try {
			// 실제 음성 채널 접속. 이 시점부터 봇이 해당 채널의 오디오를 주고받을 수 있게 된다.
			audioManager.openAudioConnection(voiceChannel);
		} catch (InsufficientPermissionException exception) {
			// 봇에게 Connect 권한이 없는 채널일 수 있음 - 서버 설정에 따라 흔히 발생하는 상황이라
			// 명시적으로 잡아서 사용자에게 원인을 알려준다.
			event.reply("이 채널에 들어갈 권한이 없어요. (Connect 권한 필요)").setEphemeral(true).queue();
			return;
		}

		// 접속 직후 오디오 수신 핸들러를 등록해야, 이 채널에서 유저들이 말하는 PCM 오디오를 받기 시작한다.
		audioManager.setReceivingHandler(new UserAudioReceiveHandler());

		LOGGER.info("음성 채널 접속: {}", voiceChannel.getName());
		event.reply("`" + voiceChannel.getName() + "` 채널에 들어갈게요!").queue();
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
