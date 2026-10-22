# 운동 종목 관리 설계 문서

- 문서 버전: v0.3
- 작성일: 2026-10-10
- 상태: 초안
- 요구사항: `docs/requirements/workout-exercise-manage.md` (v0.5), 공통 `docs/requirements/workout-common.md` (v0.12)
- 공통 설계: `docs/design/architecture.md` (v0.14), `docs/design/workout-common.md` (v0.9)
- Figma: 6행 `종목 관리 목록`(131:531), `종목 추가`(131:629), `종목 수정`(131:680), `종목 삭제 확인`(131:729). 진입 버튼은 3행 `exercise-list`의 `manage-exercises-btn`(131:3)
- 운동 기록 설계 묶음 (설계 ID와 요구사항 ID를 함께 쓴다, 색인은 `workout-common.md` 부록 D):
  - `workout-common.md` 공통 — 아키텍처, 세션 API 공통 앞단, 상태, 데이터, 보안, 에러, 비기능, 컴포넌트
  - `workout-session.md` 홈·운동 진행 (Figma 2행)
  - `workout-exercise.md` 운동 선택·세트 기록 (Figma 3행)
  - `workout-history.md` 운동 기록 달력 (Figma 4행)
  - `workout-exercise-manage.md` 운동 종목 관리 (Figma 6행)
- 변경 이력:
  - v0.1 (2026-10-10) — 최초 작성. 종목을 사용자 소유로 바꾸고(스키마 변경 5), 종목 추가·수정·삭제 API 3개를 더한다
  - v0.2 (2026-10-10) — 요구사항 v0.4 반영. 종목 순서 변경 API-EXERCISE-008 추가(REQ-EXERCISE-007, BR-031·032, ERR-018, DEC-WORKOUT-029 ~ 031). 스키마 변경 없음
  - v0.3 (2026-10-22) — 이전 기록 API가 API-SET-004에서 API-SET-006으로 바뀐 것을 반영(workout-exercise v0.11). 소유 확인 방식은 같다

---

## 1. 설계 개요

### 1.1 목적
종목 목록을 사용자마다 따로 두고, 사용자가 종목을 추가·수정·삭제하고 부위 안 순서를 바꾸는 API 4개(API-EXERCISE-005 ~ 008)를 설계한다. 지금 모든 사용자가 함께 쓰는 종목 테이블은 기본 목록 템플릿이 되고, 사용자 소유 종목 테이블을 새로 둔다. 운영자가 계정을 만들면 DB 자동 동작이 기본 목록을 복사해 넣는다. 기존 기록은 스키마 변경 5에서 각 사용자의 종목으로 옮긴다. 기존 종목 조회 API는 본인 목록만 보도록 바꾼다(4.3).

### 1.2 설계 범위
- **포함:** REQ-EXERCISE-003 ~ 007, BR-022 ~ 032, ERR-016 ~ 018, IF-EXERCISE-004, IF-EXERCISE-005, 화면 EX-004 ~ EX-006, NFR-INTEG-003, NFR-LOG-001(종목 삭제)
- **영향을 받는 기존 설계:** API-EXERCISE-001, 002, 004, API-SET-006 (workout-exercise, v0.3 전에는 API-SET-004), API-STATS-002, 003 (workout-stats), 테이블과 스키마 변경 (workout-common 6장). 바뀌는 내용은 4.3과 6장에 있고, 각 문서에도 반영했다.
- **보류:** 없음
- **제외:** 요구사항 8.1의 제외 항목(부위 순서 바꾸기, 기본 순서로 되돌리기·자동 정렬, 타깃 설명 입력, 부위 변경, 기본 목록으로 되돌리기, 기본 목록 변경을 기존 사용자에게 반영, 공유, 합치기, 삭제 취소, 검색)

### 1.3 대상 시스템
- Backend API: 도메인 `exercise`(종목 추가·수정·삭제, 본인 목록 조회), `session`(종목 삭제에 따른 세션 정리)
- Database: `default_exercise`(지금의 `exercise`를 이름 변경), `exercise`(새로 만듦, 사용자 소유), `workout_session_exercise`(참조 대상 변경), `users`(자동 동작 추가)
- Mobile App: 화면 EX-004 ~ EX-006, EX-001(진입 버튼, 빈 상태)

### 1.4 기술 스택
workout-common 1.4를 따른다. 새로 필요한 기술 능력은 없다. DB 자동 동작은 이미 쓰고 있다(공통 DEC-ARCH-013, 로그인 종료).

### 1.5 설계 원칙
1. **종목은 사용자 소유 데이터다:** 종목 조회와 변경은 모두 `exercise.user_id = userId` 조건을 가진다. 다른 사용자의 종목 ID로는 아무것도 읽거나 바꿀 수 없다.
2. **지난 기록은 종목을 ID로 가리킨다:** 이름·부위를 고치면 지난 기록, 이전 기록 조회, 통계가 자동으로 새 값을 쓴다(BR-024). 기록 쪽에 이름이나 부위를 복사해 두지 않는다.
3. **기본 목록은 계정을 만들 때 DB가 넣는다:** 계정은 운영자가 SQL로만 만든다(auth DEC-AUTH-001). 애플리케이션은 그 순간을 알 수 없으므로 DB 자동 동작으로 보장한다(공통 DEC-ARCH-013과 같은 판단, DEC-WORKOUT-023).

### 1.6 요구사항 ↔ 설계 추적표
| 요구사항 ID | 요구사항 | 설계 반영 위치 | 구현 | 테스트 |
|------------|---------|--------------|-----|-------|
| REQ-EXERCISE-003 | 종목 관리 목록 조회 | 3.2, API-EXERCISE-001(본인 목록), API-EXERCISE-004 | | |
| REQ-EXERCISE-004 | 종목 추가 | 3.2, API-EXERCISE-005, exercise | | |
| REQ-EXERCISE-005 | 종목 수정 | 3.2, API-EXERCISE-006 | | |
| REQ-EXERCISE-006 | 종목 삭제 | 3.2, API-EXERCISE-007, 6.4 | | |
| BR-022 | 종목 목록은 사용자마다 | 3.5, exercise.user_id, trg_users_default_exercises, 6.5 스키마 변경 5 | | |
| BR-023 | 부위·종목명 필수, 공백 제거, 30자·50자 | 3.5, 5.2 Validation, exercise.name 문자열(30), name_en 문자열(50), ck_exercise_name, ck_exercise_name_en | | |
| BR-024 | 수정이 지난 기록에도 반영 | 1.5, 3.5, 참조 workout_session_exercise.exercise_id | | |
| BR-025 | 삭제하면 그 종목의 기록도 삭제 | 3.2, 3.5, 참조(함께 삭제) workout_session_exercise.exercise_id | | |
| BR-026 | 같은 부위에 같은 종목명 없음 | 3.5, ux_exercise_user_category_name, EXERCISE_NAME_DUPLICATED | | |
| BR-027 | 추가·부위 변경 종목은 맨 뒤 | 3.2, 3.5, DEC-WORKOUT-025 | | |
| BR-028 | 타깃 설명은 기본 종목만, 부위 바꾸면 지움 | 3.2, 3.5, exercise.target | | |
| BR-029 | 세트가 남지 않은 완료 세션 삭제 | 3.2 REQ-EXERCISE-006의 6, 3.5 | | |
| BR-030 | 마지막 종목도 삭제 가능, 빈 상태 | 3.5, 4.2 EX-001·EX-004 | | |
| REQ-EXERCISE-007 | 종목 순서 변경 | 3.2, API-EXERCISE-008, exercise.sort_order | | |
| BR-031 | 순서는 부위마다 사용자가 정하고 모든 목록이 같은 순서 | 3.5, exercise.sort_order, 4.3 정렬 | | |
| BR-032 | 새 순서는 그 부위 본인 종목을 빠짐없이 한 번씩 | 3.2 REQ-EXERCISE-007의 4, 3.5 | | |
| ERR-016 | 종목 입력값 오류 | 8.2 VALIDATION_FAILED | | |
| ERR-017 | 같은 이름의 종목 | 8.2 EXERCISE_NAME_DUPLICATED | | |
| ERR-018 | 새 순서가 지금 목록과 맞지 않음 | 8.2 EXERCISE_ORDER_OUTDATED | | |
| IF-EXERCISE-004 | 종목 추가·수정·삭제 | API-EXERCISE-005, 006, 007 | | |
| IF-EXERCISE-005 | 부위의 종목 순서를 한 번에 변경 | API-EXERCISE-008 | | |
| NFR-INTEG-003 | 종목 삭제 시 기록이 남지 않음 | 6.4, 9장 | | |
| NFR-LOG-001 | 종목 삭제 추적 | 9장 `exercise.deleted` | | |
| EX-004 | 종목 관리 (순서 바꾸기 포함) | 4.2 | | |
| EX-005 | 종목 추가 | 4.2 | | |
| EX-006 | 종목 수정 | 4.2 | | |

