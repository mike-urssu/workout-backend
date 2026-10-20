# 오운완 인증 사진·동영상 설계 문서

- 문서 버전: v0.5
- 변경 이력:
  - v0.5 (2026-10-20) — iPhone에서 업로드·조회가 느린 문제. 업로드 때 보기용 사본(사진 긴 변 2048 JPEG, 동영상 720p H.264 MP4)을 만들고 미디어 뷰어는 새 API-MEDIA-004로 사본을 본다. 원본은 그대로 두고 앨범 저장에만 쓴다(DEC-MEDIA-010). 앱은 파일을 3개까지 동시에 올린다(DEC-MEDIA-011). 요구사항 변경 없음
  - v0.1 — 운동 완료 시 첨부(REQ-MEDIA-001). 코드 반영됨
  - v0.2 (2026-10-09) — 목록(REQ-MEDIA-002, workout-history API-WORKOUT-008), 미리보기·원본 내려받기(API-MEDIA-002·003), 미디어 뷰어(MEDIA-001) 설계. D-TODO-MEDIA-001 결정. 요구사항 v0.4 반영(부록 C-1 ~ 3 해결)
  - v0.3 (2026-10-11) — 요구사항 v0.5 반영(BR-005 세션당 5개). 업로드·완료 검증과 오류 문구를 5개로. 조건 검사 `ck_workout_media_sort_order`는 1~10 그대로 둔다(DEC-MEDIA-008)
  - v0.4 (2026-10-11) — 요구사항 v0.6 반영(REQ-MEDIA-004 앨범 저장, BR-008, ERR-008·009). 새 API 없이 앱이 API-MEDIA-003으로 원본을 받아 앨범에 넣는다(DEC-MEDIA-009). 서버 변경 없음
- 작성일: 2026-10-09
- 상태: 초안
- 요구사항: `docs/requirements/workout-media.md` (v0.6)
- 공통 설계: `docs/design/architecture.md` (v0.10)
- 관련 설계: `docs/design/workout-session.md` (v0.8, 운동 세션 완료 API-WORKOUT-003·세션 정리), `workout-history.md` (날짜별 기록 API-WORKOUT-008), `workout-common.md` (테이블·공통 규칙)
- Figma: `workout-complete-popup` (18:4), `workout-history`의 오운완 인증(5:31), `media-viewer-popup` (18:168)

---

## 1. 설계 개요

### 1.1 목적
운동을 완료할 때 오운완 인증 사진·동영상을 붙이고 지난 기록에서 다시 보고 기기 앨범에 저장하는 기능을, 파일 하나씩 올리는 API와 미리보기·원본을 내려받는 API 3개, 운동 완료·날짜별 기록 API(workout-session, workout-history)의 확장, 테이블 1개로 구현하는 방법을 확정한다. 파일은 오브젝트 저장소에 두고(공통 2.6), 미리보기는 업로드할 때 서버가 만든다(공통 DEC-ARCH-015).

### 1.2 설계 범위
- 포함: REQ-MEDIA-001 (운동 완료 팝업에서 첨부, Figma 2행), REQ-MEDIA-002 날짜별 목록(Figma 4행 운동 기록 달력), REQ-MEDIA-003 크게 보기(미디어 뷰어), REQ-MEDIA-004 앨범 저장(미디어 뷰어, API-MEDIA-003 재사용), 미리보기 생성과 내려받기(IF-MEDIA-003), DATA-001, 세션 삭제·정리 때의 파일 삭제(BR-004)
- 보류: 없음
- 제외: 요구사항 8.1의 범위 밖 항목(완료 후 추가·삭제, 편집, 공유, 갤러리, 운동 중 업로드, 한꺼번에 저장, 저장할 때 형식 바꾸기 등)

### 1.3 대상 시스템
- Backend API: 신규 도메인 `media`(업로드 API, 서비스), 공통 파일 저장소·미디어 처리기(공통 2.2)
- Database: 신규 테이블 `workout_media`
- Object Storage: 원본 파일과 미리보기 이미지
- Mobile App: 운동 완료 팝업(workout-session WO-002), 운동 기록 달력(workout-history WO-003), 미디어 뷰어(MEDIA-001)의 앨범 저장

### 1.4 기술 스택
공통 설계 1.4를 따른다. 이 기능에 새로 필요한 기술 능력:
| 필요한 능력 | 용도 | 이유 |
|-----------|-----|-----|
| 오브젝트 저장소 접근 | 원본·미리보기 저장, 삭제 | 사용자 결정, 공통 DEC-ARCH-014 |
| 파일 형식·길이 판별, 미리보기 생성 | 업로드 검증(BR-005, BR-006), 미리보기(IF-MEDIA-003, 요구사항 TODO-007) | 사용자 결정(서버가 만든다), 공통 DEC-ARCH-015 |
| 큰 요청 본문(파일) 받기 | 동영상 최대 100MB | BR-005 |

### 1.5 설계 원칙
1. **올리기와 붙이기를 나눈다:** 파일은 운동 완료 팝업에서 "저장하기"를 누른 뒤 하나씩 **임시로 올리고**, 운동 완료 요청이 고른 파일 ID를 받아 그때 **붙인다**. 완료되지 않은 세션의 파일은 아직 붙인 것이 아니다(BR-002). 완료할 때 고르지 않은 임시 파일은 지운다(BR-007).
2. **오래 걸리는 일은 잠금 밖에서:** 파일 처리와 저장소 업로드는 세션 변경 잠금 없이 하고, 마지막에 짧은 트랜잭션으로 상태를 다시 확인해 기록한다. 큰 동영상을 올리는 동안 세션이 잠기지 않는다.
3. **파일과 DB의 순서:** 공통 2.6과 DEC-ARCH-016을 따른다(저장은 파일 먼저, 삭제는 DB 먼저).

### 1.6 요구사항 ↔ 설계 추적표
| 요구사항 ID | 요구사항 | 설계 반영 위치 | 구현 | 테스트 |
|------------|---------|--------------|-----|-------|
| REQ-MEDIA-001 | 운동 완료 시 사진·동영상 첨부 | 3.2, API-MEDIA-001, workout-session API-WORKOUT-003 `mediaIds`, workout_media | | |
| REQ-MEDIA-002 | 날짜별 사진·동영상 목록 조회 | 3.2, workout-history API-WORKOUT-008 `media[]` | | |
| REQ-MEDIA-003 | 사진·동영상 크게 보기 | 3.2, API-MEDIA-004, 4.2 MEDIA-001, DEC-MEDIA-010 | | |
| REQ-MEDIA-004 | 사진·동영상 앨범에 저장 | 3.2, API-MEDIA-003, 4.2 MEDIA-001, DEC-MEDIA-009 | | |
| BR-001 | 본인만 조회 | 7.2, 공통 2.6(비공개 저장소), 7.4 | | |
| BR-002 | 완료할 때만 붙임, 이후 추가·삭제 불가 | 1.5, 3.4, 3.5 | | |
| BR-003 | 섞어서 여러 개, 순서 유지 | 3.5, workout_media.sort_order | | |
| BR-004 | 세션·계정 삭제 시 파일 삭제 | 6.4, 공통 7.7·10.7 | | |
| BR-005 | 5개, 사진 20MB, 동영상 100MB·1분 | 3.5, 5.3 Validation, DEC-MEDIA-008 | | |
| BR-006 | JPEG·PNG·HEIC, MP4·MOV | 3.5, 5.3 Validation | | |
| BR-007 | 하나라도 실패하면 완료하지 않음, 일부만 남기지 않음 | 3.2, 3.5, 4.2 | | |
| BR-008 | 보고 있는 하나를 원본 그대로 앨범에 저장 | 3.2, 3.5, 4.2 MEDIA-001 | | |
| ERR-001 | 로그인 없이 요청 | 8.2 UNAUTHORIZED | | |
| ERR-002 | 허용하지 않는 형식 | 8.2 MEDIA_UNSUPPORTED_TYPE | | |
| ERR-003 | 개수·크기·길이 초과 | 8.2 MEDIA_LIMIT_EXCEEDED | | |
| ERR-004 | 다른 사용자 세션·파일 | 8.2 FORBIDDEN | | |
| ERR-005 | 완료된 세션에 붙임 | 8.2 WORKOUT_SESSION_NOT_EDITABLE | | |
| ERR-006 | 올리기 실패 | 8.2 SERVICE_UNAVAILABLE, 4.2 | | |
| ERR-007 | 없거나 삭제된 파일 조회 | 8.2 MEDIA_NOT_FOUND | | |
| ERR-008 | 앨범 접근을 허락하지 않음 | 8.2 (앱), 4.2 MEDIA-001 | | |
| ERR-009 | 앨범 저장 실패 | 8.2 SERVICE_UNAVAILABLE·네트워크·기기 저장 실패(앱), 4.2 MEDIA-001 | | |
| NFR-SEC-001 | 인증된 사용자만 | 7.1 | | |
| NFR-SEC-002 | 주소를 알아도 본인만 | 7.2, 공통 DEC-ARCH-014 | | |
| NFR-PERF-001 | 목록·미리보기 p95 500ms, 업로드는 진행 상황 | 9장 | | |
| NFR-AVAIL-001 | 업로드 실패해도 세트를 잃지 않음 | 3.2, 4.2 | | |
| NFR-INTEG-001 | 세션 없는 파일이 남지 않음 | 6.4, 공통 2.6 | | |
| IF-MEDIA-001 | 완료하며 여러 개 올리기 | API-MEDIA-001 + workout-session API-WORKOUT-003 | | |
| IF-MEDIA-002 | 날짜별 목록 | workout-history API-WORKOUT-008 `media[]` | | |
| IF-MEDIA-003 | 원본과 미리보기 | 미리보기 생성 3.2, API-MEDIA-002, API-MEDIA-003 | | |
| DATA-001 | 오운완 인증 사진·동영상 | 6.2 workout_media | | |
| MEDIA-001 | 미디어 뷰어 | 4.2 | | |
| workout-history WO-003 | 운동 기록 달력의 오운완 인증 | 4.2 | | |
| workout-session WO-002 | 운동 완료 팝업의 오운완 인증 | 4.2 | | |

