package io.github.alreadybold.translator.audio;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.User;

/**
 * 유저별로 분리된 음성 채널 오디오를 수신하고, 발화 단위로 묶어내는 핸들러.
 *
 * PCM 오디오는 20ms짜리 조각으로 쪼개져서 계속 들어오기 때문에, 그대로는 STT에 넘길 수 없다.
 * 유저별로 오디오를 이어붙이다가, 일정 시간 새 오디오가 안 들어오면 "한 문장이 끝났다"고
 * 보고 그때까지 쌓인 걸 하나의 덩어리로 넘긴다. 디스코드는 "말이 끝났다"는 신호를 따로 주지
 * 않고 그냥 패킷 전송을 멈출 뿐이라서, 무음 여부는 별도 스케줄러로 주기적으로 확인해야 한다.
 * 지금 단계에서는 실제 STT 연동 전이라 로그로만 확인한다.
 */
public class UserAudioReceiveHandler implements AudioReceiveHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserAudioReceiveHandler.class);

	// 이 시간(ms) 동안 새 오디오가 안 들어오면 발화가 끝난 것으로 간주한다.
	private static final long SILENCE_THRESHOLD_MILLIS = 800;
	// 무음 여부를 확인하는 주기. 오디오 콜백과 별개로 계속 폴링해야 한다.
	private static final long SILENCE_CHECK_INTERVAL_MILLIS = 200;

	private final Map<Long, UserAudioBuffer> buffersByUserId = new ConcurrentHashMap<>();
	private final ScheduledExecutorService silenceChecker = Executors.newSingleThreadScheduledExecutor();

	public UserAudioReceiveHandler() {
		silenceChecker.scheduleAtFixedRate(
				this::flushSilentBuffers,
				SILENCE_CHECK_INTERVAL_MILLIS,
				SILENCE_CHECK_INTERVAL_MILLIS,
				TimeUnit.MILLISECONDS);
	}

	@Override
	public boolean canReceiveUser() {
		// 유저별로 분리된 오디오가 필요하다 (여러 명이 합쳐진 오디오인 canReceiveCombined는 불필요).
		// STT를 유저마다 다른 언어/엔진으로 라우팅해야 하므로, 누가 말했는지 구분이 반드시 필요하다.
		return true;
	}

	@Override
	public void handleUserAudio(UserAudio userAudio) {
		User user = userAudio.getUser();
		byte[] audioData = userAudio.getAudioData(1.0);

		buffersByUserId.computeIfAbsent(user.getIdLong(), id -> new UserAudioBuffer(user))
				.append(audioData);
	}

	/**
	 * 음성 채널 접속이 끊길 때 호출해서 백그라운드 스레드를 정리한다.
	 * 이걸 안 부르면 /leave 이후에도 이 핸들러의 스케줄러 스레드가 계속 살아있게 된다.
	 *
	 * 무음 기준 시간(800ms)이 지나기 전에 유저가 바로 나가버리면, 스케줄러만 멈추고
	 * 끝낼 경우 그 마지막 발화가 영영 flush 안 되고 유실된다. 그래서 스케줄러를 멈추기
	 * 전에 남아있는 모든 버퍼를 한 번 강제로 비워준다.
	 */
	public void shutdown() {
		silenceChecker.shutdownNow();
		flushAllBuffers();
	}

	private void flushSilentBuffers() {
		long now = System.currentTimeMillis();

		buffersByUserId.forEach((userId, buffer) -> {
			byte[] flushed = buffer.flushIfSilent(now, SILENCE_THRESHOLD_MILLIS);
			logFlushed(buffer, flushed);
		});
	}

	private void flushAllBuffers() {
		buffersByUserId.forEach((userId, buffer) -> logFlushed(buffer, buffer.flushRemaining()));
	}

	private void logFlushed(UserAudioBuffer buffer, byte[] flushed) {
		if (flushed != null) {
			// 다음 단계(STT 연동)에서는 여기서 flushed를 STT 엔진으로 넘기게 된다.
			LOGGER.info("발화 종료: {} - {} bytes", buffer.user.getName(), flushed.length);
		}
	}

	/**
	 * 유저 한 명의 오디오를 계속 이어붙이는 버퍼.
	 *
	 * append()는 오디오 수신 스레드에서, flushIfSilent()는 스케줄러 스레드에서 호출되어
	 * 서로 다른 스레드가 같은 데이터를 넘나들기 때문에 synchronized로 보호한다.
	 */
	private static final class UserAudioBuffer {

		private final User user;
		private final ByteArrayOutputStream data = new ByteArrayOutputStream();
		private long lastReceivedAtMillis = System.currentTimeMillis();

		private UserAudioBuffer(User user) {
			this.user = user;
		}

		private synchronized void append(byte[] audioData) {
			data.writeBytes(audioData);
			lastReceivedAtMillis = System.currentTimeMillis();
		}

		/**
		 * 무음 기준 시간을 넘겼으면 지금까지 쌓인 오디오를 꺼내서 비우고 반환한다.
		 * 아직 무음 기준을 안 넘겼으면 null.
		 */
		private synchronized byte[] flushIfSilent(long now, long thresholdMillis) {
			if (now - lastReceivedAtMillis < thresholdMillis) {
				return null;
			}

			return flushRemaining();
		}

		/**
		 * 무음 기준 시간과 상관없이, 지금까지 쌓인 오디오를 꺼내서 비우고 반환한다.
		 * 이미 비어있으면 null. (연결 종료 시 강제로 마지막 발화를 flush할 때 사용)
		 */
		private synchronized byte[] flushRemaining() {
			if (data.size() == 0) {
				return null;
			}

			byte[] flushed = data.toByteArray();
			data.reset();
			return flushed;
		}
	}
}
