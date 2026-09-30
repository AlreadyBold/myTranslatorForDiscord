# myTranslatorForDiscord

디스코드 음성 채널에서 실시간으로 음성을 텍스트로 바꾸고 번역해주는 봇입니다. 화자가 말하면, 그 발화가 끝나는 대로 인식된 원문과 번역문이 자막처럼 음성 채널의 텍스트 채팅에 올라옵니다.

학습을 겸한 개인 프로젝트로, 기능을 phase 단위로 하나씩 쌓아가는 중입니다.

## 지원 언어

한국어(ko), 영어(en), 중국어(cn), 일본어(ja) 4개 언어가 서로 전부 번역되는 완전 그래프 구조로, 총 12개 언어쌍을 지원합니다.

`en→ko`, `en→ja`, `en→cn`, `ko→ja`, `ko→en`, `ko→cn`, `cn→en`, `cn→ko`, `cn→ja`, `ja→ko`, `ja→en`, `ja→cn`

## 커맨드

| 커맨드 | 옵션 | 설명 |
|---|---|---|
| `/join` | 없음 | 내가 있는 음성 채널로 봇을 불러옵니다. |
| `/leave` | 없음 | 봇을 음성 채널에서 내보냅니다. |
| `/setlang` | `language` (필수), `user` (선택) | 말할 언어를 설정하고 **번역을 켭니다**. |
| `/setoutputlang` | `language` (필수), `user` (선택) | 발화를 번역해서 보여줄 언어를 설정합니다. |
| `/stoptranslate` | `user` (선택) | 번역을 끕니다. |

`user` 옵션을 비우면 커맨드를 실행한 **나 자신**에게 적용됩니다.

### 기본 사용 흐름

1. 음성 채널에 들어간 뒤 `/join`으로 봇을 부릅니다.
2. `/setlang`으로 내가 말할 언어를 고릅니다. 이 순간부터 내 말이 번역 대상이 됩니다.
3. `/setoutputlang`으로 내 말을 어떤 언어로 보여줄지 고릅니다. (안 고르면 영어)
4. 말하면, 발화가 끝날 때마다 원문과 번역문이 음성 채널의 텍스트 채팅에 올라옵니다.
5. 그만하려면 `/stoptranslate`로 내 번역만 끄거나, `/leave`로 봇을 내보냅니다.

예를 들어 한국어로 말하고 그 말을 영어로 보여주고 싶다면 `/setlang KO` → `/setoutputlang EN` 순서로 설정합니다. 말할 언어와 번역 언어는 같게 고를 수 없습니다(번역할 이유가 없으므로).

### 다른 사람 지정하기

`/setlang`, `/setoutputlang`, `/stoptranslate`에 `user`를 지정하면 다른 사람의 설정을 바꿀 수 있습니다. 다른 사람의 말이 본인 모르게 받아적히지 않도록 아래 규칙이 있습니다.

- **나와 같은 음성 채널에 있는 사람만** 지정할 수 있습니다.
- 다른 사람을 지정하면 봇이 **채널 전체가 보는 메시지로 알리고**, 대상에게 멘션을 보냅니다. (본인 설정은 나에게만 보이는 메시지로 답합니다.)
- 지정당한 사람은 언제든 `/stoptranslate`로 **스스로 번역을 끌 수 있습니다.**

### 알아둘 점

- **`/setlang`을 한 사람만 번역됩니다.** 음성 채널에 있어도 번역을 켜지 않은 사람의 음성은 처리하지 않습니다.
- **자막은 3분 뒤 자동으로 지워집니다.** 나중에 채널에 들어온 사람이 지난 대화를 스크롤해서 볼 수 없게 하기 위해서입니다.
- **설정은 봇을 재시작하면 초기화됩니다.** 재시작 후에는 `/setlang`부터 다시 해야 합니다.

## 동작 방식

