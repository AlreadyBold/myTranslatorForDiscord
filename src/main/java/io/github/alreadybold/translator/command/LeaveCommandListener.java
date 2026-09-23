package io.github.alreadybold.translator.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;

import io.github.alreadybold.translator.audio.UserAudioReceiveHandler;
import io.github.alreadybold.translator.i18n.BotMessage;
import io.github.alreadybold.translator.i18n.Language;

/**
 * "/leave" 슬래시 커맨드를 처리하는 리스너.
 *
 * 봇을 지금 접속해있는 음성 채널에서 내보낸다. 커맨드 등록 자체는
 * CommandRegistrationListener가 담당하고, 이 클래스는 실행 로직만 갖는다.
 */
public class LeaveCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(LeaveCommandListener.class);

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!"leave".equals(event.getName())) {
			return;
		}

		Guild guild = event.getGuild();

		if (guild == null) {
			// TODO: /setlang이 생기면 Language.KO 대신 이 유저가 선택한 언어를 넘기도록 변경
			event.reply(BotMessage.GUILD_ONLY.text(Language.KO)).setEphemeral(true).queue();
			return;
		}

		AudioManager audioManager = guild.getAudioManager();

		// 애초에 접속해있지 않은데 나가려는 경우 - 흔히 발생할 수 있는 사용자 실수라
		// 예외 대신 안내 메시지로 처리한다 (JoinCommandListener의 처리 방식과 동일).
		if (!audioManager.isConnected()) {
			event.reply(BotMessage.NOT_CONNECTED.text(Language.KO)).setEphemeral(true).queue();
			return;
		}

		// 오디오 버퍼링용 백그라운드 스레드(무음 감지 스케줄러)를 정리한다.
		// 안 하면 연결이 끊긴 뒤에도 그 스레드가 계속 살아남는다.
		AudioReceiveHandler receivingHandler = audioManager.getReceivingHandler();

		if (receivingHandler instanceof UserAudioReceiveHandler userAudioReceiveHandler) {
			userAudioReceiveHandler.shutdown();
		}

		audioManager.closeAudioConnection();
		LOGGER.info("음성 채널 퇴장: {}", guild.getName());
		event.reply(BotMessage.LEFT_CHANNEL.text(Language.KO)).queue();
	}
}
