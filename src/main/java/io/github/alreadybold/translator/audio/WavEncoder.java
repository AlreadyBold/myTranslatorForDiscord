package io.github.alreadybold.translator.audio;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * 순수 PCM 데이터를 WAV 파일 포맷(RIFF 컨테이너)으로 감싼다.
 *
 * Azure Speech SDK는 순수 PCM을 그대로 받아주지만, CLOVA Speech REST API는
 * WAV 같은 파일 포맷을 요구한다. WAV는 44바이트짜리 헤더 하나만 앞에 붙이면
 * 되는 가장 단순한 포맷이라, 별도 라이브러리 없이 직접 만든다.
 */
public final class WavEncoder {

	private static final int HEADER_SIZE = 44;
	private static final int PCM_FORMAT_CODE = 1;
	private static final int FMT_CHUNK_SIZE = 16;

	private WavEncoder() {
		// 상태 없는 유틸리티 클래스라 인스턴스를 만들 이유가 없다.
	}

	public static byte[] wrap(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
		int byteRate = sampleRate * channels * bitsPerSample / 8;
		int blockAlign = channels * bitsPerSample / 8;

		ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);

		header.put("RIFF".getBytes(StandardCharsets.US_ASCII));
		header.putInt(36 + pcm.length);
		header.put("WAVE".getBytes(StandardCharsets.US_ASCII));

		header.put("fmt ".getBytes(StandardCharsets.US_ASCII));
		header.putInt(FMT_CHUNK_SIZE);
		header.putShort((short) PCM_FORMAT_CODE);
		header.putShort((short) channels);
		header.putInt(sampleRate);
		header.putInt(byteRate);
		header.putShort((short) blockAlign);
		header.putShort((short) bitsPerSample);

		header.put("data".getBytes(StandardCharsets.US_ASCII));
		header.putInt(pcm.length);

		ByteArrayOutputStream wav = new ByteArrayOutputStream(HEADER_SIZE + pcm.length);
		wav.writeBytes(header.array());
		wav.writeBytes(pcm);

		return wav.toByteArray();
	}
}