공통 규칙 중 이 문서에서 바뀌는 것: BR-009(본인 목록의 종목만 세션에 추가)는 workout-common 3.5, BR-002의 예외(종목 삭제)는 3.5 BR-025. 공통 예외 ERR-003, ERR-009에 종목이 더해진 것은 8.2.

---

## 2. 시스템 아키텍처
workout-common 2장을 따른다. 이 기능에서 추가되는 것:
- **DB 자동 동작 `trg_users_default_exercises`:** `users`에 행이 추가되면 `default_exercise` 전체를 그 사용자의 `exercise`로 복사한다(BR-022). 운영자 SQL 경로만 지나는 동작이다(DEC-WORKOUT-023).
- **종목 삭제의 세션 정리:** ExerciseService가 종목을 지우고, 비게 된 완료 세션의 삭제와 파일 삭제는 WorkoutSessionService에 맡긴다. 날짜 단위 삭제(API-WORKOUT-009)와 같은 방식이다.

```
운영자 SQL: INSERT users ─▶ trg_users_default_exercises ─▶ exercise (default_exercise 복사, DB가 ID 생성)

요청 ─▶ 인증 필터(userId) ─▶ ExerciseController (API 진입점)
                               ├─▶ (삭제만) ExpiredSessionCleaner.cleanUp(userId)
                               └─▶ ExerciseService [트랜잭션]
                                      ├─ 추가·수정: ExerciseRepository (수정·삭제는 종목에 변경 잠금)
                                      ├─ 순서 변경: 그 부위의 본인 종목을 모두 변경 잠금 → 목록 비교 → sort_order 다시 매김
                                      └─ 삭제: WorkoutSessionService.lockInProgress(userId)
                                               → 종목 삭제 (참조로 세션 운동·세트 함께 삭제)
                                               → WorkoutSessionService.deleteSessionsLeftEmpty(...)  [커밋 후 파일 삭제]
```

---

## 3. 기능 설계

### 3.1 기능 목록
| 요구사항 ID | 기능 | API | 주요 컴포넌트 |
|------------|-----|-----|-------------|
| REQ-EXERCISE-003 | 종목 관리 목록, 부위별 종목 수 | API-EXERCISE-001, API-EXERCISE-004 (본인 목록으로 변경) | ExerciseQueryRepository.findByCategory, findCategories |
| REQ-EXERCISE-004 | 종목 추가 | API-EXERCISE-005 | ExerciseService.create |
| REQ-EXERCISE-005 | 종목 수정 | API-EXERCISE-006 | ExerciseService.update |
| REQ-EXERCISE-006 | 종목 삭제 | API-EXERCISE-007 | ExerciseService.delete, WorkoutSessionService.deleteSessionsLeftEmpty |
| REQ-EXERCISE-007 | 종목 순서 변경 | API-EXERCISE-008 | ExerciseService.reorder |

### 3.2 기능별 처리 흐름
공통 앞단 (A) 인증, (B) 방치된 세션 정리는 workout-common 3.2를 따른다.

**종목 입력 정리·검증** (추가·수정에서 함께 쓰는 단계, 이하 "(V)"):
1. `name`, `nameEn`의 앞뒤 공백을 지운다. 지운 뒤 `nameEn`이 빈 문자열이면 값이 없는 것(null)으로 본다 (BR-023).
2. 검증: `categoryId` 필수, UUID / `name` 필수, 지운 뒤 1~30자 / `nameEn` 있으면 지운 뒤 1~50자. 위반 시 400 `VALIDATION_FAILED`, `errors[].field`에 필드 (ERR-016).
3. `categoryId`의 부위가 없으면 400 `VALIDATION_FAILED`, `errors[].field` = `categoryId` (ERR-016, DEC-WORKOUT-026).

**같은 이름 확인** (이하 "(D)"): 이 사용자의, 저장할 부위의 종목 중 소문자로 바꾼 `name`이 같은 종목이 있으면(수정이면 자기 자신은 뺀다) 409 `EXERCISE_NAME_DUPLICATED` (BR-026, ERR-017). 동시 요청은 유일 제약 `ux_exercise_user_category_name`이 막고, 그 위반을 제약 위반 변환으로 같은 409로 바꾼다.

#### REQ-EXERCISE-003 종목 관리 목록 조회 (API-EXERCISE-001, API-EXERCISE-004)
새 API를 두지 않는다. 운동 선택과 종목 관리는 같은 목록을 보여준다(요구사항 REQ-EXERCISE-003 "순서는 EX-001과 같다").
1. API-EXERCISE-004(부위와 종목 수), API-EXERCISE-001(부위의 종목)을 그대로 쓴다. 두 API 모두 `exercise.user_id = userId`인 종목만 센다·고른다(4.3).
2. 종목 관리 화면은 API-EXERCISE-001 응답의 `lastPerformedDate`를 쓰지 않는다.

#### REQ-EXERCISE-004 종목 추가 (API-EXERCISE-005)
1. (A). 세션을 건드리지 않으므로 (B)는 하지 않는다.
2. (V).
3. (D).
4. `sort_order` = 이 사용자의 그 부위 종목 중 가장 큰 `sort_order` + 1. 종목이 없으면 1 (BR-027).
5. `exercise`를 저장한다. ID는 애플리케이션이 만든다(공통 DEC-ARCH-019). `target`은 비운다 (BR-028).
6. 201과 ExerciseResponse.

#### REQ-EXERCISE-005 종목 수정 (API-EXERCISE-006)
1. (A).
2. (V).
3. 경로의 종목을 **변경 잠금**으로 조회한다. 없으면 404 `EXERCISE_NOT_FOUND` (ERR-009). `user_id`가 다르면 403 `FORBIDDEN` (ERR-003). 잠금은 같은 종목의 삭제(REQ-EXERCISE-006)와 순서를 맞추기 위한 것이다.
4. (D). 부위를 바꾸면 새 부위에서 확인한다.
5. 부위가 바뀌면 `sort_order` = 새 부위의 가장 큰 `sort_order` + 1(BR-027), `target` = null(BR-028). 부위가 같으면 `sort_order`, `target`을 그대로 둔다.
6. `exercise_category_id`, `name`, `name_en`, `updated_at`을 저장한다. 세션 운동은 종목 ID만 가리키므로 지난 기록·진행 중 세션은 따로 고치지 않는다 (BR-024).
7. 200과 ExerciseResponse.

