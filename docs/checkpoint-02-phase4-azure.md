# 체크포인트 02 — Phase 4 Azure STT 연동 (2026-09-23)

## 이 시점까지 만든 것

- 오디오 포맷 변환 (`PcmResampler`): 디스코드 PCM(48kHz·스테레오·빅엔디안) → STT용(16kHz·모노·리틀엔디안)
- `SpeechToTextClient` 공통 인터페이스 + `AzureSpeechToTextClient` 구현체 (영어/중국어/일본어)
- `/setlang` 커맨드 + `UserLanguageRegistry` (유저별 언어 선택 → 응답 언어 + STT 라우팅 양쪽에 사용)
- `VoiceConnectionSupervisor`/`VoiceConnectionRegistry`: 접속 후 오디오 수신 상태를 감시해서 자동 재접속
- **첫 성공**: 일본어·영어 둘 다 실제 Azure STT 결과를 받는 것까지 확인 완료

---

## 이번 체크포인트의 핵심 교훈: "재현 안 되는 버그"의 진짜 원인은 다른 곳에 있었다

몇 시간 동안 "디스코드 음성 수신이 간헐적으로 실패한다"는 증상(`OPUS_INVALID_PACKET`, `Failed to decrypt audio packet`)을 쫓았다. 네트워크 문제, DAVE 프로토콜의 알려진 한계, 라이브러리 버전 충돌 등을 의심하고 하나씩 검증했는데, **결국 진짜 원인은 전혀 다른 곳(우리가 짠 Azure 연동 코드)에 있었다.**

이 과정 자체가 값진 교훈이라 순서대로 남겨둔다.

### 1단계: "오디오 수신 자체가 안 된다"고 오인

증상: `/join` 후 말을 해도 `OPUS_INVALID_PACKET`, `OPUS_BUFFER_TOO_SMALL`, `Failed to decrypt audio packet` 같은 에러가 뜨고, 정상적으로 잡히는 적도 있고 안 잡히는 적도 있었다.

**의심하고 배제한 것들:**
- 네이티브 라이브러리 경로 충돌 (Azure SDK jar 안 파일 경로 직접 확인 → 안 겹침)
- JNA 버전 충돌 (의존성 트리 확인 → 버전 하나뿐)
- Azure SDK가 원인 (recognize()가 한 번도 실행된 적 없다는 걸 나중에 확인 → 애초에 실행도 안 된 코드가 원인일 수 없음)
- 네트워크 환경(강의실 와이파이 등) → 집에서도 재현됨
- 이중 접속으로 인한 DAVE 키 재협상 → 재접속 방지 로직을 넣었는데도 재현됨

이 배제 과정에서 발견해서 같이 고친 것들(부수 효과였지만 그 자체로 유효한 개선):
- **이중 접속 방지**: 이미 같은 채널에 있으면 재접속 시도 안 함 (`JoinCommandListener`)
- **핸들러 정리 누락 수정**: 채널 이동 시 이전 `UserAudioReceiveHandler`의 스레드가 안 죽고 새던 문제

### 2단계: "세션 자체가 죽은 것"으로 보고 자동 재접속 도입

패턴을 "세션 단위로 전부 되거나 전부 안 되거나"로 오판해서, `VoiceConnectionSupervisor`를 만들었다:
- 접속 후 일정 시간 안에 오디오가 "충분히" 안 들어오면 죽은 세션으로 보고 자동으로 재접속 (최대 4번)
- 처음엔 "한 번이라도 받으면 정상"으로 판단했는데, **거의 다 실패하는 세션에서도 패킷 1~2개는 우연히 성공하는 걸 실제로 목격**하고 기준을 "최소 15개(300ms) 이상"으로 강화했다

이 supervisor 자체는 유효한 개선이지만(패킷 손실이 실제로 있을 수 있으므로), **이것만으로는 문제가 해결되지 않았다** — 즉 진짜 원인이 따로 있다는 신호였다.

### 3단계: 진짜 원인 발견 — 조용히 삼켜지던 예외

`recognitionExecutor.submit(...)`으로 STT 처리를 비동기로 던져놓고 결과(`Future`)를 아무도 확인하지 않고 있었다. **`ScheduledExecutorService`의 주기 작업과 `ExecutorService.submit()`은 예외가 나면 로그 한 줄 없이 조용히 사라진다** — 특히 `scheduleAtFixedRate`는 예외가 나는 순간 그 이후 실행이 전부 취소되는데 어떤 경고도 없다.

