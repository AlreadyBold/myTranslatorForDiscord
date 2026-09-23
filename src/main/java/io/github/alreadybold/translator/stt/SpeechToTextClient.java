package io.github.alreadybold.translator.stt;

import java.util.Optional;

import io.github.alreadybold.translator.i18n.Language;

/**
 * 음성을 텍스트로 바꾸는 STT 엔진의 공통 인터페이스.
 *
 * 유저가 선택한 언어에 따라 어떤 구현체를 쓸지 라우팅되며(한국어=CLOVA, 그 외=Azure),
 * 호출하는 쪽은 이 인터페이스만 알면 된다.
 */
public interface SpeechToTextClient {

	/**
	 * 완성된 발화 하나를 인식해서 텍스트로 반환한다.
	 *
	 * 입력은 16kHz, 16비트, 모노, 리틀엔디안 PCM이어야 한다
	 * (디스코드 원본 포맷에서 PcmResampler로 변환한 결과).
	 * language는 "이 오디오가 무슨 언어인지"를 엔진에 알려주는 값으로, 유저가 /setlang으로
	 * 미리 선택해둔 언어다. 인식된 말이 없으면(무음, 잡음뿐인 구간 등) 빈 Optional을 반환한다.
	 */
	Optional<String> recognize(byte[] pcm16kHzMono, Language language);
}