#### REQ-EXERCISE-006 종목 삭제 (API-EXERCISE-007)
1. (A), (B). 6시간이 지난 진행 중 세션을 먼저 정리해야 4의 판단이 맞다.
2. `WorkoutSessionService.lockInProgress(userId)`: 이 사용자의 진행 중 세션이 있으면 **변경 잠금**으로 조회한다. 그 세션에 세트 추가·완료가 동시에 들어와도 삭제와 섞이지 않는다. 잠금 순서는 항상 세션 → 종목이다(DEC-WORKOUT-027).
3. 경로의 종목을 변경 잠금으로 조회한다. 없으면 404 `EXERCISE_NOT_FOUND`(ERR-009, 이미 지운 종목 포함). `user_id`가 다르면 403 `FORBIDDEN` (ERR-003).
4. 이 종목을 가진 **완료된** 세션의 ID와, 지워질 세트 수를 조회한다(로그용).
5. 종목을 삭제한다. 참조(함께 삭제)로 그 종목의 세션 운동과 세트가 모든 세션(완료·진행 중)에서 함께 지워진다 (BR-025, NFR-INTEG-003). 진행 중 세션에서는 그 종목이 빠진다(REQ-EXERCISE-002와 같은 결과).
6. `WorkoutSessionService.deleteSessionsLeftEmpty(userId, 4의 세션 ID)`: 그중 세트가 하나도 남지 않은 세션을 조건부 일괄 삭제로 지우고, 지운 세션 ID를 돌려받는다. 사진·동영상 행은 참조(함께 삭제)로 지워지고, 파일은 커밋 후 작업으로 지운다(workout-media 6.4, API-WORKOUT-009와 같은 방식) (BR-029). 진행 중 세션은 4에서 고르지 않았으므로 세트가 없어져도 남는다.
7. 커밋. 로그 `exercise.deleted`(userId, exerciseId, deletedSetCount, deletedSessionCount), 지운 세션마다 `workout_session.deleted`. 204.
- 2 ~ 6은 한 트랜잭션이다. 중간에 실패하면 아무것도 지워지지 않는다 (NFR-AVAIL-001).
- 부위의 마지막 종목이어도 막지 않는다 (BR-030).

#### REQ-EXERCISE-007 종목 순서 변경 (API-EXERCISE-008)
1. (A). 세션을 건드리지 않으므로 (B)는 하지 않는다.
2. 본문 검증: `exerciseIds` 필수(빈 목록 허용), 원소는 UUID. 위반 시 400 `VALIDATION_FAILED`. 경로 `categoryId`가 UUID 형식이 아니면 400.
3. 이 사용자의 그 부위 종목(`user_id = userId`, `exercise_category_id = categoryId`)을 **모두 변경 잠금**으로 조회한다. 같은 부위의 순서 변경·수정·삭제와 하나씩 처리된다 (DEC-WORKOUT-031).
4. `exerciseIds`가 3의 종목 ID들과 같은 집합이고 겹치는 ID가 없는지 확인한다(개수가 같고, 겹침이 없고, 모두 3에 있음). 아니면 아무것도 바꾸지 않고 409 `EXERCISE_ORDER_OUTDATED` (BR-032, ERR-018). 빠진 종목, 두 번 나온 종목, 다른 부위·다른 사용자의 종목, 없는 부위(3이 비어 있음), 그 사이 다른 기기에서 추가·삭제·이동된 경우가 모두 여기에 걸린다 (DEC-WORKOUT-029, 030).
5. `exerciseIds`의 순서대로 `sort_order` = 1, 2, 3 … 으로 저장하고 `updated_at`을 바꾼다 (BR-031). 이름·부위·지난 기록은 그대로다.
6. 204.
- 3이 비어 있고 `exerciseIds`도 비어 있으면(빈 부위) 바꿀 것이 없으므로 204다.

### 3.3 주요 시나리오
```
App                         API                                   DB
 │ EX-001 "종목 관리"          │                                     │
 │ GET exercise-categories   │ 부위 4개 + 본인 종목 수                 │
 │ GET exercises?categoryId= │ 본인 종목 (sort_order, created_at, id)  │
 │                           │                                     │
 │ [추가] POST exercises      │ (V) (D) sort_order = 최대+1, 저장      │
 │◀── 201 / 400 / 409 ────────│                                     │
 │                           │                                     │
 │ [수정] PUT exercises/{id}  │ 종목 잠금, 소유 확인, (V) (D)             │
 │                           │ 부위 바뀌면 맨 뒤 + target 지움           │
 │◀── 200 / 400 / 403 / 404 / 409                                   │
 │                           │                                     │
 │ [삭제] 확인 팝업 → DELETE exercises/{id}                           │
 │                           │ cleanUp → 진행 중 세션 잠금 → 종목 잠금     │
 │                           │ 종목 삭제 (세션 운동·세트 함께)             │
 │                           │ 비게 된 완료 세션 삭제 (미디어 함께)         │
 │◀── 204 ────────────────────│ 커밋 후 파일 삭제                       │
 │ 목록·종목 수 다시 조회        │                                     │
 │                           │                                     │
 │ [순서] 옮긴 뒤 PUT exercise-categories/{id}/exercise-order          │
 │                           │ 그 부위 본인 종목 모두 잠금, 목록 비교      │
 │                           │ 같으면 sort_order = 1..n               │
 │◀── 204 / 409 ──────────────│ 409면 앱이 목록을 다시 조회              │
```

### 3.4 상태 변화
종목에는 상태가 없다(있다/없다). 세션 상태는 workout-common 3.4를 따르고, 이 기능이 더하는 경로는 하나다.

```
[COMPLETED] ──종목 삭제로 세트가 0개가 됨 (REQ-EXERCISE-006)──▶ (삭제)
[IN_PROGRESS] ──종목 삭제로 세트가 0개가 됨──▶ [IN_PROGRESS] (그대로)
```

| 작업 | 진행 중 세션에 그 종목이 있을 때 | 완료된 세션에 그 종목이 있을 때 |
|-----|------------------------------|------------------------------|
| 종목 수정 | ○ 기록한 세트 그대로, 이름·부위만 새 값으로 보임 | ○ 지난 기록에 새 값이 보임 |
| 종목 삭제 | ○ 세션에서 빠짐, 세션은 남음 | ○ 세트 삭제, 비면 세션 삭제 (BR-002의 예외) |

### 3.5 비즈니스 규칙 구현
| BR ID | 규칙 | 강제 위치 | 방법 | 위반 시 |
|-------|-----|----------|-----|--------|
| BR-022 | 종목 목록은 사용자마다 | DB, 서비스, 조회 조건 | `exercise.user_id` 참조(함께 삭제). 새 계정은 DB 자동 동작 `trg_users_default_exercises`가 기본 목록을 복사. 단건은 조회 후 `user_id` 비교, 목록·집계는 조회 조건 `user_id = userId` | 403 FORBIDDEN |
| BR-023 | 부위·종목명 필수, 공백 제거, 30자·50자 | 서비스 (V), DB | 공백 정리 후 요청 검증(필수, 길이). 논리 타입 문자열(30)·문자열(50) + 조건 검사 `ck_exercise_name`, `ck_exercise_name_en`(앞뒤 공백 없음, 1자 이상) | 400 VALIDATION_FAILED |
| BR-024 | 수정이 지난 기록에도 반영 | 데이터 구조 | 세션 운동은 `exercise_id`만 가진다. 이름·부위·영문명은 조회할 때 `exercise`에서 읽는다 | 위반이 생길 수 없음 |
| BR-025 | 삭제하면 그 종목의 기록도 삭제 | 서비스, DB | 확인은 앱의 팝업(EX-004). 참조(함께 삭제) `workout_session_exercise.exercise_id → exercise.id`. 완료된 세션의 행도 지우는 유일한 경로다 | — |
| BR-026 | 같은 부위에 같은 종목명 없음 | 서비스 (D), DB | 사전 확인 + 유일(대소문자 무시) `ux_exercise_user_category_name` (공통 6.3), 위반을 제약 위반 변환으로 409 | 409 EXERCISE_NAME_DUPLICATED |
| BR-027 | 추가·부위 변경 종목은 맨 뒤 | 서비스 | `sort_order` = 그 부위의 최대 + 1. 동시에 추가해 같은 값이 나오면 `created_at`, `id` 순 (DEC-WORKOUT-025) | — |
| BR-028 | 타깃 설명은 기본 종목만 | 서비스, API | 추가는 `target` = null, 부위가 바뀌는 수정도 null. 요청에 `target` 필드가 없다 | — |
| BR-029 | 세트가 남지 않은 완료 세션 삭제 | 서비스 | REQ-EXERCISE-006의 6. 완료된 세션만 고른다 | — |
| BR-030 | 마지막 종목도 삭제 가능 | 서비스, 앱 | 삭제할 때 개수를 확인하지 않는다. 앱은 빈 목록일 때 빈 상태를 보여준다(4.2) | — |
| BR-031 | 순서는 부위마다 사용자가 정함, 모든 목록이 같은 순서 | 서비스, 조회 | 순서 변경은 경로의 부위 하나만 받는다(부위 간 이동은 API-EXERCISE-006). 운동 선택·종목 관리·통계 목록은 모두 `sort_order`, `created_at`, `id` 순(4.3) | — |
| BR-032 | 새 순서는 그 부위 본인 종목을 빠짐없이 한 번씩 | 서비스 | REQ-EXERCISE-007의 3·4: 잠근 목록과 집합 비교 | 409 EXERCISE_ORDER_OUTDATED |

