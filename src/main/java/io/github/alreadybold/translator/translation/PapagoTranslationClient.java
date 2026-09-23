package io.github.alreadybold.translator.translation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.alreadybold.translator.i18n.Language;

/**
 * Papago Translation REST API(NCP)를 사용하는 번역 클라이언트.
 *
 * CLOVA Speech와 마찬가지로 SDK가 없는 순수 REST API라서, Java 표준 HttpClient로
 * 직접 호출하고 응답 JSON에서 필요한 필드만 Gson으로 꺼낸다.
 */
public class PapagoTranslationClient implements TranslationClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(PapagoTranslationClient.class);

	private static final String ENDPOINT = "https://papago.apigw.ntruss.com/nmt/v1/translation";
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final String clientId;
	private final String clientSecret;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public PapagoTranslationClient(String clientId, String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public Optional<String> translate(String text, Language sourceLanguage, Language targetLanguage) {
		if (sourceLanguage == targetLanguage) {
			// 화자 언어와 듣고 싶은 언어가 같으면 번역할 필요가 없다. 그대로 Papago에
			// 보내면 같은 언어끼리 번역을 시도해서 오류가 나거나 쓸데없이 과금만 된다.
			return Optional.of(text);
		}

		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("source", toPapagoLanguageCode(sourceLanguage));
		requestBody.addProperty("target", toPapagoLanguageCode(targetLanguage));
		requestBody.addProperty("text", text);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(ENDPOINT))
				.header("Content-Type", "application/json; charset=UTF-8")
				.header("X-NCP-APIGW-API-KEY-ID", clientId)
				.header("X-NCP-APIGW-API-KEY", clientSecret)
				.timeout(REQUEST_TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
				.build();

		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return toTranslatedText(response);
		} catch (IOException exception) {
			LOGGER.warn("Papago 번역 호출 중 네트워크 오류가 발생했습니다.", exception);
			return Optional.empty();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.warn("Papago 번역 호출이 중단됐습니다.", exception);
			return Optional.empty();
		}
	}

	private Optional<String> toTranslatedText(HttpResponse<String> response) {
		if (response.statusCode() != 200) {
			LOGGER.warn("Papago가 오류 응답을 반환했습니다 ({}): {}", response.statusCode(), response.body());
			return Optional.empty();
		}

		JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
		String translatedText = body.getAsJsonObject("message")
				.getAsJsonObject("result")
				.get("translatedText")
				.getAsString();

		return Optional.of(translatedText);
	}

	private String toPapagoLanguageCode(Language language) {
		switch (language) {
			case EN:
				return "en";
			case CN:
				return "zh-CN";
			case JA:
				return "ja";
			default:
				return "ko";
		}
	}
}
