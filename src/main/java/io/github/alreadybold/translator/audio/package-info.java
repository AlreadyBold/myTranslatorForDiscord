/**
 * 음성 채널에서 캡처한 오디오를 처리하는 패키지.
 *
 * 유저별로 분리된 PCM 오디오를 수신(AudioReceiveHandler)하고,
 * 이후 STT로 넘기기 전 버퍼링/무음 구간 감지 등을 이 패키지에서 담당한다.
 * command 패키지(슬래시 커맨드 처리)와 책임을 분리하기 위해 별도 패키지로 뺐다.
 */
package io.github.alreadybold.translator.audio;
