# 운동 기록 공통 설계 문서

- 문서 버전: v0.11
- 작성일: 2026-10-05
- 상태: 초안
- 요구사항: `docs/requirements/workout-common.md` (v0.12)
- 공통 설계: `docs/design/architecture.md` (v0.14)
- 관련 설계: `docs/design/auth.md` (인증 필터, users, 홈의 로그아웃), `docs/design/workout-media.md` (운동 완료 시 사진·동영상)
- 운동 기록 설계 묶음 (설계 ID와 요구사항 ID를 함께 쓴다, 색인은 `workout-common.md` 부록 D):
  - `workout-common.md` 공통 — 아키텍처, 세션 API 공통 앞단, 상태, 데이터, 보안, 에러, 비기능, 컴포넌트
  - `workout-session.md` 홈·운동 진행 (Figma 2행)
  - `workout-exercise.md` 운동 선택·세트 기록 (Figma 3행)
  - `workout-history.md` 운동 기록 달력 (Figma 4행)
  - `workout-exercise-manage.md` 운동 종목 관리 (Figma 6행)
- 변경 이력 (v0.7까지는 분리 전 `workout-record.md` 설계의 이력):
  - v0.2 — 기술 중립 용어로 다시 씀. 기술 대응은 공통 설계 10.1을 따른다
  - v0.3 — 모든 PK를 UUIDv7로 변경(공통 DEC-ARCH-010). 세트·운동 순서와 목록 정렬을 ID 대신 시각 컬럼 기준으로 변경
  - v0.4 — 인증 설계 반영: `workout_session.user_id`를 참조(함께 삭제)로 변경(auth BR-012, DEC-WORKOUT-014), 스키마 변경 순서를 인증 스키마 뒤로, 사용자 ID 출처를 인증 필터로
  - v0.5 — 구현 반영: 방치 세션 정리의 기준 시각을 DB 시각 대신 주입한 시계로(공통 10.1, 테스트에서 시간 이동), 스키마 변경 번호를 인증 V1 통합에 맞춰 2·3으로
  - v0.6 (2026-10-09) — 요구사항 v0.5와 Figma 2행(홈, 운동 완료 팝업)에 맞춤.
    - 추가: 부위 목록(API-EXERCISE-004, `exercise_category`, 초기 운동 목록 24개), 진행 중 세션 현황의 부위별·종목별 볼륨(REQ-WORKOUT-009), 운동 취소(REQ-WORKOUT-010, API-WORKOUT-006을 진행 중 세션 전용으로), 완료 시 사진·동영상(`mediaIds`, workout-media), 방치된 세션 정리 때 파일 삭제
    - 요구사항에서 빠진 것 정리: 메모(TODO-020), 운동 검색(TODO-017), 종목 이미지(TODO-018, BR-014 폐기), 목록·상세 조회 API-WORKOUT-004·005 폐기(REQ-WORKOUT-003·004 폐기), 같은 종목 중복 추가 금지(REQ-EXERCISE-001)
    - 운영 사용 전이라 스키마 변경 2·3을 다시 만든다(사용자 결정, DEC-WORKOUT-016)
    - Figma 3·4행 기능(이전 기록 불러오기, 운동 기록 달력, 날짜별 기록·삭제)은 보류(D-TODO-WORKOUT-004)
  - v0.7 (2026-10-09) — Figma 3·4행 설계(D-TODO-WORKOUT-004 결정): 이전 기록 조회(API-SET-004)와 종목 세트 모두 삭제(API-SET-005), 월별 운동 달력(API-WORKOUT-007), 날짜별 기록(API-WORKOUT-008), 날짜 단위 삭제(API-WORKOUT-009), 화면 EX-001 ~ EX-003·WO-003 연계. v0.6 설계는 코드에 반영됨(공통 v0.9, workout-media v0.1과 함께)

  - v0.8 (2026-10-09) — `workout-record.md` 설계 v0.7을 요구사항 분리(v0.7)에 맞춰 `workout-common.md`, `workout-session.md`, `workout-exercise.md`, `workout-history.md`로 나눔. 설계 ID와 내용은 바꾸지 않았다
  - v0.9 (2026-10-10) — 요구사항 v0.9(종목 관리) 반영. 종목을 사용자 소유로: `exercise` → `default_exercise`(템플릿), 사용자 소유 `exercise` 신규, 세션 운동의 종목 참조를 함께 삭제로, 새 계정에 기본 목록 복사(DEC-WORKOUT-023 ~ 028, 스키마 변경 5). API-EXERCISE-005 ~ 007 추가(`workout-exercise-manage.md`)
  - v0.10 (2026-10-10) — 요구사항 v0.11(종목 순서 변경) 반영. API-EXERCISE-008 추가(workout-exercise-manage v0.2)
  - v0.11 (2026-10-22) — 요구사항 v0.12 반영. 이전 기록 불러오기 폐기로 API-SET-004·005 폐기, 이전 기록 조회 API-SET-006 추가(workout-exercise v0.11). 스키마 변경 없음

---

## 1. 설계 개요

### 1.1 목적
운동 기록 기능(홈·운동 진행, 운동 선택·세트 기록, 운동 기록 달력)이 함께 쓰는 설계를 한곳에 둔다: 컴포넌트와 요청 흐름, 세션 API의 공통 앞단, 세션 상태, 테이블 6개, 보안, 에러 코드, 비기능 대응, 설계 결정. 기능별 처리 흐름·화면 연계·API 상세는 workout-session, workout-exercise, workout-history, workout-exercise-manage에 있다. 운동 기록 API는 모두 20개다(5.1).

### 1.2 설계 범위
- **포함:** 요구사항 workout-common의 공통 규칙(BR-001, 002, 004, 008, 009, 010, 011, 015, 022), 공통 예외(ERR-001, 003, 007, 009), DATA-001 ~ 006, NFR 전부
- **보류:** 없음
- **제외:** 요구사항 8.1의 범위 밖 항목. 폐기된 요구사항 REQ-SET-004, REQ-SET-005, REQ-WORKOUT-003, REQ-WORKOUT-004, BR-006, BR-014, BR-017, BR-021, ERR-006, IF-SET-002, IF-SET-003, IF-WORKOUT-004, IF-WORKOUT-005, 화면 WO-004는 설계하지 않는다.

### 1.3 대상 시스템
- Backend API: 도메인 `exercise`(부위·운동 목록), `session`(운동 세션·세션 운동·세트)
- Database: `exercise_category`, `default_exercise`(v0.9: 지금의 `exercise`를 이름 변경), `exercise`(v0.9: 사용자 소유), `workout_session`, `workout_session_exercise`, `workout_set` (+ workout-media의 `workout_media`)
- Mobile App: 화면 WO-001, WO-002(이 문서에서 상세), EX-001 ~ EX-003, EX-007(유지·매핑만)

### 1.4 기술 스택
공통 설계 1.4를 따른다. 이 기능에 새로 필요한 기술 능력:
| 필요한 능력 | 용도 | 이유 |
|-----------|-----|-----|
| 공개 정적 파일 제공 | 부위 이미지 | 서비스가 준비하는 고정 이미지 4개라 외부 저장소가 필요 없다 (DEC-WORKOUT-008) |

방치된 세션 자동 완료(REQ-WORKOUT-006)는 주기 작업 없이 처리하므로 그 능력은 필요 없다 (DEC-WORKOUT-004). 사진·동영상의 파일 저장은 workout-media 설계를 따른다.

