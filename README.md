# myTranslatorForDiscord

디스코드 음성 채널에서 실시간으로 음성을 텍스트로 바꾸고 번역해주는 봇입니다. 화자가 말하면, 그 발화가 끝나는 대로 인식된 원문과 번역문이 자막처럼 음성 채널의 텍스트 채팅에 올라옵니다.

학습을 겸한 개인 프로젝트로, 기능을 phase 단위로 하나씩 쌓아가는 중입니다.

## 지원 언어

한국어(ko), 영어(en), 중국어(cn), 일본어(ja) 4개 언어가 서로 전부 번역되는 완전 그래프 구조로, 총 12개 언어쌍을 지원합니다.

`en→ko`, `en→ja`, `en→cn`, `ko→ja`, `ko→en`, `ko→cn`, `cn→en`, `cn→ko`, `cn→ja`, `ja→ko`, `ja→en`, `ja→cn`

## 사용법

| 커맨드 | 설명 |
|---|---|
| `/join` | 커맨드를 실행한 유저가 있는 음성 채널로 봇을 불러옵니다. |
| `/leave` | 봇을 음성 채널에서 내보냅니다. |
| `/setlang` | 내가 말할 언어를 설정합니다. |
| `/setoutputlang` | 내 발화를 번역해서 보여줄 언어를 설정합니다. |

`/setlang`과 `/setoutputlang` **둘 다 화자 본인이 자기 발화 기준으로 설정**합니다. 예를 들어 한국어로 말하고(`/setlang KO`) 그 말을 영어로 보여주고 싶다면(`/setoutputlang EN`), 봇이 접속한 음성 채널에서 말할 때마다 원문과 영어 번역이 함께 자막으로 올라옵니다. 두 설정을 같은 언어로 맞출 수는 없습니다(번역할 이유가 없으므로).

## 동작 방식

1. 유저별로 분리된 음성 데이터를 실시간으로 수신하고, 무음 구간(800ms)을 기준으로 발화 단위로 묶습니다.
2. 화자가 `/setlang`으로 설정한 언어에 따라 STT 엔진(한국어는 CLOVA Speech, 그 외는 Azure Speech)으로 라우팅해서 텍스트로 변환합니다.
3. 인식된 텍스트를 화자가 `/setoutputlang`으로 설정한 언어로 Papago Translation을 통해 번역합니다.
4. 원문과 번역문을 봇이 접속한 음성 채널 자체의 텍스트 채팅에 올립니다.

실시간 언어 자동 감지는 하지 않습니다 — 감지 실패 시 엉뚱한 엔진으로 보내 인식이 통째로 망가지는 것보다, 화자가 미리 선택해두는 쪽이 안정적이기 때문입니다.

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
