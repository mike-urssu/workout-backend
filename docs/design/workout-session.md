# 홈·운동 진행 설계 문서

- 문서 버전: v0.8
- 작성일: 2026-10-05
- 상태: 초안
- 요구사항: `docs/requirements/workout-session.md` (v0.7), 공통 `docs/requirements/workout-common.md` (v0.7)
- 공통 설계: `docs/design/architecture.md`, `docs/design/workout-common.md`
- Figma: 2행 `home-workout-start`(24:2), `home-muscle-selection`(4:8), `workout-complete-popup`(18:4), `로그아웃 확인 팝업`(83:104)
- 운동 기록 설계 묶음 (설계 ID와 요구사항 ID를 함께 쓴다, 색인은 `workout-common.md` 부록 D):
  - `workout-common.md` 공통 — 아키텍처, 세션 API 공통 앞단, 상태, 데이터, 보안, 에러, 비기능, 컴포넌트
  - `workout-session.md` 홈·운동 진행 (Figma 2행)
  - `workout-exercise.md` 운동 선택·세트 기록 (Figma 3행)
  - `workout-history.md` 운동 기록 달력 (Figma 4행)
  - `workout-exercise-manage.md` 운동 종목 관리 (Figma 6행)
- 변경 이력:
  - v0.2 ~ v0.7: `workout-common.md` 변경 이력 참고 (분리 전 `workout-record.md`)
  - v0.8 (2026-10-09) — `workout-record.md` 설계 v0.7을 요구사항 분리(v0.7)에 맞춰 `workout-common.md`, `workout-session.md`, `workout-exercise.md`, `workout-history.md`로 나눔. 설계 ID와 내용은 바꾸지 않았다

---

## 1. 설계 개요

### 1.1 목적
운동 세션 시작·완료(사진·동영상 첨부 포함)·취소, 방치된 세션 정리, 홈·완료 팝업이 쓰는 진행 중 현황을 API 4개로 구현하는 방법을 확정한다. 세션 안의 운동·세트 API는 workout-exercise, 테이블은 workout-common 6장에 있다.

### 1.2 설계 범위
- **포함:** REQ-WORKOUT-001, 002, 006, 009, 010, 화면 WO-001, WO-002
- **보류:** 없음
- **제외:** 요구사항 workout-common 8.1의 범위 밖 항목과 폐기된 요구사항.

### 1.3 대상 시스템
- Backend API: 도메인 `session`
- Mobile App: 화면 WO-001, WO-002

### 1.4 기술 스택
workout-common 1.4를 따른다. 방치된 세션 정리는 주기 작업 없이 처리한다(DEC-WORKOUT-004).

### 1.5 설계 원칙
workout-common 1.5를 따른다.

### 1.6 요구사항 ↔ 설계 추적표
이 문서의 요구사항 ID만 둔다. 공통 ID(BR-001 등, DATA, NFR)는 workout-common 1.6에 있다.

| 요구사항 ID | 요구사항 | 설계 반영 위치 | 구현 | 테스트 |
|------------|---------|--------------|-----|-------|
| REQ-WORKOUT-001 | 운동 세션 시작 | 3.2, API-WORKOUT-001, workout_session | | |
| REQ-WORKOUT-002 | 운동 세션 완료 | 3.2, API-WORKOUT-003, workout-media 3.2 | | |
| REQ-WORKOUT-006 | 방치된 세션 자동 완료 | 3.2, ExpiredSessionCleaner, DEC-WORKOUT-004 | | |
| REQ-WORKOUT-009 | 진행 중인 세션 현황 | 3.2, API-WORKOUT-002, 5.2 WorkoutSessionResponse | | |
| REQ-WORKOUT-010 | 진행 중인 운동 취소 | 3.2, API-WORKOUT-006 | | |
| BR-012 | 세트가 있어야 완료 | 3.5, API-WORKOUT-003 | | |
| BR-013 | 6시간 지나면 시스템이 정리 | 3.2, 3.5, ExpiredSessionCleaner | | |
| BR-019 | 세션 없이 종목을 고르면 자동 시작 | 3.2, workout-exercise 4.2 EX-001 (앱이 시작 후 추가) | | |
| ERR-008 | 완료된 세션 재완료·취소 | 8.2 WORKOUT_SESSION_ALREADY_COMPLETED | | |
| ERR-011 | 진행 중 세션이 있는데 시작 | 8.2 WORKOUT_SESSION_ALREADY_IN_PROGRESS | | |
| ERR-012 | 세트 없는 세션 완료 | 8.2 WORKOUT_SESSION_HAS_NO_SETS | | |
| IF-WORKOUT-001 | 세션 시작 | API-WORKOUT-001 | | |
| IF-WORKOUT-002 | 진행 중 세션과 현황 조회 | API-WORKOUT-002 | | |
| IF-WORKOUT-003 | 세션 완료 | API-WORKOUT-003 | | |
| IF-WORKOUT-009 | 진행 중 세션 취소 | API-WORKOUT-006 | | |
| WO-001 | 홈 | 4.2 | | |
| WO-002 | 운동 완료 팝업 | 4.2, workout-media 4.2 | | |