### 1.5 설계 원칙
1. **세션이 중심:** 운동·세트 변경은 모두 소속 세션을 통해 접근하고, 세션 하나의 상태·소유자 확인을 거친다.
2. **계산할 수 있는 값은 저장하지 않는다:** 세트 번호, 운동 순서, 세션 요약, 부위별·종목별 볼륨은 저장하지 않고 조회 때 계산한다. 저장하면 값이 서로 어긋날 수 있다(DEC-WORKOUT-002, 003).
3. **변경 요청은 세션에 변경 잠금을 건다:** 완료와 세트 변경이 동시에 들어와도 완료된 세션에 세트가 추가되지 않게 한다(DEC-WORKOUT-005).
4. **세션을 지우는 모든 경로는 파일도 지운다:** 운동 취소, 방치된 세션 정리는 workout-media 6.4의 파일 삭제를 커밋 후 작업으로 부른다.

### 1.6 요구사항 ↔ 설계 추적표
공통 ID만 둔다. 기능별 ID는 각 문서 1.6에 있다.

| 요구사항 ID | 요구사항 | 설계 반영 위치 | 구현 | 테스트 |
|------------|---------|--------------|-----|-------|
| BR-001 | 본인 기록만 | 3.5, 7.2 | | |
| BR-002 | 완료된 세션 수정 불가 | 3.4, 3.5, WORKOUT_SESSION_NOT_EDITABLE | | |
| BR-004 | 수행 날짜 = 시작 시각의 현지 날짜 | 3.2, 3.5, API-WORKOUT-001 `X-Time-Zone` | | |
| BR-008 | 종료 시각 ≥ 시작 시각 | 3.5, ck_workout_session_ended | | |
| BR-009 | 본인 목록의 종목만 추가 (v0.9) | 3.5, 참조 workout_session_exercise.exercise_id, workout-exercise-manage 7.2 | | |
| BR-010 | 요약은 모든 세트로 계산 | 3.5, DEC-WORKOUT-003, 5.2 summary | | |
| BR-011 | 진행 중 세션은 하나 | 3.5, ux_workout_session_user_in_progress | | |
| BR-015 | 부위별·종목별 볼륨·세트 수 | 3.5, 5.2 `exercises[].volume`, `categories[]` | | |
| BR-022 | 종목 목록은 사용자마다 | workout-exercise-manage 3.5, exercise.user_id, trg_users_default_exercises | | |
| ERR-001 | 비로그인 요청 | 8.2 UNAUTHORIZED | | |
| ERR-003 | 다른 사용자 데이터 접근 | 7.2, 8.2 FORBIDDEN | | |
| ERR-007 | 완료된 세션 변경 | 8.2 WORKOUT_SESSION_NOT_EDITABLE | | |
| ERR-009 | 없는 세션·운동·세트 | 8.2 WORKOUT_SESSION_NOT_FOUND, SESSION_EXERCISE_NOT_FOUND, WORKOUT_SET_NOT_FOUND | | |
| NFR-PERF-001 | p95 500ms | 9장 | | |
| NFR-PERF-002 | 기록이 많아도 달력·날짜별·이전 기록 조회 성능 유지 | 9장, 6.3 | | |
| NFR-PERF-003 | 동시 1,000명 | 9장 | | |
| NFR-SEC-001 | 인증된 사용자만 | 7.1 | | |
| NFR-SEC-002 | 본인 데이터만 | 7.2 | | |
| NFR-AVAIL-001 | 오류 반환, 부분 저장 없음 | 9장, 3.2 트랜잭션 | | |
| NFR-INTEG-001 | 없는 사용자의 기록 생성 불가 | 6.2 참조 workout_session.user_id | | |
| NFR-INTEG-002 | 삭제 시 하위 데이터 남지 않음 | 6.4 참조(함께 삭제), workout-media 6.4 | | |
| NFR-INTEG-003 | 종목 삭제 시 기록 남지 않음 | 6.4, 참조(함께 삭제) workout_session_exercise.exercise_id | | |
| NFR-LOG-001 | 주요 행위·자동 처리·오류 추적 | 9장 로그 이벤트 | | |
| DATA-001 | 운동 세션 | 6.2 workout_session | | |
| DATA-002 | 운동(종목) | 6.2 exercise·default_exercise (workout-exercise-manage 6.2), 6.5 초기 목록·스키마 변경 5 | | |
| DATA-003 | 세션 내 운동 | 6.2 workout_session_exercise | | |
| DATA-004 | 세트 | 6.2 workout_set | | |
| DATA-005 | 세션 요약 | DEC-WORKOUT-003, 5.2 summary·categories | | |
| DATA-006 | 운동 카테고리(부위) | 6.2 exercise_category, 6.5 초기 목록 | | |

---

## 2. 시스템 아키텍처
공통 설계 2장을 따른다. 이 기능에서 추가되는 것:

- 추가 컴포넌트: **ExpiredSessionCleaner**(서비스) — 시작 후 6시간이 지난 진행 중 세션을 정리한다(REQ-WORKOUT-006). 주기 작업이 아니라, 세션 관련 API가 처리 전에 **요청한 사용자의 세션만** 정리한다. **독립 트랜잭션**으로 실행해, 뒤이은 요청 처리가 실패해도 정리 결과는 남는다(DEC-WORKOUT-004). 정리한 세션의 임시 사진·동영상 파일은 커밋 후 작업으로 지운다(workout-media 6.4).
- 공개 정적 파일: 부위 이미지를 URL `/images/exercise-categories/**`로 제공한다(DEC-WORKOUT-008).

```
요청 ─▶ 인증 필터(userId) ─▶ WorkoutSessionController / ExerciseController (API 진입점)
                               │
                               ├─▶ ExpiredSessionCleaner.cleanUp(userId)   [독립 트랜잭션, 조건부 일괄 갱신, 커밋 후 파일 삭제]
                               │
                               └─▶ WorkoutSessionService [트랜잭션]
                                      ├─ 쓰기·단건 조회: 저장소 (세션에 변경 잠금)
                                      ├─ 세션 현황·이전 기록: WorkoutSessionQueryRepository (조회 저장소)
                                      └─ 파일 삭제 요청: WorkoutMediaService (커밋 후 작업)

요청 ─▶ 인증 필터(userId) ─▶ WorkoutDayController (API 진입점, 운동한 날)
                               ├─▶ ExpiredSessionCleaner.cleanUp(userId)
                               └─▶ WorkoutDayService [트랜잭션]
                                      ├─ 달력·날짜별 기록: WorkoutDayQueryRepository (조회 저장소)
                                      └─ 날짜 단위 삭제: 조건부 일괄 갱신 → 파일 삭제 요청(커밋 후)
```

---

## 3. 기능 설계

### 3.1 기능 목록
기능별 처리 흐름은 각 문서 3.2에 있다.

| 문서 | 요구사항 | API |
|-----|---------|-----|
| workout-session | REQ-WORKOUT-001, 002, 006, 009, 010 | API-WORKOUT-001, 002, 003, 006 |
| workout-exercise | REQ-EXERCISE-001, 002, REQ-SET-001 ~ 003, 006 | API-EXERCISE-001 ~ 004, API-SET-001 ~ 003, 006 |
| workout-history | REQ-WORKOUT-005, 007, 008 | API-WORKOUT-007, 008, 009 |
| workout-exercise-manage | REQ-EXERCISE-003 ~ 007 | API-EXERCISE-005 ~ 008 (+ API-EXERCISE-001, 004 재사용) |

