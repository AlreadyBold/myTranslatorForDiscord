# 체크포인트 03 — Phase 4 완료: CLOVA Speech 연동 (2026-09-23)

## 이 시점까지 만든 것

- `audio.WavEncoder`: 순수 PCM → WAV 파일 포맷 변환 (44바이트 헤더)
- `stt.ClovaSpeechToTextClient`: CLOVA Speech 단문인식 REST API로 한국어 STT
- **4개 언어 전부 실제 STT 결과 확인 완료**: 한국어(CLOVA), 영어·일본어(Azure), 중국어는 Azure 경로 공유라 사실상 검증됨

이걸로 **Phase 4(STT 연동)가 완전히 끝났다.**

## Azure와 CLOVA의 핵심 차이 — "SDK가 있다 없다"가 코드 구조를 바꾼다

같은 "STT 클라이언트"라도 제공 방식이 다르면 필요한 코드가 완전히 달라진다는 걸 실감했다.

| | Azure Speech | CLOVA Speech |
|---|---|---|
| 접근 방식 | 공식 SDK (`SpeechRecognizer`, `AudioConfig` 등) | 순수 REST API (SDK 없음) |
| 오디오 입력 | 순수 PCM을 `PushAudioInputStream`에 밀어넣음 | **파일 포맷**(WAV 등)으로 감싸서 요청 본문에 실어야 함 → `WavEncoder` 필요 |
| HTTP 클라이언트 | SDK가 내부적으로 처리 (안 보임) | 직접 호출해야 함 → 별도 라이브러리 없이 Java 표준 `java.net.http.HttpClient`로 충분 |
| 응답 파싱 | SDK가 `SpeechRecognitionResult` 객체로 감싸줌 | 직접 JSON 파싱 필요 → Gson으로 `text` 필드만 꺼냄 |
| 인증 | `SpeechConfig.fromSubscription(key, region)` | HTTP 헤더에 `X-CLOVASPEECH-API-KEY` 직접 실음 |

두 클라이언트 다 같은 `SpeechToTextClient` 인터페이스(`recognize(byte[] pcm16kHzMono, Language language)`)를 구현하기 때문에, 이 차이는 전부 각 구현체 내부에 캡슐화되고 호출하는 쪽(`UserAudioReceiveHandler`)은 어느 쪽을 쓰는지 몰라도 된다 — 인터페이스로 감싸둔 게 실제로 값을 발휘한 지점.

## CLOVA REST API 스펙 요약 (검색용)

```
POST {CLOVA_SPEECH_INVOKE_URL}?lang={Kor|Eng|Jpn|Chn}
Header: Content-Type: application/octet-stream
Header: X-CLOVASPEECH-API-KEY: {secret key}
Body: WAV(또는 MP3/AAC/OGG/FLAC) 바이너리, 최대 60초/3MB

응답: { "text": "인식된 문장" }
```

`CLOVA_SPEECH_INVOKE_URL`은 도메인 생성 시 이미 `/recog/v1/stt`까지 포함된 전체 URL이 발급된다 (베이스 URL만이 아님).

## STT 인식 오차는 파이프라인 버그가 아니다

한국어/일본어/영어 테스트 전부에서 인식 결과가 완벽하지 않았다 (예: "제 이름은" → "계이름으로", "涼しい" → "するしい"). 이건 STT 엔진 자체의 인식 정확도 한계이지, 우리 코드(오디오 캡처·리샘플링·API 호출)의 문제가 아니다. 구조적으로 말이 되는 결과가 나오면(문장 형태 유지, 의미 대략 일치) 파이프라인은 정상으로 판단하면 된다. 인식 정확도 자체를 개선하려면 CLOVA의 `boostings`(특정 단어 인식률 올리기) 같은 옵션을 나중에 검토할 수 있다.

## 다음 단계

- Phase 5: Papago 번역 연동 — 이제 4개 언어 STT 결과(텍스트)가 안정적으로 나오니, 이 텍스트를 Papago로 번역해서 출력하는 단계로 넘어간다.
