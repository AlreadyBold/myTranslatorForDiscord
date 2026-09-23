# 체크포인트 05 — Phase 7 완료: 에러 처리 마무리 + API 비용 절감 (2026-09-23)

## 이 시점까지 만든 것

- `audio.BotVoiceDisconnectListener`: 봇이 `/leave`를 거치지 않고 음성 채널에서 나가는 경우를 감지해서 감시 스케줄러 정리
- `VoiceConnectionSupervisor`의 `reconnecting` 플래그: 자동 재접속 스스로의 연결 끊김과, 외부 요인으로 인한 연결 끊김을 구분
- `JoinCommandListener`: `InsufficientPermissionException` 외의 예상 못한 예외도 사용자에게 안내
- `UserAudioReceiveHandler`: 400ms 미만 짧은 발화는 STT 호출 자체를 생략, 번역 결과를 (원문 언어·번역 언어·원문 텍스트) 키로 캐싱

이걸로 **Phase 7(에러 처리 마무리)이 끝났다.** 원래 로드맵엔 없었지만 실사용을 시작하면서 바로 급해진 API 비용 절감도 같은 타이밍에 처리했다.

## "우리가 끊은 연결"과 "남이 끊은 연결"을 구분해야 했던 이유

`/leave`를 거치지 않고 봇이 음성 채널에서 나가는 경우(서버 관리자가 강제로 연결을 끊는 등)를 처리하려고 `GuildVoiceUpdateEvent`를 감지하는 리스너를 추가하려는데, 생각보다 까다로운 함정이 있었다.

`VoiceConnectionSupervisor`는 이미 스스로 연결을 끊었다 다시 여는 경우가 있다 - 오디오가 안 들어오는 죽은 세션을 감지하면 `handler.shutdown()` + `audioManager.closeAudioConnection()`을 호출하고 1초 뒤 재접속을 시도하는 자동 재접속 로직이다(`docs/checkpoint-02-phase4-azure.md` 참고). 이 재접속도 "봇이 음성 채널에서 나가는" 이벤트를 그대로 발생시킨다.

만약 새 리스너가 "봇이 나가는 이벤트가 오면 무조건 supervisor를 정리"하도록 구현했다면, 자동 재접속이 스스로 연결을 끊는 그 순간에 리스너가 끼어들어서 재접속 중인 supervisor를 지워버렸을 것이다 - 재접속 로직 자체가 전혀 동작하지 않게 되는 회귀 버그다. 겉보기엔 "봇이 나감을 감지해서 정리한다"는 멀쩡한 기능이 다른 기존 기능을 조용히 망가뜨리는 전형적인 케이스.

해결은 `VoiceConnectionSupervisor`에 `reconnecting` 플래그를 추가하는 것. 재접속을 위해 스스로 연결을 끊기 직전에 `true`로 세팅하고, `connectAttempt()`가 다시 호출되는 시점(재접속 성공/재시도 시작)에 `false`로 되돌린다. `BotVoiceDisconnectListener`가 나가는 이벤트를 받으면, 등록된 supervisor가 있는지 확인하고 `isReconnecting()`이 `true`면 아무것도 안 하고 넘어간다 - 재접속 로직이 알아서 처리 중이라는 뜻이므로. `false`인데도 나갔다면 그건 진짜 외부 요인(관리자가 끊음, 디스코드 자체 문제 등)이므로 그때 정리한다.

`/leave` 경로는 이 플래그와 아예 관계가 없다 - `LeaveCommandListener`가 `connectionRegistry.unregisterAndShutdown()`을 먼저 호출해서 supervisor를 레지스트리에서 지운 다음에 `audioManager.closeAudioConnection()`을 부르기 때문에, 실제 나가는 이벤트가 디스코드에서 도착할 때쯤엔 이미 레지스트리에 supervisor가 없다. `BotVoiceDisconnectListener`가 조회했을 때 `null`이 나오므로 자연스럽게 아무 일도 안 한다.

**교훈**: "이 상태 변화를 우리가 일으켰는가, 외부에서 일으켰는가"를 구분해야 하는 감지 로직을 만들 땐, 우리 시스템 자신이 같은 종류의 이벤트를 발생시키는 다른 경로가 없는지부터 찾아봐야 한다. 있다면 반드시 구분할 플래그/상태가 필요하다.

## API 비용 절감 — 실사용을 시작하자마자 급해진 문제

Phase 5·6까지 만들고 나서 실제로 계속 써보니, CLOVA와 Papago 둘 다 실비용이 나가는 유료 API라는 게 바로 체감됐다(CLOVA는 무료 티어가 월 20분뿐이고, Papago는 무료 티어 자체가 없다). 발화 하나당 API 호출이 최소 1~2번(STT + 번역) 나가는 구조라, 대화가 길어질수록 그대로 비용이 쌓인다.

가장 먼저 손댈 지점은 두 군데였다.

**1. 잡음까지 STT로 보내고 있었다.** 무음 감지(800ms 기준)는 "새 오디오가 안 들어온 지 800ms가 지났는가"만 보지, 쌓인 오디오가 실제로 의미 있는 말인지는 전혀 안 본다. 마이크 잡음, 숨소리, 짧은 헛기침도 무조건 STT로 넘어가고 있었다 - CLOVA는 분당 과금이라 이런 잡음까지 그대로 비용이 나간다. `submitForRecognition()`에 400ms 미만이면 STT 호출 자체를 생략하는 필터를 추가했다. 400ms는 "네", "응" 같은 짧은 대답은 살리면서 순간 잡음은 대부분 걸러지는 절충값으로 잡았다.

```java
// 디스코드 PCM(48kHz, 스테레오, 16비트) 기준 1ms당 192바이트
private static final long MINIMUM_UTTERANCE_MILLIS = 400;
private static final int DISCORD_PCM_BYTES_PER_MILLISECOND = 192;
```

**2. 같은 문장을 매번 다시 번역시키고 있었다.** 테스트 중 같은 말을 반복하거나("안녕하세요"를 여러 번 테스트), 흔한 인사말이 자주 겹치는 상황에서도 Papago를 매번 새로 호출하고 있었다. Papago는 완전히 같은 입력에 항상 같은 출력을 내므로, (원문 언어, 번역 언어, 원문 텍스트)를 키로 하는 캐시를 만들어서 캐시에 있으면 API를 아예 안 부르도록 했다. 캐시는 최대 200개 항목만 유지하는 LRU(`LinkedHashMap`의 접근 순서 모드 + `removeEldestEntry` 오버라이드)라서 메모리가 무한정 늘어나지 않는다.

**STT 결과 자체는 캐싱하지 않았다** - 같은 말을 해도 음성 파형은 매번 미세하게 달라서(마이크 잡음, 발화 속도 차이 등) 오디오 바이트 단위 캐시는 사실상 적중하지 않는다. 반면 STT가 뽑아낸 *텍스트*는 같은 문장이면 항상 똑같으므로, 번역 캐시만으로 Papago 쪽 절감 효과는 충분하다.

이 두 최적화는 Phase 5에서 확정한 "화자가 번역 방향을 직접 설정"하는 설계(체크포인트 04 참고) 덕분에 더 잘 맞아떨어진다 - 번역이 발화당 정확히 1번만 일어나는 구조라서, 캐시 키 공간이 단순하고 캐시 적중 여부 판단도 명확하다.

## 다음 단계

- Phase 8: 배포
