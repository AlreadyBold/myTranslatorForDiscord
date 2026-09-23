package io.github.alreadybold.translator.audio;

/**
 * 디스코드가 주는 PCM 오디오(48kHz, 16비트, 스테레오, 빅엔디안)를
 * STT 엔진이 요구하는 포맷(16kHz, 16비트, 모노, 리틀엔디안)으로 변환한다.
 *
 * 세 가지를 동시에 바꿔야 한다.
 * 1. 스테레오 -> 모노: 좌우 채널 값을 평균낸다.
 * 2. 48kHz -> 16kHz: 정확히는 저역통과 필터를 거친 리샘플링을 해야 하지만, 여기서는
 *    3개 샘플을 하나로 평균내는 단순한 방식을 쓴다. 평균을 내는 것만으로도 거친
 *    저역통과 효과가 생기고, 음성 인식 엔진은 이 정도 품질 손실에는 충분히 관대하다.
 * 3. 빅엔디안 -> 리틀엔디안: 놓치기 쉬운 부분인데, 바이트 순서가 뒤집히면 파형이
 *    완전히 망가져서 인식률이 0에 수렴한다.
 */
public final class PcmResampler {

	private static final int SOURCE_SAMPLE_RATE = 48000;
	private static final int TARGET_SAMPLE_RATE = 16000;
	private static final int DOWNSAMPLE_FACTOR = SOURCE_SAMPLE_RATE / TARGET_SAMPLE_RATE;

	// 스테레오 1프레임 = 좌(2바이트) + 우(2바이트)
	private static final int STEREO_FRAME_BYTES = 4;

	private PcmResampler() {
		// 상태 없는 유틸리티 클래스라 인스턴스를 만들 이유가 없다.
	}

	public static byte[] discordAudioToSttFormat(byte[] discordPcm) {
		short[] monoSamples = toMonoSamples(discordPcm);
		short[] downsampled = downsample(monoSamples);
		return toLittleEndianBytes(downsampled);
	}

	private static short[] toMonoSamples(byte[] discordPcm) {
		int frameCount = discordPcm.length / STEREO_FRAME_BYTES;
		short[] monoSamples = new short[frameCount];

		for (int i = 0; i < frameCount; i++) {
			int offset = i * STEREO_FRAME_BYTES;
			short left = bigEndianToShort(discordPcm, offset);
			short right = bigEndianToShort(discordPcm, offset + 2);
			monoSamples[i] = (short) ((left + right) / 2);
		}

		return monoSamples;
	}

	private static short[] downsample(short[] monoSamples) {
		int outputLength = monoSamples.length / DOWNSAMPLE_FACTOR;
		short[] downsampled = new short[outputLength];

		for (int i = 0; i < outputLength; i++) {
			int base = i * DOWNSAMPLE_FACTOR;
			int sum = 0;

			for (int offset = 0; offset < DOWNSAMPLE_FACTOR; offset++) {
				sum += monoSamples[base + offset];
			}

			downsampled[i] = (short) (sum / DOWNSAMPLE_FACTOR);
		}

		return downsampled;
	}

	private static short bigEndianToShort(byte[] data, int offset) {
		return (short) ((data[offset] << 8) | (data[offset + 1] & 0xFF));
	}

	private static byte[] toLittleEndianBytes(short[] samples) {
		byte[] bytes = new byte[samples.length * 2];

		for (int i = 0; i < samples.length; i++) {
			bytes[i * 2] = (byte) (samples[i] & 0xFF);
			bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
		}

		return bytes;
	}
}
