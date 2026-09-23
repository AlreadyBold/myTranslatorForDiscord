package io.github.alreadybold.translator.translation;

import java.util.Optional;

import io.github.alreadybold.translator.i18n.Language;

/**
 * 텍스트를 번역하는 엔진의 공통 인터페이스.
 *
 * 지금은 Papago 하나뿐이지만, STT처럼 나중에 다른 엔진으로 바뀔 수 있어서 인터페이스로 감싼다.
 */
public interface TranslationClient {

	/**
	 * text를 sourceLanguage에서 targetLanguage로 번역한다.
	 * 실패하면(네트워크 오류, API 오류 등) 빈 Optional을 반환한다.
	 */
	Optional<String> translate(String text, Language sourceLanguage, Language targetLanguage);
}