### 3.6 기능 간 의존관계
- 스키마 변경 5(6.5)가 먼저다. 그 전에는 종목이 공용이라 소유자 확인을 할 수 없다.
- 기존 API의 본인 목록 전환(4.3)은 추가·수정·삭제 API보다 먼저 구현한다. 사용자가 만든 종목이 다른 사용자에게 보이면 안 된다.
- 종목 삭제는 ExpiredSessionCleaner(REQ-WORKOUT-006), 세션 변경 잠금(DEC-WORKOUT-005), 미디어 파일 삭제(workout-media 6.4)에 의존한다.

---

## 4. 화면 / API 연계 설계

### 4.1 화면-API 매핑
| 화면 ID | 화면 | 사용자 행동 | API |
|--------|-----|-----------|-----|
| EX-001 | 운동 선택 | "종목 관리" | (이동만) EX-004 |
| EX-004 | 종목 관리 | 진입, 부위 선택 | API-EXERCISE-004 `GET /api/v1/exercise-categories`, API-EXERCISE-001 `GET /api/v1/exercises?categoryId=` |
| EX-004 | 종목 관리 | 삭제 → 확인 | API-EXERCISE-007 `DELETE /api/v1/exercises/{exerciseId}` |
| EX-004 | 종목 관리 | 순서 바꾸기 → 저장 | API-EXERCISE-008 `PUT /api/v1/exercise-categories/{categoryId}/exercise-order` |
| EX-005 | 종목 추가 | 추가 | API-EXERCISE-005 `POST /api/v1/exercises` |
| EX-006 | 종목 수정 | 저장 | API-EXERCISE-006 `PUT /api/v1/exercises/{exerciseId}` |

### 4.2 화면별 연계 상세
공통 응답 처리(401·403·404·400·409·500·503)는 공통 설계 4장을 따른다.

#### EX-004 종목 관리 (Figma `종목 관리 목록`, `종목 삭제 확인`)
- 진입 조건: EX-001에서 "종목 관리" (`categoryId` — EX-001에서 보던 부위)
- 필요 데이터: API-EXERCISE-004 → 부위 칩(`name`), 고른 부위의 종목 수(`exerciseCount`) / API-EXERCISE-001 → 종목 카드(`id`, `name`, `nameEn`). `nameEn`이 null이면 둘째 줄을 비운다
- 사용자 입력: 부위 칩 선택, "종목 추가", 카드의 "수정"·"삭제", 종목 위치 옮기기, 뒤로
- API 호출: 진입 시 두 API. 부위를 바꾸면 API-EXERCISE-001만 다시 조회. 삭제 팝업에서 "삭제"를 누르면 API-EXERCISE-007. 순서를 저장할 때 화면에 보이는 그 부위의 종목 ID 전체를 새 순서로 API-EXERCISE-008에 보낸다(옮길 때마다 보낼지 다 옮긴 뒤 한 번 보낼지는 디자인에서 정한다, 요구사항 8.2)
- 성공 처리: 204 → 목록과 종목 수를 다시 조회. EX-005·EX-006에서 돌아오면 다시 조회
- 실패 처리: 삭제 404 `EXERCISE_NOT_FOUND` → 다른 기기에서 이미 지운 경우. 목록을 다시 조회 / 순서 409 `EXERCISE_ORDER_OUTDATED` → "목록이 바뀌었어요. 다시 불러왔으니 다시 옮겨 주세요" 안내 후 API-EXERCISE-001 다시 조회(ERR-018)
- 로딩 상태: 목록 조회 중 표시, 삭제 요청 중 팝업 버튼 막기, 순서 저장 중 옮기기 막기
- 빈 상태: 고른 부위에 종목 없음(BR-030) → "종목이 없어요"와 "종목 추가"
- 삭제 확인 팝업: 제목 "<종목명>를 삭제할까요?", 부위명. 문구는 지난 기록과 통계가 함께 지워지고 되돌릴 수 없다고 알려야 한다(요구사항 8.2, 디자인 변경 필요)
- 뒤로: EX-001로 돌아가 API-EXERCISE-001을 다시 조회한다(바뀐 이름·순서 반영)

#### EX-005 종목 추가 (Figma `종목 추가`)
- 진입 조건: EX-004에서 "종목 추가" (`categoryId` — EX-004에서 고른 부위가 처음 골라져 있다)
- 필요 데이터: 부위 칩은 EX-004가 받은 API-EXERCISE-004 응답을 쓴다
- 사용자 입력: 부위(필수, 하나), 종목명(필수, 30자), 영문명(선택, 50자). 앱도 앞뒤 공백을 지운 뒤 같은 길이로 미리 검증한다. 종목명이 비어 있으면 "추가" 비활성
- API 호출: "추가" → API-EXERCISE-005
- 성공 처리: 201 → EX-004로 돌아가 다시 조회
- 실패 처리: 400 `VALIDATION_FAILED` → `errors[].field`의 칸에 `reason` / 409 `EXERCISE_NAME_DUPLICATED` → 종목명 칸에 "이 부위에 같은 이름의 종목이 있어요" (디자인에 오류 표시가 없다, 요구사항 8.2)
- 로딩 상태: 요청 중 "추가" 막기(중복 요청 방지)
- 빈 상태: 해당 없음

#### EX-006 종목 수정 (Figma `종목 수정`)
- 진입 조건: EX-004에서 카드의 "수정" (`exerciseId`, 지금 부위·`name`·`nameEn` — EX-004의 목록 응답에서 넘긴다)
- 필요 데이터: 추가 화면과 같다. 입력란을 지금 값으로 채운다
- 사용자 입력: EX-005와 같다
- API 호출: "저장" → API-EXERCISE-006 (세 값을 모두 보낸다)
- 성공 처리: 200 → EX-004로 돌아가 다시 조회. 부위를 바꿨으면 그 종목은 새 부위의 맨 뒤에 있다
- 실패 처리: EX-005와 같고, 404 `EXERCISE_NOT_FOUND` → 다른 기기에서 지운 경우. 안내 후 EX-004
- 로딩 상태: 요청 중 "저장" 막기
- 빈 상태: 해당 없음

#### EX-001 운동 선택 (변경, workout-exercise 4.2)
- 머리글의 "종목 관리"(`manage-exercises-btn`)를 누르면 EX-004로 간다.
- 빈 상태: 고른 부위에 종목 없음(BR-030) → "종목이 없어요"와 "종목 관리"로 가는 안내.

### 4.3 기존 API의 변경
| API | 바뀌는 것 | 근거 |
|-----|---------|-----|
| API-EXERCISE-001 부위의 운동 목록 | `exercise.user_id = userId`인 종목만. 정렬 `sort_order`, `created_at`, `id`. `nameEn`, `target`은 null일 수 있다 | BR-022, BR-023, BR-027, BR-028 |
| API-EXERCISE-004 부위 목록 | `exerciseCount`는 본인 종목 수. 0일 수 있다 | BR-022, BR-030 |
| API-EXERCISE-002 세션에 운동 추가 | 종목이 없으면 404 `EXERCISE_NOT_FOUND`, 다른 사용자의 종목이면 403 `FORBIDDEN` | BR-009, ERR-002, ERR-003 |
| API-SET-006 이전 기록 조회 | 같은 소유 확인(404 / 403) | BR-022, ERR-003 |
| API-STATS-002 부위별 종목 목록 | 정렬 `sort_order`, `created_at`, `id` | workout-stats BR-017 |
| API-STATS-003 종목별 볼륨 | 본인 종목 목록에 없는 `exerciseIds`(다른 사용자의 종목 포함)는 없는 종목과 같이 400 | workout-stats ERR-005, DEC-STATS-005 |
| WorkoutSessionResponse, WorkoutDayResponse | `exercises[].nameEn`, `target`이 null일 수 있다. 이름·부위는 지금 값 | BR-024, BR-028 |