### 3.2 기능별 처리 흐름 — 공통 앞단
모든 흐름의 공통 앞단:
- (A) 인증 필터에서 userId를 얻는다. 없거나 잘못되면 401 `UNAUTHORIZED` (ERR-001).
- (B) `ExpiredSessionCleaner.cleanUp(userId)` 실행 (REQ-WORKOUT-006 흐름 참고). 운동 목록 조회(API-EXERCISE-001)도 최근 수행일이 정리 결과를 반영하도록 실행한다. 종목 삭제(API-EXERCISE-007)도 세션을 지우므로 실행한다. 부위 목록(API-EXERCISE-004)과 종목 추가·수정(API-EXERCISE-005, 006)은 세션과 관계없으므로 실행하지 않는다.

**편집 가능 세션 확보** (운동·세트 변경, 완료, 취소에서 공통으로 쓰는 단계, 이하 "(E)"):
1. 세션을 **변경 잠금**으로 조회한다. 없으면 404 `WORKOUT_SESSION_NOT_FOUND` (ERR-009).
2. `user_id`가 userId와 다르면 403 `FORBIDDEN` (BR-001, ERR-003).
3. 상태가 `COMPLETED`면 409 `WORKOUT_SESSION_NOT_EDITABLE` (BR-002, ERR-007). 완료·취소 API에서는 대신 409 `WORKOUT_SESSION_ALREADY_COMPLETED` (ERR-008).

### 3.3 주요 시나리오
해당 없음 — 각 문서 3.3에 있다(시작부터 완료까지는 workout-session 3.3).

### 3.4 상태 변화
```
           POST /workout-sessions
                    │
                    ▼
            [IN_PROGRESS] ──POST /complete (세트 ≥ 1)──────────────▶ [COMPLETED]
              │      │                                                │
              │      └──6시간 경과 + 세트 ≥ 1 (cleanUp)──────────────▶ │
              │                                                       │
              ├──6시간 경과 + 세트 0 (cleanUp)──▶ (삭제)                │
              └──DELETE (운동 취소)──▶ (삭제)   (삭제) ◀──DELETE /workout-days/{date}─┘
```

| 작업 | IN_PROGRESS | COMPLETED |
|-----|-------------|-----------|
| 현황 조회(API-WORKOUT-002) | ○ | — (진행 중 세션만 대상) |
| 이전 기록(API-SET-006)·달력·날짜별 기록의 대상 | ✕ | ○ |
| 운동 추가·삭제, 세트 추가·수정·삭제, 사진·동영상 임시 올리기 | ○ | ✕ 409 WORKOUT_SESSION_NOT_EDITABLE |
| 완료 | ○ (세트 ≥ 1) | ✕ 409 WORKOUT_SESSION_ALREADY_COMPLETED |
| 취소(API-WORKOUT-006) | ○ | ✕ 409 WORKOUT_SESSION_ALREADY_COMPLETED |
| 날짜 단위 삭제(API-WORKOUT-009) | — (지우지 않는다) | ○ 그날 완료된 세션 모두 |

### 3.5 비즈니스 규칙 구현
공통 규칙만 둔다. 기능별 규칙은 각 문서 3.5에 있다.

| BR ID | 규칙 | 강제 위치 | 방법 | 위반 시 |
|-------|-----|----------|-----|--------|
| BR-001 | 본인 기록만 | 서비스, 조회 조건 | 단건: 조회 후 `user_id` 비교 / 목록·집계: 조회 조건에 `user_id = userId` | 403 FORBIDDEN |
| BR-002 | 완료된 세션 수정 불가 | 서비스 (E) | 세션 변경 잠금 후 상태 확인. 예외: 종목 삭제는 완료된 세션의 그 종목 기록도 지운다(workout-exercise-manage 3.2, BR-025) | 409 WORKOUT_SESSION_NOT_EDITABLE |
| BR-004 | 수행 날짜 = 시작 시각의 현지 날짜 | 서비스 | `X-Time-Zone`으로 `started_at`의 날짜 계산, 입력으로 받지 않음 | 헤더 오류 시 400 |
| BR-008 | 종료 ≥ 시작 | 서비스, DB | 종료 시각은 항상 현재 시각 또는 시작 이후 추가된 세트 시각, 조건 검사 `ck_workout_session_ended` | 정상 흐름에서 발생 불가 → 500 |
| BR-009 | 본인 목록의 종목만 (v0.9) | 서비스, DB | `exercise` 존재·소유자 확인 + 참조 `exercise_id → exercise.id` | 404 EXERCISE_NOT_FOUND / 403 FORBIDDEN |
| BR-010 | 요약은 모든 세트로 | 조회 계산 | 현황 조회 결과로 합계 계산 (DEC-WORKOUT-003) | — |
| BR-011 | 진행 중 세션 하나 | 서비스, DB | 시작 전 조회 + 조건부 유일 `ux_workout_session_user_in_progress`, 위반을 제약 위반 변환으로 409 | 409 WORKOUT_SESSION_ALREADY_IN_PROGRESS |
| BR-015 | 부위별·종목별 볼륨·세트 수 | 조회 계산 | workout-session 3.2 REQ-WORKOUT-009의 4. BR-010과 같은 세트로 계산 | — |

### 3.6 기능 간 의존관계
- 세트 추가(REQ-SET-001)는 세션 운동(REQ-EXERCISE-001)이 있어야 한다.
- 세션 운동 추가는 그 사용자의 종목(`exercise`)이 있어야 한다. 기본 목록은 계정을 만들 때 DB 자동 동작이 복사한다(workout-exercise-manage 6.2, DEC-WORKOUT-023).
- 완료(REQ-WORKOUT-002)와 시작의 응답은 현황 조회(REQ-WORKOUT-009)와 같은 형태를 재사용한다.
- 완료의 사진·동영상(`mediaIds`)과 취소·정리의 파일 삭제는 workout-media 설계에 의존한다.
- 모든 세션 API와 운동 목록·이전 기록·달력·날짜별 기록 API는 ExpiredSessionCleaner(REQ-WORKOUT-006)에 의존한다(6시간 지난 세션이 정리된 결과를 보여야 한다).
- 날짜별 기록의 사진·동영상 주소와 내려받기는 workout-media API-MEDIA-002·003에 의존한다.

---

## 4. 화면 / API 연계 설계
해당 없음 — 화면은 각 문서 4장에 있다(WO-001·WO-002는 workout-session, EX-001 ~ 003은 workout-exercise, WO-003은 workout-history).

---

## 5. API 설계
URL·필드·날짜·페이지 규칙은 공통 설계 5장을 따른다. API 상세는 각 문서 5.3에 있다.