---

## 2. 시스템 아키텍처
공통 설계 2장, 2.6을 따른다. 이 기능에서 추가되는 것:

```
App ──multipart──▶ WorkoutMediaController ─▶ WorkoutMediaService
                                               ├─ (잠금 없이) 세션 소유·상태 확인
                                               ├─ 미디어 처리기: 형식·크기·길이 확인, 미리보기 생성 (임시 파일)
                                               ├─ 파일 저장소: 원본·미리보기 저장 ─▶ 오브젝트 저장소
                                               └─ [트랜잭션] 세션 변경 잠금 → 다시 확인 → workout_media 저장
                                                     실패 시 방금 저장한 파일 삭제
App ──complete(mediaIds)──▶ WorkoutSessionController ─▶ WorkoutSessionService.complete
                                               └─ 고른 파일에 순서 부여, 나머지 임시 파일 행 삭제 → 커밋 후 파일 삭제
```

---

## 3. 기능 설계

### 3.1 기능 목록
| 요구사항 ID | 기능 | API | 주요 컴포넌트 |
|------------|-----|-----|-------------|
| REQ-MEDIA-001 | 사진·동영상 임시 올리기 | API-MEDIA-001 | WorkoutMediaService.upload |
| REQ-MEDIA-001 | 고른 파일을 붙이며 완료 | workout-session API-WORKOUT-003 | WorkoutSessionService.complete |
| REQ-MEDIA-002 | 날짜별 목록 | workout-history API-WORKOUT-008 | WorkoutDayService.get |
| REQ-MEDIA-002, 003 | 미리보기 내려받기 | API-MEDIA-002 | WorkoutMediaService.download |
| REQ-MEDIA-003 | 원본 내려받기 | API-MEDIA-003 | WorkoutMediaService.download |
| REQ-MEDIA-004 | 앨범 저장 (원본 내려받기 + 기기 앨범에 넣기) | API-MEDIA-003 | WorkoutMediaService.download, 앱 |

### 3.2 기능별 처리 흐름

#### REQ-MEDIA-001 (1) 사진·동영상 임시 올리기 (API-MEDIA-001)
1. 인증 필터에서 userId (없으면 401, ERR-001). 방치된 세션 정리(workout-session REQ-WORKOUT-006)를 먼저 실행한다.
2. 요청에 파일 부분 `file`이 없거나 비었으면 400 `VALIDATION_FAILED`.
3. **잠금 없이** 세션을 조회한다. 없으면 404 `WORKOUT_SESSION_NOT_FOUND`, 다른 사용자 것이면 403 `FORBIDDEN`(ERR-004), `COMPLETED`면 409 `WORKOUT_SESSION_NOT_EDITABLE`(ERR-005, BR-002). 이 세션의 임시 파일이 이미 5개면 400 `MEDIA_LIMIT_EXCEEDED`(`details.limit` = `COUNT`, BR-005). 오래 걸리는 처리 전에 빨리 거절하기 위한 확인이다.
4. 파일을 임시 파일로 받는다. 미디어 처리기로 **내용을 보고** 형식을 판별한다(요청의 형식 표시는 믿지 않는다, 공통 5장).
   - 허용 형식(BR-006): 사진 JPEG·PNG·HEIC, 동영상 MP4·MOV. 그 밖이거나 읽을 수 없으면 400 `MEDIA_UNSUPPORTED_TYPE` (ERR-002).
   - 크기(BR-005): 사진 20MB(20 × 1024 × 1024 바이트) 이하, 동영상 100MB(100 × 1024 × 1024 바이트) 이하. 넘으면 400 `MEDIA_LIMIT_EXCEEDED`(`details.limit` = `FILE_SIZE`) (ERR-003).
   - 동영상 길이(BR-005): 60초 이하. 넘으면 400 `MEDIA_LIMIT_EXCEEDED`(`details.limit` = `DURATION`) (ERR-003).
5. 미리보기를 만든다: 사진은 축소, 동영상은 첫 장면. JPEG, 긴 변 640픽셀(원본이 더 작으면 원본 크기) (DEC-MEDIA-003). 사진은 보기용 사본도 만든다: JPEG, 긴 변 2048픽셀 (DEC-MEDIA-010). 만들지 못하면 400 `MEDIA_UNSUPPORTED_TYPE`(손상된 파일로 본다).
6. 새 미디어 ID를 만들고 파일 저장소에 원본, 미리보기, (사진이면) 보기용 사본을 저장한다(키 6.2). 저장소 장애면 503 `SERVICE_UNAVAILABLE` (ERR-006). 저장한 키를 기억한다.
7. **트랜잭션**: 세션을 변경 잠금으로 다시 조회해 3의 확인을 다시 한다(그사이 완료·취소·자동 정리됐을 수 있다). 통과하면 `workout_media` 행을 `sort_order` 없이 저장한다.
8. 7이 실패하면(확인 실패, DB 오류) 6에서 저장한 파일을 지우고 해당 오류를 응답한다(공통 2.6 저장 순서). 임시 파일은 성공·실패와 관계없이 지운다.
9. 커밋, 로그 `workout_media.uploaded`(userId, sessionId, mediaId, 형식, 크기), 201.
10. 동영상이면 응답한 뒤 **백그라운드**에서 보기용 사본을 만든다(DEC-MEDIA-010): H.264·AAC MP4, 짧은 변 720픽셀 이하, 30fps 이하, 재생 정보를 파일 앞에 둔다. HDR(HLG·PQ)은 SDR로 바꾼다. 한 번에 하나씩, 낮은 우선순위로. 저장한 **뒤에** 미디어 행이 아직 있는지 확인하고 없으면 사본을 지운다(그사이 취소·고르지 않음으로 지워진 경우. 확인 뒤에 지워지면 6.4의 삭제가 사본까지 지운다). 실패하면 WARN 로그 `workout_media.transcode_failed`만 남기고, 성공하면 `workout_media.transcoded`(mediaId, 크기).

#### REQ-MEDIA-001 (2) 고른 파일을 붙이며 완료 (workout-session API-WORKOUT-003)
처리 흐름 전체는 workout-session 설계 3.2 REQ-WORKOUT-002. 이 기능에 해당하는 단계:
1. 본문 `mediaIds`(생략 시 빈 목록)를 검증한다: 5개 이하(BR-005), 중복 없음. 위반 시 400 `VALIDATION_FAILED`.
2. 세션 변경 잠금 뒤, `mediaIds`가 모두 **이 세션의** `workout_media` 행인지 확인한다. 아니면 400 `VALIDATION_FAILED`(`errors[].field` = `mediaIds`).
3. `mediaIds`의 순서대로 `sort_order` = 1, 2, … 를 저장한다(BR-003).
4. 이 세션의 나머지 `workout_media` 행(고르지 않았거나 실패 후 남은 임시 파일)을 삭제하고, 그 파일 키들을 **커밋 후 작업**으로 지운다(BR-007).
5. 세션 완료와 같은 트랜잭션에서 확정한다. 건너뛰기는 `mediaIds` = []로 같은 API를 부르므로 임시 파일이 모두 지워진다.