`flushSilentBuffers()`와 `recognizeAndLog()`를 try-catch로 감싸서 로그를 남기게 하자마자 실제 예외가 드러났다:

```
java.lang.RuntimeException:
	at com.microsoft.cognitiveservices.speech.util.Contracts.throwIfFail(Unknown Source)
	at com.microsoft.cognitiveservices.speech.audio.AudioConfig.fromStreamInput(Unknown Source)
	at io.github.alreadybold.translator.stt.AzureSpeechToTextClient.recognize(...)
```

**원인**: `PushAudioInputStream`에 오디오를 쓰고 `close()`까지 한 다음에 `AudioConfig.fromStreamInput()`을 호출하고 있었다. Azure Speech SDK는 `AudioConfig`가 **아직 열려있는** 스트림에 실시간으로 연결(binding)되기를 기대하는데, 이미 닫아버린 스트림을 넘기니 SDK 내부 검증에서 실패했다.

**수정**: `AudioConfig`/`SpeechRecognizer`를 먼저 만들어서 스트림에 연결해두고, 그 다음에 데이터를 쓰고 닫는 순서로 변경.

```java
// Before (틀림)
pushStream.write(pcm16kHzMono);
pushStream.close();
try (AudioConfig audioConfig = AudioConfig.fromStreamInput(pushStream); ...) { ... }

// After (맞음)
try (AudioConfig audioConfig = AudioConfig.fromStreamInput(pushStream); ...) {
    pushStream.write(pcm16kHzMono);
    pushStream.close();
    ...
}
```

이 버그 때문에 **오디오가 정상적으로 들어온 세션에서도 STT는 매번 조용히 실패**하고 있었다. 즉 지금까지 겪은 "간헐적 실패"의 상당 부분은 디스코드/DAVE 쪽 문제가 아니라 이 코드 버그였을 가능성이 높다.

---

## 겪은 버그와 원인 (검색용)

| 증상 | 원인 | 해결 |
|---|---|---|
| STT 결과가 하나도 안 찍히고 로그도 조용하다 | `ScheduledExecutorService`/`ExecutorService.submit()`이 예외를 조용히 삼킴 | 두 곳 다 try-catch로 감싸서 `LOGGER.error`로 남김 |
| Azure STT 호출이 `RuntimeException`(메시지 없음)으로 실패 | `PushAudioInputStream`을 닫은 뒤에 `AudioConfig.fromStreamInput()` 호출 - SDK가 열려있는 스트림을 기대함 | `AudioConfig`/`SpeechRecognizer`를 먼저 만들고, 그 다음에 쓰고 닫는 순서로 변경 |
| `/join` 후 몇 초 안 말하면 봇이 나갔다 다시 들어온다 (거슬림) | 자동 재접속 감시 시간(6초)이 "그냥 아직 말 안 함"과 "세션 고장"을 구분 못 함 | 감시 시간을 20초로 늘려서 자연스러운 대화 시작 텀을 봐줌 |
| "정상 확인됨" 판정이 떴는데 실제로는 발화가 하나도 안 잡힘 | 거의 다 실패하는 세션에서도 패킷 1개는 우연히 성공할 수 있어서, "한 번이라도 성공" 기준이 너무 헐거움 | 최소 15개(300ms) 이상 성공해야 정상으로 판단하도록 강화 |

## 일반적인 교훈

**증상만 보고 원인을 단정하지 말 것.** "디스코드 음성 수신이 불안정하다"는 겉보기 증상 하나에, 실제로는 서로 다른 층위의 원인 여러 개가 섞여 있었다(진짜 있을 수 있는 패킷 손실 + 완전히 별개인 코드 버그). 비동기 작업에 예외 처리를 빼먹으면 "아무 일도 안 일어난 것처럼" 보이는 게 제일 위험하다 — `submit()`/`scheduleAtFixedRate()`를 쓸 때는 항상 내부에서 예외를 잡아 로그로 남기는 습관을 들일 것.

---

## 다음 단계

- Phase 4 Step 3: CLOVA Speech 연동 (한국어)
- Phase 5: Papago 번역 연동
