package io.github.alreadybold.translator.stt;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream;

import io.github.alreadybold.translator.i18n.Language;

/**
 * Azure Speech SDK를 사용하는 STT 클라이언트. 영어/중국어/일본어 입력을 담당한다
 * (한국어는 CLOVA Speech가 담당 - Phase 4 Step 3).
 *
 * 발화 하나가 끝날 때마다 새 오디오 스트림과 인식기를 만들어 recognizeOnceAsync()로
 * 한 번만 인식한다 (실시간 스트리밍 대신 발화 단위 전송을 택한 아키텍처 결정에 따름).
 */
public class AzureSpeechToTextClient implements SpeechToTextClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(AzureSpeechToTextClient.class);

	private static final int SAMPLE_RATE = 16000;
	private static final int BITS_PER_SAMPLE = 16;
	private static final int CHANNELS = 1;

	private final String subscriptionKey;
	private final String region;

	public AzureSpeechToTextClient(String subscriptionKey, String region) {
		this.subscriptionKey = subscriptionKey;
		this.region = region;
	}

	@Override
	public Optional<String> recognize(byte[] pcm16kHzMono, Language language) {
		// 발화마다(그리고 언어마다) 새로 SpeechConfig를 만든다. 이 클라이언트 하나를
		// 여러 유저(언어가 다를 수 있음)가 동시에 쓸 수 있는데, SpeechConfig를 공유해서
		// 재사용하면 "언어 설정을 바꾸는 시점"에 다른 스레드가 끼어드는 경쟁 상태가 생긴다.
		// SpeechConfig 생성 자체는 네트워크 호출이 없는 가벼운 객체라 매번 만들어도 괜찮다.
		SpeechConfig speechConfig = SpeechConfig.fromSubscription(subscriptionKey, region);
		speechConfig.setSpeechRecognitionLanguage(toAzureLocale(language));

		AudioStreamFormat audioFormat = AudioStreamFormat.getWaveFormatPCM(
				SAMPLE_RATE, (byte) BITS_PER_SAMPLE, (byte) CHANNELS);
		PushAudioInputStream pushStream = AudioInputStream.createPushStream(audioFormat);

		// AudioConfig는 "아직 열려있는" 스트림에 실시간으로 연결(binding)돼야 한다.
		// 데이터를 다 쓰고 스트림을 닫은 다음에 AudioConfig를 만들면, 이미 죽은 스트림에
		// 연결하려는 셈이라 SDK 내부 검증(Contracts.throwIfFail)에서 실패한다. 그래서
		// AudioConfig/Recognizer를 먼저 만들어서 연결해두고, 그 다음에 데이터를 쓴다.
		try (AudioConfig audioConfig = AudioConfig.fromStreamInput(pushStream);
				SpeechRecognizer recognizer = new SpeechRecognizer(speechConfig, audioConfig)) {
			pushStream.write(pcm16kHzMono);
			// close()로 "오디오 입력이 여기서 끝났다"는 걸 알려야 recognizeOnceAsync()가
			// 끝까지 기다리지 않고 결과를 확정할 수 있다.
			pushStream.close();

			Future<SpeechRecognitionResult> recognition = recognizer.recognizeOnceAsync();
			return toText(recognition.get());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.warn("Azure STT 호출이 중단됐습니다.", exception);
			return Optional.empty();
		} catch (ExecutionException exception) {
			LOGGER.warn("Azure STT 호출 중 오류가 발생했습니다.", exception);
			return Optional.empty();
		}
	}

	private Optional<String> toText(SpeechRecognitionResult result) {
		if (result.getReason() == ResultReason.RecognizedSpeech) {
			return Optional.of(result.getText());
		}

		if (result.getReason() == ResultReason.Canceled) {
			CancellationDetails cancellation = CancellationDetails.fromResult(result);
			LOGGER.warn("Azure STT 인식이 취소됐습니다: {}", cancellation.getErrorDetails());
		}

		// NoMatch(무음/잡음뿐인 구간 등)는 에러가 아니라 정상적으로 있을 수 있는 상황이라
		// 경고 없이 빈 값만 반환한다.
		return Optional.empty();
	}

	private String toAzureLocale(Language language) {
		switch (language) {
			case EN:
				return "en-US";
			case CN:
				return "zh-CN";
			case JA:
				return "ja-JP";
			default:
				// 한국어는 이 클라이언트로 라우팅되지 않지만(CLOVA 담당), 방어적으로 처리해둔다.
				return "ko-KR";
		}
	}
}