---

## 5. API 설계
URL·필드 규칙은 공통 설계 5장을 따른다. 운동 기록 API 전체 목록은 workout-common 5.1에 있다.

### 5.1 API 목록
| API ID | Method | URL | 인증 | 설명 | 관련 요구사항 |
|--------|--------|-----|-----|-----|-------------|
| API-EXERCISE-005 | POST | /api/v1/exercises | 필요 | 종목 추가 | REQ-EXERCISE-004, IF-EXERCISE-004 |
| API-EXERCISE-006 | PUT | /api/v1/exercises/{exerciseId} | 필요 | 종목 수정 | REQ-EXERCISE-005, IF-EXERCISE-004 |
| API-EXERCISE-007 | DELETE | /api/v1/exercises/{exerciseId} | 필요 | 종목과 그 기록 삭제 | REQ-EXERCISE-006, IF-EXERCISE-004 |
| API-EXERCISE-008 | PUT | /api/v1/exercise-categories/{categoryId}/exercise-order | 필요 | 부위의 종목 순서 변경 (v0.2) | REQ-EXERCISE-007, IF-EXERCISE-005 |

**ExerciseResponse** (API-EXERCISE-005, 006)
```json
{ "id": "0199c4a2-7d10-7e21-9a3b-1c2d3e4f5a6b", "categoryId": "0199a0f0-1a20-7b44-8c03-4d6e8f0a2c01", "name": "케이블 크로스오버", "nameEn": "Cable Crossover", "target": null }
```
| 필드 | 설명 | 근거 |
|-----|-----|-----|
| `nameEn` | 없으면 null | BR-023 |
| `target` | 기본 목록에서 온 종목만 값이 있다. 추가한 종목, 부위를 바꾼 종목은 null | BR-028 |

### 5.2 API 상세

#### API-EXERCISE-005 종목 추가
- 목적: 본인 목록에 종목을 만든다.
- Method / URL: `POST /api/v1/exercises`
- 인증: 필요
- 관련 요구사항: REQ-EXERCISE-004, IF-EXERCISE-004, BR-022, BR-023, BR-026, BR-027, BR-028

Request
```json
{ "categoryId": "0199a0f0-1a20-7b44-8c03-4d6e8f0a2c01", "name": "케이블 크로스오버", "nameEn": "Cable Crossover" }
```

Response `201 Created` — ExerciseResponse

Validation (앞뒤 공백을 지운 뒤 검사)
| 필드 | 타입 | 필수 | 규칙 | 근거 |
|-----|-----|-----|-----|-----|
| categoryId | UUID | Y | UUID 형식, 있는 부위 | BR-023, ERR-016 |
| name | string | Y | 1~30자 | BR-023 |
| nameEn | string | N | 없거나 빈 문자열이면 null. 있으면 1~50자 | BR-023 |

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 400 | VALIDATION_FAILED | 필수 누락, 길이 초과, UUID 형식 오류, 없는 부위 | ERR-016 |
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 409 | EXERCISE_NAME_DUPLICATED | 그 부위에 같은 이름(대소문자 무시)의 종목이 있음 | ERR-017 |

#### API-EXERCISE-006 종목 수정
- 목적: 본인 종목의 부위·종목명·영문명을 고친다.
- Method / URL: `PUT /api/v1/exercises/{exerciseId}`
- 인증: 필요
- 관련 요구사항: REQ-EXERCISE-005, IF-EXERCISE-004, BR-022 ~ 024, BR-026 ~ 028

Request — API-EXERCISE-005와 같다. 세 값을 모두 보낸다(`nameEn`을 빼거나 비우면 영문명을 지운다).

Response `200 OK` — ExerciseResponse

Validation: API-EXERCISE-005와 같다. 경로 변수가 UUID 형식이 아니면 400.

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 400 | VALIDATION_FAILED | API-EXERCISE-005와 같다 | ERR-016 |
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 403 | FORBIDDEN | 다른 사용자의 종목 | ERR-003 |
| 404 | EXERCISE_NOT_FOUND | 종목 없음 | ERR-009 |
| 409 | EXERCISE_NAME_DUPLICATED | 저장할 부위에 같은 이름의 다른 종목이 있음 | ERR-017 |

#### API-EXERCISE-007 종목 삭제
- 목적: 본인 종목과 그 종목의 모든 기록을 지운다.
- Method / URL: `DELETE /api/v1/exercises/{exerciseId}`
- 인증: 필요
- 관련 요구사항: REQ-EXERCISE-006, IF-EXERCISE-004, BR-025, BR-029, BR-030, NFR-INTEG-003

Response `204 No Content`

Validation: 경로 변수가 UUID 형식이 아니면 400.

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 403 | FORBIDDEN | 다른 사용자의 종목 | ERR-003 |
| 404 | EXERCISE_NOT_FOUND | 종목 없음, 이미 지움 | ERR-009 |

#### API-EXERCISE-008 부위의 종목 순서 변경 (v0.2)
- 목적: 한 부위에 있는 본인 종목의 순서를 한 번에 바꾼다.
- Method / URL: `PUT /api/v1/exercise-categories/{categoryId}/exercise-order`
- 인증: 필요
- 관련 요구사항: REQ-EXERCISE-007, IF-EXERCISE-005, BR-031, BR-032, ERR-018

Request — 그 부위 본인 종목 전체를 원하는 순서대로
```json
{ "exerciseIds": ["0199a0f1-2b30-7c55-8d14-5e7f9a1b3d26", "0199a0f1-2b30-7c55-8d14-5e7f9a1b3d24", "0199a0f1-2b30-7c55-8d14-5e7f9a1b3d25"] }
```

Response `204 No Content`

Validation
| 필드 | 타입 | 필수 | 규칙 | 근거 |
|-----|-----|-----|-----|-----|
| categoryId (경로) | UUID | Y | UUID 형식 | — |
| exerciseIds | UUID 목록 | Y | 빈 목록 허용. 지금 그 부위의 본인 종목과 같은 집합, 겹침 없음(맞지 않으면 409) | BR-032 |

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 400 | VALIDATION_FAILED | `exerciseIds` 없음, UUID 형식 오류 | — |
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 409 | EXERCISE_ORDER_OUTDATED | 빠진 종목, 두 번 나온 종목, 다른 부위·다른 사용자의 종목, 없는 부위, 그 사이 목록이 바뀜 | ERR-018 |

---

## 6. 데이터 설계
이름 규칙·논리 타입·제약 종류는 공통 설계 6장을 따른다. 테이블 정의 전체는 workout-common 6.2에 있고, 여기서는 바뀌는 것만 쓴다.

### 6.1 ERD
```
                 exercise_category 1 ──── N default_exercise   (기본 목록 템플릿, 참조 없음)
                         1
                         │
                         N
users 1 ──── N exercise (사용자 소유)
  1               1
  │               │ (함께 삭제)
  N               N
workout_session 1 ──── N workout_session_exercise 1 ──── N workout_set
```

### 6.2 테이블 정의

#### default_exercise — 근거: BR-022, 요구사항 workout-common 부록 A (지금의 `exercise`를 이름 변경)
기본 목록 템플릿이다. 새 계정의 종목은 이 테이블에서 복사한다. 아무 테이블도 이것을 참조하지 않는다.

| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | DB 생성 (공통 DEC-ARCH-018) | PK | DEC-ARCH-010 |
| exercise_category_id | ID | N | | 부위 | DATA-006 |
| name | 문자열(30) | N | | 종목명 (v0.9: 100 → 30) | BR-023 |
| name_en | 문자열(50) | N | | 영문명 (v0.9: 100 → 50) | BR-023 |
| target | 문자열(50) | N | | 타깃 설명 | BR-028 |
| sort_order | 정수 | N | | 부위 안 순서(1부터) | 부록 A |
| created_at, updated_at | 시각 | N | 현재 시각 | | 공통 |

- 참조(삭제 금지): `exercise_category_id → exercise_category.id`
- 유일 `ux_default_exercise_name`(`name`), `ux_default_exercise_category_order`(`exercise_category_id, sort_order`) — 기존 제약의 이름만 바꾼다
- 행은 스키마 변경 스크립트로만 바꾼다. 바꿔도 이미 있는 계정의 종목에는 반영하지 않는다(요구사항 8.1 제외)

#### exercise — 근거: DATA-002 (v0.9 새로 만듦, 사용자 소유)
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | DB 생성. 애플리케이션이 만드는 행은 애플리케이션이 생성 (공통 DEC-ARCH-019) | PK | DEC-ARCH-010 |
| user_id | ID | N | | 소유자 | BR-022 |
| exercise_category_id | ID | N | | 부위 | DATA-002, BR-023 |
| name | 문자열(30) | N | | 종목명. 앞뒤 공백 없음 | BR-023 |
| name_en | 문자열(50) | Y | | 영문명. 없으면 Null | BR-023 |
| target | 문자열(50) | Y | | 타깃 설명. 기본 목록에서 복사한 종목만 값이 있고, 부위를 바꾸면 Null | BR-028 |
| sort_order | 정수 | N | | 부위 안 순서. 같으면 `created_at`, `id` 순 | BR-027, DEC-WORKOUT-025 |
| created_at | 시각 | N | 현재 시각 | | 공통 |
| updated_at | 시각 | N | 현재 시각 | | 공통 |

- PK: `id`
- 참조(함께 삭제): `user_id → users.id` — 계정을 지우면 종목도 지운다 (auth BR-012, 공통 6.1)
- 참조(삭제 금지): `exercise_category_id → exercise_category.id`
- 유일(대소문자 무시) `ux_exercise_user_category_name`: `(user_id, exercise_category_id, 소문자(name))` — BR-026. 위반은 제약 위반 변환으로 409 `EXERCISE_NAME_DUPLICATED`
- 조건 검사 `ck_exercise_name`: `name`이 앞뒤 공백 없이 1자 이상 — BR-023
- 조건 검사 `ck_exercise_name_en`: `name_en`이 Null이거나 앞뒤 공백 없이 1자 이상 — BR-023
- `sort_order`에는 유일 제약을 두지 않는다 (DEC-WORKOUT-025)

#### workout_session_exercise — 바뀌는 것
- 참조 `exercise_id → exercise.id`를 **삭제 금지에서 함께 삭제로** 바꾼다 (BR-025, NFR-INTEG-003). 기존 행은 각 사용자의 종목을 가리키게 옮긴다(6.5).

#### users — 바뀌는 것 (DB 자동 동작)
- `trg_users_default_exercises`: `users`에 행이 추가된 뒤, `default_exercise`의 모든 행을 그 사용자의 `exercise`로 복사한다(`name`, `name_en`, `target`, `exercise_category_id`, `sort_order` 그대로, ID는 DB 생성) (BR-022, DEC-WORKOUT-023).

### 6.3 인덱스
| 인덱스 | 테이블 | 컬럼 | 대상 조회 | 근거 |
|-------|-------|-----|---------|-----|
| ux_exercise_user_category_name | exercise | (user_id, exercise_category_id, 소문자(name)), 유일(대소문자 무시) | 같은 이름 확인(D), 부위의 본인 종목 목록(API-EXERCISE-001), 부위별 종목 수(API-EXERCISE-004), 최대 `sort_order` | BR-026, REQ-EXERCISE-003, 004 |
| idx_workout_session_exercise_exercise | workout_session_exercise | (exercise_id) | 종목 삭제 시 함께 삭제할 세션 운동 찾기, 그 종목이 있는 완료 세션 찾기 | REQ-EXERCISE-006, NFR-PERF-001 |
| ux_default_exercise_name, ux_default_exercise_category_order | default_exercise | 기존과 같다(이름만 변경) | 템플릿 중복 방지, 복사 순서 | 부록 A |

- 부위의 종목 목록은 `ux_exercise_user_category_name`으로 사용자·부위를 좁힌 뒤 몇 개뿐인 행을 정렬한다. 정렬용 인덱스를 따로 두지 않는다.
- workout-common v0.8의 "`exercise_id`에는 인덱스를 두지 않는다"는 종목을 지울 수 있게 되어 바뀐다.
- 순서 변경(API-EXERCISE-008)의 잠금 조회도 `ux_exercise_user_category_name`으로 사용자·부위를 좁힌다. 새 인덱스나 스키마 변경은 없다(v0.2).

### 6.4 삭제 정책
- 종목 삭제(API-EXERCISE-007) → 참조(함께 삭제)로 `workout_session_exercise` → `workout_set`이 모든 세션에서 지워진다. 그 뒤 세트가 남지 않은 완료 세션을 지우면 `workout_media` 행이 함께 지워지고, 파일은 커밋 후 작업으로 지운다 (BR-025, BR-029, workout-media 6.4).
- 계정 삭제 → `exercise`와 `workout_session` 두 경로로 모두 함께 삭제된다. 순서와 관계없이 남는 행이 없다.
- `default_exercise`, `exercise_category`는 지우지 않는다.

### 6.5 스키마 변경 목록
스키마 변경 4(workout_media) 다음이다. 이미 적용된 2·3은 고치지 않는다(공통 6.1). 운영 DB에 기존 기록이 있어도 그대로 이어지게 한 번에 옮긴다(요구사항 8.2).

| 순서 | 변경 | 내용 |
|-----|-----|-----|
| 5 | 종목을 사용자 소유로 | ① `exercise`를 `default_exercise`로 이름 변경, 유일 제약 이름 변경, `name`·`name_en` 길이를 30·50으로 줄임(부록 A의 값은 모두 들어간다) ② 6.2의 `exercise`와 제약·인덱스 생성 ③ 이미 있는 모든 사용자에게 `default_exercise`를 복사하면서 (사용자, 기본 종목) → 새 종목의 임시 대응표를 만든다 ④ `workout_session_exercise.exercise_id`를 그 세션 사용자의 대응 종목으로 바꾼다 ⑤ `workout_session_exercise.exercise_id`의 참조를 `exercise.id`(함께 삭제)로 바꾸고 `idx_workout_session_exercise_exercise` 생성 ⑥ 자동 동작 `trg_users_default_exercises` 생성(③과 같은 복사 절차를 쓴다) |

- ④ 뒤에도 `ux_workout_session_exercise_session_exercise`는 지켜진다. 한 사용자 안에서 기본 종목과 새 종목이 1:1이기 때문이다.
- 적용 후 확인: 사용자마다 종목 24개, 기존 세션 운동 수와 세트 수가 그대로, 모든 세션 운동의 종목 소유자 = 세션 소유자.

---

## 7. 인증 / 인가 및 보안 설계
공통 설계 7장과 workout-common 7장을 따른다.

### 7.1 API별 인증
API-EXERCISE-005 ~ 007 모두 인증 필요 (NFR-SEC-001).

### 7.2 사용자별 데이터 접근 제한
- 경로의 `exerciseId`(API-EXERCISE-006, 007, API-SET-006)와 본문의 `exerciseId`(API-EXERCISE-002): 종목 조회 → 없으면 404 `EXERCISE_NOT_FOUND` → `user_id` 불일치면 403 `FORBIDDEN` (ERR-003, DEC-WORKOUT-007과 같은 방식).
- 목록·집계(API-EXERCISE-001, 004, API-STATS-002)는 조회 조건 `exercise.user_id = userId`.
- API-STATS-003의 `exerciseIds`는 본인 종목만 받는다. 다른 사용자의 종목은 "없는 종목"과 같이 400이다(workout-stats DEC-STATS-005).
- 종목 삭제는 본인 진행 중 세션만 잠그고, 본인 종목을 가진 세션만 지운다. 세션 운동은 종목과 같은 사용자의 세션에만 있다(6.5 확인 항목).