#### REQ-MEDIA-002 날짜별 사진·동영상 목록 (workout-history API-WORKOUT-008)
1. 날짜별 기록 처리(workout-history 3.2 REQ-WORKOUT-008)의 5단계에서, 그날 **완료된** 세션의 `workout_media`를 세션 시작 순 → `sort_order` 순으로 조회한다(쿼리 1회). 임시 파일은 진행 중 세션에만 있으므로 들어오지 않는다.
2. 항목마다 `id`, `mediaType`, `contentType`, `previewUrl` = `/api/v1/media/{id}/preview`, `displayUrl` = `/api/v1/media/{id}/display`, `originalUrl` = `/api/v1/media/{id}/original`을 준다. 파일 자체는 주지 않는다.

#### REQ-MEDIA-002, 003 미리보기·보기용 사본·원본 내려받기 (API-MEDIA-002 ~ 004)
1. 인증 필터에서 userId (없으면 401, ERR-001).
2. `workout_media`와 소속 세션을 조회한다. 없거나, 소속 세션이 진행 중(임시 파일)이면 404 `MEDIA_NOT_FOUND` (ERR-007). 다른 사용자의 세션이면 403 `FORBIDDEN` (ERR-004, BR-001).
3. 파일 키(6.2)로 오브젝트 저장소에서 읽어 그대로 흘려보낸다(공통 5장 파일 내려받기). 미리보기는 `image/jpeg`, 원본은 저장된 `content_type`.
4. 원본과 보기용 사본은 `Range` 요청을 지원한다(206, 동영상 넘겨 보기). 저장소에 범위 읽기로 넘긴다.
5. 보기용 사본은 사진이면 `image/jpeg`, 동영상이면 `video/mp4`. 사본이 아직 없으면(동영상 변환 중, v0.5 전에 올린 파일) 원본을 대신 준다(DEC-MEDIA-010).
6. 파일은 바뀌지 않으므로 `Cache-Control: private, max-age=31536000, immutable`을 준다(DEC-MEDIA-006). 5에서 원본을 대신 줄 때만 `private, no-store`(나중에 생길 사본 대신 원본이 캐시에 남지 않게). 저장소 장애는 503 (공통 2.6).
- 방치된 세션 정리(앞단 B)는 하지 않는다. 완료된 세션의 파일만 내려주므로 정리 결과와 관계없다.

#### REQ-MEDIA-004 앨범에 저장 (API-MEDIA-003, 앱)
서버는 위 원본 내려받기를 그대로 쓴다(DEC-MEDIA-009). 앱이 하는 일:
1. 미디어 뷰어에서 저장을 누르면 기기 앨범 **쓰기** 권한을 확인한다. 처음이면 묻고, 거부되면 내려받지 않고 4.2의 ERR-008 안내 (BR-008, ERR-008).
2. 지금 보고 있는 항목 **하나**의 `originalUrl`을 `Authorization` 헤더를 붙여 `Range` 없이 받는다(200, 전체 본문). 앱 임시 위치에 파일로 받는다. `Content-Length`로 진행 상황을 보여준다(NFR-PERF-001). 401·403·404·503은 3.2 내려받기와 같다(ERR-001, ERR-004, ERR-007, ERR-009).
3. 받은 파일의 확장자는 응답 `Content-Type`(= 항목의 `contentType`)으로 정한다: `image/jpeg` → `.jpg`, `image/png` → `.png`, `image/heic` → `.heic`, `video/mp4` → `.mp4`, `video/quicktime` → `.mov`. 기기 앨범은 확장자로 사진·동영상을 가른다. 파일 이름은 `<mediaId>.<확장자>`(원래 파일 이름은 저장하지 않는다, 7.3).
4. 파일을 바꾸지 않고 기기 앨범에 넣는다(BR-008, 형식 바꾸기는 요구사항 8.1 제외). 성공하면 "저장했어요". 넣지 못하면(저장 공간 부족 등) ERR-009.
5. 성공·실패와 관계없이 앱 임시 파일을 지운다. 같은 항목을 다시 저장하면 1부터 다시 한다(이미 저장했는지 기억하지 않는다).

### 3.3 주요 시나리오 — 사진 2장과 동영상 1개를 붙여 완료
```
App                              API                                   DB / 오브젝트 저장소
 │ [저장하기]                      │                                        │
 │ POST .../media (사진1)          │ 형식·크기 확인, 미리보기, 원본·미리보기 저장 ─▶ 저장소
 │───────────────────────────────▶│ 세션 잠금·확인, workout_media 저장 ─────▶ DB
 │◀── 201 {id: m1} ────────────────│                                        │
 │ POST .../media (사진2) → 201 m2  │                                        │
 │ POST .../media (동영상) ─────────▶│ 저장소 장애                             │
 │◀── 503 SERVICE_UNAVAILABLE ─────│ (DB 기록 없음)                           │
 │ "올리지 못했어요. 다시 시도 / 건너뛰기"  (세션은 진행 중)                        │
 │ [다시 시도] POST .../media (동영상) → 201 m3                                 │
 │ POST .../complete {mediaIds:[m1,m2,m3]} │ 세션 잠금, 순서 저장, 완료 ──────▶ DB
 │◀── 200 세션 ────────────────────│                                        │
```

### 3.4 상태 변화
`workout_media` 행에는 상태 컬럼을 두지 않는다. 소속 세션의 상태가 곧 파일의 상태다(DEC-MEDIA-001).

| 소속 세션 | 파일의 의미 | `sort_order` | 일어날 수 있는 일 |
|---------|-----------|-------------|----------------|
| IN_PROGRESS | 임시로 올린 파일. 아직 붙지 않음 | 없음 | 완료 시 고르면 순서를 받고, 고르지 않으면 삭제 / 운동 취소·자동 정리 시 삭제 |
| COMPLETED | 붙은 파일 | 1 ~ 10 | 세션(그날 기록) 삭제 시에만 삭제. 따로 추가·삭제 불가(BR-002) |

### 3.5 비즈니스 규칙 구현
| BR ID | 규칙 | 강제 위치 | 방법 | 위반 시 |
|-------|-----|----------|-----|--------|
| BR-001 | 본인만 조회 | 서비스, 공통 2.6 | 세션 소유자 확인. 저장소는 비공개, 파일 키를 요청으로 받지 않음 | 403 FORBIDDEN |
| BR-002 | 완료할 때만 붙임, 이후 추가·삭제 불가 | 서비스, API 설계 | 업로드는 진행 중 세션에만(완료면 409). 붙이기는 완료 요청 안에서만. 완료 후 추가·삭제 API를 두지 않음 | 409 WORKOUT_SESSION_NOT_EDITABLE |
| BR-003 | 섞어서 여러 개, 순서 유지 | 서비스, DB | 완료 요청의 `mediaIds` 순서로 `sort_order` 저장. 유일 `ux_workout_media_session_order` | — |
| BR-004 | 세션·계정 삭제 시 파일 삭제 | DB, 서비스, 운영 절차 | 행은 참조(함께 삭제). 파일은 세션을 지우는 모든 경로에서 커밋 후 작업으로 세션 접두어 삭제(6.4). 계정 삭제는 공통 10.7 절차 | — |
| BR-005 | 5개, 사진 20MB, 동영상 100MB·1분 | 서비스, DB | 업로드 시 임시 파일 수·크기·길이 확인, 완료 시 `mediaIds` 5개 이하. 조건 검사 `ck_workout_media_sort_order`(1~10, 줄이기 전 기록 때문에 그대로, DEC-MEDIA-008), `ck_workout_media_file_size` | 400 MEDIA_LIMIT_EXCEEDED / VALIDATION_FAILED |
| BR-006 | JPEG·PNG·HEIC, MP4·MOV | 미디어 처리기, DB | 내용으로 형식 판별. 조건 검사 `ck_workout_media_content_type` | 400 MEDIA_UNSUPPORTED_TYPE |
| BR-007 | 하나라도 실패하면 완료 안 함, 일부만 남기지 않음 | 앱, 서비스 | 앱은 모든 업로드가 성공해야 완료를 부른다(4.2). 서버는 완료 때 고르지 않은 임시 파일을, 취소·자동 정리 때 모든 임시 파일을 지운다. DB 기록 실패 시 방금 저장한 파일을 지운다 | — |
| BR-008 | 보고 있는 하나를 원본 그대로 앨범에 저장 | 앱, API 설계 | 저장은 미디어 뷰어에서 지금 항목의 `originalUrl` 하나만 받는다. 원본 API는 올린 파일을 바이트 그대로 준다. 여러 개를 한꺼번에 받는 API는 두지 않는다 | — |

