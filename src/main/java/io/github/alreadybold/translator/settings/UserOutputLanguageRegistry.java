package io.github.alreadybold.translator.settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.alreadybold.translator.i18n.Language;

/**
 * 유저별로 "자기 발화를 번역해서 보여줄 언어"를 기억하는 저장소.
 *
 * UserLanguageRegistry(화자가 말하는 언어 = 번역의 source)와 짝을 이루는 target 쪽 설정이다.
 * 둘 다 화자 본인이 정한다 - 예를 들어 "나는 한국어로 말하고(UserLanguageRegistry=KO),
 * 그걸 영어로 번역해서 보여줘(UserOutputLanguageRegistry=EN)"처럼, 화자가 자기 발화의
 * 번역 방향을 스스로 설정하는 구조다 (리스너 각자가 원하는 언어를 고르는 방식이 아님 -
 * 그러면 채널에 누가 있는지, 그 사람들이 뭘 원하는지까지 알아야 해서 훨씬 복잡해진다).
 *
 * 구조가 UserLanguageRegistry와 완전히 동일하다고 그 클래스를 그대로 재사용하지 않은
 * 이유는, 그 클래스는 이름/문서 전부 "화자가 말하는 언어" 전용으로 되어 있어서 다른
 * 의미로 재사용하면 헷갈리기 때문이다 - 두 벌 유지하는 비용보다 헷갈림을 피하는 이득이
 * 크다고 판단했다.
 *
 * 현재는 메모리에만 들고 있어서 봇을 재시작하면 초기화된다 (UserLanguageRegistry와 동일한 이유).
 */
public class UserOutputLanguageRegistry {

	// 아직 출력 언어를 선택하지 않은 유저에게 적용할 기본값.
	private static final Language DEFAULT_LANGUAGE = Language.KO;

	private final Map<Long, Language> languageByUserId = new ConcurrentHashMap<>();

	public void set(long userId, Language language) {
		languageByUserId.put(userId, language);
	}

	/**
	 * 해당 유저가 선택한, "자기 발화를 번역해줄" 목표 언어. 아직 선택한 적이 없으면
	 * 기본값(KO)을 반환한다 - 화자의 spoken language 기본값도 KO라서, 아무것도
	 * 설정 안 하면 source==target이 되어 번역 없이 원문 그대로 나가는 게 자연스러운
	 * 기본 동작이 된다 (PapagoTranslationClient가 이 경우를 감지해서 API 호출 자체를 생략함).
	 */
	public Language get(long userId) {
		return languageByUserId.getOrDefault(userId, DEFAULT_LANGUAGE);
	}
}
