package io.github.alreadybold.translator.audio;

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * 봇 자신이 /leave 커맨드를 거치지 않고 음성 채널에서 나가게 되는 경우를 감지한다.
 *
 * 예: 서버 관리자가 봇을 음성 채널에서 강제로 연결 끊기(disconnect)하는 경우. 이런
 * 경로는 LeaveCommandListener를 안 거쳐서 VoiceConnectionRegistry의 supervisor가
 * 정리되지 않고, 감시 스케줄러가 이미 나간 채널에 계속 재접속을 시도하며 스레드가
 * 새게 된다. 실제 정리 로직(자동 재접속 중인지 구분하는 것 포함)은
 * VoiceConnectionRegistry.cleanupIfExternallyDisconnected()에 있고, 이 클래스는
 * "봇 자신의 음성 상태가 나가는 쪽으로 바뀌었다"는 이벤트만 감지해서 넘겨준다.
 */
public class BotVoiceDisconnectListener extends ListenerAdapter {

	private final VoiceConnectionRegistry connectionRegistry;

	public BotVoiceDisconnectListener(VoiceConnectionRegistry connectionRegistry) {
		this.connectionRegistry = connectionRegistry;
	}

	@Override
	public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
		boolean isBotItself = event.getMember().getIdLong() == event.getJDA().getSelfUser().getIdLong();

		// getChannelJoined()가 null이 아니면 "들어간" 이벤트(최초 접속, 다른 채널로 이동
		// 포함)라서 여기서 신경 쓸 대상이 아니다 - 나가는 경우(null)만 본다.
		if (!isBotItself || event.getChannelJoined() != null) {
			return;
		}

		connectionRegistry.cleanupIfExternallyDisconnected(event.getGuild().getIdLong());
	}
}