---

## 2. 시스템 아키텍처
workout-common 2장을 따른다.
- 추가 컴포넌트 ExpiredSessionCleaner(REQ-WORKOUT-006)와 요청 흐름은 workout-common 2장에 있다. 운동 기록의 모든 세션·운동·날짜 API가 앞단에서 이 컴포넌트를 부른다.

---

## 3. 기능 설계

### 3.1 기능 목록
| 요구사항 ID | 기능 | API | 주요 컴포넌트 |
|------------|-----|-----|-------------|
| REQ-WORKOUT-001 | 운동 세션 시작 | API-WORKOUT-001 | WorkoutSessionService.start |
| REQ-WORKOUT-009 | 진행 중 세션 현황 | API-WORKOUT-002 | WorkoutSessionService.getInProgress |
| REQ-WORKOUT-002 | 운동 세션 완료 (사진·동영상 포함) | API-WORKOUT-003 | WorkoutSessionService.complete |
| REQ-WORKOUT-010 | 진행 중인 운동 취소 | API-WORKOUT-006 | WorkoutSessionService.cancel |
| REQ-WORKOUT-006 | 방치된 세션 자동 완료 | (모든 세션·운동 API 앞단) | ExpiredSessionCleaner.cleanUp |

### 3.2 기능별 처리 흐름
모든 흐름의 공통 앞단 (A) 인증, (B) 방치된 세션 정리, (E) 편집 가능 세션 확보는 workout-common 3.2를 따른다.

#### REQ-WORKOUT-001 운동 세션 시작 (API-WORKOUT-001)
1. (A), (B).
2. `X-Time-Zone` 헤더를 IANA 시간대로 해석한다. 없거나 잘못되면 400 `VALIDATION_FAILED` (ERR-010).
3. 트랜잭션 시작. 사용자의 `IN_PROGRESS` 세션이 있으면 409 `WORKOUT_SESSION_ALREADY_IN_PROGRESS` (BR-011, ERR-011).
4. `started_at` = 현재 시각(UTC), `performed_date` = `started_at`을 헤더 시간대로 바꾼 날짜 (BR-004), `status` = `IN_PROGRESS`로 저장한다.
5. 3을 동시에 통과한 두 요청 중 하나는 조건부 유일 제약 `ux_workout_session_user_in_progress` 위반이 난다. **제약 위반 변환**으로 409 `WORKOUT_SESSION_ALREADY_IN_PROGRESS`를 반환한다 (BR-011).
6. 커밋, 로그 `workout_session.started`, 201과 WorkoutSessionResponse(운동 없음).
- **BR-019 자동 시작:** 별도 API를 두지 않는다. 진행 중 세션 없이 운동 선택 화면에서 종목을 고르면, 앱이 이 API를 부른 뒤 API-EXERCISE-002를 부른다. 이 API가 409를 주면(다른 기기에서 이미 시작) 앱은 API-WORKOUT-002로 세션 ID를 얻어 추가한다 (DEC-WORKOUT-017).

#### REQ-WORKOUT-009 진행 중 세션 현황 (API-WORKOUT-002)
1. (A), (B).
2. 사용자의 `IN_PROGRESS` 세션을 찾는다. 없으면 204 (오류 아님).
3. 조회 저장소에서 한 쿼리로 세션의 운동(`workout_session_exercise.created_at`, `id` 순), 운동·부위 정보, 세트(`workout_set.created_at`, `id` 순)를 가져온다.
4. 서비스에서 계산한다 (BR-007, BR-010, BR-015, DEC-WORKOUT-003):
   - 운동별: 세트 번호(1부터), `setCount`, `totalRepetitions`, `volume` = Σ(중량 × 반복 횟수), `firstSetAt` = 가장 먼저 추가된 세트의 `created_at`(세트가 없으면 null)
   - 부위별(`categories[]`): 세트가 1개 이상인 운동만 부위로 묶어 `setCount`, `volume`, 그 부위 운동의 `sessionExerciseIds`(추가 순). 부위 순서는 `exercise_category.sort_order`
   - 합계(`summary`): `exerciseCount`(세트가 1개 이상인 운동 수), `totalSets`, `totalRepetitions`, `totalVolume`, `durationSeconds`(진행 중이면 null)
5. 200과 WorkoutSessionResponse. 불러왔지만 적용하지 않은 값은 서버에 없으므로 들어가지 않는다(BR-017, DEC-WORKOUT-022).