### 3.6 기능 간 의존관계
- 운동 세션(workout-session)이 있어야 올릴 수 있다. 완료 API의 `mediaIds`는 workout-session API-WORKOUT-003에 들어간다.
- 세션을 지우는 모든 경로(운동 취소 REQ-WORKOUT-010, 자동 정리 REQ-WORKOUT-006, 날짜 단위 삭제 REQ-WORKOUT-005)는 6.4의 파일 삭제를 호출해야 한다.
- 오브젝트 저장소 접속 정보(공통 D-TODO-ARCH-007)와 미디어 처리 도구가 든 실행 환경(공통 D-TODO-ARCH-008)이 있어야 운영에서 동작한다.

---

## 4. 화면 / API 연계 설계

### 4.1 화면-API 매핑
| 화면 ID | 화면 | 사용자 행동 | API |
|--------|-----|-----------|-----|
| workout-session WO-002 | 운동 완료 팝업 | 저장하기 (고른 파일마다) | API-MEDIA-001 `POST /api/v1/workout-sessions/{sessionId}/media` |
| workout-session WO-002 | 운동 완료 팝업 | 저장하기 (모두 올린 뒤) / 건너뛰기 | workout-session API-WORKOUT-003 `POST /api/v1/workout-sessions/{sessionId}/complete` |
| workout-history WO-003 | 운동 기록 달력 | 날짜 선택 (오운완 인증 목록) | workout-history API-WORKOUT-008 `media[]`, 미리보기 API-MEDIA-002 |
| MEDIA-001 | 미디어 뷰어 | 사진·동영상 크게 보기, 넘기기 | API-MEDIA-004 `GET /api/v1/media/{mediaId}/display` |
| MEDIA-001 | 미디어 뷰어 | 저장 (지금 보고 있는 것 하나) | API-MEDIA-003 `GET /api/v1/media/{mediaId}/original` (`Range` 없이 전체) |

### 4.2 화면별 연계 상세

#### workout-session WO-002 운동 완료 팝업 — 오운완 인증 영역
- 진입 조건: 홈에서 운동 종료 (workout-session 4.2)
- 필요 데이터: 진행 중 세션 ID (workout-session API-WORKOUT-002)
- 사용자 입력: "사진 또는 동영상 추가"로 앨범에서 고르거나 바로 촬영(요구사항 TODO-006). 앱은 "고른 개수/5"를 보여주고 고를 때 5개까지만 받고, 형식(JPEG·PNG·HEIC·MP4·MOV)과 크기·길이를 미리 확인해 넘는 파일은 고르지 않게 안내한다(서버가 다시 확인한다).
- API 호출:
  - **저장하기**: 아직 올리지 않은 파일을 고른 순서대로 꺼내 **3개까지 동시에** API-MEDIA-001 (DEC-MEDIA-011). 하나가 실패하면 새 파일은 꺼내지 않고, 올리던 것은 끝까지 기다린다(다시 시도할 때 같은 파일을 두 번 올리지 않게). 파일마다 진행 상황을 보여준다(NFR-PERF-001). 받은 `id`를 파일별로 기억한다. 모두 성공하면 API-WORKOUT-003 `{ "mediaIds": [고른 순서] }`.
  - **건너뛰기**: API-WORKOUT-003 `{ "mediaIds": [] }` (이미 올린 임시 파일은 서버가 지운다).
  - **팝업 닫기**: 호출 없음. 세션은 진행 중. 이미 올린 임시 파일 ID는 앱이 버린다(서버의 임시 파일은 다음 완료·취소·자동 정리 때 지워진다).
- 성공 처리: 완료 200 → 홈(WO-001), 진행 중 카드 사라짐
- 실패 처리 (업로드 중 하나라도 실패하면 **완료를 부르지 않는다**, BR-007):
  - 503 `SERVICE_UNAVAILABLE` / 네트워크 오류 → "올리지 못했어요" + 다시 시도(실패한 파일부터) / 건너뛰기 (ERR-006)
  - 400 `MEDIA_UNSUPPORTED_TYPE` → "지원하지 않는 파일이에요" + 그 파일을 빼고 다시 저장 (ERR-002)
  - 400 `MEDIA_LIMIT_EXCEEDED` → `details.limit`에 따라 "5개까지", "사진은 20MB까지", "동영상은 100MB·1분까지" 안내 (ERR-003)
  - 409 `WORKOUT_SESSION_NOT_EDITABLE` → 6시간이 지나 자동으로 완료된 경우. "운동이 자동으로 완료되어 사진을 붙일 수 없어요" 후 홈 (ERR-005)
  - 404 `WORKOUT_SESSION_NOT_FOUND` → 다른 기기에서 취소된 경우. 안내 후 홈
- 로딩 상태: 업로드·완료 중 저장하기·건너뛰기 비활성, 파일별 진행 표시
- 빈 상태: 고른 파일 없음 → "사진 또는 동영상 추가"만 표시. 저장하기는 `mediaIds` = []로 완료(건너뛰기와 같은 결과)

#### workout-history WO-003 운동 기록 달력 — 오운완 인증 영역 (Figma `workout-history`)
- 진입 조건: 날짜를 고르고 그날 기록에 `media[]`가 있을 때만 영역을 보여준다
- 필요 데이터: API-WORKOUT-008 → `media[]`의 `mediaType`, `previewUrl`
- API 호출: 칸마다 `previewUrl`을 `Authorization` 헤더를 붙여 불러온다(앱의 이미지 컴포넌트가 헤더를 지원해야 한다). 사진은 미리보기, 동영상은 첫 장면 미리보기 위에 재생 표시(요구사항 TODO-007)
- 성공 처리: 칸을 누르면 MEDIA-001을 그 위치부터 연다
- 실패 처리: 미리보기 404·503 → 그 칸만 회색 칸과 다시 시도
- 빈 상태: `media[]`가 비면 영역을 숨긴다
- Figma와 다른 점: 동영상 칸이 빈 칸 + 재생 표시다. 첫 장면 미리보기로 바꾼다(요구사항 8.2)

#### MEDIA-001 미디어 뷰어 (Figma `media-viewer-popup`)
- 진입 조건: WO-003에서 사진·동영상 하나를 누름 (그날 `media[]`와 누른 위치)
- 필요 데이터: `media[]`의 `displayUrl`, `originalUrl`(저장), `mediaType`. 위치 표시 "1 / 3"은 앱이 계산
- 사용자 입력: 옆으로 넘기기, 저장(REQ-MEDIA-004), 닫기
- API 호출: 보이는 항목의 `displayUrl`(API-MEDIA-004). 동영상은 재생기가 `Range` 요청으로 필요한 부분만 받는다. 지금 보이는 동영상만 재생
  - **저장**: 지금 보이는 항목의 `originalUrl`을 `Range` 없이 받아 기기 앨범에 넣는다(3.2 REQ-MEDIA-004)
- 성공 처리: 닫으면 WO-003. 저장 성공 → "앨범에 저장했어요" 안내, 뷰어에 그대로 남는다
- 실패 처리: 404 `MEDIA_NOT_FOUND`(다른 기기에서 그날 기록을 지움) → 안내 후 닫고 WO-003 다시 조회 / 503·네트워크 → 다시 시도
  - 저장 중: 앨범 권한 거부 → "사진을 저장하려면 앨범 접근을 허용해 주세요" + 기기 설정으로 가기 (ERR-008) / 503·네트워크·기기 저장 실패 → "저장하지 못했어요" + 다시 저장 (ERR-009) / 404 → 위와 같다 (ERR-007)
- 로딩 상태: 보기용 사본을 받는 동안 미리보기(`previewUrl`)를 흐리게 먼저 보여준다. 저장 중에는 진행 상황을 보여주고 저장 버튼을 비활성(같은 항목을 두 번 받지 않게). 넘기거나 닫아도 진행 중인 저장은 끝까지 한다
- 빈 상태: 해당 없음

---

## 5. API 설계
URL·필드·파일 업로드 규칙은 공통 설계 5장을 따른다.

