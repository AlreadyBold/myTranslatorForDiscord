# 체크포인트 01 — Phase 1~3 완료 (2026-09-23)

## 이 시점까지 만든 것

- **Phase 1** 봇 로그인 (JDA, `.env` 토큰 관리)
- **Phase 2** `/join`, `/leave` 슬래시 커맨드로 음성 채널 접속/퇴장
- **Phase 3** 유저별 PCM 오디오 수신 + 무음 구간 감지로 발화 단위 묶기

---

## 1. 봇 아키텍처 — 우리 코드는 "클라이언트"다

디스코드 서버(회사 인프라)는 중계와 저장만 하고, **우리 봇 코드는 디스코드가 실행해주지 않는다.**
우리 프로세스가 디스코드로 **아웃바운드 WebSocket을 열어놓고 유지**하는 구조다.

```
[우리 JDA 프로세스] --WS/REST--> [디스코드 인프라] <--WS/REST-- [유저들의 디스코드 앱]
```

이 한 가지로 여러 개가 설명된다:

- 포트포워딩·방화벽 설정이 필요 없다 (웹사이트 접속과 같은 방향의 연결이라서)
- 전 세계 어디서 `/join`을 쳐도 우리 컴퓨터까지 이벤트가 도달한다 (디스코드가 중계)
- 그 프로세스가 죽으면 봇은 **모두에게** 오프라인이 된다 → 24시간 운영에는 별도 호스팅이 필요 (Phase 8)

"Developer Portal에서 봇을 만든다"는 건 **로그인용 신원(토큰)을 발급받는 것**일 뿐, 코드를 디스코드에 업로드하는 게 아니다.

## 2. JDA의 3단 스위치 — 버그 두 번의 근본 원인

| 개념 | 역할 | 안 켜면 생기는 일 |
|---|---|---|
| `GatewayIntent` | 이벤트를 **받을지** 결정 | 이벤트 자체가 안 온다 |
| `CacheFlag` | 받은 정보를 **저장해서 조회 가능하게** 할지 | `member.getVoiceState()`가 항상 `null` |
| `MemberCachePolicy` | **어떤 멤버**를 캐시에 유지할지 | SSRC→Member 매핑 실패 → `handleUserAudio`가 호출조차 안 됨 |

`JDABuilder.createLight`는 **셋 다 기본 OFF**다. "최소로 시작하고 필요한 것만 켠다"는 철학.
기능이 조용히 동작하지 않을 때는 이 세 개를 순서대로 의심하면 된다.

현재 설정:

```java
JDABuilder.createLight(token, GatewayIntent.GUILD_VOICE_STATES)
        .enableCache(CacheFlag.VOICE_STATE)
        .setMemberCachePolicy(MemberCachePolicy.VOICE)
```

`MemberCachePolicy.VOICE`는 음성 채널에 있는 동안만 캐시하므로, 승인이 까다로운 privileged 인텐트(`GUILD_MEMBERS`) 없이도 목적을 달성할 수 있다.

## 3. 비동기와 3초 제한

- `build()`는 연결을 **시작만** 하고 즉시 리턴 → `awaitReady()`로 완료를 기다린다
- JDA의 모든 REST 호출은 `RestAction`이고 `.queue()`로 비동기 실행한다
- **슬래시 커맨드는 3초 안에 ACK(응답)해야 한다.** 초과하면 `10062: Unknown interaction`
  - 오래 걸리는 작업(STT·번역 호출 등)이 필요하면 `deferReply()`로 먼저 "생각 중" 상태를 만들어 시간을 벌어야 한다
  - Phase 4부터 외부 API를 호출하게 되므로 실제로 필요해질 가능성이 높다

## 4. 오디오 파이프라인

- `handleUserAudio`는 **20ms 단위로 3840 bytes**씩 전달한다
  - `48000Hz × 2byte(16bit) × 2채널 × 0.02초 = 3840 bytes`
  - 로그에 찍힌 숫자가 이 계산과 맞는지로 파이프라인 정상 여부를 검증할 수 있다
- **SSRC** = 오디오 패킷에 붙은 화자 식별 번호. 이걸 실제 유저로 바꾸려면 멤버 캐시가 필요하다
- **디스코드는 "말이 끝났다"는 신호를 주지 않는다.** 그냥 패킷 전송을 멈출 뿐이다
  - 그래서 무음 감지는 이벤트 기반이 아니라 **폴링**(200ms마다 확인, 800ms 이상 조용하면 발화 종료)으로 구현했다
- **DAVE(음성 종단간 암호화)**: 2026-03-01부터 필수. JDA는 인터페이스만 제공하고 구현은 없어서 `libdave-jvm`을 직접 붙여야 한다
- STT 연동 시 주의: 디스코드는 **48kHz 스테레오**를 주는데 STT 엔진은 보통 **16kHz 모노**를 요구한다 → 리샘플링/모노 변환 단계가 필요