#### REQ-WORKOUT-002 운동 세션 완료 (API-WORKOUT-003)
1. (A), (B).
2. 요청 본문 `mediaIds`를 검증한다(생략 시 빈 목록, 5개 이하, 중복 없음). 위반 시 400 `VALIDATION_FAILED` (workout-media BR-005).
3. 트랜잭션 시작, (E) — 상태가 `COMPLETED`면 409 `WORKOUT_SESSION_ALREADY_COMPLETED` (ERR-008).
4. 세션의 세트 수를 센다. 0이면 409 `WORKOUT_SESSION_HAS_NO_SETS` (BR-012, ERR-012).
5. 사진·동영상을 붙인다: workout-media 3.2 (2) — `mediaIds`가 모두 이 세션의 미디어인지 확인(아니면 400), 순서 저장, 고르지 않은 임시 미디어 행 삭제와 그 파일의 커밋 후 삭제 등록.
6. `status` = `COMPLETED`, `ended_at` = 현재 시각(저장하기·건너뛰기를 누른 시각). `ended_at ≥ started_at`은 항상 참이고 조건 검사 제약으로도 보장한다 (BR-008).
7. 커밋, 로그 `workout_session.completed`(사진·동영상 개수 포함), 200과 WorkoutSessionResponse.
- 운동 완료 팝업을 닫는 것은 API를 부르지 않는다. 세션은 진행 중으로 남는다.

#### REQ-WORKOUT-010 진행 중인 운동 취소 (API-WORKOUT-006)
1. (A), (B).
2. 트랜잭션 시작, (E) — 상태가 `COMPLETED`면 409 `WORKOUT_SESSION_ALREADY_COMPLETED` (ERR-008). 완료된 기록은 날짜 단위 삭제(REQ-WORKOUT-005, API-WORKOUT-009)로만 지운다.
3. 세션을 삭제한다. 하위 운동·세트·미디어 행은 참조(함께 삭제)로 같은 트랜잭션에서 삭제된다 (NFR-INTEG-002).
4. 세션 접두어의 파일 삭제를 커밋 후 작업으로 등록한다 (workout-media 6.4).
5. 커밋, 로그 `workout_session.cancelled`, 204.

#### REQ-WORKOUT-006 방치된 세션 자동 완료 (ExpiredSessionCleaner.cleanUp)
독립 트랜잭션에서 조회 저장소의 **조건부 일괄 갱신**으로 처리한다. 기준 시각은 애플리케이션의 현재 시각(공통 10.1)이다.
1. 완료 처리 — 대상: `user_id = userId`, `status = IN_PROGRESS`, `started_at < 현재 시각 - 6시간`, 세트가 1개 이상인 세션. 변경: `status = COMPLETED`, `ended_at` = 그 세션의 세트 중 가장 늦은 `created_at`, `updated_at` = 현재 시각 (BR-013). 처리한 세션 ID를 돌려받는다.
2. 1에서 완료한 세션의 미디어 행(모두 임시 파일이다)을 삭제하고 키를 돌려받는다. 사용자가 "저장하기"를 누르지 않았으므로 붙인 파일이 없다 (workout-media 6.4).
3. 삭제 처리 — 대상: 같은 조건이면서 세트가 하나도 없는 세션. 삭제한다 (BR-013, BR-012). 하위 운동·미디어 행은 참조(함께 삭제)로 삭제된다. 처리한 세션 ID를 돌려받는다.
4. 2의 미디어 파일과 3의 세션 접두어 파일 삭제를 커밋 후 작업으로 등록한다.
5. 처리한 세션마다 로그 `workout_session.auto_completed` / `workout_session.auto_deleted` (NFR-LOG-001).
6. 조건부 일괄 갱신이라 여러 요청이 동시에 실행해도 결과가 같다(멱등).

### 3.3 주요 시나리오
#### 홈에서 운동을 시작해 완료하기 (Figma 2행)
```
App                              API                                    DB
 │ GET exercise-categories         │ 부위 4개 + 종목 수                      │
 │ GET in-progress                 │ cleanUp(userId) → 진행 중 없음           │
 │◀───────── 204 ─────────────────│                                        │
 │ [운동 시작] POST workout-sessions │ workout_session 저장 (IN_PROGRESS)     │
 │ X-Time-Zone: Asia/Seoul         │────────────────────────────────────────▶│
 │◀───────── 201 ─────────────────│                                        │
 │ (부위 → 종목 → 세트 기록: 3행 화면, API-EXERCISE-001·002, API-SET-001)        │
 │ [홈] GET in-progress            │ 세션 현황(운동별 세트 수·볼륨, 부위별)       │
 │◀───────── 200 ─────────────────│                                        │
 │ [운동 종료] → 완료 팝업 (같은 응답의 summary·categories 사용)                    │
 │ [저장하기] POST .../media × n    │ (workout-media)                        │
 │ POST .../complete {mediaIds}    │ 세션 변경 잠금 → 세트 수 확인 → 미디어 붙임 → 완료 │
 │◀───────── 200 ─────────────────│                                        │
```

