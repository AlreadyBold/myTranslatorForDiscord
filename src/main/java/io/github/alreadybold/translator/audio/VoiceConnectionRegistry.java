package io.github.alreadybold.translator.audio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 길드(서버)별로 현재 활성화된 VoiceConnectionSupervisor를 보관한다.
 *
 * /join이 새 연결을 만들 때 등록하고, /leave가 정리할 때 여기서 꺼내 shutdown한다.
 * 이렇게 한 곳에 모아두지 않으면 /leave 시점에 감시 스케줄러를 찾을 방법이 없어서
 * 계속 살아있게 되고, 유저가 이미 나간 뒤에도 "오디오 안 들어옴 -> 재접속" 판정이
 * 계속 돌게 된다.
 */
public class VoiceConnectionRegistry {

	private final Map<Long, VoiceConnectionSupervisor> supervisorsByGuildId = new ConcurrentHashMap<>();

	/**
	 * 새 supervisor를 등록한다. 같은 길드에 이미 등록된 게 있으면(다른 채널로 이동하는
	 * 경우 등) 그 이전 supervisor를 먼저 정리해서 스레드가 새지 않게 한다.
	 */
	public void register(long guildId, VoiceConnectionSupervisor supervisor) {
		VoiceConnectionSupervisor previous = supervisorsByGuildId.put(guildId, supervisor);

		if (previous != null) {
			previous.shutdown();
		}
	}

	public void unregisterAndShutdown(long guildId) {
		VoiceConnectionSupervisor supervisor = supervisorsByGuildId.remove(guildId);

		if (supervisor != null) {
			supervisor.shutdown();
		}
	}
}