## 5. 스레딩

로그의 **스레드 이름**이 디버깅에 결정적이었다:

| 스레드 이름 | 역할 |
|---|---|
| `JDA AudioConnection ... Receiving Thread` | 오디오 수신 (`handleUserAudio` 호출) |
| `pool-1-thread-1` | 우리가 만든 무음 감지 스케줄러 |
| `JDA MainWS-ReadThread` | 게이트웨이 이벤트(슬래시 커맨드 등) 처리 |

마지막 `발화 종료` 로그가 스케줄러가 아니라 `MainWS-ReadThread`에서 찍힌 것으로 **`/leave` 시 강제 flush가 실제로 동작했음을 증명**했다.

주의할 점:

- 서로 다른 스레드가 같은 버퍼를 만지므로 `synchronized`로 보호해야 한다
- `ExecutorService`는 명시적으로 `shutdown()` 하지 않으면 스레드가 계속 살아남는다 (`/leave` 시 정리하는 이유)

## 6. 슬래시 커맨드 등록의 함정

`guild.updateCommands()`는 **추가가 아니라 전체 교체**다. 커맨드마다 각자 등록하면 서로의 등록을 지워버린다.
→ 등록은 `CommandRegistrationListener` 한 곳에 모으고, 실행 로직만 커맨드별 클래스로 분리했다.

또한 길드(서버) 단위 등록은 즉시 반영되지만, 글로벌 등록은 전파에 최대 1시간이 걸린다 → 개발 중에는 길드 단위가 유리하다.

## 7. 보안

- WebSocket 파이프 자체를 통한 침투는 사실상 불가능하다 (우리가 먼저 건 아웃바운드 연결 + TLS, 외부에서 접근 가능한 listening 포트가 생기지 않음)
- 실제로 신경 써야 할 것 3가지:
  1. **토큰 유출** — 유출되면 공격자가 그 토큰으로 우리 봇인 척 접속 가능 (`.env` + `.gitignore`)
  2. **커맨드 인자를 신뢰하지 않기** — 슬래시 커맨드 입력값은 서버의 아무 유저나 만들 수 있는 외부 입력이다
  3. **최소 권한** — 봇 권한을 `Connect`/`Speak`/`View Channel`로만 제한해 사고 시 피해 범위를 줄인다

---

## 겪은 버그와 원인 (검색용)

| 증상 | 원인 | 해결 |
|---|---|---|
| `/join` 하면 음성 채널에 들어가 있는데도 "먼저 음성 채널에 들어가 있어야 해요"만 뜬다 | `CacheFlag.VOICE_STATE`가 꺼져 있어 `getVoiceState()`가 항상 `null` | `.enableCache(CacheFlag.VOICE_STATE)` |
| 봇이 음성 채널에 들어왔다 나갔다 점멸하며 반복한다 | 디스코드가 DAVE(E2EE) 미지원 연결을 거부 → JDA가 자동 재접속을 무한 반복 (`E2EE/DAVE protocol required`) | `libdave-jvm` 의존성 추가 + `setAudioModuleConfig(...withDaveSessionFactory(...))` |
| 오디오는 도착하는데 `handleUserAudio`가 호출되지 않는다 (`Received audio data with a known SSRC, but the userId ... is unknown to JDA` 경고) | 멤버 캐시가 없어 SSRC→Member 매핑 실패 | `.setMemberCachePolicy(MemberCachePolicy.VOICE)` |
| 마지막 발화가 로그(=나중엔 STT)로 넘어가지 않고 사라진다 | 무음 기준 시간(800ms)이 지나기 전에 `/leave`하면 버퍼가 flush되지 않은 채 스케줄러만 멈춤 | `shutdown()`에서 스케줄러 정지 전에 남은 버퍼를 강제 flush |
| `10062: Unknown interaction` | 인터랙션 3초 ACK 제한 초과 (한 번은 일시적 네트워크 지연으로 발생) | 오래 걸리는 처리 전에는 `deferReply()` 사용 |

---

## 다음 단계 (Phase 4 — STT 연동)

1. **Step 1** `/setlang` 커맨드 + 유저별 언어 저장 — API 키 불필요. 라우팅의 전제조건이고, i18n의 `Language.KO` 고정 TODO도 함께 해소된다
2. **Step 2** Azure Speech 연동 (en/cn/ja) — Azure 키 필요
3. **Step 3** CLOVA Speech 연동 (ko, gRPC) — NCP 키 필요

STT로 넘기기 전 **48kHz 스테레오 → 16kHz 모노 변환**이 필요하다는 점을 잊지 말 것.