### 3.4 상태 변화
workout-common 3.4를 따른다.

### 3.5 비즈니스 규칙 구현
이 문서의 규칙만 둔다. 공통 규칙은 workout-common 3.5에 있다.

| BR ID | 규칙 | 강제 위치 | 방법 | 위반 시 |
|-------|-----|----------|-----|--------|
| BR-012 | 세트가 있어야 완료 | 서비스 | 완료 전 세트 수 확인 | 409 WORKOUT_SESSION_HAS_NO_SETS |
| BR-013 | 6시간 지나면 정리 | ExpiredSessionCleaner | 세션 API 앞단에서 독립 트랜잭션 + 조건부 일괄 갱신 (DEC-WORKOUT-004) | — |
| BR-019 | 세션 없이 종목을 고르면 자동 시작 | 앱 | 앱이 API-WORKOUT-001 → API-EXERCISE-002 순으로 호출. 시작이 409면 진행 중 세션을 조회해 그 세션에 추가 (DEC-WORKOUT-017) | — |

### 3.6 기능 간 의존관계
- 완료(REQ-WORKOUT-002)와 시작의 응답은 현황 조회(REQ-WORKOUT-009)와 같은 형태를 재사용한다.
- 완료의 사진·동영상(`mediaIds`)과 취소·정리의 파일 삭제는 workout-media 설계에 의존한다.
- 공통 의존관계는 workout-common 3.6에 있다.

---

## 4. 화면 / API 연계 설계

### 4.1 화면-API 매핑
| 화면 ID | 화면 | 사용자 행동 | API |
|--------|-----|-----------|-----|
| WO-001 | 홈 | 진입 | API-EXERCISE-004 `GET /api/v1/exercise-categories`, API-WORKOUT-002 `GET /api/v1/workout-sessions/in-progress` |
| WO-001 | 홈 | 운동 시작 | API-WORKOUT-001 `POST /api/v1/workout-sessions` |
| WO-001 | 홈 | 운동 취소 (진행 중 카드) | API-WORKOUT-006 `DELETE /api/v1/workout-sessions/{sessionId}` |
| WO-001 | 홈 | 운동 종료 | (호출 없음) API-WORKOUT-002 응답으로 WO-002를 연다 |
| WO-001 | 홈 | 로그아웃 | auth API-AUTH-002 (auth 설계 4.2) |
| WO-002 | 운동 완료 팝업 | 저장하기 / 건너뛰기 | workout-media API-MEDIA-001(파일마다), API-WORKOUT-003 `POST /api/v1/workout-sessions/{sessionId}/complete` |

### 4.2 화면별 연계 상세
공통 응답 처리(401·403·404·400·409·500·503)는 공통 설계 4장을 따른다. 아래는 화면별로 추가되는 처리다.

#### WO-001 홈 (Figma `home-workout-start`, `home-muscle-selection`)
- 진입 조건: 로그인 완료, 하단 탭 "홈"
- 필요 데이터:
  - 오늘 날짜: 기기 날짜
  - 부위 카드: API-EXERCISE-004 → `name`, `imageUrl`, `exerciseCount` ("12개 운동")
  - 진행 중 카드: API-WORKOUT-002 → `startedAt`(경과 시간은 앱이 1초마다 계산), `exercises[]` 중 `setCount` > 0인 것의 `name`, `setCount` ("3세트 완료")
- 사용자 입력: 없음
- API 호출: 진입할 때마다 API-WORKOUT-002(다른 화면에서 돌아올 때 최신 현황). API-EXERCISE-004는 앱 실행 중 한 번(기준 데이터라 잘 바뀌지 않는다). "운동 시작" 시 API-WORKOUT-001(`X-Time-Zone` = 기기 시간대). "운동 취소"는 확인 후 API-WORKOUT-006.
- 성공 처리:
  - API-WORKOUT-002 200 → 진행 중 카드와 "운동 종료" 버튼 / 204 → "운동 시작" 버튼
  - 시작 201 → 홈에 머물며 진행 중 카드 표시(경과 00:00:00). 부위 카드를 눌러 EX-001
  - 취소 204 → 진행 중 카드를 없애고 "운동 시작" 버튼
  - 부위 카드 → EX-001 (`categoryId` 전달). 세션이 없어도 갈 수 있다(BR-019)
  - "운동 종료" → 지금 가진 API-WORKOUT-002 응답으로 WO-002를 연다