### 5.1 API 목록
| API ID | Method | URL | 인증 | 설명 | 관련 요구사항 |
|--------|--------|-----|-----|-----|-------------|
| API-MEDIA-001 | POST | /api/v1/workout-sessions/{sessionId}/media | 필요 | 사진·동영상 하나를 임시로 올림 | REQ-MEDIA-001, IF-MEDIA-001, IF-MEDIA-003 |
| (workout-session API-WORKOUT-003) | POST | /api/v1/workout-sessions/{sessionId}/complete | 필요 | `mediaIds`로 붙이며 완료 | REQ-MEDIA-001, IF-MEDIA-001 |
| (workout-history API-WORKOUT-008) | GET | /api/v1/workout-days/{date} | 필요 | 그날 기록과 `media[]` 목록 | REQ-MEDIA-002, IF-MEDIA-002 |
| API-MEDIA-002 | GET | /api/v1/media/{mediaId}/preview | 필요 | 미리보기 JPEG | REQ-MEDIA-002, IF-MEDIA-003 |
| API-MEDIA-003 | GET | /api/v1/media/{mediaId}/original | 필요 | 원본(사진·동영상), Range 지원. 앨범 저장 | REQ-MEDIA-004, IF-MEDIA-003 |
| API-MEDIA-004 | GET | /api/v1/media/{mediaId}/display | 필요 | 보기용 사본, Range 지원. 크게 보기 | REQ-MEDIA-003, IF-MEDIA-003 |

### 5.2 공통 응답 모델
**WorkoutMediaResponse** (API-MEDIA-001)
```json
{
  "id": "019a1c2e-5f00-7d11-9a3b-2c4d6e8f0a12",
  "mediaType": "VIDEO",
  "contentType": "video/quicktime",
  "fileSize": 48213504,
  "createdAt": "2026-10-09T10:05:12Z"
}
```
| 필드 | 설명 | 근거 |
|-----|-----|-----|
| `mediaType` | `PHOTO` / `VIDEO` | DATA-001 |
| `contentType` | 서버가 내용으로 판별한 형식: `image/jpeg`, `image/png`, `image/heic`, `video/mp4`, `video/quicktime` | BR-006 |
| `fileSize` | 바이트 | BR-005 |

### 5.3 API 상세

#### API-MEDIA-001 사진·동영상 임시 올리기
- 목적: 운동 완료 전에 파일 하나를 검증·저장하고 미리보기를 만든다. 완료 요청에서 고르기 전까지는 붙은 것이 아니다.
- Method / URL: `POST /api/v1/workout-sessions/{sessionId}/media`
- 인증: 필요
- 관련 요구사항: REQ-MEDIA-001, IF-MEDIA-001, IF-MEDIA-003, BR-002, BR-005, BR-006, BR-007

Request
- `Content-Type: multipart/form-data`
- 파일 부분 `file`: 사진 또는 동영상 하나

Response `201 Created` — WorkoutMediaResponse

Validation
| 필드 | 타입 | 필수 | 규칙 | 근거 |
|-----|-----|-----|-----|-----|
| sessionId (경로) | UUID | Y | UUID 형식 | 공통 5장 |
| file | 파일 | Y | 내용 기준 JPEG·PNG·HEIC(사진) 또는 MP4·MOV(동영상) | BR-006 |
| file 크기 | — | — | 사진 ≤ 20 × 1024 × 1024 바이트, 동영상 ≤ 100 × 1024 × 1024 바이트 | BR-005 |
| 동영상 길이 | — | — | ≤ 60초 | BR-005 |
| 세션의 임시 파일 수 | — | — | 올리기 전 4개 이하 (올린 뒤 5개 이하) | BR-005 |

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 400 | VALIDATION_FAILED | `file` 없음·빈 파일, 서버 전체 상한(공통 10.1) 초과, 경로 UUID 형식 오류 | — |
| 400 | MEDIA_UNSUPPORTED_TYPE | 허용하지 않는 형식, 읽을 수 없는 파일 | ERR-002 |
| 400 | MEDIA_LIMIT_EXCEEDED | 개수(`COUNT`)·크기(`FILE_SIZE`)·길이(`DURATION`) 초과 | ERR-003 |
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 403 | FORBIDDEN | 다른 사용자의 세션 | ERR-004 |
| 404 | WORKOUT_SESSION_NOT_FOUND | 세션 없음(취소·자동 삭제 포함) | workout-common ERR-009 |
| 409 | WORKOUT_SESSION_NOT_EDITABLE | 완료된 세션 | ERR-005 |
| 503 | SERVICE_UNAVAILABLE | 오브젝트 저장소 장애 | ERR-006 |

#### API-MEDIA-002 미리보기 내려받기
- 목적: 운동 기록 달력의 작은 칸에 보여줄 미리보기를 준다.
- Method / URL: `GET /api/v1/media/{mediaId}/preview`
- 인증: 필요
- 관련 요구사항: REQ-MEDIA-002, IF-MEDIA-003, BR-001, NFR-PERF-001

Response `200 OK` — 본문이 JPEG 파일, `Content-Type: image/jpeg`, `Cache-Control: private, max-age=31536000, immutable`

Validation: 경로 변수가 UUID 형식이 아니면 400

Errors
| HTTP | 에러 코드 | 조건 | 관련 |
|------|----------|-----|-----|
| 401 | UNAUTHORIZED | 인증 없음 | ERR-001 |
| 403 | FORBIDDEN | 다른 사용자의 파일 | ERR-004 |
| 404 | MEDIA_NOT_FOUND | 없음, 삭제됨, 아직 붙지 않은 임시 파일 | ERR-007 |
| 503 | SERVICE_UNAVAILABLE | 오브젝트 저장소 장애 | — |

#### API-MEDIA-003 원본 내려받기
- 목적: 미디어 뷰어에서 사진을 크게 보거나 동영상을 재생하고, 앱이 기기 앨범에 저장할 원본을 받는다(DEC-MEDIA-009).
- Method / URL: `GET /api/v1/media/{mediaId}/original`
- 인증: 필요
- 관련 요구사항: REQ-MEDIA-003, REQ-MEDIA-004, IF-MEDIA-003, BR-001, BR-008

Request
- Header (선택): `Range: bytes=0-1048575` — 동영상 재생기가 보낸다

Response `200 OK` — 본문이 원본 파일, `Content-Type` = 저장된 형식, `Accept-Ranges: bytes`, `Content-Length` = 원본 바이트 수(앨범 저장 진행 표시), `Cache-Control: private, max-age=31536000, immutable` / `206 Partial Content` — `Range`가 있을 때 그 범위와 `Content-Range`

Validation: 경로 변수가 UUID 형식이 아니면 400. 범위가 파일 밖이면 416(HTTP 표준 동작, DEC-MEDIA-007)

Errors: API-MEDIA-002와 같다.

#### API-MEDIA-004 보기용 사본 내려받기
- 목적: 미디어 뷰어에서 사진을 크게 보고 동영상을 재생할 작은 사본을 준다(DEC-MEDIA-010). 원본(API-MEDIA-003)은 앨범 저장에만 쓴다.
- Method / URL: `GET /api/v1/media/{mediaId}/display`
- 인증: 필요
- 관련 요구사항: REQ-MEDIA-003, IF-MEDIA-003, BR-001

Request
- Header (선택): `Range` — API-MEDIA-003과 같다

Response `200 OK` / `206 Partial Content` — 본문이 보기용 사본, `Content-Type` = `image/jpeg`(사진) 또는 `video/mp4`(동영상), `Accept-Ranges: bytes`, `Cache-Control: private, max-age=31536000, immutable`. 사본이 아직 없으면 원본을 API-MEDIA-003과 같은 형식으로 주되 `Cache-Control: private, no-store` (3.2)

Validation, Errors: API-MEDIA-003과 같다.

---

## 6. 데이터 설계
이름 규칙·논리 타입·제약 종류는 공통 설계 6장을 따른다.

### 6.1 ERD
```
users 1 ── N workout_session 1 ── N workout_media
                                     │
                                     └─ 파일: users/{userId}/workout-sessions/{sessionId}/media/{mediaId}/original
                                              users/{userId}/workout-sessions/{sessionId}/media/{mediaId}/preview.jpg
                                              users/{userId}/workout-sessions/{sessionId}/media/{mediaId}/display
```

### 6.2 테이블 정의

