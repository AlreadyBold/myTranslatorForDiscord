package io.github.alreadybold.translator.audio;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import io.github.alreadybold.translator.i18n.Language;
import io.github.alreadybold.translator.settings.UserLanguageRegistry;
import io.github.alreadybold.translator.settings.UserOutputLanguageRegistry;
import io.github.alreadybold.translator.stt.SpeechToTextClient;
import io.github.alreadybold.translator.translation.TranslationClient;

/**
 * 유저별로 분리된 음성 채널 오디오를 수신하고, 발화 단위로 묶어서 STT로 넘기는 핸들러.
 *
 * PCM 오디오는 20ms짜리 조각으로 쪼개져서 계속 들어오기 때문에, 그대로는 STT에 넘길 수 없다.
 * 유저별로 오디오를 이어붙이다가, 일정 시간 새 오디오가 안 들어오면 "한 문장이 끝났다"고
 * 보고 그때까지 쌓인 걸 하나의 덩어리로 넘긴다. 디스코드는 "말이 끝났다"는 신호를 따로 주지
 * 않고 그냥 패킷 전송을 멈출 뿐이라서, 무음 여부는 별도 스케줄러로 주기적으로 확인해야 한다.
 */
public class UserAudioReceiveHandler implements AudioReceiveHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserAudioReceiveHandler.class);

	// 이 시간(ms) 동안 새 오디오가 안 들어오면 발화가 끝난 것으로 간주한다.
	private static final long SILENCE_THRESHOLD_MILLIS = 800;
	// 무음 여부를 확인하는 주기. 오디오 콜백과 별개로 계속 폴링해야 한다.
	private static final long SILENCE_CHECK_INTERVAL_MILLIS = 200;

	private final UserLanguageRegistry languageRegistry;
	private final Map<Language, SpeechToTextClient> sttClientsByLanguage;
	private final UserOutputLanguageRegistry outputLanguageRegistry;
	private final TranslationClient translationClient;
	private final GuildMessageChannel outputChannel;

	// 정상적인 세션으로 인정하기 위한 최소 성공 패킷 수 (20ms짜리 조각 15개 = 300ms 분량).
	// 실제로 겪어보니 "완전히 죽은 세션"도 수백 개 중 우연히 패킷 1~2개는 성공하는 경우가
	// 있어서, 단순히 "한 번이라도 받았는가"만 보면 거의 안 되는 세션도 정상으로 오판한다.
	private static final int HEALTHY_PACKET_COUNT_THRESHOLD = 15;

	private final Map<Long, UserAudioBuffer> buffersByUserId = new ConcurrentHashMap<>();
	private final ScheduledExecutorService silenceChecker = Executors.newSingleThreadScheduledExecutor();
	// 이 핸들러가 살아있는 동안 성공적으로 받은 오디오 패킷 개수.
	// VoiceConnectionSupervisor가 "이 접속이 죽은 세션인지"를 판단하는 데 사용한다.
	private final AtomicInteger receivedPacketCount = new AtomicInteger(0);
	// STT 호출은 네트워크를 타는 느린 작업이라, 200ms마다 도는 무음 감지 스케줄러나
	// /leave 커맨드 처리 스레드(3초 ACK 제한)를 막지 않도록 별도 스레드에서 실행한다.
	private final ExecutorService recognitionExecutor = Executors.newCachedThreadPool();

	public UserAudioReceiveHandler(
			UserLanguageRegistry languageRegistry,
			Map<Language, SpeechToTextClient> sttClientsByLanguage,
			UserOutputLanguageRegistry outputLanguageRegistry,
			TranslationClient translationClient,
			GuildMessageChannel outputChannel) {
		this.languageRegistry = languageRegistry;
		this.sttClientsByLanguage = sttClientsByLanguage;
		this.outputLanguageRegistry = outputLanguageRegistry;
		// Papago 키가 없으면 null - 이 경우 STT 결과만 로그로 남기고 번역은 건너뛴다.
		this.translationClient = translationClient;
		this.outputChannel = outputChannel;

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
		receivedPacketCount.incrementAndGet();

		User user = userAudio.getUser();
		byte[] audioData = userAudio.getAudioData(1.0);

		buffersByUserId.computeIfAbsent(user.getIdLong(), id -> new UserAudioBuffer(user))
				.append(audioData);
	}

	/**
	 * 이 핸들러가 만들어진 이후 오디오를 "충분히 안정적으로" 받았는지.
	 *
	 * 단순히 "한 번이라도 받았는가"로는 부족하다 - 디스코드의 DAVE(E2EE) 복호화가
	 * 사실상 다 실패하는 죽은 세션에서도, 수백 개 패킷 중 우연히 1~2개는 성공하는
	 * 경우가 있어서 그 기준으로는 죽은 세션을 정상으로 오판하게 된다. 그래서 최소
	 * 개수(HEALTHY_PACKET_COUNT_THRESHOLD) 이상 받았을 때만 정상으로 판단한다.
	 */
	public boolean hasReceivedAudio() {
		return receivedPacketCount.get() >= HEALTHY_PACKET_COUNT_THRESHOLD;
	}

	/**
	 * 음성 채널 접속이 끊길 때 호출해서 백그라운드 스레드를 정리한다.
	 * 이걸 안 부르면 /leave 이후에도 이 핸들러의 스케줄러 스레드가 계속 살아있게 된다.
	 *
	 * 무음 기준 시간(800ms)이 지나기 전에 유저가 바로 나가버리면, 스케줄러만 멈추고
	 * 끝낼 경우 그 마지막 발화가 영영 flush 안 되고 유실된다. 그래서 스케줄러를 멈추기
	 * 전에 남아있는 모든 버퍼를 한 번 강제로 비워준다.
	 *
	 * recognitionExecutor는 shutdownNow()가 아니라 shutdown()으로 정리한다 - 방금
	 * flushAllBuffers()가 던져 넣은 마지막 STT 작업은 봇이 채널을 나간 뒤에도 끝까지
	 * 완료되게 두고, 새 작업만 더 이상 안 받도록 하기 위함이다.
	 */
	public void shutdown() {
		silenceChecker.shutdownNow();
		flushAllBuffers();
		recognitionExecutor.shutdown();
	}

	private void flushSilentBuffers() {
		// ScheduledExecutorService#scheduleAtFixedRate는 태스크가 예외를 던지면 그
		// 순간부터 이후 실행을 전부 조용히 취소해버린다 (로그 한 줄도 안 남기고).
		// 그러면 오디오는 계속 들어오는데 무음 감지 자체가 영원히 멈춰서, 원인을
		// 알아낼 방법이 없어진다. 그래서 여기서 반드시 잡아서 로그로 남긴다.
		try {
			long now = System.currentTimeMillis();

			buffersByUserId.forEach((userId, buffer) -> {
				byte[] flushed = buffer.flushIfSilent(now, SILENCE_THRESHOLD_MILLIS);
				submitForRecognition(buffer, flushed);
			});
		} catch (RuntimeException exception) {
			LOGGER.error("무음 감지 스케줄러에서 예외가 발생했습니다.", exception);
		}
	}

	private void flushAllBuffers() {
		buffersByUserId.forEach((userId, buffer) -> submitForRecognition(buffer, buffer.flushRemaining()));
	}

	private void submitForRecognition(UserAudioBuffer buffer, byte[] flushed) {
		if (flushed != null) {
			recognitionExecutor.submit(() -> {
				try {
					recognizeAndLog(buffer, flushed);
				} catch (RuntimeException exception) {
					// submit()으로 던진 작업은 결과(Future)를 아무도 확인하지 않으므로,
					// 여기서 안 잡으면 예외가 로그 한 줄 없이 완전히 사라진다.
					LOGGER.error("STT 처리 중 예외가 발생했습니다: {}", buffer.user.getName(), exception);
				}
			});
		}
	}

	private void recognizeAndLog(UserAudioBuffer buffer, byte[] flushed) {
		Language sourceLanguage = languageRegistry.get(buffer.user.getIdLong());
		SpeechToTextClient sttClient = sttClientsByLanguage.get(sourceLanguage);

		if (sttClient == null) {
			// 예: 한국어는 CLOVA 연동 전까지 담당 엔진이 없었다 (지금은 있음, 혹시
			// 나중에 지원 언어가 더 늘어나면 이 분기가 다시 의미를 갖게 된다).
			LOGGER.info(
					"발화 종료(STT 미지원 언어 {}): {} - {} bytes", sourceLanguage, buffer.user.getName(), flushed.length);
			return;
		}

		byte[] sttFormatAudio = PcmResampler.discordAudioToSttFormat(flushed);
		Optional<String> recognized = sttClient.recognize(sttFormatAudio, sourceLanguage);

		if (recognized.isEmpty()) {
			LOGGER.info("발화 종료(인식 결과 없음): {} - {} bytes", buffer.user.getName(), flushed.length);
			return;
		}

		String recognizedText = recognized.get();
		LOGGER.info("STT 인식 결과: {} ({}) -> {}", buffer.user.getName(), sourceLanguage, recognizedText);

		translateAndPost(buffer.user, recognizedText, sourceLanguage);
	}

	private void translateAndPost(User speaker, String recognizedText, Language sourceLanguage) {
		if (translationClient == null) {
			// Papago 키가 없으면(.env 미설정) 번역 없이 STT 결과만 로그로 남긴 상태로 끝낸다.
			return;
		}

		// 번역 target은 화자 본인이 /setoutputlang으로 미리 정해둔 값이다 (채널에 누가
		// 있는지와 무관 - 화자가 "내 말을 이 언어로 보여줘"를 직접 설정하는 구조).
		Language targetLanguage = outputLanguageRegistry.get(speaker.getIdLong());
		Optional<String> translated = translationClient.translate(recognizedText, sourceLanguage, targetLanguage);

		if (translated.isEmpty()) {
			LOGGER.warn("번역 실패: {} ({} -> {})", speaker.getName(), sourceLanguage, targetLanguage);
			return;
		}

		String translatedText = translated.get();
		LOGGER.info("번역 결과: {} ({} -> {}) -> {}", speaker.getName(), sourceLanguage, targetLanguage, translatedText);

		// 자막은 원문과 번역문을 같이 보여준다 - 번역문만 보여주면, 원문 언어를 아는 사람이
		// 번역이 이상할 때 뭐가 잘못 들렸는지 확인할 방법이 없다.
		//
		// 이름은 User.getName()(글로벌 유저네임) 대신 서버 별명(Member.getEffectiveName())을
		// 쓴다 - 채널에서 서로 부르는 이름과 일치시키기 위함. 멤버 캐시는 MemberCachePolicy.VOICE로
		// 음성 채널에 있는 동안 채워지므로, 지금 막 말한 화자는 항상 캐시에 있어야 정상이지만
		// 혹시 못 찾으면(캐시 미스) 유저네임으로 안전하게 대체한다.
		Member speakerMember = outputChannel.getGuild().getMember(speaker);
		String speakerName = speakerMember == null ? speaker.getName() : speakerMember.getEffectiveName();

		outputChannel.sendMessage("**" + speakerName + "**: " + recognizedText + "\n" + translatedText).queue();
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
