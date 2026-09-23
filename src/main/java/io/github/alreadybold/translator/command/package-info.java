/**
 * 디스코드 슬래시 커맨드(예: /join)에 대한 이벤트 리스너들을 모아두는 패키지.
 *
 * 상위 패키지(io.github.alreadybold.translator)의 Main 클래스는 "봇을 어떻게 켤지"만
 * 책임지고, 커맨드별 실제 동작(음성 채널 접속, 이후 추가될 leave/setlang 등)은
 * 전부 이 패키지 아래에 모아서 부팅 로직과 기능 로직의 책임을 분리한다.
 */
package io.github.alreadybold.translator.command;
