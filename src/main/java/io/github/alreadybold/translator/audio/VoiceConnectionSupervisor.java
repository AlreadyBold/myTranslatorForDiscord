package io.github.alreadybold.translator.audio;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.managers.AudioManager;

import io.github.alreadybold.translator.i18n.Language;
import io.github.alreadybold.translator.settings.UserLanguageRegistry;
import io.github.alreadybold.translator.settings.UserOutputLanguageRegistry;
import io.github.alreadybold.translator.stt.SpeechToTextClient;
import io.github.alreadybold.translator.translation.TranslationClient;

/**
 * 음성 연결이 "죽은 세션"으로 뽑히는 경우를 감지해서 자동으로 재접속을 시도한다.
 *
 * 디스코드가 DAVE(E2EE) 암호화를 강제한 이후, 제3자 라이브러리의 음성 수신 구현체가
 * 간헐적으로 복호화에 실패하는 문제가 있다 - 연결 자체는 성공하지만 오디오가 통째로
 * 하나도 안 들어오는 상태가 된다. 이 실패는 세션 단위로 전부 되거나 전부 안 되는
 * 식이라(한 세션 안에서 부분적으로 끊기는 게 아니라), 연결을 새로 맺으면 다시 정상
 * 확률을 얻을 수 있다. 그래서 접속 직후 일정 시간 안에 오디오가 한 번도 안 들어오면
 * 죽은 세션으로 보고 자동으로 다시 접속한다.
 */
public class VoiceConnectionSupervisor {

	private static final Logger LOGGER = LoggerFactory.getLogger(VoiceConnectionSupervisor.class);

	// 이 시간 안에 오디오가 충분히 안 들어오면 세션이 죽은 것으로 판단한다.
	// "죽어서 안 들어오는 것"과 "아직 말을 안 해서 안 들어오는 것"을 코드로 구분할 방법이
	// 없어서(디스코드가 "말하려는 중" 신호를 안 줌), 너무 짧게 잡으면 그냥 잠깐 조용한
	// 것뿐인데 죽은 세션으로 오판해서 불필요하게 재접속(눈에 보이는 나갔다 들어오기)을
	// 하게 된다. 사람이 자연스럽게 대화를 시작하는 데 걸리는 시간을 감안해서 넉넉하게 잡는다.
	private static final long HEALTH_CHECK_DELAY_SECONDS = 20;
	// 재접속 사이에 살짝 텀을 둔다 - 끊자마자 바로 다시 열면 디스코드 쪽에서 이전
	// 연결 종료 처리가 끝나기 전에 새 연결 요청이 겹칠 수 있다.
	private static final long RECONNECT_DELAY_SECONDS = 1;
	// 무한 재시도를 막기 위한 최대 시도 횟수.
	private static final int MAX_ATTEMPTS = 4;

	private final AudioManager audioManager;
	private final AudioChannelUnion voiceChannel;
	private final UserLanguageRegistry languageRegistry;
	private final Map<Language, SpeechToTextClient> sttClientsByLanguage;
	private final UserOutputLanguageRegistry outputLanguageRegistry;
	private final TranslationClient translationClient;
	private final ScheduledExecutorService healthChecker = Executors.newSingleThreadScheduledExecutor();

	private volatile UserAudioReceiveHandler currentHandler;
	// 자동 재접속(checkHealth)이 스스로 연결을 끊었다가 다시 여는 중인지 표시한다.
	// BotVoiceDisconnectListener가 "봇이 음성 채널에서 나갔다"는 이벤트를 받았을 때,
	// 이게 우리 자신이 재접속하려고 일부러 끊은 것인지, 아니면 관리자가 강제로 내보내는 등
	// 외부 요인으로 끊긴 것인지 구분하는 데 쓴다 - 전자라면 여기서 건드리면 안 되고
	// (재접속 로직이 이미 처리 중), 후자라면 죽은 채로 남은 supervisor를 정리해야 한다.
	private volatile boolean reconnecting;

