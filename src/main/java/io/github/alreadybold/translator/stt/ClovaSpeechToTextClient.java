package io.github.alreadybold.translator.stt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.alreadybold.translator.audio.WavEncoder;
import io.github.alreadybold.translator.i18n.Language;

/**
 * CLOVA Speech(단문인식) REST API를 사용하는 STT 클라이언트. 한국어 입력을 담당한다
 * (영어/중국어/일본어는 Azure Speech가 담당).
 *
 * Azure와 달리 SDK가 없는 순수 REST API라서, 발화 하나를 WAV로 감싸서 그대로
 * POST 요청 본문에 실어 보내고, 응답 JSON의 text 필드를 읽는다.
 */
public class ClovaSpeechToTextClient implements SpeechToTextClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(ClovaSpeechToTextClient.class);

	private static final int SAMPLE_RATE = 16000;
	private static final int BITS_PER_SAMPLE = 16;
	private static final int CHANNELS = 1;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

	private final String invokeUrl;
	private final String secretKey;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public ClovaSpeechToTextClient(String invokeUrl, String secretKey) {
		this.invokeUrl = invokeUrl;
		this.secretKey = secretKey;
	}

	@Override
	public Optional<String> recognize(byte[] pcm16kHzMono, Language language) {
		byte[] wavAudio = WavEncoder.wrap(pcm16kHzMono, SAMPLE_RATE, CHANNELS, BITS_PER_SAMPLE);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(invokeUrl + "?lang=" + toClovaLanguageCode(language)))
				.header("Content-Type", "application/octet-stream")
				.header("X-CLOVASPEECH-API-KEY", secretKey)
				.timeout(REQUEST_TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofByteArray(wavAudio))
				.build();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return toText(response);
		} catch (IOException exception) {
			LOGGER.warn("CLOVA Speech 호출 중 네트워크 오류가 발생했습니다.", exception);
			return Optional.empty();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.warn("CLOVA Speech 호출이 중단됐습니다.", exception);
			return Optional.empty();
		}
	}

	private Optional<String> toText(HttpResponse<String> response) {
		if (response.statusCode() != 200) {
			LOGGER.warn("CLOVA Speech가 오류 응답을 반환했습니다 ({}): {}", response.statusCode(), response.body());
			return Optional.empty();
		}

		JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
		String text = body.has("text") ? body.get("text").getAsString() : "";

		return text.isBlank() ? Optional.empty() : Optional.of(text);
	}

	private String toClovaLanguageCode(Language language) {
		switch (language) {
			case EN:
				return "Eng";
			case CN:
				return "Chn";
			case JA:
				return "Jpn";
			default:
				// 영어/중국어/일본어는 이 클라이언트로 라우팅되지 않지만(Azure 담당),
				// 방어적으로 처리해둔다.
				return "Kor";
		}
	}
}