### 7.3 민감 데이터 / 로그
종목명은 사용자가 입력한 문자열이지만 민감 정보로 다루지 않는다. 그래도 로그에는 종목명을 남기지 않고 `exerciseId`만 남긴다.

---

## 8. 예외 / 에러 처리 설계
응답 형식·상태 코드 정책은 공통 설계 8장, 공통 에러 코드는 workout-common 8.2를 따른다.

### 8.1 에러 응답 예
```json
{
  "code": "EXERCISE_NAME_DUPLICATED",
  "message": "같은 부위에 같은 이름의 종목이 있습니다.",
  "timestamp": "2026-10-10T09:00:00Z"
}
```

### 8.2 이 기능의 에러 코드
| 요구사항 ERR | 에러 코드 | HTTP | 메시지 | 발생 위치 |
|-------------|----------|------|-------|----------|
| ERR-016 | VALIDATION_FAILED (공통) | 400 | 입력값이 올바르지 않습니다. (`errors[]`에 필드별 사유) | 요청 검증, ExerciseService (V) |
| ERR-017 | EXERCISE_NAME_DUPLICATED | 409 | 같은 부위에 같은 이름의 종목이 있습니다. | ExerciseService (D), 제약 위반 변환 `ux_exercise_user_category_name` |
| ERR-018 | EXERCISE_ORDER_OUTDATED | 409 | 종목 목록이 바뀌었습니다. 다시 불러와 순서를 정해 주세요. | ExerciseService.reorder |
| ERR-003 | FORBIDDEN (공통) | 403 | 접근할 수 없는 데이터입니다. | ExerciseService 소유자 확인 |
| ERR-009 | EXERCISE_NOT_FOUND (기존, ERR-002와 같은 코드) | 404 | 운동을 찾을 수 없습니다. | ExerciseService |

- 제약 위반 변환 대상에 `ux_exercise_user_category_name` → 409 `EXERCISE_NAME_DUPLICATED`를 더한다. `ck_exercise_name`, `ck_exercise_name_en` 위반은 (V)를 거친 뒤라 정상 흐름에서 생기지 않으므로 500이다.

---

## 9. 비기능 요구사항 설계
| NFR ID | 요구사항 | 설계 대응 | 확인 방법 |
|--------|---------|----------|----------|
| NFR-PERF-001 | p95 500ms | 추가·수정: 쿼리 3~4회(부위 확인, 같은 이름 확인, 최대 순서, 저장). 삭제: cleanUp + 잠금 2회 + 완료 세션 조회 1회 + 삭제 1회 + 빈 세션 삭제 1회. 함께 삭제는 `idx_workout_session_exercise_exercise`로 찾는다 | 한 종목에 세션 1,000건이 있을 때 삭제 응답 시간 측정 |
| NFR-PERF-002 | 기록이 많아도 성능 유지 | 기존 조회는 `exercise.user_id` 조건이 더해질 뿐 시작점(사용자 세션 인덱스)은 같다 | workout-common 9장과 같다 |
| NFR-SEC-002 | 본인 데이터만 | 7.2. 순서 변경은 잠금 조회에 `user_id = userId` 조건을 걸어, 다른 사용자의 종목 ID는 "목록에 없는 종목"(409)이 된다 | 다른 사용자의 종목 ID로 API-EXERCISE-002, 006, 007, API-SET-006이 403, API-STATS-003이 400인지 테스트 |
| NFR-AVAIL-001 | 부분 저장 없음 | 삭제 2 ~ 6을 한 트랜잭션, 파일은 커밋 후 | 6에서 강제 예외 시 종목·세트·세션 모두 그대로인지 테스트 |
| NFR-INTEG-003 | 종목 삭제 시 기록이 남지 않음 | 참조(함께 삭제) `workout_session_exercise.exercise_id` | 삭제 후 그 종목의 세션 운동·세트 0건 테스트 |
| NFR-LOG-001 | 종목 삭제 추적 | INFO `exercise.deleted`(userId, exerciseId, deletedSetCount, deletedSessionCount), 지운 세션마다 `workout_session.deleted` | 로그 출력 확인 테스트 |

---

## 10. 구현 구조 및 개발 전략

### 10.1 컴포넌트 구성
| 도메인 | 컴포넌트 | 역할 | 책임 |
|-------|---------|-----|-----|
| exercise | ExerciseController | API 진입점 | 기존 API에 API-EXERCISE-005 ~ 007 추가 |
| exercise | ExerciseService | 서비스 | (V), (D), 순서 계산, 소유자 확인, 종목 잠금, 삭제 흐름 조정, 순서 변경(목록 비교, 다시 매김) |
| exercise | ExerciseRepository | 저장소 | 종목 저장·수정·삭제, 단건·변경 잠금 조회, 같은 이름 확인, 부위의 최대 순서, 사용자·부위의 종목 전체 변경 잠금 조회 |
| exercise | ExerciseQueryRepository | 조회 저장소 | 기존 조회에 `user_id` 조건과 정렬 추가 |
| session | WorkoutSessionService | 서비스 | `lockInProgress(userId)`, `deleteSessionsLeftEmpty(userId, sessionIds)`(조건부 일괄 삭제, 파일 삭제 등록, 로그) 추가 |

### 10.2 구현 순서
| 순서 | 작업 | 관련 요구사항 | 완료 기준 |
|-----|-----|-------------|----------|
| 1 | 스키마 변경 5 | BR-022, DATA-002 | 기존 데이터가 있는 DB에 적용 성공. 사용자마다 종목 24개, 세션 운동·세트 수 그대로, 새 계정 SQL 추가 시 종목 24개 |
| 2 | 기존 API를 본인 목록으로(4.3) | BR-009, BR-022 | 다른 사용자가 만든 종목이 목록·종목 수에 없음, 다른 사용자 종목으로 세션 추가 403 테스트 |
| 3 | 종목 추가·수정 API | REQ-EXERCISE-004, 005, BR-023, 026 ~ 028, ERR-016, 017 | 경계값·같은 이름·부위 변경 시 맨 뒤와 타깃 설명 지움 테스트 |
| 4 | 종목 삭제 API | REQ-EXERCISE-006, BR-025, 029, 030 | 완료 세션 세트 삭제, 빈 완료 세션과 사진 삭제, 진행 중 세션 유지 테스트 |
| 5 | 종목 순서 변경 API (v0.2) | REQ-EXERCISE-007, BR-031, 032, ERR-018 | 새 순서가 운동 선택·종목 관리·통계 목록에 반영, 빠짐·겹침·다른 사용자 종목·없는 부위 409, 순서 변경 뒤 추가한 종목은 맨 뒤 테스트 |

모두 구현되었다(2026-10-10). 테스트: `ExerciseManageApiTest`, `ExerciseServiceTest`(BR-032), `SchemaChange5Test`.

