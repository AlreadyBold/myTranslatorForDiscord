package io.github.alreadybold.translator.settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.alreadybold.translator.i18n.Language;

/**
 * 유저별로 "자기가 말하는 언어"를 기억하는 저장소.
 *
 * 이 값이 두 곳에서 쓰인다.
 * 1. 봇이 그 유저에게 응답할 때 어떤 언어로 보여줄지
 * 2. (Phase 4 이후) 그 유저의 음성을 어느 STT 엔진으로 보낼지 (한국어=CLOVA, 그 외=Azure)
 *
 * 실시간 언어 자동 감지를 하지 않고 유저가 직접 선택하게 만든 이유는, 감지 실패 시 엉뚱한
 * 엔진으로 보내 인식이 통째로 망가지는 것보다, 한 번 선택해두는 쪽이 훨씬 안정적이기 때문이다.
 *
 * 현재는 메모리에만 들고 있어서 봇을 재시작하면 초기화된다. 지금 단계에서 DB를 붙이는 건
 * 과하다고 보고 미뤘고, 재시작 후에도 유지해야 할 필요가 생기면 그때 영속화하면 된다.
 */
public class UserLanguageRegistry {

	// 아직 언어를 선택하지 않은 유저에게 적용할 기본값.
	private static final Language DEFAULT_LANGUAGE = Language.KO;

	// 오디오 수신 스레드와 커맨드 처리 스레드가 동시에 접근할 수 있어서 ConcurrentHashMap을 쓴다.
	private final Map<Long, Language> languageByUserId = new ConcurrentHashMap<>();

	public void set(long userId, Language language) {
		languageByUserId.put(userId, language);
	}

	/**
	 * 해당 유저가 선택한 언어. 아직 선택한 적이 없으면 기본값을 반환한다.
	 */
	public Language get(long userId) {
		return languageByUserId.getOrDefault(userId, DEFAULT_LANGUAGE);
	}

	/**
	 * 번역을 끈다(/stoptranslate). 이후 get()은 기본값을 돌려주고, hasExplicitLanguage()는
	 * false가 되어 이 유저의 음성은 다시 처리되지 않는다.
	 */
	public void remove(long userId) {
		languageByUserId.remove(userId);
	}

	/**
	 * 해당 유저의 번역이 켜져 있는지 (/setlang으로 켜고 /stoptranslate로 끈다).
	 *
	 * 음성 채널에는 이 봇을 쓸 생각이 없는 사람도 같이 있을 수 있는데, 그런 사람들
	 * 말까지 전부 STT·번역으로 넘기면 안 된다(원치 않는 사람 발화까지 처리되는 문제 +
	 * 쓸데없는 API 비용). 그래서 말할 언어가 명시적으로 설정돼 있는지를 그 자체로 켜짐
	 * 여부로 쓴다 - 별도 on 커맨드 없이도, 언어를 설정했다는 것 자체가 번역 대상이
	 * 되겠다는 의사표시다. UserAudioReceiveHandler가 오디오를 버퍼링하기 전에 이
	 * 메서드로 걸러낸다.
	 */
	public boolean hasExplicitLanguage(long userId) {
		return languageByUserId.containsKey(userId);
	}
}