- 실패 처리:
  - 시작 409 `WORKOUT_SESSION_ALREADY_IN_PROGRESS` → API-WORKOUT-002 다시 호출해 진행 중 카드 표시
  - 취소 409 `WORKOUT_SESSION_ALREADY_COMPLETED` → "이미 완료된 운동이에요"(6시간 경과로 자동 완료) 후 API-WORKOUT-002 다시 호출 / 404 → 이미 취소됨, 다시 호출
- 로딩 상태: 진입 조회 중 시작·종료 버튼 비활성
- 빈 상태: 204 → 진행 중 카드 없음. 진행 중인데 세트가 하나도 없음 → "기록된 운동" 아래 비어 있음 안내, "운동 종료"를 누르면 WO-002 대신 "세트를 하나 이상 기록하거나 운동을 취소하세요" 안내(BR-012, ERR-012)
- Figma와 다른 점: "운동 취소" 버튼이 아직 디자인에 없다(요구사항 8.2, TODO-019 결정). 위치는 진행 중 카드 안으로 둔다

#### WO-002 운동 완료 팝업 (Figma `workout-complete-popup`)
- 진입 조건: 홈에서 "운동 종료", 진행 중 세션에 세트 ≥ 1
- 필요 데이터 (API-WORKOUT-002 응답, 추가 호출 없음):
  - 날짜: `performedDate` (예: 2026년 9월 27일 (일))
  - 운동 시간 배지: 앱이 `startedAt`부터 팝업을 연 시각까지 계산 (예: 47분 23초). 저장 후 `summary.durationSeconds`가 확정값
  - 총 볼륨: `summary.totalVolume`
  - 부위 카드: `categories[]`의 `name`, `volume`, `setCount` ("4,320kg · 6세트"). 펼치면 `sessionExerciseIds`로 `exercises[]`를 찾아 `name`, `volume`, `setCount` ("2,340kg · 3세트")
- 사용자 입력: 오운완 인증 사진·동영상 (workout-media 4.2)
- API 호출: 저장하기·건너뛰기 → workout-media 4.2의 순서로 API-MEDIA-001, API-WORKOUT-003
- 성공 처리: 완료 200 → 팝업 닫고 홈, 진행 중 카드 사라짐
- 실패 처리: 409 `WORKOUT_SESSION_ALREADY_COMPLETED` → 자동 완료 안내 후 홈 / 409 `WORKOUT_SESSION_HAS_NO_SETS` → 다른 기기에서 세트를 지운 경우, 안내 후 팝업 닫기 / 404 → 다른 기기에서 취소됨, 홈 / 사진·동영상 오류는 workout-media 4.2
- 로딩 상태: 업로드·완료 중 버튼 비활성
- 빈 상태: 해당 없음 (세트 ≥ 1일 때만 열린다)
- 팝업을 닫으면 API 호출 없이 홈으로, 세션은 진행 중

---

## 5. API 설계
URL·필드·날짜·페이지 규칙은 공통 설계 5장을 따른다. 운동 기록 API 전체 목록은 workout-common 5.1에 있다.

### 5.1 API 목록
| API ID | Method | URL | 인증 | 설명 | 관련 요구사항 |
|--------|--------|-----|-----|-----|-------------|
| API-WORKOUT-001 | POST | /api/v1/workout-sessions | 필요 | 세션 시작 | REQ-WORKOUT-001, IF-WORKOUT-001 |
| API-WORKOUT-002 | GET | /api/v1/workout-sessions/in-progress | 필요 | 진행 중 세션과 현황 | REQ-WORKOUT-009, IF-WORKOUT-002 |
| API-WORKOUT-003 | POST | /api/v1/workout-sessions/{sessionId}/complete | 필요 | 세션 완료(사진·동영상 붙임) | REQ-WORKOUT-002, IF-WORKOUT-003, workout-media IF-MEDIA-001 |
| API-WORKOUT-006 | DELETE | /api/v1/workout-sessions/{sessionId} | 필요 | 진행 중인 운동 취소 (v0.6: 진행 중 세션만) | REQ-WORKOUT-010, IF-WORKOUT-009 |