1. 번역이 켜진 유저의 음성만 받아서, 무음 구간(800ms)을 기준으로 발화 단위로 묶습니다. 400ms보다 짧은 소리는 잡음으로 보고 버립니다.
2. 화자의 말할 언어에 따라 STT 엔진(한국어는 CLOVA Speech, 그 외는 Azure Speech)으로 보내서 텍스트로 변환합니다.
3. 인식된 텍스트를 화자가 고른 번역 언어로 Papago Translation을 통해 번역합니다. 같은 문장은 캐시해서 다시 번역 요청하지 않습니다.
4. `**서버 별명**: 원문` + 번역문 형태로 음성 채널 자체의 텍스트 채팅에 올리고, 3분 뒤 지웁니다.

실시간 언어 자동 감지는 하지 않습니다. 감지 실패 시 엉뚱한 엔진으로 보내 인식이 통째로 망가지는 것보다, 화자가 미리 선택해두는 쪽이 안정적이기 때문입니다.

## 기술 스택

- **Discord 연동**: [JDA](https://github.com/discord-jda/JDA) 6.7.0
- **음성 종단간 암호화(DAVE)**: [libdave-jvm](https://github.com/KyokoBot/libdave-jvm) — 디스코드가 2026-03-01부터 DAVE 프로토콜 없는 음성 연결을 거부하기 시작해서 필요
- **STT (한국어)**: CLOVA Speech (Naver Cloud Platform)
- **STT (영어/중국어/일본어)**: Azure Speech
- **번역**: Papago Translation (Naver Cloud Platform)

## 시작하기

### 요구사항

- JDK 21
- 디스코드 봇 토큰 ([Discord Developer Portal](https://discord.com/developers/applications)에서 발급)
- Naver Cloud Platform 계정 (CLOVA Speech, Papago Translation API 키)
- Azure 계정 (Azure Speech API 키)

디스코드 봇에는 별도의 Privileged Gateway Intent가 필요 없습니다. 봇 초대 시 아래 권한만 있으면 됩니다.

- `Connect`(음성 채널 접속 — 없으면 `/join` 시 안내 메시지가 뜹니다)
- `Send Messages`(음성 채널 텍스트 채팅에 자막 전송)

### 실행

```bash
git clone <repository-url>
cd myTranslatorForDiscord

cp .env.example .env
# .env를 열어서 DISCORD_BOT_TOKEN, PAPAGO_*, CLOVA_SPEECH_*, AZURE_SPEECH_* 값을 채워넣기

./gradlew run
```

STT/번역 API 키 중 일부가 비어있어도 봇 자체는 켜집니다 — 해당 기능만 경고 로그와 함께 비활성화됩니다.

### 기타 명령어

```bash
./gradlew build             # 컴파일 + 테스트 + checkstyle
./gradlew checkstyleMain    # 컨벤션 검사만 실행 (report: build/reports/checkstyle/main.xml)
```

## 진행 상황

- [x] Phase 1 — 로그인
- [x] Phase 2 — `/join`/`/leave` 음성 채널 접속·퇴장
- [x] Phase 3 — 유저별 음성 캡처 + 무음 구간 감지
- [x] Phase 4 — STT 연동 (CLOVA/Azure, 4개 언어 전부 검증 완료)
- [x] Phase 5 — Papago 번역 연동
- [x] Phase 6 — 번역 결과를 음성 채널 텍스트 채팅에 자막으로 출력
- [x] Phase 7 — 연결 끊김 처리, 에러 처리, API 사용량 절감
- [x] 번역 켜기/끄기(`/stoptranslate`), 다른 사람 지정, 자막 3분 뒤 자동 삭제
- [ ] Phase 8 — 배포

개발 중 겪은 문제와 설계 결정의 배경은 [docs/](docs/)에 단계별로 정리되어 있습니다.

## 프로젝트 구조

```
src/main/java/io/github/alreadybold/translator/
├── Main.java           # 부팅(토큰 로드, 인텐트/리스너 설정)만 담당
├── command/            # 슬래시 커맨드 리스너
├── audio/              # 오디오 수신·버퍼링·포맷 변환·음성 연결 감시
├── stt/                # STT 엔진 클라이언트 (CLOVA, Azure)
├── translation/        # 번역 엔진 클라이언트 (Papago)
├── settings/           # 유저별 설정 상태 (언어 선택 등)
└── i18n/               # 다국어 응답 문구
```