### 5.1 API 목록
| API ID | Method | URL | 인증 | 설명 | 관련 요구사항 |
|--------|--------|-----|-----|-----|-------------|
| API-WORKOUT-001 | POST | /api/v1/workout-sessions | 필요 | 세션 시작 | REQ-WORKOUT-001, IF-WORKOUT-001 |
| API-WORKOUT-002 | GET | /api/v1/workout-sessions/in-progress | 필요 | 진행 중 세션과 현황 | REQ-WORKOUT-009, IF-WORKOUT-002 |
| API-WORKOUT-003 | POST | /api/v1/workout-sessions/{sessionId}/complete | 필요 | 세션 완료(사진·동영상 붙임) | REQ-WORKOUT-002, IF-WORKOUT-003, workout-media IF-MEDIA-001 |
| ~~API-WORKOUT-004~~ | ~~GET~~ | ~~/api/v1/workout-sessions~~ | — | **폐기 (v0.6)** — REQ-WORKOUT-003 폐기. 달력·날짜별 조회로 바뀐다(보류) | — |
| ~~API-WORKOUT-005~~ | ~~GET~~ | ~~/api/v1/workout-sessions/{sessionId}~~ | — | **폐기 (v0.6)** — REQ-WORKOUT-004 폐기. 진행 중 세션은 API-WORKOUT-002로 본다 | — |
| API-WORKOUT-006 | DELETE | /api/v1/workout-sessions/{sessionId} | 필요 | 진행 중인 운동 취소 (v0.6: 진행 중 세션만) | REQ-WORKOUT-010, IF-WORKOUT-009 |
| API-EXERCISE-001 | GET | /api/v1/exercises?categoryId= | 필요 | 부위의 운동 목록 (v0.6: 검색 대신 부위) | REQ-EXERCISE-001, IF-EXERCISE-001 |
| API-EXERCISE-002 | POST | /api/v1/workout-sessions/{sessionId}/exercises | 필요 | 세션에 운동 추가 (v0.6: 이미 있으면 200) | REQ-EXERCISE-001, IF-EXERCISE-002 |
| API-EXERCISE-003 | DELETE | /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId} | 필요 | 세션에서 운동 삭제 | REQ-EXERCISE-002, IF-EXERCISE-002 |
| API-EXERCISE-004 | GET | /api/v1/exercise-categories | 필요 | 부위 목록과 종목 수 | REQ-EXERCISE-001, IF-EXERCISE-003 |
| API-SET-001 | POST | /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets | 필요 | 세트 추가 | REQ-SET-001, IF-SET-001 |
| API-SET-002 | PUT | /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setId} | 필요 | 세트 수정 | REQ-SET-002, IF-SET-001 |
| API-SET-003 | DELETE | /api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets/{setId} | 필요 | 세트 삭제 | REQ-SET-003, IF-SET-001 |
| ~~API-SET-004~~ | ~~GET~~ | ~~/api/v1/exercises/{exerciseId}/last-record~~ | — | **폐기 (v0.11)** — API-SET-006으로 대체 | ~~REQ-SET-005~~ |
| ~~API-SET-005~~ | ~~DELETE~~ | ~~/api/v1/workout-sessions/{sessionId}/exercises/{sessionExerciseId}/sets~~ | — | **폐기 (v0.11)** — 덮어쓰기 없음 | ~~REQ-SET-005~~ |
| API-SET-006 | GET | /api/v1/exercises/{exerciseId}/records?page= | 필요 | 종목의 이전 기록, 한 페이지에 완료 세션 하나 | REQ-SET-006, IF-SET-004 |
| API-WORKOUT-007 | GET | /api/v1/workout-days?month=YYYY-MM | 필요 | 월별 운동한 날과 부위 | REQ-WORKOUT-007, IF-WORKOUT-007 |
| API-WORKOUT-008 | GET | /api/v1/workout-days/{date} | 필요 | 날짜별 운동 기록 | REQ-WORKOUT-008, IF-WORKOUT-008 |
| API-WORKOUT-009 | DELETE | /api/v1/workout-days/{date} | 필요 | 그날 완료된 기록 삭제 | REQ-WORKOUT-005, IF-WORKOUT-006 |
| API-EXERCISE-005 | POST | /api/v1/exercises | 필요 | 종목 추가 (v0.9) | REQ-EXERCISE-004, IF-EXERCISE-004 |
| API-EXERCISE-006 | PUT | /api/v1/exercises/{exerciseId} | 필요 | 종목 수정 (v0.9) | REQ-EXERCISE-005, IF-EXERCISE-004 |
| API-EXERCISE-007 | DELETE | /api/v1/exercises/{exerciseId} | 필요 | 종목과 그 기록 삭제 (v0.9) | REQ-EXERCISE-006, IF-EXERCISE-004 |
| API-EXERCISE-008 | PUT | /api/v1/exercise-categories/{categoryId}/exercise-order | 필요 | 부위의 종목 순서 변경 (v0.10) | REQ-EXERCISE-007, IF-EXERCISE-005 |

(살아 있는 API 20개)

### 5.2 응답 모델
WorkoutSessionResponse는 workout-session 5.2, WorkoutSetResponse는 workout-exercise 5.2, WorkoutDayResponse는 workout-history 5.2, ExerciseResponse는 workout-exercise-manage 5.1에 있다.

### 5.3 API 상세
해당 없음 — 각 문서 5.3에 있다.

---

## 6. 데이터 설계
이름 규칙·논리 타입·제약 종류·`users` 테이블은 공통 설계 6장을 따른다.

### 6.1 ERD
```
exercise_category 1 ──── N default_exercise   (기본 목록 템플릿, v0.9)
        1
        └──── N exercise N ──── 1 users        (사용자 소유, v0.9)
                   1                 1
                   │ (함께 삭제)       │
                   N                 N
       workout_session_exercise N ──── 1 workout_session
                   1                 1
                   N                 └──── N workout_media   (workout-media 설계)
              workout_set
```

### 6.2 테이블 정의

#### exercise_category — 근거: DATA-006 (v0.6 신규)
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | DB 생성 (공통 DEC-ARCH-018) | PK | DEC-ARCH-010 |
| name | 문자열(20) | N | | 부위명 (가슴, 등, 어깨, 하체) | DATA-006 |
| image_url | 문자열(300) | N | | 부위 이미지의 URL 경로. 예: `/images/exercise-categories/chest.jpg` | DATA-006, DEC-WORKOUT-008 |
| sort_order | 정수 | N | | 화면 순서(1부터) | DATA-006 |
| created_at | 시각 | N | 현재 시각 | | 공통 |

- PK: `id`
- 유일 `ux_exercise_category_name`: `name`
- 유일 `ux_exercise_category_sort_order`: `sort_order`
- 사용자는 이 테이블을 바꾸지 않는다(BR-009). 행은 스키마 변경 스크립트로만 넣고 고친다. 수정이 스크립트뿐이라 `updated_at`을 두지 않는다.

#### exercise — 근거: DATA-002
**v0.9: 아래 정의는 스키마 변경 4까지의 것이다.** 스키마 변경 5에서 이 테이블은 `default_exercise`(기본 목록 템플릿)로 이름이 바뀌고, 사용자 소유 `exercise`를 새로 만든다. 지금 정의는 workout-exercise-manage 6.2를 따른다.

(v0.6 변경: `category`·`image_url` 대신 부위 참조, 영문명·타깃·순서 추가)
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | DB 생성 (공통 DEC-ARCH-018) | PK | DEC-ARCH-010 |
| exercise_category_id | ID | N | | 부위 | DATA-002, DATA-006 |
| name | 문자열(100) | N | | 운동명 | DATA-002 |
| name_en | 문자열(100) | N | | 영문 운동명 | DATA-002 |
| target | 문자열(50) | N | | 타깃 설명 (예: 가슴 중부 타겟) | DATA-002 |
| sort_order | 정수 | N | | 부위 안 순서(1부터) | DATA-002 |
| created_at | 시각 | N | 현재 시각 | | 공통 |
| updated_at | 시각 | N | 현재 시각 | | 공통 |

