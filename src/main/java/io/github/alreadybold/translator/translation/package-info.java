/**
 * 번역 엔진 연동을 담당하는 패키지.
 *
 * stt 패키지와 같은 이유로 공통 인터페이스(TranslationClient)를 두고 구현체(현재는
 * Papago 하나)를 감싼다 - 나중에 번역 엔진을 바꾸거나 추가해도 호출하는 쪽 코드가
 * 안 바뀌게 하기 위함이다.
 */
package io.github.alreadybold.translator.translation;
