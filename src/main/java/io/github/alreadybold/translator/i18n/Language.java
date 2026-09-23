package io.github.alreadybold.translator.i18n;

/**
 * 이 프로젝트가 지원하는 4개 언어. STT 입력 언어와 번역 출력 언어 전부 이 안에서 결정된다.
 * (스펙상 일본어는 항상 번역 결과로만 나오는 게 아니라 소스로도 쓰이므로 4개 다 대등하다.)
 *
 * displayName은 유저에게 보여줄 이름이라, 각 언어를 그 언어 화자가 부르는 이름으로 적어둔다
 * (예: 중국어 화자에게는 "中文"으로 보이는 게 자연스러움).
 */
public enum Language {
	KO("한국어"),
	EN("English"),
	CN("中文"),
	JA("日本語");

	private final String displayName;

	Language(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