- PK: `id`
- 참조(삭제 금지): `exercise_category_id → exercise_category.id`
- 유일 `ux_exercise_name`: `name` — 같은 운동이 다른 행으로 중복되지 않게 (요구사항 TODO-002 결정 이유)
- 유일 `ux_exercise_category_order`: `(exercise_category_id, sort_order)`
- 종목 이미지는 두지 않는다 (요구사항 TODO-018, BR-014 폐기)

#### workout_session — 근거: DATA-001 (v0.6 변경: `memo` 제거)
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | 애플리케이션 생성 | PK | DEC-ARCH-010 |
| user_id | ID | N | | 소유자 | BR-001, NFR-INTEG-001 |
| status | 열거(IN_PROGRESS, COMPLETED) | N | | 세션 상태 | DATA-001 |
| performed_date | 날짜 | N | | 수행 날짜(사용자 현지) | BR-004 |
| started_at | 시각 | N | | 시작 시각 | DATA-001 |
| ended_at | 시각 | Y | | 종료 시각, 진행 중이면 Null | DATA-001, BR-008 |
| created_at | 시각 | N | 현재 시각 | | 공통 |
| updated_at | 시각 | N | 현재 시각 | | 공통 |

- PK: `id`
- 참조(함께 삭제): `user_id → users.id` — 운영자가 계정을 삭제하면 그 사용자의 운동 기록도 모두 삭제 (auth BR-012, 공통 6.1, DEC-WORKOUT-014)
- 조건 검사 `ck_workout_session_ended`: `(status = IN_PROGRESS 이고 ended_at 없음) 또는 (status = COMPLETED 이고 ended_at 있음 이고 ended_at >= started_at)` — BR-008
- 조건부 유일 `ux_workout_session_user_in_progress`: `status = IN_PROGRESS`인 행 사이에서 `user_id` 유일 — BR-011. 위반은 제약 위반 변환으로 409

#### workout_session_exercise — 근거: DATA-003 (v0.6 변경: 같은 운동 중복 금지)
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | 애플리케이션 생성 | PK | DEC-ARCH-010 |
| workout_session_id | ID | N | | 소속 세션 | DATA-003 |
| exercise_id | ID | N | | 운동 | DATA-003, BR-009 |
| created_at | 시각 | N | 현재 시각 | 추가한 시각. 세션 안 운동 순서의 기준 | DATA-003, DEC-WORKOUT-002 |

- PK: `id`
- 참조(함께 삭제): `workout_session_id → workout_session.id` (NFR-INTEG-002)
- 참조(함께 삭제): `exercise_id → exercise.id` (v0.9: 삭제 금지에서 변경. 종목을 지우면 그 기록도 지운다, BR-025)
- 유일 `ux_workout_session_exercise_session_exercise`: `(workout_session_id, exercise_id)` — 한 세션에 같은 운동은 하나 (REQ-EXERCISE-001, DEC-WORKOUT-018)
- 수정되지 않는 행이라 `updated_at`이 없다.

#### workout_set — 근거: DATA-004 (변경 없음)
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | 애플리케이션 생성 | PK | DEC-ARCH-010 |
| workout_session_exercise_id | ID | N | | 소속 세션 운동 | DATA-004 |
| weight | 소수(6,2) | N | | 중량(kg) | BR-005 |
| repetitions | 정수 | N | | 반복 횟수 | BR-003 |
| created_at | 시각 | N | 현재 시각 | 추가한 시각. 세트 순서의 기준 | DATA-004, BR-007, BR-013 |
| updated_at | 시각 | N | 현재 시각 | | 공통 |

- PK: `id`
- 참조(함께 삭제): `workout_session_exercise_id → workout_session_exercise.id` (NFR-INTEG-002)
- 조건 검사 `ck_workout_set_weight`: `0 <= weight <= 1000` (BR-005)
- 조건 검사 `ck_workout_set_repetitions`: `1 <= repetitions <= 1000` (BR-003)

### 6.3 인덱스
| 인덱스 | 테이블 | 컬럼 | 대상 조회 | 근거 |
|-------|-------|-----|---------|-----|
| ux_exercise_category_name | exercise_category | (name), 유일 | 중복 방지 | DATA-006 |
| ux_exercise_category_sort_order | exercise_category | (sort_order), 유일 | 부위 목록 정렬 | API-EXERCISE-004 |
| ~~ux_exercise_name~~, ~~ux_exercise_category_order~~ | — | — | v0.9: `default_exercise`로 옮겨 이름이 바뀐다 | workout-exercise-manage 6.3 |
| ux_exercise_user_category_name | exercise | (user_id, exercise_category_id, 소문자(name)), 유일(대소문자 무시) | 같은 이름 확인, 부위의 본인 종목 목록, 부위별 종목 수 | BR-026, API-EXERCISE-001, API-EXERCISE-004 (workout-exercise-manage 6.3) |
| idx_workout_session_exercise_exercise | workout_session_exercise | (exercise_id) | 종목 삭제 시 함께 삭제할 행 찾기 | REQ-EXERCISE-006 |
| ux_workout_session_user_in_progress | workout_session | (user_id), 조건부 유일: status = IN_PROGRESS | 진행 중 세션 조회(API-WORKOUT-002), 시작 시 확인, cleanUp 대상 조회 | BR-011, BR-013 |
| idx_workout_session_user_performed | workout_session | (user_id, performed_date 내림차순, started_at 내림차순, id 내림차순) | 운동별 최근 수행일, 이전 기록(완료 세션 최근순 페이지), 월별 달력(날짜 범위), 날짜별 기록·삭제(같은 날짜), 가장 늦은 운동한 날 | REQ-EXERCISE-001, REQ-SET-006, REQ-WORKOUT-005, 007, 008, NFR-PERF-002 |
| ux_workout_session_exercise_session_exercise | workout_session_exercise | (workout_session_id, exercise_id), 유일 | 같은 운동 중복 확인, 세션별 운동 조회, 함께 삭제 | REQ-EXERCISE-001 |
| idx_workout_session_exercise_session | workout_session_exercise | (workout_session_id, created_at, id) | 세션 현황의 운동을 추가 순서로 조회 | REQ-WORKOUT-009 |
| idx_workout_set_session_exercise | workout_set | (workout_session_exercise_id, created_at, id) | 운동별 세트를 추가 순서로 조회, 세트 번호 계산, cleanUp의 세트 존재 확인, 함께 삭제 | REQ-WORKOUT-009, BR-013 |

운동별 최근 수행일은 사용자 세션에서 출발한다(idx_workout_session_user_performed → ux_workout_session_exercise_session_exercise). v0.9부터 종목을 지울 수 있어 `workout_session_exercise.exercise_id`에 인덱스를 둔다.

