/**
 * STT(음성 인식) 엔진 연동을 담당하는 패키지.
 *
 * 엔진마다 별도 구현체를 두고(Azure = 영어/중국어/일본어, 이후 CLOVA = 한국어),
 * 공통 인터페이스 SpeechToTextClient로 감싼다. 이렇게 해두면 호출하는 쪽
 * (audio 패키지의 UserAudioReceiveHandler)은 "어느 엔진을 쓰는지" 몰라도 되고,
 * 엔진을 추가하거나 교체해도 호출부 코드가 바뀌지 않는다.
 */
package io.github.alreadybold.translator.stt;