#### workout_media — 근거: DATA-001
| 컬럼 | 타입 | Null | 기본값 | 설명 | 근거 |
|-----|-----|------|-------|-----|-----|
| id | ID | N | 애플리케이션 생성 | PK. 파일 키의 일부 | 공통 DEC-ARCH-010 |
| workout_session_id | ID | N | | 소속 세션 | DATA-001 |
| media_type | 열거(PHOTO, VIDEO) | N | | 사진/동영상 | DATA-001, BR-003 |
| content_type | 열거(image/jpeg, image/png, image/heic, video/mp4, video/quicktime) | N | | 내용으로 판별한 형식. 내려받을 때 `Content-Type` | BR-006 |
| file_size | 정수 | N | | 원본 바이트 수 | BR-005 |
| sort_order | 정수 | Y | | 붙은 순서(1부터). 임시 파일이면 없음 | BR-003, DEC-MEDIA-001 |
| created_at | 시각 | N | 현재 시각 | 올린 시각 | DATA-001 |

- PK: `id`
- 참조(함께 삭제): `workout_session_id → workout_session.id` — 세션이 지워지면 행도 지워진다(BR-004). 파일은 6.4
- 유일 `ux_workout_media_session_order`: `(workout_session_id, sort_order)` — 붙은 순서가 겹치지 않게. `sort_order`가 없는 행끼리는 비교하지 않는다 (BR-003)
- 조건 검사 `ck_workout_media_sort_order`: `sort_order`가 없거나 1 이상 10 이하. BR-005의 5개는 서비스가 확인하고, 이 검사는 상한을 줄이기 전에 붙은 기록까지 담는 바깥 한계다 (BR-005, DEC-MEDIA-008)
- 조건 검사 `ck_workout_media_file_size`: `file_size`가 1 이상 104,857,600(100 × 1024 × 1024) 이하. 사진의 20MB 상한은 형식별이라 서비스가 확인한다 (BR-005)
- 조건 검사 `ck_workout_media_type`: `media_type`과 `content_type`이 맞는다(PHOTO ↔ image/*, VIDEO ↔ video/*) (BR-006)
- 파일 키는 저장하지 않는다. `users/{userId}/workout-sessions/{workout_session_id}/media/{id}/` 아래 `original`, `preview.jpg`, `display`로 정해진다(계산할 수 있는 값은 저장하지 않는다, DEC-MEDIA-002). 원본의 형식은 `content_type`, 보기용 사본의 형식은 `media_type`으로 정해진다(사진 JPEG, 동영상 MP4). 사본이 만들어졌는지는 저장하지 않고 저장소에 있는지로 본다(DEC-MEDIA-010)
- 수정되는 컬럼은 `sort_order` 한 번뿐이라 `updated_at`을 두지 않는다.

### 6.3 인덱스
| 인덱스 | 테이블 | 컬럼 | 대상 조회 | 근거 |
|-------|-------|-----|---------|-----|
| ux_workout_media_session_order | workout_media | (workout_session_id, sort_order), 유일 | 세션의 파일을 붙은 순서로 조회(날짜별 목록), 업로드 시 임시 파일 수 세기, 완료 시 세션 파일 조회, 함께 삭제. 내려받기는 PK로 조회 | BR-003, BR-005, REQ-MEDIA-002 |

### 6.4 삭제 정책
- 행: 세션이 지워지면 참조(함께 삭제)로 지워진다. 완료할 때 고르지 않은 임시 파일 행은 서비스가 지운다.
- 파일: DB 행을 지운 트랜잭션의 **커밋 후 작업**으로 지운다(공통 2.6).
  | 경로 | 지우는 파일 |
  |-----|----------|
  | 운동 완료(고르지 않은 임시 파일) | 그 미디어의 `original`, `preview.jpg`, `display` |
  | 운동 취소(workout-session REQ-WORKOUT-010), 날짜 단위 삭제(REQ-WORKOUT-005, API-WORKOUT-009) | 세션 접두어 `users/{userId}/workout-sessions/{sessionId}/` 전체(그날 삭제한 세션마다) |
  | 방치된 세션 자동 완료(REQ-WORKOUT-006) | 그 세션의 임시 파일 전부(완료되지만 고른 파일이 없다) |
  | 방치된 세션 자동 삭제(REQ-WORKOUT-006) | 세션 접두어 전체 |
  | 계정 삭제(운영자) | `users/{userId}/` 전체 — 공통 10.7 절차 |
- 파일 삭제가 실패하면 WARN 로그만 남긴다. 그 파일은 DB가 가리키지 않아 사용자에게 보이지 않는다(공통 DEC-ARCH-016).

### 6.5 스키마 변경 목록
workout-common 설계 6.5의 스키마 변경 3 다음에 적용한다.

| 순서 | 변경 | 내용 |
|-----|-----|-----|
| 4 | workout_media 생성 | 6.2의 테이블·제약, 6.3의 인덱스 |

---

## 7. 인증 / 인가 및 보안 설계
공통 설계 7장을 따른다.

### 7.1 API별 인증
- API-MEDIA-001 ~ 003은 모두 인증 필요 (NFR-SEC-001). 파일 내려받기도 공개 경로가 아니다.

### 7.2 사용자별 데이터 접근 제한
- 업로드는 경로의 세션 소유자를 확인한다. 다른 사용자 것이면 403 (ERR-004, 부록 C-1).
- 완료 요청의 `mediaIds`는 그 세션의 행만 인정한다. 다른 세션·다른 사용자의 미디어 ID를 넣으면 400.
- 파일 키는 서버가 ID로 만들고, 요청으로 받지 않는다. 오브젝트 저장소는 비공개다(공통 2.6, NFR-SEC-002).
- 내려받기는 미디어 ID로 소속 세션의 소유자를 확인한다. 다른 사용자 것이면 403 (ERR-004, BR-001). 미디어 ID를 알아도 본인이 아니면 받을 수 없다.
- 응답의 `Cache-Control`은 `private`이라 중간 캐시(프록시)에 남지 않는다.

### 7.3 민감 데이터 / 로그
- 사진·동영상은 개인의 모습이 담긴 민감 정보다. 파일 내용, 파일 이름(기기에서 온 원래 이름)은 로그에 남기지 않는다. 원래 파일 이름은 저장하지도 않는다.
- 로그에는 이벤트 이름, userId, sessionId, mediaId, 형식, 크기만 남긴다.

---

## 8. 예외 / 에러 처리 설계
응답 형식과 상태 코드 정책은 공통 설계 8장을 따른다.

### 8.1 에러 응답 예
```json
{
  "code": "MEDIA_LIMIT_EXCEEDED",
  "message": "동영상은 1분까지 올릴 수 있습니다.",
  "timestamp": "2026-10-09T10:05:12Z",
  "details": { "limit": "DURATION", "max": 60 }
}
```

### 8.2 이 기능의 에러 코드
| 요구사항 ERR | 에러 코드 | HTTP | 메시지 | details | 발생 위치 |
|-------------|----------|------|-------|--------|----------|
| ERR-001 | UNAUTHORIZED (공통) | 401 | 로그인이 필요합니다. | — | 인증 필터 |
| ERR-002 | MEDIA_UNSUPPORTED_TYPE | 400 | 지원하지 않는 파일 형식입니다. 사진은 JPEG·PNG·HEIC, 동영상은 MP4·MOV만 올릴 수 있습니다. | — | 미디어 처리기 |
| ERR-003 | MEDIA_LIMIT_EXCEEDED | 400 | 상한별 문장: "사진·동영상은 5개까지 올릴 수 있습니다." / "사진은 20MB까지 올릴 수 있습니다." / "동영상은 100MB까지 올릴 수 있습니다." / "동영상은 1분까지 올릴 수 있습니다." | `limit`: `COUNT` \| `FILE_SIZE` \| `DURATION`, `max`: 5 \| 바이트 수 \| 60 | WorkoutMediaService.upload |
| ERR-004 | FORBIDDEN (공통) | 403 | 접근할 수 없는 데이터입니다. | — | WorkoutMediaService 소유자 확인 |
| ERR-005 | WORKOUT_SESSION_NOT_EDITABLE (workout-common) | 409 | 완료된 운동 기록은 수정할 수 없습니다. | — | WorkoutMediaService.upload |
| ERR-006 | SERVICE_UNAVAILABLE (공통) | 503 | 일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. | — | 파일 저장소 |
| ERR-007 | MEDIA_NOT_FOUND | 404 | 사진·동영상을 찾을 수 없습니다. | — | WorkoutMediaService.download |
| ERR-008 | (서버 응답 없음) | — | 앱: "사진을 저장하려면 앨범 접근을 허용해 주세요" | — | 앱(기기 앨범 권한 확인, 요청 전) |
| ERR-009 | SERVICE_UNAVAILABLE (공통) / 네트워크 오류 / 기기 저장 실패 | 503 / — / — | 앱: "저장하지 못했어요" + 다시 저장 | — | 파일 저장소, 앱 |

제약 위반 변환 대상: 없음. `ux_workout_media_session_order` 위반은 세션 변경 잠금 안에서 순서를 매기므로 정상 흐름에서 생기지 않는다(생기면 500).

---

## 9. 비기능 요구사항 설계
| NFR ID | 요구사항 | 설계 대응 | 확인 방법 |
|--------|---------|----------|----------|
| NFR-SEC-001 | 인증된 사용자만 | 7.1 | 토큰 없이 업로드 401 테스트 |
| NFR-SEC-002 | 주소를 알아도 본인만 | 비공개 저장소, 파일 키를 서버가 만듦, 소유자 확인 (공통 DEC-ARCH-014) | 다른 사용자 세션에 업로드 403, 다른 세션의 mediaId로 완료 400 테스트 |
| NFR-PERF-001 | 목록·미리보기 p95 500ms, 업로드는 시간 기준 없이 진행 상황 | 업로드는 파일 하나씩 받아 앱이 파일별 진행을 보여줄 수 있다. 미리보기(긴 변 640픽셀 JPEG)는 업로드 때 만들어 두어 내려받을 때 처리하지 않는다. 목록은 날짜별 기록 안의 쿼리 1회(6.3 인덱스). 내려받기는 PK 조회 1회 + 저장소 읽기. 바뀌지 않는 파일이라 앱이 캐시한다(DEC-MEDIA-006). 원본은 Range로 필요한 부분만. 앨범 저장은 시간 기준 없이 `Content-Length`로 진행 상황을 보여준다(REQ-MEDIA-004) | 부하 테스트 (공통 D-TODO-ARCH-004) |
| NFR-AVAIL-001 | 업로드 실패해도 세트를 잃지 않음 | 업로드는 세션을 바꾸지 않는다. 실패해도 세션은 진행 중이고 완료는 따로 부른다 | 저장소 장애(컨테이너 중지) 시 업로드 503, 세션·세트 그대로 테스트 |
| NFR-INTEG-001 | 세션 없는 파일이 남지 않음 | 공통 2.6 순서, 6.4 삭제 경로 | 취소·완료(고르지 않음)·자동 정리 후 저장소에 그 키가 없는지 테스트 |

---

## 10. 구현 구조 및 개발 전략

### 10.1 컴포넌트 구성
| 도메인 | 컴포넌트 | 역할 | 책임 |
|-------|---------|-----|-----|
| media | WorkoutMediaController | API 진입점 | API-MEDIA-001 ~ 004 |
| media | WorkoutMediaService | 서비스 | 3.2 (1)의 순서: 빠른 확인 → 처리 → 저장 → 잠금 후 기록, 실패 시 파일 삭제, 동영상 사본 변환 대기열(한 번에 하나, 메모리). 붙이기(`attach`), 내려받기 권한 확인(`download`), 세션 삭제 경로의 파일 삭제(`deleteFilesOfSessions`, `discardStaged`) |
| media | WorkoutMediaRepository | 저장소 | 행 저장, 세션별 개수, 세션별 조회, 고르지 않은 행 삭제 |
| common | ObjectStorage | 파일 저장소 | 공통 10.1 |
| common | MediaProcessor | 미디어 처리기 | 형식 판별, 길이, 미리보기·사진 사본(`still`), 동영상 사본(`transcode`) (공통 10.1) |
| session | WorkoutSessionService.complete | 서비스 | `mediaIds` 처리(3.2 (2)), workout-session 설계 |

### 10.2 구현 순서
| 순서 | 작업 | 관련 요구사항 | 완료 기준 |
|-----|-----|-------------|----------|
| 0 | 오브젝트 저장소 연결, 미디어 처리 도구가 든 실행 환경 (공통 D-TODO-ARCH-007, 008) | — | 테스트 컨테이너에 파일 저장·조회·접두어 삭제 테스트 |
| 1 | 스키마 변경 4 | DATA-001 | 적용 성공, 조건 검사 테스트 |
| 2 | 미디어 처리기 | BR-005, BR-006, IF-MEDIA-003 | JPEG·PNG·HEIC·MP4·MOV 판별, 다른 형식 거부, 61초 동영상 거부, 미리보기 긴 변 640 테스트 |
| 3 | 업로드 API | REQ-MEDIA-001, ERR-002 ~ ERR-006 | 201, 형식·크기·길이·개수 400, 완료된 세션 409, 저장소 장애 503(DB 행 없음) 테스트 |
| 4 | 완료 API의 `mediaIds` (workout-session 10.2와 함께) | BR-003, BR-007 | 순서 저장, 고르지 않은 행·파일 삭제, 건너뛰기 시 모두 삭제 테스트 |
| 5 | 세션 삭제 경로의 파일 삭제 | BR-004, NFR-INTEG-001 | 취소·자동 정리 후 저장소에 파일 없음 테스트 |
| 6 | 날짜별 목록(workout-history 10.2 순서 10과 함께) | REQ-MEDIA-002 | 붙은 순서, 임시 파일 제외 테스트 |
| 7 | 미리보기·원본 내려받기 | REQ-MEDIA-003, ERR-004, ERR-007 | 200·형식, Range 206, 임시 파일 404, 다른 사용자 403, 날짜 삭제 후 404 테스트 |
| 8 | 앨범 저장 (앱만) | REQ-MEDIA-004, BR-008, ERR-008, ERR-009 | 서버 변경 없음. 앱에서 사진(JPEG·PNG·HEIC)·동영상(MP4·MOV) 각각 저장 후 앨범에서 열림, 권한 거부·네트워크 끊김 안내 확인 |

순서 0 ~ 5는 v0.1 반영으로 끝났다(2026-10-09).

### 10.3 테스트 포인트
- BR-005 경계값: 사진 20MB / 20MB+1바이트, 동영상 100MB / 100MB+1바이트, 60초 / 61초, 임시 파일 5개째 성공·6개째 400 `COUNT`, 완료 `mediaIds` 5개 200·6개 400.
- BR-006: 확장자를 `.jpg`로 바꾼 GIF·PDF는 400 (내용으로 판별).
- BR-007: 업로드 3개 중 1개 실패 후 완료하지 않으면 세션은 진행 중. 이후 `mediaIds` 2개로 완료하면 나머지 1개 행·파일이 지워진다.
- BR-002: 완료된 세션에 업로드 409. 완료 요청의 `mediaIds`에 다른 세션 ID → 400.
- 동시성: 업로드 처리 중(저장 후, 기록 전) 세션이 완료되면 업로드는 409이고 방금 저장한 파일이 지워진다.
- NFR-AVAIL-001: 저장소 컨테이너를 멈추고 업로드 → 503, `workout_media` 행 0건, 세션 상태 그대로.
- REQ-MEDIA-003: 원본에 `Range: bytes=0-99` → 206, 본문 100바이트, `Content-Range` 확인.
- ERR-007: 아직 붙지 않은 임시 파일의 ID로 내려받으면 404.
- REQ-MEDIA-004, BR-008 (서버 쪽): `Range` 없이 받은 원본이 올린 파일과 바이트 단위로 같고 `Content-Type`이 저장된 형식이다 — REQ-MEDIA-003 원본 테스트가 이미 확인한다. 앱 쪽은 10.2 순서 8.

---

## 부록 A. 설계 결정 기록
| ID | 결정 | 이유 | 버린 대안 |
|----|-----|-----|----------|
| DEC-MEDIA-001 | 파일은 하나씩 임시로 올리고(세션은 진행 중), 완료 요청의 `mediaIds`로 붙인다. 상태 컬럼 없이 소속 세션의 상태로 임시/붙음을 구분한다 | 파일마다 진행 상황을 보여주고 실패한 파일만 다시 올릴 수 있다(BR-007, NFR-PERF-001). 붙이기와 완료가 한 트랜잭션이라 "완료됐는데 일부만 붙은" 상태가 없다. 진행 중 세션의 행은 모두 임시, 완료 세션의 행은 모두 붙은 것이므로 상태 컬럼이 필요 없다 | 완료 요청 하나에 모든 파일을 담기: 최대 1GB 요청, 하나만 실패해도 전부 다시 올림 / 상태 컬럼(PENDING·ATTACHED): 세션 상태와 어긋날 수 있음 |
| DEC-MEDIA-002 | 파일 키를 저장하지 않고 `users/{userId}/workout-sessions/{sessionId}/media/{mediaId}/`로 계산 | 키와 ID가 어긋날 수 없고, 세션·사용자 접두어로 한 번에 지울 수 있다(6.4, 공통 7.7) | 키 컬럼 저장: 저장 값과 실제 위치가 어긋날 수 있음 |
| DEC-MEDIA-003 | 미리보기는 JPEG, 긴 변 640픽셀, 동영상은 첫 장면(0초) | 목록의 작은 칸에 충분하고 파일이 작다. HEIC도 JPEG로 바꿔 어느 기기에서나 보인다. 첫 장면은 요구사항 TODO-007 결정 | 여러 크기 생성: 쓰는 곳이 하나뿐 |
| DEC-MEDIA-004 | 업로드는 오래 걸리는 처리(형식 판별·미리보기·저장)를 잠금 없이 하고, 마지막 기록 때만 세션 변경 잠금 | 100MB 동영상을 올리는 동안 같은 세션의 세트 기록·완료가 막히지 않는다. 마지막에 다시 확인하므로 완료된 세션에 붙지 않는다 | 처음부터 잠금: 업로드 동안 세션 전체가 멈춤 |
| DEC-MEDIA-005 | 크기 상한의 MB는 1024 × 1024 바이트 | 휴대폰과 앱이 보여주는 크기 표기와 맞춘다 | 1,000,000 바이트: 앱 표시와 몇 % 차이 |
| DEC-MEDIA-006 | 미리보기·원본 응답에 `Cache-Control: private, max-age=31536000, immutable` | 파일은 올린 뒤 바뀌지 않는다(BR-002). 앱이 한 번 받은 파일을 다시 받지 않아 목록이 빠르고(NFR-PERF-001) 서버를 거치는 전송이 준다(공통 DEC-ARCH-014의 한계 완화). `private`라 공용 캐시에는 남지 않는다 | 캐시 없음: 달력에서 날짜를 오갈 때마다 다시 받음 |
| DEC-MEDIA-007 | 원본은 `Range` 요청을 지원하고 저장소의 범위 읽기로 넘긴다 | 동영상 재생기는 범위 요청으로 앞부분부터 재생하고 넘겨 보기를 한다. 100MB 동영상을 다 받은 뒤 재생하지 않아도 된다 | 전체만 지원: 긴 동영상의 첫 재생이 늦고 넘겨 보기가 안 됨 |
| DEC-MEDIA-008 | 세션당 개수를 5개로 줄여도(요구사항 v0.5) 조건 검사 `ck_workout_media_sort_order`는 1~10으로 두고, 5개는 업로드·완료 검증에서 지킨다 | 줄이기 전에 6~10개를 붙여 완료한 기록이 있으면 검사를 1~5로 바꾸는 스키마 변경이 실패한다. 그 기록은 고칠 수 없고 그대로 보여준다(요구사항 8.2). 새로 붙는 행은 모두 완료 검증(5개 이하)을 거친다 | 검사를 1~5로 바꿈: 기존 기록을 지우거나 순서를 바꿔야 함 / 새 행만 검사하는 제약: DB에 따라 지원이 다르고 얻는 것이 없음 |
| DEC-MEDIA-009 | 앨범 저장(REQ-MEDIA-004)은 새 API 없이 원본 내려받기(API-MEDIA-003)를 그대로 쓴다. 확장자는 앱이 `Content-Type`으로 정한다 | 저장할 것이 원본 그대로이고(BR-008) 권한 확인(BR-001)도 같다. 앱은 응답 본문을 파일로 받아 앨범에 넣으므로, 브라우저용 `Content-Disposition: attachment`나 파일 이름이 필요 없다. 서버 코드·테스트를 늘리지 않는다 | 저장 전용 API(`/download` 또는 `?download=true` + `Content-Disposition`): 같은 파일을 같은 검사로 두 번 내려주고 앱에서 쓰는 곳이 없음 / 원래 파일 이름 저장: 민감 정보라 저장하지 않기로 함(7.3) |

| DEC-MEDIA-010 | 업로드 때 보기용 사본을 만들어 뷰어는 사본(API-MEDIA-004)을 보고, 원본은 바꾸지 않고 앨범 저장에만 쓴다. 사진은 업로드 중에 긴 변 2048 JPEG, 동영상은 응답 뒤 백그라운드에서 720p·30fps 이하 H.264 MP4(HDR은 SDR로). 사본이 없으면 원본을 캐시하지 않게 대신 준다 | iPhone 원본은 사진 3~12MB, 4K HEVC 동영상은 분당 170~400MB(약 50Mbps)라 휴대폰 망에서 크게 보기와 재생이 느렸다(v0.5). 사본은 사진 수백 KB, 동영상 분당 수 MB다. 동영상 변환은 서버에서 수십 초~수 분이라 업로드를 기다리게 하지 않는다. 원본을 유지하라는 사용자 결정(BR-008, REQ-MEDIA-003 원본 그대로) | 앱에서 압축 후 업로드: 원본이 남지 않음 / 동영상도 업로드 중 변환: 업로드가 더 느려짐 / 사본 상태 컬럼: 스키마 변경이 필요하고 저장소와 어긋날 수 있음 / 재시작해도 남는 변환 대기열: 재시작 때 빠진 동영상은 원본을 보여 줄 뿐이라 지금은 필요 없음 |
| DEC-MEDIA-011 | 앱은 파일을 3개까지 동시에 올린다. 하나가 실패하면 새로 꺼내지 않고 올리던 것을 기다린 뒤 실패로 끝낸다 | 한 파일의 서버 처리(형식 판별·미리보기·저장) 시간과 다음 파일의 전송이 겹쳐 전체 시간이 준다. 서버는 파일마다 독립이고, 개수 상한(BR-005)은 세션 잠금 안에서 다시 확인하므로 동시에 와도 넘지 않는다 | 하나씩(v0.4): 처리 시간이 그대로 더해짐 / 5개 모두 동시에: 망이 좁으면 각 파일이 늦게 끝나 진행 표시가 오래 멈춘다 |

## 부록 B. 설계 미결정 사항
- **D-TODO-MEDIA-001** ~~날짜별 목록, 크게 보기, 없는 파일 오류, 미디어 뷰어 연계~~ **결정됨 (v0.2):** 3.2, 4.2, API-MEDIA-002·003, workout-history API-WORKOUT-008

## 부록 C. 요구사항 피드백
1. ~~**ERR-004의 "존재 여부도 드러내지 않는다":**~~ **해결 (요구사항 v0.4):** 문구를 빼고 권한 오류(403)로 맞췄다. 권한 오류(403)를 돌려주면 그 ID의 세션·파일이 **있다는 것**이 드러난다. 숨기려면 다른 사용자 것도 "찾을 수 없음"(404)으로 응답해야 하는데, 그러면 ERR-004의 "권한 오류"와 다르다. 운동 기록(workout-common 부록 C-5, DEC-WORKOUT-007)도 같은 문제를 403으로 정했다. (설계: 403. 요구사항에서 "존재 여부도 드러내지 않는다"를 빼거나, 두 문서 모두 404로 바꾸기를 제안한다)
2. ~~**BR-002 "진행 중인 세션에는 붙일 수 없다"와 임시 업로드:**~~ **해결 (요구사항 v0.4):** "먼저 올려 두고 완료하면서 붙인다"로 고쳤다. 설계는 완료 전에 진행 중 세션으로 파일을 임시로 올린다(DEC-MEDIA-001). 붙는 시점은 완료 요청이므로 규칙은 지켜지지만, 요구사항 문구만 보면 진행 중 세션에 올리는 것이 금지로 읽힐 수 있다. (설계: 임시 업로드는 붙인 것으로 보지 않는다)
3. ~~**팝업을 닫은 뒤 남는 임시 파일:**~~ **해결 (요구사항 v0.4):** 요구사항 3.1에 설명을 넣었다. 요구사항 3.1은 "팝업 닫기 → 아무것도 올리지 않는다"인데, 저장하기 중 일부를 올리고 실패한 뒤 팝업을 닫으면 올린 파일이 서버에 임시로 남는다. 다음 완료·취소·자동 정리 때 지워진다. (설계: 그대로. 사용자에게는 보이지 않는다)