### 6.4 삭제 정책
- 요구사항 TODO-001 결정대로 실제 삭제한다. 보관 컬럼(`deleted_at`)은 두지 않는다.
- `workout_session` 삭제(운동 취소, 방치된 세션 자동 삭제) → `workout_session_exercise` → `workout_set`, 그리고 `workout_media`가 참조(함께 삭제)로 같은 트랜잭션에서 삭제된다 (NFR-INTEG-002). 파일은 커밋 후 작업으로 지운다 (workout-media 6.4).
- 계정(`users`) 삭제 → 그 사용자의 `workout_session`과 하위 데이터가 모두 참조(함께 삭제)로 삭제된다 (auth BR-012). 파일은 공통 10.7 절차.
- 종목(`exercise`) 삭제 → 참조(함께 삭제)로 모든 세션의 그 종목 `workout_session_exercise` → `workout_set`. 세트가 남지 않은 완료 세션은 서비스가 지운다(미디어 함께, 파일은 커밋 후). (v0.9, workout-exercise-manage 6.4)
- 계정 삭제 → `exercise`도 참조(함께 삭제)로 지워진다.
- `default_exercise`, `exercise_category`는 삭제하지 않는다.

### 6.5 스키마 변경 목록
인증 설계의 스키마 변경 1(users, login_session) 다음 순서로 적용한다. 스크립트 파일 규칙은 공통 설계 10.1을 따른다.

**v0.6: 운영 사용 전이라 2·3을 고쳐 다시 만든다 (사용자 결정, DEC-WORKOUT-016).** 배포 전에 운영 DB에서 스키마 변경 2·3의 적용 기록과 그 테이블을 지워야 한다(10.2 순서 0).

| 순서 | 변경 | 내용 |
|-----|-----|-----|
| 2 | 운동 부위·종목 생성과 초기 목록 | `exercise_category`, `exercise`(6.2), 6.3의 인덱스. 초기 데이터: 부위 4개(가슴 1, 등 2, 어깨 3, 하체 4, 이미지 `/images/exercise-categories/{chest,back,shoulders,legs}.jpg`)와 요구사항 부록 A의 종목 24개(순서·영문명·타깃 그대로) (요구사항 TODO-014 결정, D-TODO-WORKOUT-001 결정) |
| 3 | 운동 세션 생성 | `workout_session`(메모 없음), `workout_session_exercise`(중복 금지 유일 제약), `workout_set`과 6.2의 제약, 6.3의 인덱스 |
| 4 | workout_media 생성 | workout-media 설계 6.5 |
| 5 | 종목을 사용자 소유로 (v0.9) | `exercise` → `default_exercise`, 사용자 소유 `exercise` 생성, 기존 사용자에게 복사하고 세션 운동을 옮김, 참조를 함께 삭제로, 자동 동작 `trg_users_default_exercises`. 기존 기록을 지우지 않는다 (workout-exercise-manage 6.5, DEC-WORKOUT-028) |

부위 이미지 파일 4개는 공개 정적 파일로 함께 배포한다. 원본은 앱 저장소의 부위 이미지(`assets/images/groups/`)를 쓴다.

---
## 7. 인증 / 인가 및 보안 설계
인증 흐름·토큰·CORS·CSRF는 공통 설계 7장을 따른다.

### 7.1 API별 인증
- 5.1의 살아 있는 API 20개는 모두 인증 필요 (NFR-SEC-001).
- 공개 정적 파일 `GET /images/exercise-categories/**`는 인증 없이 허용한다. 부위 이미지만 있고 사용자 데이터가 없다 (DEC-WORKOUT-008). v0.5의 `/images/exercises/**`는 없앤다.

### 7.2 사용자별 데이터 접근 제한
- userId는 인증 필터가 확인한 로그인에서만 얻는다 (auth 설계 3.2).
- 경로의 `sessionId`: 세션 조회 → 없으면 404 → `user_id` 불일치면 403 `FORBIDDEN` (BR-001, ERR-003, DEC-WORKOUT-007).
- 경로의 `sessionExerciseId`, `setId`: 상위 리소스에 속하는지 확인한다(`workout_session_exercise.workout_session_id = sessionId`, `workout_set.workout_session_exercise_id = sessionExerciseId`). 속하지 않으면 404.
- 진행 중 세션 현황·운동별 최근 수행일·이전 기록·달력·날짜별 기록·날짜 단위 삭제·cleanUp은 모두 `user_id = userId` 조건을 가진다 (NFR-SEC-002). 날짜로 접근하는 API는 다른 사용자의 기록에 닿을 수 없다.
- 부위 목록은 모든 사용자에게 같은 기준 데이터다. 종목은 사용자 소유다(v0.9): 단건은 소유자 확인(없으면 404, 다르면 403), 목록·집계는 `exercise.user_id = userId` (workout-exercise-manage 7.2).

### 7.3 민감 데이터 / 로그
- 로그에는 이벤트 이름, userId, sessionId만 남긴다. 사진·동영상은 workout-media 7.3.

---
## 8. 예외 / 에러 처리 설계
응답 형식·상태 코드 정책·공통 에러 코드는 공통 설계 8장을 따른다.

### 8.1 에러 응답 예
```json
{
  "code": "WORKOUT_SESSION_HAS_NO_SETS",
  "message": "세트를 하나 이상 기록해야 운동을 완료할 수 있습니다.",
  "timestamp": "2026-10-05T18:00:00Z"
}
```

### 8.2 이 기능의 에러 코드
공통 예외만 둔다. 기능별 에러 코드는 각 문서 8.2에 있다.

| 요구사항 ERR | 에러 코드 | HTTP | 메시지 | 발생 위치 |
|-------------|----------|------|-------|----------|
| ERR-001 | UNAUTHORIZED (공통) | 401 | 로그인이 필요합니다. | 인증 필터 |
| ERR-003 | FORBIDDEN (공통) | 403 | 접근할 수 없는 데이터입니다. | WorkoutSessionService, ExerciseService 소유자 확인 |
| ERR-007 | WORKOUT_SESSION_NOT_EDITABLE | 409 | 완료된 운동 기록은 수정할 수 없습니다. | WorkoutSessionService (E) |
| ERR-009 | WORKOUT_SESSION_NOT_FOUND | 404 | 운동 기록을 찾을 수 없습니다. | WorkoutSessionService (E) |
| ERR-009 | SESSION_EXERCISE_NOT_FOUND | 404 | 운동 기록에서 해당 운동을 찾을 수 없습니다. | WorkoutSessionService |
| ERR-009 | WORKOUT_SET_NOT_FOUND | 404 | 세트를 찾을 수 없습니다. | WorkoutSessionService |
| ERR-009 | EXERCISE_CATEGORY_NOT_FOUND (v0.6 신규) | 404 | 운동 부위를 찾을 수 없습니다. | ExerciseService (API-EXERCISE-001) |

제약 위반 변환 대상: `ux_workout_session_user_in_progress` → 409 `WORKOUT_SESSION_ALREADY_IN_PROGRESS`, `ux_exercise_user_category_name` → 409 `EXERCISE_NAME_DUPLICATED`(v0.9). `ux_workout_session_exercise_session_exercise`는 세션 변경 잠금 안에서 먼저 확인하므로 정상 흐름에서 위반이 나지 않는다. 그 밖의 DB 제약 위반은 버그이므로 500으로 두고 ERROR 로그를 남긴다(공통 8.4).

---

