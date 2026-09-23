/**
 * 유저별 설정 상태를 보관하는 패키지.
 *
 * i18n 패키지는 "보여줄 문구를 언어별로 관리"하는 게 책임이고, 이 패키지는 "각 유저가
 * 무엇을 선택했는지"를 들고 있는 게 책임이라 서로 분리했다. command 패키지(응답 언어 결정)와
 * audio 패키지(STT 엔진 라우팅) 양쪽에서 이 설정을 읽어가게 된다.
 */
package io.github.alreadybold.translator.settings;
