package io.github.alreadybold.translator.audio;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 길드(서버)별로 현재 활성화된 VoiceConnectionSupervisor를 보관한다.
 *
 * /join이 새 연결을 만들 때 등록하고, /leave가 정리할 때 여기서 꺼내 shutdown한다.
 * 이렇게 한 곳에 모아두지 않으면 /leave 시점에 감시 스케줄러를 찾을 방법이 없어서
 * 계속 살아있게 되고, 유저가 이미 나간 뒤에도 "오디오 안 들어옴 -> 재접속" 판정이
 * 계속 돌게 된다.
 */
public class VoiceConnectionRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger(VoiceConnectionRegistry.class);

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

	/**
	 * 봇이 /leave를 거치지 않고 음성 채널에서 나가게 됐을 때(관리자가 강제로 내보냄,
	 * 디스코드 자체의 연결 종료 등) 호출한다.
	 *
	 * 이 경로로 들어온 경우에도 등록된 supervisor가 자동 재접속을 스스로 진행하는
	 * 중일 수 있다(VoiceConnectionSupervisor.isReconnecting()) - 그럴 땐 여기서
	 * 건드리면 재접속 로직을 깨버리므로 그냥 둔다. 재접속 중이 아닌데도 채널에서
	 * 나갔다면 진짜 외부 요인으로 끊긴 것이므로, 감시 스케줄러가 이미 나간 채널에
	 * 계속 재접속을 시도하며 스레드가 새지 않도록 정리한다.
	 */
	public void cleanupIfExternallyDisconnected(long guildId) {
		VoiceConnectionSupervisor supervisor = supervisorsByGuildId.get(guildId);

		if (supervisor == null || supervisor.isReconnecting()) {
			return;
		}

		unregisterAndShutdown(guildId);
		LOGGER.info("음성 채널에서 예기치 않게 나가져서 연결 감시를 정리했습니다: {}", guildId);
	}
}