## 9. 비기능 요구사항 설계
| NFR ID | 요구사항 | 설계 대응 | 확인 방법 |
|--------|---------|----------|----------|
| NFR-PERF-001 | 요청의 95%가 500ms 이내 | 요청당 쿼리 수 고정: 현황 1회(세션·운동·부위·세트 조인), 부위 목록 1회, 운동 목록 1회, cleanUp 3회(완료·미디어 정리·삭제). 모든 조회 조건에 6.3 인덱스 | 부하 테스트 p95 측정 (공통 D-TODO-ARCH-004) |
| NFR-PERF-002 | 기록이 많아도 성능 유지 | 최근 수행일·이전 기록·달력·날짜별 기록·날짜 삭제는 모두 `idx_workout_session_user_performed`의 사용자·날짜 범위로 좁힌 뒤 조인한다. 달력은 한 달, 날짜별은 하루만 읽는다. 요청당 쿼리: 이전 기록 2회(세션 수 + 그 페이지의 세트), 달력 2회(가장 늦은 날 + 그 달), 날짜별 2회(운동·세트 + 사진·동영상) | 사용자 1명에 세션 1,000건을 넣고 각 API 응답 시간 측정 |
| NFR-PERF-003 | 동시 1,000명 | 무상태 서버(공통 9장). 변경 잠금은 사용자 자신의 세션에만 걸려 사용자 간 경합이 없다 | 부하 테스트 (공통 D-TODO-ARCH-004) |
| NFR-SEC-001 | 인증된 사용자만 | 7.1, 모든 API 인증 필요 | 토큰 없는 요청이 API마다 401인지 테스트 |
| NFR-SEC-002 | 본인 데이터만 | 7.2 소유자 확인, 상위 리소스 소속 확인, 조회 조건 `user_id` | 다른 사용자 세션·세트에 대해 API마다 403/404 테스트 |
| NFR-AVAIL-001 | 장애 시 오류 반환, 부분 저장 없음 | 요청 하나 = 트랜잭션 하나, 삭제는 참조(함께 삭제)로 한 번에, 파일은 공통 2.6 순서, 오류는 공통 8장 형식 | 강제 예외 시 500 JSON과 데이터 무변경 테스트 |
| NFR-INTEG-001 | 없는 사용자의 기록 생성 불가 | 참조 `workout_session.user_id → users.id` | 없는 userId로 세션 생성 시 실패 테스트 |
| NFR-INTEG-002 | 삭제 시 하위 데이터 남지 않음 | 참조(함께 삭제) 3단 + 파일 커밋 후 삭제 | 취소 후 운동·세트·미디어 행 0건, 저장소 파일 없음 테스트 |
| NFR-INTEG-003 | 종목 삭제 시 기록 남지 않음 | 참조(함께 삭제) `workout_session_exercise.exercise_id` (workout-exercise-manage 9장) | 삭제 후 그 종목의 세션 운동·세트 0건 테스트 |
| NFR-LOG-001 | 주요 행위·자동 처리·오류 추적 | INFO 이벤트: `workout_session.started`, `.completed`, `.cancelled`, `.auto_completed`, `.auto_deleted`, `.deleted` (userId, sessionId), `exercise.deleted` (userId, exerciseId, deletedSetCount, deletedSessionCount, v0.9). 처리하지 못한 예외는 ERROR | 로그 출력 확인 테스트 |

---
## 10. 구현 구조 및 개발 전략

### 10.1 컴포넌트 구성
역할은 공통 설계 2.2, 패키지·파일 배치와 구현 기술은 공통 설계 10장을 따른다.

| 도메인 | 컴포넌트 | 역할 | 책임 |
|-------|---------|-----|-----|
| exercise | ExerciseController | API 진입점 | API-EXERCISE-001, API-EXERCISE-004 ~ 007, API-SET-006(종목 아래 경로) |
| exercise | ExerciseService | 서비스 | 부위 존재 확인, 목록 조회, 종목 추가·수정·삭제와 소유자 확인 (v0.9) |
| exercise | ExerciseRepository | 저장소 | 종목 단건·존재·소유 확인 (BR-009), 저장·수정·삭제, 변경 잠금 조회, 같은 이름 확인, 최대 순서 (v0.9) |
| exercise | ExerciseQueryRepository | 조회 저장소 | 부위 목록 + 본인 종목 수, 부위의 본인 종목 + 최근 수행일 |
| session | WorkoutSessionController | API 진입점 | `/workout-sessions/**` API 10개(세션·운동·세트) |
| session | WorkoutSessionService | 서비스 | 세션 묶음의 모든 쓰기와 규칙: 소유자·상태 확인, 변경 잠금, 완료 조건, 미디어 붙이기 호출, 취소, 현황 계산. 종목 삭제용 진행 중 세션 잠금과 빈 완료 세션 삭제 (v0.9) |
| session | ExpiredSessionCleaner | 서비스 (독립 트랜잭션) | REQ-WORKOUT-006 정리, 정리한 세션의 파일 삭제 등록 |
| session | WorkoutSessionRepository, WorkoutSessionExerciseRepository, WorkoutSetRepository | 저장소 | 행 저장·수정·삭제, 단건 조회, 세션 변경 잠금 조회 |
| session | WorkoutSessionQueryRepository | 조회 저장소 | 세션 현황 조회, 이전 기록 조회(세션 수, 페이지), cleanUp 조건부 일괄 갱신 |
| session | WorkoutDayController | API 진입점 | `/workout-days/**` API 3개 |
| session | WorkoutDayService | 서비스 | 날짜별 기록 합치기, 날짜 단위 삭제와 파일 삭제 요청 |
| session | WorkoutDayQueryRepository | 조회 저장소 | 월별 운동한 날·부위, 날짜의 세션·운동·세트, 날짜 단위 조건부 일괄 삭제 |

### 10.2 구현 순서
기능별 순서는 각 문서 10.2에 있다. 순서 번호는 분리 전 설계의 번호다.

| 순서 | 작업 | 관련 요구사항 | 완료 기준 |
|-----|-----|-------------|----------|
| 0 | 운영 DB 정리: 스키마 변경 2·3의 적용 기록과 `exercise`·`workout_*` 테이블 삭제 (DEC-WORKOUT-016) | — | 배포 시 새 2·3·4가 오류 없이 적용 |
| 1 | 스키마 변경 2·3 다시 쓰기 + 부위 이미지 정적 파일 | DATA-001 ~ 004, DATA-006 | 적용 성공, 부위 4개·종목 24개, 중복 운동 추가가 DB에서 거절 |

순서 0 ~ 11 모두 끝났다(2026-10-09).

### 10.3 테스트 포인트
- BR-011: 같은 사용자가 동시에 두 번 시작해도 진행 중 세션은 하나이고, 하나는 409.
- BR-004: `X-Time-Zone: Asia/Seoul`로 UTC 15:30(현지 다음 날 00:30)에 시작하면 `performedDate`가 현지 날짜.
- BR-010·BR-015: 가슴 2종목·등 1종목에 세트를 넣으면 `categories`가 가슴→등 순서, 부위별 `volume` = 그 부위 세트의 Σ(중량×반복), `summary.totalVolume` = 부위 합. 세트 없는 운동은 `categories`와 `exerciseCount`에서 빠짐.
- NFR-SEC-002: 다른 사용자의 세션 ID로 모든 세션 API를 호출하면 403. 다른 사용자의 세트 ID를 자기 세션 경로에 넣으면 404.

---

## 부록 A. 설계 결정 기록
기능별 결정은 각 문서 부록 A에 있다(부록 D).

