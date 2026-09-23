# 개발 노트

주요 분기점(Phase 완료 시점 등)마다, 그때까지 배운 개념과 삽질 기록을 정리해두는 폴더.

같은 문제를 다시 만났을 때, 또는 "왜 코드가 이렇게 생겼는지" 궁금할 때 여기부터 보면 된다.
(코드 컨벤션·빌드 방법 같은 상시 정보는 이 폴더가 아니라 프로젝트 루트의 개발 가이드에 있다.)

## 체크포인트 목록

| 문서 | 시점 | 주요 내용 |
|---|---|---|
| [checkpoint-01-phase1-3.md](checkpoint-01-phase1-3.md) | Phase 1~3 완료 (2026-09-23) | 봇 아키텍처, JDA 3단 스위치(Intent/CacheFlag/MemberCachePolicy), 오디오 파이프라인, DAVE 프로토콜, 스레딩, 슬래시 커맨드 등록 함정, 겪은 버그 4건 |
| [checkpoint-02-phase4-azure.md](checkpoint-02-phase4-azure.md) | Phase 4 Azure STT 첫 성공 (2026-09-23) | 간헐적 음성 수신 실패를 몇 시간 쫓다가 찾아낸 진짜 원인(비동기 작업의 조용한 예외 삼킴 + Azure SDK 스트림 순서 버그), 자동 재접속 supervisor, "증상만 보고 원인 단정하지 말 것" 교훈 |
| [checkpoint-03-phase4-complete.md](checkpoint-03-phase4-complete.md) | Phase 4 완료 (2026-09-23) | CLOVA Speech(한국어) 연동, SDK 있는 STT(Azure)와 순수 REST STT(CLOVA)의 구조 차이(WAV 인코딩, HTTP 클라이언트, JSON 파싱 직접 처리), 4개 언어 전부 실제 인식 결과 확인 |

## 작성 규칙

- 한 분기점 = 한 문서. 파일명은 `checkpoint-NN-<범위>.md`
- 개념 설명만 쓰지 말고, **실제로 겪은 증상 → 원인 → 해결**을 같이 남긴다 (나중에 검색으로 찾게 되는 건 대부분 이쪽)
- 새 문서를 추가하면 위 목록 표에도 한 줄 추가