	public VoiceConnectionSupervisor(
			AudioManager audioManager,
			AudioChannelUnion voiceChannel,
			UserLanguageRegistry languageRegistry,
			Map<Language, SpeechToTextClient> sttClientsByLanguage,
			UserOutputLanguageRegistry outputLanguageRegistry,
			TranslationClient translationClient) {
		this.audioManager = audioManager;
		this.voiceChannel = voiceChannel;
		this.languageRegistry = languageRegistry;
		this.sttClientsByLanguage = sttClientsByLanguage;
		this.outputLanguageRegistry = outputLanguageRegistry;
		this.translationClient = translationClient;
	}

	/**
	 * 최초 접속을 시도한다. 권한 부족(InsufficientPermissionException)은 유저에게
	 * 바로 안내해야 하는 상황이라 여기서 잡지 않고 호출한 쪽으로 그대로 던진다.
	 */
	public void connect() {
		connectAttempt(1);
	}

	private void connectAttempt(int attemptNumber) {
		reconnecting = false;

		// 음성 채널의 자막은 그 채널에 딸린 텍스트 채팅(voiceChannel 자신이 GuildMessageChannel이기도
		// 함)에 올린다 - 통화 중인 사람들이 보고 있을 확률이 가장 높은 위치라 별도 채널 설정 없이
		// 바로 쓸 수 있다.
		UserAudioReceiveHandler handler = new UserAudioReceiveHandler(
				languageRegistry,
				sttClientsByLanguage,
				outputLanguageRegistry,
				translationClient,
				voiceChannel.asGuildMessageChannel());
		currentHandler = handler;
		audioManager.setReceivingHandler(handler);

		try {
			audioManager.openAudioConnection(voiceChannel);
		} catch (InsufficientPermissionException exception) {
			if (attemptNumber == 1) {
				throw exception;
			}

			// 재시도 도중 권한이 바뀌는 건 흔치 않은 경우라 로그만 남기고 포기한다.
			LOGGER.warn("재접속 시도 중 권한 문제로 실패했습니다: {}", voiceChannel.getName());
			return;
		}

		LOGGER.info("음성 연결 시도 {}/{}: {}", attemptNumber, MAX_ATTEMPTS, voiceChannel.getName());

		healthChecker.schedule(
				() -> checkHealth(handler, attemptNumber), HEALTH_CHECK_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	private void checkHealth(UserAudioReceiveHandler handler, int attemptNumber) {
		if (handler.hasReceivedAudio()) {
			LOGGER.info("음성 연결 정상 확인됨: {}", voiceChannel.getName());
			return;
		}

		if (attemptNumber >= MAX_ATTEMPTS) {
			LOGGER.warn("음성 연결이 계속 불안정해서 자동 재시도를 포기합니다: {}", voiceChannel.getName());
			return;
		}

		LOGGER.warn(
				"음성 연결에서 오디오가 전혀 안 들어와 재접속을 시도합니다 ({}/{}): {}",
				attemptNumber, MAX_ATTEMPTS, voiceChannel.getName());
		reconnecting = true;
		handler.shutdown();
		audioManager.closeAudioConnection();

		healthChecker.schedule(() -> connectAttempt(attemptNumber + 1), RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * 지금 이 연결 끊김이 자동 재접속 로직 스스로가 일으킨 것인지.
	 *
	 * BotVoiceDisconnectListener가 이 값을 확인해서, true면 재접속이 알아서 처리할
	 * 상황이니 아무것도 안 하고, false인데 봇이 채널에서 나갔다면 외부 요인(관리자가
	 * 강제로 내보냄 등)으로 판단해 supervisor를 정리한다.
	 */
	public boolean isReconnecting() {
		return reconnecting;
	}

	/**
	 * 이 supervisor가 관리하던 연결이 끝날 때(/leave, 다른 채널로 이동 등) 호출해서
	 * 감시 스케줄러와 현재 핸들러의 백그라운드 스레드를 정리한다.
	 *
	 * 실제 디스코드 음성 연결을 끊는 것(closeAudioConnection)은 이 메서드의 책임이
	 * 아니다 - 호출하는 쪽(LeaveCommandListener 등)이 그 시점을 결정한다.
	 */
	public void shutdown() {
		healthChecker.shutdownNow();

		UserAudioReceiveHandler handler = currentHandler;

		if (handler != null) {
			handler.shutdown();
		}
	}
}