### 10.3 테스트 포인트
- BR-022: 운영자 SQL로 계정을 추가하면 종목 24개가 부록 A 순서로 생긴다. A가 만든 종목은 B의 목록·종목 수에 없다.
- 스키마 변경 5: 적용 전 A가 벤치프레스를 기록했다면, 적용 후 A의 벤치프레스 이전 기록·최근 수행일·통계가 적용 전과 같다.
- BR-023 경계값: 종목명 공백만 / 1자 / 30자 / 31자, 앞뒤 공백 붙은 30자(통과), 영문명 빈 문자열(null로 저장) / 50자 / 51자.
- BR-026: 가슴에 "벤치프레스"가 있으면 가슴에 " 벤치프레스 " 추가는 409, 등에 "벤치프레스" 추가는 201. "bench press"와 "Bench Press"는 같은 이름이다. 같은 이름 두 개를 동시에 추가하면 하나만 201, 하나는 409.
- BR-024·BR-027·BR-028: 벤치프레스를 등으로 옮기면 등의 맨 뒤, `target` null. 지난 날짜별 기록과 통계의 부위별 볼륨에서 그 볼륨이 등으로 옮겨 간다.
- BR-025·BR-029: 9/20에 벤치프레스만, 9/24에 벤치프레스와 스쿼트를 했다면 벤치프레스를 지운 뒤 9/20 세션과 그 사진은 없어지고, 9/24는 스쿼트만 남는다. 진행 중 세션에 벤치프레스만 있었으면 세션은 세트 없이 남는다.
- BR-031·BR-032: 가슴 5개를 거꾸로 보내면 운동 선택·종목 관리·통계의 가슴 목록이 거꾸로 나온다. 4개만 보내거나, 하나를 두 번 보내거나, 등 종목·다른 사용자의 종목을 섞으면 409이고 순서는 그대로다. 순서를 바꾼 뒤 종목을 추가하면 맨 뒤에 온다.
- 동시성: 진행 중 세션에 세트를 추가하는 요청과 그 종목 삭제가 동시에 와도 500이 나지 않는다(한쪽이 먼저 끝나고, 세트 추가가 늦으면 404 `SESSION_EXERCISE_NOT_FOUND`).

---

## 부록 A. 설계 결정 기록
| ID | 결정 | 이유 | 버린 대안 |
|----|-----|-----|----------|
| DEC-WORKOUT-023 | 사용자 종목은 계정을 만들 때 DB 자동 동작(`trg_users_default_exercises`)이 기본 목록을 복사해 만든다 | 계정은 운영자 SQL로만 생긴다(auth DEC-AUTH-001). 운영자가 따로 복사 SQL을 돌릴 필요가 없다(공통 DEC-ARCH-013과 같은 판단) | 첫 요청 때 애플리케이션이 복사: "종목을 모두 지운 사용자"와 "아직 복사하지 않은 사용자"를 구분하는 표시가 따로 필요하고 동시 첫 요청 처리가 필요 / 운영 절차에 복사 SQL 추가: 빠뜨리면 빈 목록 |
| DEC-WORKOUT-024 | 기본 목록은 템플릿 테이블(`default_exercise`)로 두고, 사용자마다 종목 행을 복사한다 | 기본 종목도 사용자가 고치고 지울 수 있다(BR-022). 복사본이면 수정·삭제가 다른 종목과 똑같이 처리되고, 기록은 늘 자기 종목만 가리킨다 | 공용 기본 종목 + 사용자별 덮어쓰기·숨김 테이블: 모든 조회가 두 곳을 합쳐야 하고, 수정된 기본 종목과 지난 기록을 잇는 규칙이 복잡 / 한 테이블에 `user_id` Null = 기본: 모든 조회에 조건을 빠뜨리면 템플릿이 섞여 나옴 |
| DEC-WORKOUT-025 | `sort_order`에 유일 제약을 두지 않고, 같으면 `created_at`, `id` 순으로 정렬한다 | 추가·부위 변경은 "최대 + 1"이라 동시에 오면 값이 겹칠 수 있다. 겹쳐도 순서가 정해지므로 잠금이나 재시도가 필요 없다 | 유일 제약 + 사용자 잠금: 순서 하나 때문에 동시 요청을 줄 세움 / 순서를 `created_at`만으로: 기본 종목은 한 번에 복사돼 시각이 같다 |
| DEC-WORKOUT-026 | 본문의 `categoryId`가 없는 부위면 404가 아니라 400 `VALIDATION_FAILED` | 요구사항 ERR-016이 "입력값 오류"로 정했다. 통계의 없는 부위·종목(DEC-STATS-005)과 같다 | 404 `EXERCISE_CATEGORY_NOT_FOUND`(DEC-WORKOUT-009 방식): 요구사항과 다름 |
| DEC-WORKOUT-027 | 종목 삭제는 진행 중 세션 → 종목 순서로 변경 잠금을 건다 | 세트 추가는 세션을 잠근 뒤 그 종목의 세션 운동에 행을 넣는다. 같은 순서로 잠가야 서로 기다리다 멈추지 않고, 삭제 도중 세트가 들어오지 않는다 | 종목만 잠금: 진행 중 세션에 세트 추가가 끼어들어 참조 오류(500) |
| DEC-WORKOUT-028 | 스키마 변경 5는 기존 기록을 지우지 않고 사용자 종목으로 옮긴다 | 운영 중인 DB에 기록이 있을 수 있다. 요구사항 8.2가 기록이 그대로 이어져야 한다고 정했다 | 스키마 변경 2·3을 다시 만들기(DEC-WORKOUT-016 방식): 운영 기록을 잃음 |
| DEC-WORKOUT-029 | 순서 변경은 부위의 종목 ID 전체를 새 순서로 한 번에 받고, 지금 목록과 집합이 같을 때만 바꾼다 (v0.2) | 요구사항 BR-032와 TODO-031 결정("목록이 바뀌었으면 바꾸지 않는다")을 비교 한 번으로 지킨다. 앱이 보고 있던 목록이 낡았는지 별도 버전 값 없이 알 수 있다 | 한 종목씩 위치를 옮기는 API: 다른 기기의 변경을 알아채기 어렵고 옮길 때마다 요청 / 목록에 버전 값을 두고 비교: 컬럼이 늘고 추가·수정·삭제마다 갱신해야 함 |
| DEC-WORKOUT-030 | ERR-018은 409 `EXERCISE_ORDER_OUTDATED` (v0.2) | 요구사항이 "목록이 바뀌었으니 다시 불러오라"고 정했다. 입력 형식이 아니라 서버의 지금 목록과 맞지 않는 상태 충돌이다(공통 상태 코드 정책). 잘못 섞인 종목 ID도 같은 409라 다른 사용자 종목의 존재가 드러나지 않는다 | 400 `VALIDATION_FAILED`: 앱이 "다시 불러오기"로 처리할 경우를 구분하기 어려움 |
| DEC-WORKOUT-031 | 순서 변경은 그 사용자·부위의 종목 행을 모두 변경 잠금으로 조회한 뒤 비교한다 (v0.2) | 두 기기의 순서 변경이 겹쳐도 하나씩 처리되고, 같은 부위 종목의 수정·삭제(종목 행 잠금)와도 섞이지 않는다. **한계:** 새 행을 만드는 추가는 막지 못한다. 추가가 먼저 커밋되면 비교에서 걸리고(409), 나중이면 순서 변경 뒤의 "최대 + 1"로 맨 뒤에 온다 | 사용자 행 잠금: users 테이블에 기능이 의존 / 잠금 없음: 동시 변경 시 서로 다른 순서가 섞임 |

## 부록 B. 설계 미결정 사항
없음.

## 부록 C. 요구사항 피드백
1. **없는 부위의 오류 종류가 입력 위치에 따라 다르다:** 쿼리의 `categoryId`(REQ-EXERCISE-003)는 ERR-009(찾을 수 없음)이고, 본문의 `categoryId`(REQ-EXERCISE-004, 005)는 ERR-016(입력값 오류)이다. 둘 다 요구사항대로 설계했다(404 / 400, DEC-WORKOUT-026). 앱은 두 경우를 따로 처리해야 한다. 같게 맞추려면 요구사항을 고쳐야 한다.
2. **다른 사용자의 종목 ID를 통계에 넣을 때:** 요구사항 2장은 다른 사용자의 종목에 권한 오류(ERR-003)를 정했지만, 통계는 없는 종목을 입력값 오류(workout-stats ERR-005)로 정했다. 설계는 통계에서는 "본인 목록에 없는 종목"으로 보고 400으로 둔다(존재 여부도 드러나지 않는다). 다른 API는 403이다.
3. **빈 부위의 순서 변경:** REQ-EXERCISE-007의 사전 조건은 "고른 부위에 본인 종목이 있어야 한다"이지만, 종목이 없는 부위에 빈 순서를 보내면 바꿀 것이 없을 뿐 잘못된 요청은 아니다. 설계는 오류 없이 204로 둔다(3.2 REQ-EXERCISE-007). 종목이 없는 부위에 종목 ID를 보내면 ERR-018(409)이다.