### 5.2 응답 모델
**WorkoutSessionResponse** (API-WORKOUT-001, 002, 003) — v0.6에서 WorkoutSessionDetailResponse를 바꿈(DEC-WORKOUT-015)
```json
{
  "id": "0199b2d4-6c40-7a3e-8f21-3c5d7e9a1b02",
  "status": "IN_PROGRESS",
  "performedDate": "2026-09-27",
  "startedAt": "2026-09-27T05:00:00Z",
  "endedAt": null,
  "exercises": [
    {
      "sessionExerciseId": "0199b2d5-0a11-7b42-9c03-4d6e8f0a2c13",
      "exerciseId": "0199a0f1-2b30-7c55-8d14-5e7f9a1b3d24",
      "name": "벤치프레스",
      "nameEn": "Bench Press",
      "target": "가슴 중부 타겟",
      "category": { "id": "0199a0f0-1a20-7b44-8c03-4d6e8f0a2c01", "name": "가슴" },
      "sets": [
        { "id": "0199b2d6-1f22-7d63-a025-6f8a0b2c4e35", "setNumber": 1, "weight": 60.00, "repetitions": 12, "createdAt": "2026-09-27T05:23:10Z" },
        { "id": "0199b2da-3e83-7e74-b136-7a9b1c3d5f46", "setNumber": 2, "weight": 70.00, "repetitions": 10, "createdAt": "2026-09-27T05:26:02Z" }
      ],
      "setCount": 2,
      "totalRepetitions": 22,
      "volume": 1420.00,
      "firstSetAt": "2026-09-27T05:23:10Z"
    }
  ],
  "categories": [
    { "id": "0199a0f0-1a20-7b44-8c03-4d6e8f0a2c01", "name": "가슴", "setCount": 2, "volume": 1420.00,
      "sessionExerciseIds": ["0199b2d5-0a11-7b42-9c03-4d6e8f0a2c13"] }
  ],
  "summary": {
    "durationSeconds": null,
    "exerciseCount": 1,
    "totalSets": 2,
    "totalRepetitions": 22,
    "totalVolume": 1420.00
  }
}
```
| 필드 | 설명 | 근거 |
|-----|-----|-----|
| `status` | `IN_PROGRESS` / `COMPLETED` | DATA-001 |
| `endedAt` | 진행 중이면 null | DATA-001 |
| `exercises` | 추가한 순서. 세트가 없는 운동도 들어간다(EX-002). 홈·오늘 한 운동은 `setCount` > 0만 보여준다 | REQ-EXERCISE-001, REQ-WORKOUT-009 |
| `exercises[].nameEn`, `target` | 영문명, 타깃 설명 (EX-002 머리글). 둘 다 null일 수 있다. 이름·부위는 지금 종목 값이다 (workout-exercise-manage 4.3) | DATA-002, BR-024, BR-028 |
| `exercises[].sets` | 추가한 순서, `setNumber`는 1부터 연속, `createdAt`은 추가 시각(EX-002 세트 시각) | BR-007, DATA-004 |
| `exercises[].volume` | Σ(중량 × 반복 횟수), 소수 둘째 자리 | BR-015 |
| `exercises[].firstSetAt` | 첫 세트의 추가 시각(EX-003). 세트가 없으면 null | REQ-WORKOUT-009 |
| `categories` | 세트가 1개 이상인 운동을 부위로 묶은 것. 부위 순서(`exercise_category.sort_order`) | BR-015, WO-002 |
| `summary.durationSeconds` | `endedAt - startedAt` 초. 진행 중이면 null | DATA-005 |
| `summary.exerciseCount` | 세트가 1개 이상인 운동 수 | BR-010 |
| `summary.totalSets` / `totalRepetitions` / `totalVolume` | 모든 세트 수 / 반복 횟수 합 / Σ(중량 × 반복 횟수) | BR-010 |
| ID 필드 | UUID 문자열 (공통 5장) | DEC-ARCH-010 |

### 5.3 API 상세

#### API-WORKOUT-001 운동 세션 시작
- 목적: 진행 중 운동 세션을 만든다.
- Method / URL: `POST /api/v1/workout-sessions`
- 인증: 필요
- 관련 요구사항: REQ-WORKOUT-001, IF-WORKOUT-001, BR-004, BR-011, BR-019

Request
- Header: `X-Time-Zone: Asia/Seoul` (필수)
- Body: 없음

Response `201 Created` — WorkoutSessionResponse (`status` = `IN_PROGRESS`, `exercises` = [], `categories` = [], `summary`의 합계 0, `durationSeconds` null). `Location: /api/v1/workout-sessions/in-progress`

Validation
| 필드 | 타입 | 필수 | 규칙 | 근거 |
|-----|-----|-----|-----|-----|
| X-Time-Zone (헤더) | string | Y | IANA 시간대 데이터베이스의 이름(예: `Asia/Seoul`) | BR-004 |

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 400 | VALIDATION_FAILED | 헤더 없음·잘못된 시간대 | ERR-010 |
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 409 | WORKOUT_SESSION_ALREADY_IN_PROGRESS | 진행 중 세션이 이미 있음 | ERR-011 |

