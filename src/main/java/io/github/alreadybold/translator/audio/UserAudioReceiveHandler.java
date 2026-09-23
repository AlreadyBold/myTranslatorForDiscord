package io.github.alreadybold.translator.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;

/**
 * 유저별로 분리된 음성 채널 오디오를 수신하는 핸들러.
 *
 * 지금은 "실제로 오디오가 잘 들어오는지"만 검증하는 단계라, 받은 오디오를 로그로만 찍는다.
 * 다음 단계에서 이 데이터를 유저별로 버퍼링하고 무음 구간을 감지해서 STT로 넘길 예정이다.
 */
public class UserAudioReceiveHandler implements AudioReceiveHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserAudioReceiveHandler.class);

	@Override
	public boolean canReceiveUser() {
		// 유저별로 분리된 오디오가 필요하다 (여러 명이 합쳐진 오디오인 canReceiveCombined는 불필요).
		// STT를 유저마다 다른 언어/엔진으로 라우팅해야 하므로, 누가 말했는지 구분이 반드시 필요하다.
		return true;
	}

	@Override
	public void handleUserAudio(UserAudio userAudio) {
		// 유저가 말하는 동안 20ms 분량의 PCM 데이터(48kHz, 16비트, 스테레오)가 계속 들어온다.
		// 지금은 파이프라인이 실제로 동작하는지 검증하는 단계라 바이트 수만 로그로 확인한다.
		byte[] audioData = userAudio.getAudioData(1.0);
		LOGGER.info("오디오 수신: {} - {} bytes", userAudio.getUser().getName(), audioData.length);
	}
}