| ID | 결정 | 이유 | 버린 대안 |
|----|-----|-----|----------|
| DEC-WORKOUT-001 | 세션·세션 운동·세트를 하나의 묶음으로 보고 `/workout-sessions/**` 아래 중첩 경로, API 진입점·서비스 각 1개로 다룬다 | 모든 변경이 세션 상태·소유자 확인을 거쳐야 한다. 확인 로직이 한 곳에 모인다 | 운동·세트별 컴포넌트: 같은 세션 확인을 여러 곳에서 반복 |
| DEC-WORKOUT-002 | 세트 번호와 세션 안 운동 순서를 저장하지 않고 추가한 시각(`created_at` 오름차순, 같으면 `id`)으로 계산 (v0.3: `id` 순서에서 변경, DEC-ARCH-010) | BR-007(빈 번호 없음)이 자동으로 지켜지고, 삭제 시 번호를 다시 쓰는 갱신이 없다. 변경 요청은 세션 변경 잠금으로 하나씩 처리되므로 같은 운동의 세트끼리 추가 시각이 겹치지 않는다 | 번호 컬럼 저장: 삭제할 때마다 재정렬 갱신, 동시 변경 시 중복 위험 |
| DEC-WORKOUT-003 | 세션 요약, 부위별·종목별 볼륨(DATA-005, BR-015)을 저장하지 않고 조회 결과로 계산 (v0.6: 부위별 추가) | 세트가 바뀔 때 요약을 함께 갱신할 필요가 없어 값이 어긋날 수 없다. 세션당 세트 수가 적어 계산 비용이 작다 | 요약 컬럼 저장: 세트 변경마다 갱신 필요 |
| DEC-WORKOUT-005 | 세션 변경 요청(운동·세트 변경, 완료, 취소)은 세션을 변경 잠금으로 조회 | 완료와 세트 추가가 동시에 와도 완료된 세션에 세트가 생기지 않는다. 잠금은 사용자 자신의 세션에만 걸려 경합이 거의 없다 | 낙관적 잠금(버전 비교): 충돌 시 재시도 처리가 앱까지 필요 |
| DEC-WORKOUT-006 | BR-011은 조회 확인 + 조건부 유일 제약, 위반을 409로 변환 | 조회 확인만으로는 동시 요청을 막지 못한다 | 사용자 행에 변경 잠금: users 테이블에 기능이 의존하게 됨 |
| DEC-WORKOUT-007 | 다른 사용자의 데이터는 403 | 요구사항 ERR-003("권한 오류")을 따른다. 존재 노출 문제는 부록 C-5 | 404로 숨김: 요구사항과 다름 |
| DEC-WORKOUT-010 | ~~JPA 엔티티 간 연관관계 매핑 없이 참조 ID만~~ **폐기 (v0.2)** — 공통 설계 DEC-ARCH-008로 옮김 | — | — |
| DEC-WORKOUT-013 | ~~반복 횟수에 소수가 오면 400~~ **폐기 (v0.2)** — 공통 설계 5장으로 옮김 | — | — |
| DEC-WORKOUT-014 | `workout_session.user_id`를 참조(함께 삭제)로 둔다 (v0.4) | 계정을 삭제하면 그 사용자의 운동 기록도 모두 삭제해야 한다(auth BR-012) | 삭제 금지 유지 + 운영자가 운동 기록부터 삭제: 절차가 길고 빠뜨리면 계정 삭제가 실패 |
| DEC-WORKOUT-016 | 스키마 변경 2·3을 고쳐 다시 만든다(운영 DB 초기화) (v0.6) | 사용자 결정. 운영에 지킬 운동 기록이 없다. 메모 제거·부위 테이블·초기 목록을 처음부터 맞는 스키마로 둔다 (공통 v0.8의 인증 V1 통합과 같은 판단) | 스키마 변경 4 이후로 추가: 쓰지 않는 단계와 기존 데이터 이전 작업이 남음 |

## 부록 B. 설계 미결정 사항
- **D-TODO-WORKOUT-001** ~~초기 운동 목록 투입과 이미지 파일~~ **결정됨 (v0.6):** 요구사항 TODO-014 결정(부록 A, 24개). 스키마 변경 2에 넣는다. 종목 이미지는 두지 않고 부위 이미지 4개만 둔다 (6.5)
- **D-TODO-WORKOUT-002** ~~운동 목록 페이지 나누기 필요 여부~~ **결정됨 (v0.6):** 부위당 5~8개라 페이지 없음 (DEC-WORKOUT-011)
- **D-TODO-WORKOUT-003** ~~Figma 화면과 4장 대조~~ **결정됨 (v0.7):** 2·3·4행 모두 4.2에 반영. Figma와 다른 점은 화면별 "Figma와 다른 점"
- **D-TODO-WORKOUT-004** ~~Figma 3·4행 기능의 설계~~ **결정됨 (v0.7):** 3.2, 4.2, 5장의 API-SET-004·005, API-WORKOUT-007·008·009

## 부록 C. 요구사항 피드백
번호는 분리 전 설계 부록 C의 번호다. 나머지는 각 문서 부록 C에 있다.

1. **BR-004 시간대 출처:** "사용자가 있는 지역의 날짜"를 알려면 시스템이 사용자 시간대를 알아야 하는데, 요구사항에 출처가 없다. (설계: 앱이 `X-Time-Zone` 헤더로 기기 시간대를 보낸다. DEC-ARCH-007)
2. ~~**메모 길이**~~ **해결 (v0.6):** 요구사항 TODO-020으로 메모를 두지 않는다.
5. **ERR-003과 2.2의 충돌 가능성:** 2.2는 "다른 사용자의 기록은 존재 여부와 관계없이 볼 수 없다"고 하는데, ERR-003의 403 응답은 그 ID의 기록이 **존재한다는 사실**을 드러낸다. 존재 자체를 숨기려면 다른 사용자의 기록도 404로 응답해야 한다. (설계: ERR-003대로 403. DEC-WORKOUT-007. workout-media 부록 C-1도 같은 문제)

## 부록 D. ID 색인
| 종류 | workout-common | workout-session | workout-exercise | workout-history | workout-exercise-manage |
|-----|---------------|-----------------|------------------|-----------------|-------------------------|
| API | — | API-WORKOUT-001, 002, 003, 006 | API-EXERCISE-001 ~ 004, API-SET-001 ~ 003, ~~004~~, ~~005~~, 006 | ~~API-WORKOUT-004, 005~~, API-WORKOUT-007, 008, 009 | API-EXERCISE-005 ~ 008 |
| 응답 모델 | — | WorkoutSessionResponse | WorkoutSetResponse | WorkoutDayResponse | ExerciseResponse |
| 설계 결정 | DEC-WORKOUT-001 ~ 003, 005 ~ 007, ~~010~~, ~~013~~, 014, 016 | DEC-WORKOUT-004, 015, 017 | DEC-WORKOUT-008, 009, 011, 012, 018, ~~021~~, ~~022~~, 032 | DEC-WORKOUT-019, 020 | DEC-WORKOUT-023 ~ 031 |
| 설계 미결정 | D-TODO-WORKOUT-001 ~ 004 (모두 결정) | — | — | — | — |
| 테이블 | 모두 (6장). `exercise`·`default_exercise`의 지금 정의는 workout-exercise-manage 6.2 | — | — | — | default_exercise, exercise |