#### API-WORKOUT-002 진행 중 세션과 현황
- 목적: 홈·오늘 한 운동·운동 완료 팝업·운동 기록 화면에 진행 중 세션과 현황을 준다.
- Method / URL: `GET /api/v1/workout-sessions/in-progress`
- 인증: 필요
- 관련 요구사항: REQ-WORKOUT-009, IF-WORKOUT-002, BR-010, BR-011, BR-015

Request: 없음

Response `200 OK` — WorkoutSessionResponse / `204 No Content` — 진행 중 세션 없음

Validation: 해당 없음

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |

#### API-WORKOUT-003 운동 세션 완료
- 목적: 진행 중 세션을 완료해 기록으로 확정하고, 고른 사진·동영상을 붙인다.
- Method / URL: `POST /api/v1/workout-sessions/{sessionId}/complete`
- 인증: 필요
- 관련 요구사항: REQ-WORKOUT-002, IF-WORKOUT-003, BR-002, BR-008, BR-010, BR-012, workout-media REQ-MEDIA-001, BR-003, BR-005, BR-007

Request (본문 생략 가능 — 생략하면 `mediaIds` = [])
```json
{ "mediaIds": ["019a1c2e-5f00-7d11-9a3b-2c4d6e8f0a12", "019a1c2f-0b10-7e22-8b4c-3d5e7f9a1b23"] }
```

Response `200 OK` — WorkoutSessionResponse (`status` = `COMPLETED`)

Validation
| 필드 | 타입 | 필수 | 규칙 | 근거 |
|-----|-----|-----|-----|-----|
| sessionId (경로) | UUID | Y | UUID 형식 | 공통 5장 |
| mediaIds | UUID 배열 | N | 0~5개, 중복 없음, 모두 이 세션의 미디어 | workout-media BR-005, BR-002 |

메모(`memo`)는 받지 않는다 (v0.6, 요구사항 TODO-020).

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 400 | VALIDATION_FAILED | `mediaIds` 6개 이상·중복·이 세션의 미디어가 아님 | workout-media BR-005 |
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 403 | FORBIDDEN | 다른 사용자의 세션 | ERR-003 |
| 404 | WORKOUT_SESSION_NOT_FOUND | 세션 없음 | ERR-009 |
| 409 | WORKOUT_SESSION_ALREADY_COMPLETED | 이미 완료됨 | ERR-008 |
| 409 | WORKOUT_SESSION_HAS_NO_SETS | 세트가 하나도 없음 | ERR-012 |

#### API-WORKOUT-006 진행 중인 운동 취소
- 목적: 진행 중 세션과 그 운동·세트·임시 사진·동영상을 지운다.
- Method / URL: `DELETE /api/v1/workout-sessions/{sessionId}`
- 인증: 필요
- 관련 요구사항: REQ-WORKOUT-010, IF-WORKOUT-009, BR-001, NFR-INTEG-002

Response `204 No Content`

Validation: 경로 변수가 UUID 형식이 아니면 400

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 403 | FORBIDDEN | 다른 사용자의 세션 | ERR-003 |
| 404 | WORKOUT_SESSION_NOT_FOUND | 세션 없음·이미 취소됨 | ERR-009 |
| 409 | WORKOUT_SESSION_ALREADY_COMPLETED | 완료된 세션 (v0.6: 완료된 기록은 이 API로 지우지 않는다) | ERR-008 |

---

## 6. 데이터 설계
workout-common 6장을 따른다. 이 문서의 기능은 `workout_session`, `workout_session_exercise`, `workout_set`과 workout-media의 `workout_media`를 쓴다.

---

## 7. 인증 / 인가 및 보안 설계
workout-common 7장을 따른다.

---

## 8. 예외 / 에러 처리 설계
응답 형식·상태 코드 정책은 공통 설계 8장, 공통 에러 코드(ERR-001, 003, 007, 009)는 workout-common 8.2를 따른다.

### 8.1 에러 응답 예
workout-common 8.1을 따른다.

### 8.2 이 기능의 에러 코드
| 요구사항 ERR | 에러 코드 | HTTP | 메시지 | 발생 위치 |
|-------------|----------|------|-------|----------|
| ERR-008 | WORKOUT_SESSION_ALREADY_COMPLETED | 409 | 이미 완료된 운동입니다. | WorkoutSessionService.complete, cancel |
| ERR-011 | WORKOUT_SESSION_ALREADY_IN_PROGRESS | 409 | 이미 진행 중인 운동이 있습니다. | WorkoutSessionService.start (조회 확인 + 제약 위반 변환) |
| ERR-012 | WORKOUT_SESSION_HAS_NO_SETS | 409 | 세트를 하나 이상 기록해야 운동을 완료할 수 있습니다. | WorkoutSessionService.complete |

---

## 9. 비기능 요구사항 설계
workout-common 9장을 따른다.

---

## 10. 구현 구조 및 개발 전략

