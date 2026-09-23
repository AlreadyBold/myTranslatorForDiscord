package io.github.alreadybold.translator.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.managers.AudioManager;

/**
 * "/join" 슬래시 커맨드를 등록하고 처리하는 리스너.
 *
 * 커맨드를 실행한 유저가 지금 들어가 있는 음성 채널로 봇을 접속시킨다.
 * 이후 STT 연동 단계에서, 이 접속된 음성 채널을 통해 유저별 오디오를 캡처하게 된다.
 */
public class JoinCommandListener extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(JoinCommandListener.class);

	/**
	 * 디스코드 연결이 완료(READY)되면 슬래시 커맨드를 등록한다.
	 *
	 * 글로벌 커맨드로 등록하면 모든 서버에 반영되기까지 최대 1시간이 걸릴 수 있어서,
	 * 개발 중에는 봇이 들어가 있는 길드(서버) 단위로 등록해 즉시 테스트할 수 있게 한다.
	 */
	@Override
	public void onReady(ReadyEvent event) {
		for (Guild guild : event.getJDA().getGuilds()) {
			guild.updateCommands()
					.addCommands(Commands.slash("join", "봇을 내가 있는 음성 채널로 불러옵니다"))
					.queue();
		}
	}

	/**
	 * 슬래시 커맨드 실행을 처리한다.
	 *
	 * 이 리스너에 앞으로 다른 커맨드가 추가될 수도 있으므로, 이름으로 먼저 필터링한다
	 * (지금은 "join" 하나뿐이지만 미리 방어적으로 작성).
	 */
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