### 10.1 컴포넌트 구성
workout-common 10.1을 따른다.

### 10.2 구현 순서
| 순서 | 작업 | 관련 요구사항 | 완료 기준 |
|-----|-----|-------------|----------|
| 3 | 응답 모델 변경(WorkoutSessionResponse: `categories`, `volume`, `firstSetAt`, 세트 `createdAt`) | REQ-WORKOUT-009, BR-015 | 부위별·종목별 볼륨·세트 수 계산 테스트 |
| 5 | 운동 취소(API-WORKOUT-006을 진행 중 전용으로) | REQ-WORKOUT-010, ERR-008 | 진행 중 204·하위 행 0건, 완료된 세션 409 테스트 |
| 7 | 완료의 `mediaIds`, 취소·cleanUp의 파일 삭제 (workout-media 10.2와 함께) | REQ-WORKOUT-002, REQ-WORKOUT-006, workout-media BR-007 | workout-media 10.2 완료 기준 |

모두 구현되었다(2026-10-09).

### 10.3 테스트 포인트
- BR-013: `started_at`을 6시간 전으로 만든 세션 — 세트가 있으면 다음 요청에서 `COMPLETED`이고 `ended_at` = 마지막 세트의 `created_at`, 임시 미디어 행·파일 삭제. 세트가 없으면 삭제됨. 정리 직후 새 세션을 시작할 수 있다.
- BR-013 + BR-002: 6시간 지난 세션에 세트를 추가하면 409 `WORKOUT_SESSION_NOT_EDITABLE`, 취소하면 409 `WORKOUT_SESSION_ALREADY_COMPLETED`.
- BR-012 + 동시성: 완료 요청과 세트 추가가 동시에 와도 완료된 세션에 세트가 생기지 않는다.

---

## 부록 A. 설계 결정 기록
| ID | 결정 | 이유 | 버린 대안 |
|----|-----|-----|----------|
| DEC-WORKOUT-004 | 방치된 세션 정리(REQ-WORKOUT-006)를 주기 작업 없이, 세션 관련 API 처리 전에 요청한 사용자 것만 독립 트랜잭션으로 실행 | 사용자가 결과를 보는 시점(다음 요청)에 항상 정리되어 있다. 주기 작업 실행 환경과 여러 서버 간 중복 실행 문제가 없다. **한계:** 사용자가 다시 접속하지 않으면 6시간 넘은 `IN_PROGRESS` 행과 그 임시 파일이 남는다. 통계도 요청한 사용자 것만 보므로(workout-stats BR-001) 영향이 없다 | 주기 작업: 실행 간격만큼 늦게 정리되어 결국 요청 시 확인도 필요하고, 여러 서버 실행 대비가 필요 |
| DEC-WORKOUT-015 | 세션 응답을 WorkoutSessionResponse 하나로 두고, 현황(부위별 `categories`, 종목별 `volume`·`firstSetAt`, 세트 `createdAt`)을 함께 담는다. 볼륨 이름을 `volume`/`totalVolume`으로 통일 (v0.6) | 홈·오늘 한 운동·완료 팝업·운동 기록 화면이 같은 세션의 다른 부분을 보여주므로 API 하나로 충분하다(요청 수 감소). 화면과 요구사항이 "볼륨"이라 부르므로 이름을 맞춘다(v0.5의 `totalWeight`, `totalSets`(운동별)는 바꾼다) | 화면마다 다른 응답: API가 늘고 계산이 흩어짐 |
| DEC-WORKOUT-017 | BR-019(세션 없이 종목 선택 시 자동 시작)는 별도 API 없이 앱이 시작 → 추가 순으로 호출 | 기존 API 두 개로 된다. 서버에 "시작하면서 추가" 같은 겹치는 경로를 두지 않는다 | 운동 추가 API가 세션이 없으면 자동 생성: 경로에 세션 ID가 있어 맞지 않고, 시간대 헤더가 추가 API에도 필요해짐 |

## 부록 B. 설계 미결정 사항
없음. 결정된 항목은 workout-common 부록 B에 있다.

## 부록 C. 요구사항 피드백
번호는 분리 전 `workout-record.md` 설계 부록 C의 번호다.

7. **운동 취소 버튼 위치:** 요구사항 TODO-019는 "홈의 진행 중인 운동"에서 취소한다고 했고 Figma 2행에는 아직 버튼이 없다. (설계: 진행 중 카드 안에 둔다. 디자인 추가 필요)
8. **홈의 부위별 종목 수:** Figma 홈은 "12개 운동"처럼 디자인 시안 숫자를 보여준다. 실제로는 요구사항 부록 A의 수(가슴 5, 등 6, 어깨 5, 하체 8)가 나온다. (설계: DB의 종목 수)
