# Let's Workout 공통 설계 문서

- 문서 버전: v0.15
- 작성일: 2026-10-05
- 상태: 초안
- 적용 대상: 모든 기능 설계 문서(`docs/design/<기능>.md`)가 이 문서를 따른다.
- 변경 이력:
  - v0.2 — 기술에 묶인 내용을 1.4(기술 스택)와 10장(기술 대응표, 코드 구조)으로 모으고, 나머지는 기술 중립 용어로 바꿈
  - v0.3 — 모든 PK를 UUIDv7로 통일 (DEC-ARCH-010). 순서는 ID가 아니라 시각 컬럼으로 정함
  - v0.4 — 인증 방식을 "로그인 토큰 + 서버 조회"로 변경(DEC-ARCH-011, DEC-ARCH-005 대체). 에러 응답에 `details` 추가. DB 자동 동작 개념, users ID의 DB 생성 예외, 계정 관리 SQL 절차 추가
  - v0.5 — 인증 구현 반영: 한 테이블의 단순 조건부 갱신은 JPA(10.3), UUIDv7 생성 수단 확정(D-TODO-ARCH-006), `users.id`는 버전 무관 `uuid_v7()` 함수, 현재 시각은 주입한 시계(10.1)
  - v0.6 — CI/CD 추가: 컨테이너 이미지 + 레지스트리 + CI 서버 + 서버 1대 compose 배포(2.4, 10.8), PostgreSQL 18 확정(D-TODO-ARCH-001)
  - v0.7 — `users.id` 기본값을 직접 정의한 `uuid_v7()` 대신 PostgreSQL 18 내장 `uuidv7()`로 교체 (스키마 변경 V3)
  - v0.8 — 운영 사용 전이라 인증 스키마 변경 V1~V3을 V1 하나로 합침. D-TODO-ARCH-005 결정(PL/pgSQL은 `[jooq ignore]` 주석으로 코드 생성에서 제외). CI 에이전트 label·`DEPLOY_HOST` 전역 속성(10.8)
  - v0.9 (2026-10-09) — 오운완 사진·동영상을 위해 오브젝트 저장소(SeaweedFS, S3 API)와 서버의 미리보기 생성(FFmpeg)을 추가(DEC-ARCH-014 ~ 016). DB와 파일의 일관성 규칙(2.6), 커밋 후 작업(2.3), 파일 업로드·내려받기 규칙(5장), 503 응답(DEC-ARCH-017), 서비스가 미리 넣는 기준 데이터의 ID(DEC-ARCH-018) 추가. 1.4의 PostgreSQL 버전을 18로 바로잡음. 운동 기록 스키마 변경 V2·V3은 운영 사용 전이라 다시 만든다(workout-record 설계 6.5)
  - v0.10 (2026-10-09) — 파일 내려받기의 범위 요청과 캐시 규칙(5장, 10.1), SeaweedFS 4의 S3 인증 설정(D-TODO-ARCH-007), FFmpeg는 9.x에서 확인(D-TODO-ARCH-008). v0.9는 코드에 반영됨
  - v0.11 (2026-10-09) — 앱 컨테이너 기반 이미지를 Debian trixie + OpenJDK 21 + FFmpeg 7.1로 확정(D-TODO-ARCH-008 결정, `deploy/base-image/Dockerfile`), SeaweedFS 네트워크 `seaweedfs`(D-TODO-ARCH-007 일부 결정)
  - v0.12 (2026-10-09) — DB를 compose 안의 전용 컨테이너 대신 서버에 이미 떠 있는 PostgreSQL 컨테이너(`postgres`, 네트워크 `postgresql`)로 바꿈. Jenkins 기반 이미지 단계의 GHCR 로그인 방식(10.8)
  - v0.13 (2026-10-09) — 운동 기록 설계를 요구사항 분리에 맞춰 `workout-common`·`workout-session`·`workout-exercise`·`workout-history`로 나눔. 통계 설계(`workout-stats`)를 위해 값 여러 개인 쿼리 파라미터 규칙(5장) 추가
  - v0.14 (2026-10-10) — 운동 종목 관리(`workout-exercise-manage`)를 위해 제약 종류 "유일(대소문자 무시)"(6.3, 10.1)와, 애플리케이션과 DB가 함께 행을 만드는 테이블의 ID 규칙(6.2, DEC-ARCH-019) 추가
  - v0.15 (2026-10-12) — 오브젝트 저장소 제품을 SeaweedFS에서 RustFS 1.0.1로 교체(DEC-ARCH-020). 운영 사용자와 저장된 파일이 없어 옮길 데이터가 없다. 앱 코드는 S3 API만 쓰므로 바뀌지 않고(DEC-ARCH-014), 테스트 컨테이너(10.1)·compose 네트워크(10.8)·D-TODO-ARCH-007만 바뀐다

---

## 1. 설계 개요

### 1.1 목적
모든 기능이 공유하는 아키텍처, 기술 중립 용어, API·데이터 규칙, 인증 방식, 에러 정책을 한 곳에서 확정한다. 기능 설계 문서는 이 문서의 용어로만 쓰고, 이 문서와 달라지는 점만 기록한다.

**기술 의존 내용의 위치:** 언어, 프레임워크, DB 제품에 묶인 내용은 1.4(기술 스택), 10장(기술 대응표, 코드 구조), 그리고 기술 선택에 관한 부록 A·B 항목에만 둔다. 스택을 바꾸면 이곳만 고치고, 기능 설계 문서는 그대로 둔다.

### 1.2 설계 범위
- 포함: 백엔드(이 저장소) 전체에 공통으로 적용되는 결정. 인증 방식의 상세(로그인 API, 로그인 테이블)는 기능 설계 `docs/design/auth.md`.
- 제외: 모바일 클라이언트 구현

### 1.3 대상 시스템
| 시스템 | 설명 |
|-------|-----|
| Mobile App | 모바일 앱. 이 저장소의 API를 호출하는 유일한 클라이언트 |
| Backend API | 이 저장소. HTTP/JSON 기반 REST API |
| Database | 관계형 DB. 운영자가 계정 관리를 위해 직접 접근한다(7.7) |
| Object Storage | 오브젝트 저장소. 사용자가 올린 사진·동영상 파일과 미리보기를 보관한다. Backend API만 접근하고 외부에 공개하지 않는다(2.6) |

### 1.4 기술 스택
| 구성 요소 | 버전 | 선택 이유 | 버린 대안 |
|----------|-----|----------|----------|
| Kotlin | 2.3.21 | 저장소에 이미 설정됨. null 안정성으로 요청·응답 모델의 필수/선택 구분이 타입에 드러난다 | Java: 같은 JVM 생태계지만 이미 Kotlin으로 시작함 |
| JVM | Java 21 toolchain | 저장소 설정. 장기 지원(LTS) 버전 | — |
| Spring Boot | 4.1.1 | 저장소 설정. 웹, 데이터 접근, 보안, 테스트를 한 생태계에서 해결 | — |
| Spring Web MVC | Boot 관리 | 저장소 설정. 요청 수가 크지 않은 동기 CRUD API에 충분하고 단순하다 | WebFlux: 비동기가 필요한 요구사항이 없음 |
| PostgreSQL | 18 (D-TODO-ARCH-001 결정) | 저장소 설정. 6장의 제약 종류(조건부 유일, 조건 검사)와 시각 타입, 트리거를 모두 기본 지원한다 | — |
| Flyway | Boot 관리 | 저장소 설정. 스키마 변경 스크립트를 버전 관리하고 앱 시작 시 적용 | JPA 자동 DDL: 운영 스키마를 코드가 암묵적으로 바꾸게 됨 |
| Spring Data JPA | Boot 관리 | 저장소(단순 저장·조회)를 적은 코드로 구현 (DEC-ARCH-001) | — |
| jOOQ | Boot 관리 | 조회 저장소(조인·집계·페이지·조건부 일괄 갱신)를 타입 안전한 SQL로 구현 (DEC-ARCH-001) | JPQL/네이티브 쿼리: 집계 쿼리가 문자열이 되어 컴파일 시 검증이 안 됨 |
| jOOQ 코드 생성 (Gradle 플러그인, DDL 기반) | jOOQ와 동일 | Flyway 스크립트를 읽어 테이블 클래스를 만든다. 빌드에 DB가 필요 없다 (DEC-ARCH-002) | 실행 중인 DB에서 생성: 빌드에 DB나 컨테이너가 필요 |
| Spring Security | Boot 관리, **추가** | 인증 필터 체인, 인증 실패 처리, 경로별 인증 적용을 표준 구조로 제공한다. 토큰 조회 필터 하나만 직접 만든다 (DEC-ARCH-011) | 보안 라이브러리 없이 필터 직접 구현: 경로별 적용·예외 처리까지 직접 만들어야 함 |
| Bean Validation | Boot 관리, **추가** | 요청 검증을 선언적으로 처리 | 서비스에서 직접 검증: 검증 코드가 흩어짐 |
| 컨테이너 이미지 (Jib Gradle 플러그인, 기반 `ghcr.io/mike-urssu/workout-backend:21-ffmpeg7` = Debian trixie + OpenJDK 21 JRE + FFmpeg 7.1) | 3.5.4 | 실행 환경을 이미지 하나로 고정해 서버 차이를 없앤다. Jib은 Dockerfile·Docker 데몬 없이 Gradle에서 바로 이미지를 만들어 푸시한다 (10.8) | 서버에 JDK 직접 설치: 서버마다 환경이 달라짐 / Dockerfile + `docker build`: Dockerfile 관리와 빌드용 Docker 데몬이 필요 |
| GitHub Container Registry (GHCR) | — | 사용자 결정. 이미지 저장소 | — |
| Jenkins (Multibranch Pipeline) | — | 사용자 결정. 모든 브랜치 테스트, main만 이미지 배포 (10.8) | — |
| Docker Compose | — | 서버 1대에 앱과 DB를 함께 띄운다. 배포 = 이미지 태그 교체 후 재기동 (10.8) | Kubernetes: 지금 규모에 운영 부담이 큼 |
| Testcontainers (PostgreSQL, RustFS) | 저장소 설정, RustFS는 **추가** | 테스트가 운영과 같은 DB 엔진과 같은 오브젝트 저장소에서 돈다. DB 제약과 트리거, 파일 저장·삭제까지 검증 | 내장 DB(H2): 운영 DB 전용 기능을 검증할 수 없음 / 파일 저장소 가짜 구현: S3 호환성 차이를 놓침 |
| RustFS (S3 호환 API) | 1.0.1 (`rustfs/rustfs:1.0.1`) | 사용자 결정(v0.15에 SeaweedFS에서 교체, DEC-ARCH-020). 파일을 DB 밖에 두어 DB 크기와 백업을 가볍게 한다. S3 API로만 접근해 다른 S3 호환 저장소로 옮기기 쉽다 (DEC-ARCH-014) | 앱 서버 디스크에 직접 저장: 서버를 늘리면 파일을 공유할 수 없음 / DB에 파일 저장: DB와 백업이 커짐 |
| AWS SDK for Java 2.x (S3 클라이언트) | **추가**, 구현 시 최신 2.x로 고정 | S3 API 표준 클라이언트. 접속 주소를 RustFS로 바꿔 쓴다 (DEC-ARCH-014) | 저장소 제품 전용 API: 저장소를 바꾸면 코드를 다시 써야 함 |
| FFmpeg (`ffmpeg`, `ffprobe` 명령) | 7.1 (Debian trixie 패키지, D-TODO-ARCH-008 결정) | 사용자 결정(미리보기는 서버가 만든다). 동영상 첫 장면 추출, 동영상 길이 확인, HEIC 등 사진 축소를 한 도구로 처리한다 (DEC-ARCH-015) | JVM 이미지 라이브러리: HEIC·동영상을 읽지 못함 / JavaCV: 네이티브 라이브러리를 앱에 묶어 이미지가 커지고 arm64 확인이 필요 |

### 1.5 설계 원칙
1. **요구사항 추적:** 모든 API, 테이블, 에러 코드는 요구사항 ID를 근거로 가진다.
2. **규칙은 두 겹으로:** 중요한 비즈니스 규칙은 요청 검증과 DB 제약(6.3)을 함께 둔다. 동시 요청, 다른 경로로 들어온 데이터(운영자의 SQL 포함)도 막기 위해서다.
3. **무상태 서버:** 서버는 로그인 상태를 메모리에 두지 않는다. 로그인 상태는 DB에 있고 매 요청 조회한다(7.1). 서버를 여러 대로 늘릴 수 있다.
4. **스키마 변경은 스크립트로만:** 스키마는 버전 관리되는 스키마 변경 스크립트로만 바꾼다. 애플리케이션은 시작 시 스키마가 코드와 맞는지 검증만 한다.
5. **기능 설계는 기술 중립:** 기능 설계는 2·6장의 용어로 쓴다. 용어의 구현은 10장 기술 대응표에서 정한다.
6. **필요한 것만:** 요구사항이 없는 기능(관리자 화면, 요청 수 제한 등)은 만들지 않는다.

### 1.6 요구사항 ↔ 설계 추적
기능별 추적표는 각 기능 설계 문서 1.6에 둔다. 이 문서의 공통 결정이 지원하는 요구사항:

| 공통 설계 | 지원하는 요구사항 |
|----------|----------------|
| 7장 인증 방식 | auth NFR-SEC-001, workout-common NFR-SEC-001, workout-common ERR-001 |
| 7.4 데이터 접근 제한 | workout-common NFR-SEC-002, BR-001, ERR-003 |
| 7.7 계정 관리 절차 | auth 8.2 |
| 8장 에러 정책 | 모든 ERR, NFR-AVAIL-001 |
| 9장 비기능 공통 | NFR-PERF-001, NFR-PERF-003, NFR-AVAIL-001, NFR-LOG-001 |
| 2.6 오브젝트 저장소 연계, 7.4 파일 접근 제한 | workout-media NFR-SEC-001, NFR-SEC-002, NFR-AVAIL-001, NFR-INTEG-001, BR-004 |

---

## 2. 시스템 아키텍처

### 2.1 시스템 구성도
```
┌──────────────────────┐
│  Mobile App          │
│  (토큰: 기기 보안 저장소) │
└──────────┬───────────┘
           │ HTTPS, Authorization: Bearer <login token>
           ▼
┌────────────────────────────────────────────────┐
│  Backend API                                   │
│  ┌──────────┐   ┌───────────┐   ┌───────────┐  │
│  │ 인증 필터 │ → │ API 진입점 │ → │  서비스    │  │
│  └────┬─────┘   └───────────┘   └─────┬─────┘  │
│       │ 로그인 조회      ┌──────────────┴─────┐ │
│       │                 │ 저장소 │ 조회 저장소 │ │
│       │                 └────┬──────────┬────┘ │
└───────┼──────────────────────┼──────────┼──────┘
        ▼                      ▼          ▼
     ┌──────────────────────────────────────┐        ┌──────────┐
     │   관계형 DB                           │ ◀──SQL── │ 운영자    │
     └──────────────────────────────────────┘        └────┬─────┘
                                                          │ 계정 삭제 시 파일 삭제(10.7)
  서비스 ─▶ 파일 저장소 ─S3 API(내부망)─▶ ┌───────────────────┐   │
  서비스 ─▶ 미디어 처리기(ffmpeg)          │ 오브젝트 저장소     │ ◀─┘
                                         │ (외부 비공개)      │
                                         └───────────────────┘
```

### 2.2 컴포넌트 역할
기능 설계는 아래 역할 이름으로 컴포넌트를 나눈다. 각 역할의 구현 방식은 10.1 기술 대응표를 따른다.

| 역할 | 책임 |
|-----|-----|
| 인증 필터 | 요청의 로그인 토큰으로 로그인을 조회·검증하고, 유지 기간을 연장하고, 사용자 ID를 정한다. 실패 시 401 (상세는 auth 설계) |
| API 진입점 | 요청 경로 매핑, 요청 검증, 응답 변환. 비즈니스 규칙을 두지 않는다 |
| 서비스 | 비즈니스 규칙, 소유자 확인, 상태 확인, 트랜잭션 경계 |
| 저장소 | 단순한 데이터 접근: 행 하나를 저장·수정·삭제, ID로 단건 조회, 존재 확인 |
| 조회 저장소 | 복잡한 데이터 접근: 조인, 집계, 페이지 목록, 조건부 일괄 갱신 |
| 전역 예외 처리기 | 모든 예외를 8장 에러 응답으로 변환 |
| 파일 저장소 | 오브젝트 저장소에 파일을 넣고, 꺼내고, 지운다. 파일 키 규칙(2.6)을 한 곳에서 정한다. 비즈니스 규칙을 두지 않는다 |
| 미디어 처리기 | 올라온 파일의 실제 형식 확인, 동영상 길이 확인, 미리보기 이미지 생성. 임시 파일을 쓰고 끝나면 지운다 |
| Mobile App | 화면 표시, 사용자 입력, 토큰 보관(기기 보안 저장소), 에러 코드별 화면 처리 |
| 관계형 DB | 영속 데이터, 6장 제약과 자동 동작으로 규칙 보장 |
| 운영자 | DB에 직접 접근해 계정을 관리한다(7.7). 서비스의 컴포넌트는 아니다 |

### 2.3 동시성·트랜잭션 용어
기능 설계에서 쓰는 개념이다. 구현은 10.1을 따른다.

| 용어 | 의미 |
|-----|-----|
| 트랜잭션 | 요청 하나를 하나의 트랜잭션으로 처리한다. 실패하면 그 요청의 변경은 모두 취소된다 |
| 변경 잠금 | 같은 대상에 대한 변경 요청을 한 번에 하나씩 처리하도록, 대상 행을 읽을 때 잠근다(비관적). 다른 요청은 잠금이 풀릴 때까지 기다린다 |
| 독립 트랜잭션 | 호출한 쪽의 트랜잭션과 별도로 실행하고 먼저 확정한다. 호출한 쪽이 실패해도 결과가 남는다 |
| 조건부 일괄 갱신 | 조건에 맞는 행을 한 문장으로 갱신·삭제한다. 여러 번, 여러 서버에서 동시에 실행해도 결과가 같다(멱등) |
| 제약 위반 변환 | 이름으로 지정한 DB 제약의 위반을 정해진 에러 코드로 바꾼다 |
| 확정 후 오류 응답 | 변경을 먼저 확정(커밋)한 뒤 오류 응답을 보낸다. 오류 응답이어도 남아야 하는 기록(예: 로그인 실패 횟수)에 쓴다 |
| 커밋 후 작업 | 트랜잭션이 확정(커밋)된 뒤에만 실행하는 작업. 트랜잭션이 취소되면 실행하지 않는다. 실패해도 확정된 DB 변경은 되돌리지 않는다. 오브젝트 저장소의 파일 삭제에 쓴다(2.6) |
| DB 자동 동작 | 특정 컬럼이 바뀌면 DB가 정해진 갱신을 스스로 수행한다. 애플리케이션을 거치지 않는 변경(운영자 SQL)에도 규칙을 지키게 할 때만 쓴다 (DEC-ARCH-013) |

### 2.4 서버 / 네트워크 / 배포
- 서버: 애플리케이션 하나(무상태). 늘릴 때는 같은 애플리케이션을 여러 대 띄운다.
- 네트워크: 클라이언트와 서버 사이는 HTTPS만 허용한다. TLS는 서버의 Traefik이 종료하고 `https://workout-api.jjoon.cloud`를 앱으로 전달한다(10.8).
- DB 접근: 애플리케이션과 운영자만 접근한다. 운영자 접근 경로와 권한은 배포 환경에서 정한다(7.7, D-TODO-ARCH-002).
- 배포: CI 서버가 테스트를 통과한 커밋의 컨테이너 이미지를 레지스트리에 올리고, 서버 1대에 접속해 그 이미지로 앱을 교체한 뒤 기동을 확인한다. DB는 같은 서버에 따로 운영 중인 PostgreSQL 컨테이너(`postgres`)를 쓰고, 그 안의 `workout` DB와 계정은 미리 만들어 둔다. 상세는 10.8
- 오브젝트 저장소: 같은 서버에 따로 띄운 오브젝트 저장소 컨테이너를 쓴다(제품은 1.4). 앱 컨테이너는 서버 내부 네트워크로 S3 API에 접근하고, 저장소는 외부에 공개하지 않는다. 접속 정보는 D-TODO-ARCH-007
- 남은 결정: DB와 파일 백업, 운영자 DB 접근 경로 (D-TODO-ARCH-002)

### 2.5 데이터 흐름
```
요청 → 인증 필터(로그인 조회·연장, userId) → API 진입점(요청 검증) → 서비스(트랜잭션: 규칙·소유자·상태 확인)
     → 저장소 / 조회 저장소 → 관계형 DB → 응답 변환 → JSON
파일 업로드 시 → 미디어 처리기(형식·길이 확인, 미리보기) → 파일 저장소 → 오브젝트 저장소 → (그다음) DB 기록
예외 발생 시 → 전역 예외 처리기(8장) → 에러 JSON
```

### 2.6 외부 시스템 연계

#### 오브젝트 저장소 (DEC-ARCH-014, DEC-ARCH-016)
| 항목 | 규칙 |
|-----|-----|
| 접근 | Backend API의 파일 저장소 컴포넌트만 S3 API로 접근한다. 버킷 하나를 쓴다(D-TODO-ARCH-007) |
| 공개 여부 | 비공개. 앱에 저장소 주소나 서명된 임시 주소를 주지 않는다. 앱은 API로 올리고, API가 소유자를 확인한 뒤 내려준다(7.4) |
| 파일 키 | `users/{userId}/` 아래에 둔다. 기능별 하위 경로는 기능 설계에서 정한다(예: workout-media 6.2). 사용자 한 명의 파일을 접두어 하나로 지울 수 있게 하기 위해서다(7.7) |
| 저장 순서 | 파일을 먼저 저장하고, 그다음 DB에 기록한다. DB 기록이 실패하면 방금 저장한 파일을 지운다. 지우지 못한 파일은 DB가 가리키지 않으므로 사용자에게 보이지 않는다 |
| 삭제 순서 | DB 행을 먼저 지우고, **커밋 후 작업**으로 파일을 지운다. 파일 삭제가 실패하면 WARN 로그(`storage.delete_failed`, 키)를 남기고 요청은 성공으로 끝낸다 |
| 장애 | 저장소에 접근하지 못하면 503 `SERVICE_UNAVAILABLE`(8.3). DB 변경은 확정하지 않는다 |

---

## 3. 기능 설계
해당 없음 — 기능별 처리 흐름은 각 기능 설계 문서 3장에서 다룬다. 공통 규칙: 비즈니스 규칙과 상태 확인은 서비스에서 한다. API 진입점과 저장소에는 비즈니스 규칙을 두지 않는다.

---

## 4. 화면 / API 연계 설계
공통 규칙만 정한다. 화면별 연계는 기능 설계 문서 4장에서 다룬다.

| 응답 | 앱 처리 원칙 |
|-----|------------|
| 401 | 기기에 저장한 토큰을 지우고 로그인 화면으로 이동. 에러 코드에 따라 사유를 안내한다(auth 설계 4장) |
| 403 | "접근할 수 없는 기록" 안내 후 이전 화면 |
| 404 | "찾을 수 없음" 안내 후 목록 화면 등 상위 화면 |
| 400 | 응답의 `errors[].field`로 해당 입력 칸에 메시지 표시 |
| 409 | 응답의 `message`를 안내하고 최신 상태를 다시 조회 |
| 500 / 네트워크 오류 | 일시적 오류 안내와 재시도 버튼 |

---

## 5. API 설계 규칙
| 항목 | 규칙 |
|-----|-----|
| 접두어 | `/api/v1` |
| 리소스 이름 | 복수형 명사, kebab-case. 예: `/workout-sessions` |
| 하위 리소스 | 소속이 분명하면 경로로 중첩. 예: `/workout-sessions/{sessionId}/exercises` |
| 상태 변경 동작 | CRUD로 표현하기 어려운 상태 변경은 동사 하위 경로. 예: `POST /workout-sessions/{id}/complete`, `POST /auth/login` |
| JSON 필드 | camelCase |
| ID | JSON 문자열, UUID 표준 표기(소문자, 하이픈 포함). 예: `"0192f0c8-7b3a-7c41-9d2e-5a1b3c4d5e6f"`. 경로 변수 이름은 `{리소스Id}`. UUID 형식이 아니면 400 |
| 정수 필드 | 소수(예: `8.5`)가 오면 400. 자동으로 버리거나 반올림하지 않는다 |
| 날짜 | `YYYY-MM-DD` |
| 시각 | ISO-8601 UTC. 예: `2026-10-05T09:00:00Z` |
| 소수 | JSON 숫자. 예: `22.5` |
| 응답 코드 | 생성 201, 조회·수정·동작 200, 삭제 204, 결과 없음(단건 선택 조회) 204 |
| 페이지 요청 | `page`(0부터, 기본 0), `size`(기본 20, 최대 100) |
| 페이지 응답 | `{ "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }` |
| 값 여러 개인 쿼리 파라미터 | 같은 이름을 반복한다. 예: `?exerciseIds=a&exerciseIds=b`. 쉼표로 이어 붙이지 않는다 |
| 사용자 시간대 | 사용자 현지 날짜가 필요한 요청은 `X-Time-Zone` 헤더(IANA 시간대 이름, 예: `Asia/Seoul`)로 받는다 (DEC-ARCH-007) |
| 인증 | `Authorization: Bearer <로그인 토큰>` (7.1) |
| 파일 업로드 | `multipart/form-data`, 파일 부분 이름 `file`, 한 요청에 파일 하나. 실제 형식은 내용으로 확인하고 요청의 형식 표시는 믿지 않는다. 서버 전체 상한은 10.1 |
| 파일 내려받기 | 본문이 파일 자체, `Content-Type`은 저장된 형식. JSON으로 감싸지 않는다. `Range` 요청은 기능 설계가 지원한다고 정한 경우만 206으로 응답한다. 바뀌지 않는 파일은 `Cache-Control: private`로 캐시를 허용한다 |

---

## 6. 데이터 설계 규칙

### 6.1 이름·공통 규칙
| 항목 | 규칙 |
|-----|-----|
| 테이블 이름 | snake_case 단수형. 예: `workout_session`. 예약어와 겹치면 복수형(`users`) |
| 컬럼 이름 | snake_case |
| PK | 모든 테이블의 PK는 `id` 하나이고 논리 타입 ID(UUIDv7). 복합 PK나 다른 타입의 PK는 두지 않는다 (DEC-ARCH-010) |
| 순서 | ID로 순서를 정하지 않는다. 순서가 필요하면 시각 컬럼(예: `created_at`)으로 정하고, 시각이 같을 때만 ID로 순서를 정한다 (DEC-ARCH-010) |
| 참조 컬럼 | `<참조 테이블>_id` |
| 공통 컬럼 | `created_at` 시각, 기본값 현재 시각. 수정 가능한 테이블은 `updated_at` 시각도 둔다 |
| 시각 저장 | UTC 기준 |
| 상태 값 | 논리 타입 열거 |
| 삭제 | 기본은 실제 삭제. 하위 데이터는 참조의 "함께 삭제"로 지운다 |
| 사용자 소유 데이터 | `users`를 참조하는 데이터는 참조(함께 삭제)로 둔다. 계정을 삭제하면 그 사용자의 데이터가 모두 지워진다 (auth BR-012) |
| 제약 이름 | 유일 `ux_<테이블>_<컬럼들>`, 인덱스 `idx_<테이블>_<컬럼들>`, 조건 검사 `ck_<테이블>_<설명>`, 자동 동작 `trg_<테이블>_<설명>` |
| 스키마 변경 | 버전 번호가 붙은 스키마 변경 스크립트로만. 이미 적용된 스크립트는 고치지 않고 새 스크립트를 추가한다 |

### 6.2 논리 타입
기능 설계의 컬럼 타입은 아래 이름으로 쓴다. DB 타입 대응은 10.1.

| 논리 타입 | 의미 |
|----------|-----|
| ID | UUIDv7(RFC 9562). 앞부분이 생성 시각(밀리초)이라 대체로 시간 순이지만, 같은 밀리초 안이나 여러 서버에서 만든 값끼리는 순서가 보장되지 않는다. **애플리케이션이 저장 전에 생성한다** (DEC-ARCH-010). **예외:** 운영자가 SQL로 행을 만드는 테이블(`users`)은 DB가 생성한다 (DEC-ARCH-012). DB 자동 동작과 애플리케이션이 함께 행을 만드는 테이블(`exercise`)은 DB 기본값을 두고, 애플리케이션이 만드는 행은 애플리케이션이 생성한다 (DEC-ARCH-019). 참조 컬럼도 같은 타입 |
| 정수 | 32비트 정수 |
| 소수(p,s) | 고정 소수점. 전체 p자리 중 소수 s자리. 부동소수점을 쓰지 않는다(무게·금액 오차 방지) |
| 문자열(n) | 최대 n자 |
| 날짜 | 시간대 없는 달력 날짜 |
| 시각 | 특정 시점. UTC로 저장하고 UTC로 응답 |
| 열거(값, ...) | 나열한 값 중 하나만 허용 |

### 6.3 제약 종류
| 제약 | 의미 |
|-----|-----|
| 유일 | 컬럼(들)의 값 조합이 테이블 전체에서 하나 |
| 조건부 유일 | 조건을 만족하는 행 사이에서만 유일. 예: 진행 중인 행 사이에서 사용자당 하나 |
| 유일(대소문자 무시) | 지정한 문자열 컬럼을 소문자로 바꾼 값으로 유일을 따진다. 예: 사용자·부위 안에서 종목명 하나 |
| 조건 검사 | 행이 항상 만족해야 하는 조건. 예: 값 범위, 형식(패턴), 상태와 컬럼 값의 일관성 |
| 참조(함께 삭제) | 참조 대상이 있어야 하고, 참조 대상이 삭제되면 이 행도 삭제 |
| 참조(삭제 금지) | 참조 대상이 있어야 하고, 이 행이 참조하는 동안 참조 대상을 삭제할 수 없음 |
| 인덱스 | 조회 경로를 빠르게 하는 정렬 구조. 대상 조회를 반드시 적는다 |
| 조건부 인덱스 | 조건을 만족하는 행만 담는 인덱스 |

### 6.4 users 테이블
계정 테이블이다. 정의는 기능 설계 `docs/design/auth.md` 6.2. 다른 기능의 사용자 소유 데이터는 `users.id`를 참조(함께 삭제)한다.

---

## 7. 인증 / 인가 및 보안 설계

### 7.1 인증 방식 (DEC-ARCH-011)
```
로그인: 아이디 + PIN (auth 설계)
  ↓
서버: 추측할 수 없는 로그인 토큰을 만들어 응답 본문으로 준다. 서버에는 토큰의 해시만 저장한다
  ↓
앱: 토큰을 기기 보안 저장소에 보관 (10.1)
  ↓
API 요청: Authorization: Bearer <로그인 토큰>
  ↓
인증 필터: 토큰 해시로 로그인을 조회 → 상태·만료 확인 → 유지 기간 연장 → 사용자 ID
  ↓ 실패 시 401 (사유별 에러 코드)
앱: 토큰을 지우고 로그인 화면
```

| 항목 | 정책 |
|-----|-----|
| 로그인 토큰 | 암호학적으로 안전한 난수 256비트. 서버에는 해시만 저장한다(DB가 유출돼도 토큰으로 쓸 수 없게). 만료·종료 정보는 서버의 로그인 행에 있다 |
| 재발급 | 없다. 로그인 유지 기간 연장은 요청마다 서버에서 한다 |
| 만료 | 마지막 사용 후 30일, 사용할 때마다 연장 (auth BR-006) |
| 즉시 종료 | 서버의 로그인 행 상태를 바꾸면 다음 요청부터 거절된다 (auth BR-005, BR-011) |
| 토큰 전달 | 응답 본문으로 주고, 요청은 `Authorization` 헤더. 쿠키는 쓰지 않는다 |

### 7.2 권한
현재 역할은 "일반 사용자" 하나다. 역할 기반 권한 설정은 두지 않는다. 인증 여부와 데이터 소유자 확인으로 충분하다. 운영자는 서비스 안의 역할이 아니다(7.7).

### 7.3 인증 적용 범위
- `/api/**`: 인증 필요. 예외는 auth 설계 7.1에서 정한 로그인·로그아웃 API.
- 공개 정적 파일: 인증 불필요. 경로는 기능 설계에서 정한다.

### 7.4 사용자별 데이터 접근 제한
- 사용자 ID는 항상 인증 필터가 확인한 로그인에서 얻는다. 요청 본문이나 경로로 받은 사용자 ID는 쓰지 않는다.
- 단건 대상(경로의 ID)은 서비스에서 조회 후 소유자를 비교한다. 없으면 404, 다른 사용자 것이면 403.
- 목록 조회는 조회 조건에 `user_id = 현재 사용자`를 반드시 넣는다.
- 파일은 DB의 소유자를 확인한 뒤에만 API가 오브젝트 저장소에서 읽어 내려준다. 파일 키를 요청으로 받지 않는다(키는 서버가 ID로 만든다).

### 7.5 기타 보안
| 항목 | 정책 |
|-----|-----|
| PIN | 평문 저장(auth 요구사항 8.2 사용자 결정). 비교는 걸리는 시간이 값에 따라 달라지지 않는 방식으로 한다. 보완책은 7.7 |
| CORS | 사용하지 않는다. 클라이언트가 모바일 앱뿐이라 브라우저 교차 출처 요청이 없다 |
| CSRF | 끈다. 쿠키로 인증하지 않으므로 CSRF 공격 대상이 아니다 |
| 요청 수 제한 | 요구사항 없음. 만들지 않는다 (로그인 추측은 auth BR-007 잠금으로 막는다) |
| HTTPS | 필수 (2.4) |
| SQL Injection | 모든 쿼리는 바인드 변수로 값을 전달한다. 문자열로 SQL을 이어 붙이지 않는다 |
| 로그 보안 | 로그인 토큰, PIN, Authorization 헤더 값은 로그에 남기지 않는다 |

### 7.6 테스트에서의 인증
테스트는 테스트 데이터로 `users`와 로그인 행을 직접 만들고, 그 토큰을 `Authorization` 헤더에 넣어 요청한다. 구현 도구는 10.1.

### 7.7 계정 관리 (운영자)
- 계정 발급, PIN 재발급, 잠금 해제, 계정 삭제는 운영자가 DB에 직접 SQL로 한다(auth 요구사항 8.2). 서비스에는 관리 API·화면이 없다.
- 계정을 삭제할 때는 오브젝트 저장소의 `users/{userId}/` 아래 파일도 지운다. DB 참조(함께 삭제)는 파일까지 지우지 못하므로 운영 절차에 넣는다(10.7, workout-media BR-004).
- SQL로 바꾸더라도 규칙이 지켜지도록 DB가 보장한다: 아이디 유일·형식, PIN 형식은 제약으로, PIN 변경 시 로그인 종료는 DB 자동 동작으로, 계정 삭제 시 사용자 데이터 삭제는 참조(함께 삭제)로 (auth 설계 6장).
- 절차(SQL)는 10.7.
- 평문 PIN 보완책: DB 접근 계정은 운영자와 애플리케이션만 갖고 권한을 최소화한다. DB 백업은 암호화해 보관한다. 상세는 배포 환경과 함께 정한다 (D-TODO-ARCH-002).

---

## 8. 예외 / 에러 처리 설계

### 8.1 에러 응답 형식
```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값이 올바르지 않습니다.",
  "timestamp": "2026-10-05T18:00:00Z",
  "errors": [
    { "field": "repetitions", "reason": "1 이상 1000 이하의 정수여야 합니다." }
  ],
  "details": { }
}
```
- `errors`는 입력값 검증 실패(400)일 때만 넣는다.
- `details`는 에러 코드별로 앱이 쓸 추가 정보가 있을 때만 넣는다. 내용은 기능 설계 8.2에서 정한다(예: 잠금 해제 시각).
- `message`는 사용자에게 보여줄 수 있는 문장이다. 스택 트레이스, SQL, 내부 클래스 이름은 넣지 않는다.

### 8.2 상태 코드 정책
| 상태 코드 | 용도 |
|----------|------|
| 400 | 입력값 검증 실패 (형식, 범위, 필수값, 잘못된 헤더) |
| 401 | 인증 실패 (로그인 토큰 없음·모름·만료·종료, 로그인 실패, 로그인 잠금) |
| 403 | 인가 실패 (다른 사용자의 데이터) |
| 404 | 대상 없음 |
| 409 | 현재 상태와 충돌하는 요청 (이미 완료됨, 이미 진행 중인 것이 있음 등 비즈니스 상태 위반) |
| 500 | 예상하지 못한 서버 오류 |
| 503 | 의존하는 외부 시스템(오브젝트 저장소) 장애. 요청은 처리되지 않았고 다시 시도할 수 있다 (DEC-ARCH-017) |

### 8.3 공통 에러 코드
| 에러 코드 | HTTP | 메시지 |
|----------|------|-------|
| VALIDATION_FAILED | 400 | 입력값이 올바르지 않습니다. |
| UNAUTHORIZED | 401 | 로그인이 필요합니다. |
| FORBIDDEN | 403 | 접근할 수 없는 데이터입니다. |
| NOT_FOUND | 404 | 요청한 경로를 찾을 수 없습니다. (정의되지 않은 URL) |
| INTERNAL_ERROR | 500 | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. |
| SERVICE_UNAVAILABLE | 503 | 일시적으로 처리할 수 없습니다. 잠시 후 다시 시도해 주세요. |

기능별 에러 코드는 기능 설계 문서 8장에서 `<대상>_<상황>` 형식으로 정한다. 예: `WORKOUT_SESSION_NOT_FOUND`.

### 8.4 예외를 응답으로 바꾸는 위치
- 서비스는 규칙 위반 시 에러 코드를 담은 비즈니스 예외를 던진다. 에러 코드가 HTTP 상태와 메시지를 가진다.
- 전역 예외 처리기 한 곳이 모든 예외를 8.1 형식으로 바꾼다.
  - 비즈니스 예외 → 에러 코드의 상태
  - 요청 검증 실패, JSON 형식 오류, 타입 불일치(정수 필드에 소수 포함, UUID 형식 오류) → 400 VALIDATION_FAILED
  - 웹 프레임워크가 판단한 그 밖의 요청 오류(지원하지 않는 Content-Type·HTTP 메서드 등) → 400 VALIDATION_FAILED, ERROR 로그를 남기지 않음
  - 정의되지 않은 URL → 404 NOT_FOUND
  - 업로드 크기가 서버 전체 상한(10.1)을 넘음 → 400 VALIDATION_FAILED. 기능별 상한(예: 사진 20MB)은 서비스가 확인해 기능 에러 코드로 응답한다
  - 파일 저장소가 오브젝트 저장소에 접근하지 못함 → 503 SERVICE_UNAVAILABLE, ERROR 로그
  - 기능 설계에서 "제약 위반 변환"으로 지정한 DB 제약 위반 → 지정한 에러 코드
  - 그 밖의 예외(지정하지 않은 DB 제약 위반 포함) → 500 INTERNAL_ERROR, ERROR 로그
- 인증 필터에서 나는 401도 같은 형식으로 응답한다.

---

## 9. 비기능 요구사항 설계 (공통)
| 분류 | 설계 |
|-----|-----|
| 성능 | 요청당 쿼리 수를 고정한다(목록·상세는 조회 저장소에서 조인/집계로 한 번에 읽어 N+1을 만들지 않는다). 인증 필터는 요청당 조회 1회 + 연장 갱신 1회. 목록은 항상 페이지 단위. 조회 조건에는 인덱스를 둔다 |
| 확장성 | 무상태 서버. 로그인 상태는 DB에 있어 어느 서버로 요청이 가도 같다. 서버 여러 대에서 동시에 돌 수 있는 작업은 조건부 일괄 갱신처럼 멱등하게 만든다 |
| 가용성 | 요청 하나 = 트랜잭션 하나로 일부만 저장되는 일이 없게 한다. 파일과 DB는 2.6의 순서로 맞춘다. 오브젝트 저장소 장애는 503으로 알리고 DB는 바꾸지 않는다. 오류는 8장 형식. DB·파일 백업 정책은 배포 환경과 함께 정한다 (D-TODO-ARCH-002) |
| 로깅 | 주요 사용자 행위는 INFO로 `event`, `userId`, 대상 ID를 남긴다. 처리하지 못한 예외는 ERROR와 스택 트레이스. 토큰·PIN은 남기지 않는다(7.5) |
| 성능 검증 | 응답 시간·동시 사용자 목표는 부하 테스트로 확인한다. 도구와 환경은 D-TODO-ARCH-004 |

---

## 10. 구현 구조 및 개발 전략
**이 장은 기술 스택에 의존한다.** 스택을 바꾸면 1.4와 이 장을 고친다.

### 10.1 기술 대응표
기능 설계에서 쓰는 기술 중립 용어가 현재 스택(Kotlin / Spring Boot / PostgreSQL)에서 무엇으로 구현되는지 정한다.

**컴포넌트 역할 (2.2)**
| 용어 | 현재 구현 |
|-----|---------|
| 인증 필터 | Spring Security 필터 체인에 등록한 `OncePerRequestFilter` 하나. `Authorization: Bearer` 토큰을 읽어 auth 설계의 로그인 조회를 수행하고 `SecurityContext`에 사용자 ID를 넣는다. 인증 실패는 `AuthenticationEntryPoint`로 8.1 형식 응답 |
| API 진입점 | `@RestController` 클래스 |
| 서비스 | `@Service` 클래스, 공개 메서드에 `@Transactional` |
| 저장소 | Spring Data JPA Repository 인터페이스 + JPA 엔티티. 엔티티 간 연관관계를 매핑하지 않고 참조 ID 필드만 둔다 (DEC-ARCH-008) |
| 조회 저장소 | jOOQ `DSLContext`를 쓰는 `*QueryRepository` 클래스 |
| 전역 예외 처리기 | `@RestControllerAdvice` + 인증 실패 처리기(같은 응답 형식) |
| 파일 저장소 | `ObjectStorage` 클래스 하나. AWS SDK v2 `S3Client`(접속 주소 `endpointOverride`, 경로 방식 주소 `forcePathStyle(true)`, 접속 키는 환경 변수)로 `putObject`/`getObject`/`deleteObject`/`listObjectsV2`+`deleteObjects`(접두어 삭제). `SdkException`은 503 `SERVICE_UNAVAILABLE`로 바꾼다 |
| 미디어 처리기 | `MediaProcessor` 클래스. `ProcessBuilder`로 `ffprobe`(형식·길이)와 `ffmpeg`(미리보기 JPEG)를 실행한다. 입력은 임시 파일, 실행 시간 제한을 두고, 끝나면 임시 파일을 지운다 |

**동시성·트랜잭션 (2.3)**
| 용어 | 현재 구현 |
|-----|---------|
| 트랜잭션 | 서비스 메서드의 `@Transactional` |
| 변경 잠금 | JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` 조회 → PostgreSQL `SELECT ... FOR UPDATE` |
| 독립 트랜잭션 | 별도 빈의 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 메서드 (같은 클래스 안 호출은 적용되지 않으므로 반드시 별도 빈) |
| 조건부 일괄 갱신 | 한 테이블의 단순 조건이면 Spring Data JPA `@Modifying @Query`(JPQL `update`/`delete`, 반환값으로 처리한 행 수 확인). 조인·서브쿼리가 필요하면 jOOQ `UPDATE ... WHERE ...` / `DELETE ... WHERE ...` + `RETURNING` (10.3) |
| 제약 위반 변환 | 저장 후 즉시 `flush`하고 `DataIntegrityViolationException`의 제약 이름을 비교해 비즈니스 예외로 변환 |
| 확정 후 오류 응답 | 서비스가 예외 대신 결과 값(성공/실패 사유)을 반환해 트랜잭션을 정상 커밋하고, API 진입점이 실패 결과를 비즈니스 예외로 바꿔 응답 (`@Transactional`은 예외 시 롤백하므로) |
| DB 자동 동작 | PostgreSQL 트리거(`CREATE TRIGGER trg_... AFTER UPDATE OF <컬럼>` + PL/pgSQL 함수). 스키마 변경 스크립트로 만든다 |
| JPA 변경 후 jOOQ 조회 | 같은 트랜잭션에서 JPA로 바꾼 내용을 jOOQ로 읽기 전에 `flush` |
| 커밋 후 작업 | `TransactionSynchronizationManager.registerSynchronization`의 `afterCommit`에 등록. 트랜잭션 밖에서 호출되면 바로 실행 |

**논리 타입 (6.2)**
| 논리 타입 | PostgreSQL | Kotlin |
|----------|-----------|--------|
| ID | `uuid` (PK·참조 컬럼 모두. 애플리케이션 생성 테이블은 DB 기본값 없음) | `java.util.UUID`. `@Id @GeneratedValue @UuidGenerator(style = UuidGenerator.Style.VERSION_7)` (Hibernate 7.4) |
| ID (DB 생성 예외, `users`) | `uuid DEFAULT uuidv7()`. PostgreSQL 18 내장 함수 | `java.util.UUID`. 애플리케이션은 `users`를 만들지 않는다 |
| ID (DB·애플리케이션 함께 생성, `exercise`) | `uuid DEFAULT uuidv7()` (트리거가 넣는 행) | 애플리케이션이 넣는 행은 `@UuidGenerator(style = VERSION_7)`로 값을 채워 보낸다 |
| 정수 | `integer` | `Int` |
| 소수(p,s) | `numeric(p,s)` | `BigDecimal` |
| 문자열(n) | `varchar(n)` | `String` |
| 날짜 | `date` | `LocalDate` |
| 시각 | `timestamptz` | `Instant` |
| 열거(값, ...) | `varchar` + `CHECK (컬럼 IN (...))` | `enum class` (JPA `EnumType.STRING`) |

**제약 (6.3)**
| 제약 | PostgreSQL |
|-----|-----------|
| 유일 | `CREATE UNIQUE INDEX ux_...` |
| 조건부 유일 | 부분 유니크 인덱스 `CREATE UNIQUE INDEX ux_... ON t (cols) WHERE 조건` |
| 유일(대소문자 무시) | 식 유니크 인덱스 `CREATE UNIQUE INDEX ux_... ON t (cols, lower(col))`. 조회 조건도 `lower(col) = lower(?)`로 써야 인덱스를 탄다 |
| 조건 검사 | `CONSTRAINT ck_... CHECK (...)`. 형식(패턴)은 정규식 연산자 `~` |
| 참조(함께 삭제) | `FOREIGN KEY ... ON DELETE CASCADE` |
| 참조(삭제 금지) | `FOREIGN KEY ...` (기본 `NO ACTION`) |
| 인덱스 / 조건부 인덱스 | `CREATE INDEX idx_...` / `CREATE INDEX idx_... WHERE 조건` |
| 기본값 현재 시각 | `DEFAULT now()` |

**기타**
| 용어 | 현재 구현 |
|-----|---------|
| 현재 시각 | `java.time.Clock` 빈(`Clock.systemUTC()`)을 주입받아 `clock.instant()`로 얻는다. 테스트는 시간을 앞으로 돌릴 수 있는 `MutableClock`을 `@Primary` 빈으로 바꿔 끼운다. 단, DB 자동 동작이 기록하는 시각(`ended_at`)은 DB의 `now()` |
| 요청 검증 (필수, 범위, 소수 자릿수, 길이, 패턴) | Bean Validation: `@NotNull`, `@NotBlank`, `@Min`/`@Max`, `@DecimalMin`/`@DecimalMax`, `@Digits`, `@Size`, `@Pattern` |
| UUID 형식 검증 (경로 변수·본문 ID) | 컨트롤러 파라미터·DTO 필드를 `UUID` 타입으로 받고, 변환 실패는 전역 예외 처리기에서 400 VALIDATION_FAILED |
| 정수 필드에 소수 입력 거부 (5장) | Jackson의 "소수를 정수로 받기" 기능(`ACCEPT_FLOAT_AS_INT`)을 끈다 |
| IANA 시간대 해석 | `java.time.ZoneId.of(...)`, 실패 시 400 |
| 기기 보안 저장소 (앱) | iOS Keychain / Android Keystore |
| 로그인 토큰 생성 | `java.security.SecureRandom`으로 32바이트 → Base64URL(패딩 없음, 43자) |
| 로그인 토큰 해시 | SHA-256 → 소문자 16진수 64자 (`java.security.MessageDigest`) |
| 시간이 일정한 비교 (PIN) | `java.security.MessageDigest.isEqual` (바이트 배열 비교) |
| 스키마 변경 스크립트 | Flyway, `src/main/resources/db/migration/V<번호>__<설명>.sql`. 애플리케이션 시작 시 적용, JPA `ddl-auto: validate` (DEC-ARCH-003). jOOQ 오픈소스 DDL 파서가 읽지 못하는 구문(PL/pgSQL 함수·트리거)은 `-- [jooq ignore start]` / `-- [jooq ignore stop]` 주석으로 감싼다 (D-TODO-ARCH-005) |
| 공개 정적 파일 | `src/main/resources/static/` 아래 파일을 URL 루트 기준으로 제공. 보안 설정에서 해당 경로를 인증 없이 허용 |
| 파일 업로드 (5장) | `MultipartFile`. 서버 전체 상한 `spring.servlet.multipart.max-file-size`·`max-request-size` = `110MB`(기능 상한 100MB보다 조금 크게 두어, 기능 상한 초과를 서비스가 정확한 에러 코드로 알리게 한다). 초과 시 `MaxUploadSizeExceededException` → 400 |
| 파일 내려받기 (5장) | `ResponseEntity<StreamingResponseBody>`로 S3 `GetObject` 스트림을 그대로 흘린다. `Content-Type`·`Content-Length` 지정. `Range` 헤더는 `HttpRange.parseRanges`로 해석해 `GetObject`의 `range`로 넘기고 206·`Content-Range`로 응답 |
| 서비스가 미리 넣는 기준 데이터의 ID (DEC-ARCH-018) | 스키마 변경 스크립트의 `INSERT`에서 PostgreSQL 18 `uuidv7()` |
| API 수준 통합 테스트 | MockMvc + Testcontainers PostgreSQL(`TestcontainersConfiguration`). 파일을 다루는 테스트는 RustFS 컨테이너(`rustfs/rustfs:1.0.1`, 접속 키는 환경 변수 `RUSTFS_ACCESS_KEY`·`RUSTFS_SECRET_KEY`, `/health` 응답을 기다림)를 함께 띄운다. 미디어 처리 테스트는 실행 환경에 FFmpeg가 있어야 한다(D-TODO-ARCH-008) |
| 테스트 인증 | 테스트 픽스처가 `users`·`login_session` 행을 직접 넣고 그 토큰을 `Authorization` 헤더로 보낸다 |

### 10.2 패키지 구조
패키지 루트는 저장소의 `cloud.jjoon.workout`이다. 도메인별로 나누고, 그 안을 역할별로 나눈다.
```
cloud.jjoon.workout
├── common/
│   ├── error/        ErrorCode, BusinessException, GlobalExceptionHandler, ErrorResponse
│   ├── security/     SecurityConfig, 인증 필터, 현재 사용자 ID 주입
│   ├── storage/      ObjectStorage(파일 저장소), MediaProcessor(미디어 처리기), 커밋 후 작업 도우미
│   └── web/          PageResponse, 시간대 헤더 처리
├── <도메인>/
│   ├── controller/   API 진입점, 요청/응답 DTO
│   ├── service/      서비스
│   ├── repository/   저장소(*Repository), 조회 저장소(*QueryRepository)
│   └── domain/       JPA 엔티티, 상태 enum
└── BackendApplication.kt
```

### 10.3 저장소와 조회 저장소 나누는 기준 (DEC-ARCH-001)
| 데이터 접근 | 역할 | 현재 구현 |
|-----------|-----|---------|
| 행 하나를 저장·수정·삭제 | 저장소 | JPA |
| ID로 단건 조회, 단순 존재 확인, 변경 잠금 조회 | 저장소 | JPA |
| 한 테이블의 단순 조건부 일괄 갱신·삭제 (예: 상태가 ACTIVE일 때만 갱신) | 저장소 | JPA (`@Modifying @Query`) |
| 조인, 집계(SUM/COUNT/MAX), 페이지 목록 | 조회 저장소 | jOOQ |
| 조인·서브쿼리가 필요한 조건부 일괄 갱신 | 조회 저장소 | jOOQ |

### 10.4 이름 규칙
| 대상 | 규칙 | 예 |
|-----|-----|---|
| 요청 DTO | `<동작><대상>Request` | `AddSetRequest`, `LoginRequest` |
| 응답 DTO | `<대상>Response` | `WorkoutSessionResponse` |
| 테스트 | `<대상>Test`, 테스트 이름에 요구사항 ID | `` `BR-011 진행 중 세션이 있으면 시작할 수 없다`() `` |

### 10.5 테스트 전략
- 기본은 API 수준 통합 테스트(10.1). 요청부터 DB까지 실제로 거친다.
- 모든 테스트 이름에 검증하는 요구사항 ID를 넣는다(추적성).
- DB 자동 동작(트리거)과 DB 제약은 테스트에서 SQL로 직접 데이터를 바꿔 검증한다(운영자 경로).
- 테스트 DB 이미지는 운영 버전과 같게 고정한다(`postgres:18`).

### 10.6 공통 기반 구현 순서
1. 의존성 추가: Spring Security, Bean Validation, jOOQ 코드 생성 플러그인 (정확한 artifact 이름은 Spring Boot 4.1 기준으로 확인)
2. 스키마 변경 스크립트 위치와 `users`·`login_session`(auth 설계 6.5)
3. 전역 예외 처리기(8장)와 보안 설정·인증 필터(7장, auth 설계), Jackson 정수 설정(10.1)
4. jOOQ 코드 생성 확인 (D-TODO-ARCH-005)

완료 기준: 토큰 없는 요청이 401 JSON으로, 테스트 픽스처로 만든 로그인 토큰 요청이 통과하는 테스트

### 10.7 계정 관리 SQL (운영 절차)
운영자가 DB에 직접 실행한다(7.7). 규칙 위반(형식, 중복)은 DB 제약이 거절한다. 테이블 정의는 auth 설계 6.2.

```sql
-- 계정 발급 (id, created_at은 DB가 채운다)
INSERT INTO users (login_id, pin) VALUES ('demo.user', '123456');

-- PIN 재발급: 트리거가 그 계정의 활성 로그인을 즉시 끝낸다(REVOKED)
UPDATE users SET pin = '654321', failed_pin_count = 0, locked_until = NULL, updated_at = now()
 WHERE login_id = 'demo.user';

-- 잠금 해제
UPDATE users SET failed_pin_count = 0, locked_until = NULL, updated_at = now()
 WHERE login_id = 'demo.user';

-- 계정 삭제: 로그인과 운동 기록 등 모든 사용자 데이터가 함께 삭제된다(되돌릴 수 없음)
-- 1) 먼저 사용자 ID를 확인한다
SELECT id FROM users WHERE login_id = 'demo.user';
-- 2) 오브젝트 저장소에서 그 사용자의 파일을 지운다 (아래 셸 명령)
-- 3) 계정을 지운다
DELETE FROM users WHERE login_id = 'demo.user';
```

```sh
# 계정 삭제 2단계: 오브젝트 저장소의 사용자 파일 삭제 (S3 호환 CLI 예. 접속 정보는 D-TODO-ARCH-007)
aws s3 rm --recursive "s3://<버킷>/users/<사용자 ID>/" --endpoint-url "<RustFS S3 주소>"
```
파일 삭제를 빠뜨리고 계정을 먼저 지웠다면, 지운 사용자 ID로 같은 명령을 실행하면 된다(DB는 그 파일을 더 이상 가리키지 않는다).


### 10.8 CI/CD
| 파일 | 역할 |
|-----|-----|
| `Jenkinsfile` | Test(`./gradlew clean test`, 테스트 결과 수집) → main만: Base image(`deploy/base-image`가 마지막 성공 빌드 뒤 바뀌었거나 레지스트리에 없을 때만 `ghcr.io/<owner>/workout-backend:21-ffmpeg7`을 `--pull`로 다시 만들어 푸시) → Publish image(`./gradlew jib`로 `ghcr.io/<owner>/workout-backend:<버전>-<커밋 12자리>`와 `:latest` 푸시) → Deploy(SSH로 compose 파일·`release.env` 전송 후 `pull`·`up -d`) → Verify(서버에서 `GET /api/v1/auth/session`이 401을 줄 때까지 최대 60초 확인) |
| `build.gradle.kts`의 `version`·`jib` | `version`은 시맨틱 버전이고, 이미지 태그 `<version>-<커밋 12자리>`의 앞부분이 된다. 기반은 `ghcr.io/mike-urssu/workout-backend:21-ffmpeg7`(`deploy/base-image/Dockerfile`, 가져올 때도 `GHCR_USER`/`GHCR_TOKEN`으로 인증), `linux/arm64` 이미지(운영 서버가 Apple Silicon + Colima), 일반 사용자(UID 501, GID 20)로 실행, 포트 8080. 이미지 이름은 CI가 `-Djib.to.image`로, 레지스트리 인증은 환경 변수 `GHCR_USER`/`GHCR_TOKEN`으로 넘긴다 |
| `deploy/compose.yaml` | `app` 하나(이미지 `${IMAGE}:${IMAGE_TAG}`). 서버에 이미 있는 외부 네트워크 세 개에 붙는다: `proxy`(Traefik이 `workout-api.jjoon.cloud`를 받아 전달, 진입점 `websecure`, 인증서 `letsencrypt`), `postgresql`(DB 컨테이너 `postgres`, 접속은 `POSTGRES_HOST`(기본 `postgres`)·`POSTGRES_DB`·`POSTGRES_USER`·`POSTGRES_PASSWORD`), `rustfs`(S3 API, `STORAGE_S3_*`). 8080은 서버 localhost에만 열어 Verify에 쓴다 |
| `deploy/base-image/Dockerfile` | 앱 컨테이너의 기반 이미지: Debian trixie + OpenJDK 21 JRE + FFmpeg 7.1(아이폰 HEIC 타일 사진을 읽는다. Ubuntu 기반 eclipse-temurin의 FFmpeg 6.1은 못 읽음). CI의 Base image 단계가 이 디렉터리가 바뀔 때 만들어 올린다. 보안 업데이트만 받으려면 Dockerfile을 고치거나(주석 포함) 레지스트리의 태그를 지워 다시 만들게 한다 |
| `deploy/.env.example` | 서버의 `<DEPLOY_DIR>/.env` 견본(DB 이름·계정·비밀번호, 오브젝트 저장소 접속 정보). 실제 파일은 저장소에 넣지 않는다 |

Jenkins 준비:
- 빌드 에이전트 label `macbook`. 전역 도구 JDK 이름 `jdk21`. 에이전트는 Docker를 실행할 수 있어야 하고(Testcontainers, 기반 이미지 빌드) `ffmpeg`·`ffprobe`가 PATH에 있어야 한다(미디어 테스트). 앱 이미지 빌드에는 Docker가 필요 없다(Jib).
- 자격 증명: `ghcr-credentials`(GitHub 사용자 + `write:packages` PAT), `deploy-ssh-key`(SSH 개인 키).
- 전역 속성(Manage Jenkins → System → Global properties): `DEPLOY_HOST`(배포 서버 `user@host`). 선택 환경 변수 `GHCR_OWNER`(이미지 소유 계정·조직, 없으면 GHCR 사용자 이름), `DEPLOY_PORT`(배포 서버 SSH 포트, 없으면 22).

서버 준비(한 번):
- Docker와 Compose 플러그인, `curl` 설치. 배포 사용자가 `docker`를 실행할 수 있어야 한다.
- `/opt/projects/workout/.env`를 `.env.example`로 만들고 실제 값을 넣는다.
- `docker login ghcr.io`를 **`read:packages`만 있는 토큰**으로 한 번 해 둔다. Jenkins의 쓰기 토큰은 서버로 보내지 않는다.

롤백: 서버의 `release.env`에서 `IMAGE_TAG`를 이전 태그로 바꾸고 `docker compose --env-file .env --env-file release.env up -d`.

---

## 부록 A. 설계 결정 기록
| ID | 결정 | 이유 | 버린 대안 |
|----|-----|-----|----------|
| DEC-ARCH-001 | 단순한 데이터 접근은 저장소(JPA), 복잡한 데이터 접근(조인·집계·페이지·조건부 일괄 갱신)은 조회 저장소(jOOQ). 기준은 10.3 | 사용자 결정. 단순 CRUD는 코드가 짧고, 집계는 SQL을 직접 통제한다 | JPA만: 집계가 문자열 쿼리가 됨 / jOOQ만: 단순 CRUD에도 SQL 작성 |
| DEC-ARCH-002 | jOOQ 클래스는 Flyway 스크립트에서 생성(DDL 기반) | 빌드에 DB가 필요 없고, 스키마의 단일 출처가 스키마 변경 스크립트가 된다 | 실행 중인 DB에서 생성: 빌드에 DB·컨테이너 필요 |
| DEC-ARCH-003 | 스키마는 스키마 변경 스크립트로만 바꾸고, 애플리케이션은 검증만 | 운영 스키마 변경을 리뷰 가능한 스크립트로 남긴다 | 애플리케이션이 스키마 자동 변경: 의도치 않은 변경 |
| DEC-ARCH-004 | 상태 코드 정책 8.2, 비즈니스 상태 위반은 409 | 400(입력이 틀림)과 409(입력은 맞지만 지금 상태에서 불가)를 구분해 앱이 처리를 다르게 할 수 있다 | 422: 400과의 경계가 모호함 |
| DEC-ARCH-005 | ~~Access Token(JWT) + Refresh Token~~ **대체됨 (v0.4) → DEC-ARCH-011** | — | — |
| DEC-ARCH-006 | ~~`users` 테이블을 최소 컬럼으로 먼저 만들고 인증 설계에서 확장~~ **완료 (v0.4)** — auth 설계 6.2에서 정의 | — | — |
| DEC-ARCH-007 | 사용자 현지 날짜가 필요한 요청은 `X-Time-Zone` 헤더(IANA)로 시간대를 받는다 | 사용자 프로필(시간대 저장)이 아직 범위 밖이다. 앱은 기기 시간대를 항상 알고 있다 | 사용자 정보에 시간대 저장: 프로필 기능이 필요 / 서버 시간대 고정: 해외에서 날짜가 어긋남 |
| DEC-ARCH-008 | JPA 엔티티 간 연관관계를 매핑하지 않고 참조 ID 필드만 둔다 (v0.2에서 DEC-WORKOUT-010을 공통으로 옮김) | 여러 테이블을 함께 읽는 조회는 조회 저장소가 맡으므로 연관관계가 필요 없다. 지연 로딩·N+1 위험이 없다 | 양방향 연관관계: 조회 경로가 둘로 나뉘고 N+1 위험 |
| DEC-ARCH-009 | 기능 설계는 기술 중립 용어(2.2, 2.3, 6.2, 6.3)로 쓰고, 기술 의존 내용은 1.4, 10장, 기술 선택에 관한 부록 A·B 항목에만 둔다 | 언어·DB가 바뀌어도 기능 설계를 다시 쓰지 않는다. 보장해야 할 성질은 기능 설계에 남아 새 스택에서 무엇으로 지킬지 판단할 근거가 된다 | 기능 설계에 구현 문법까지 기록: 스택 변경 시 모든 기능 설계 수정 |
| DEC-ARCH-010 | 모든 PK를 UUIDv7로 통일하고 애플리케이션에서 생성한다. 순서는 ID가 아니라 시각 컬럼으로 정한다 | 사용자 결정. ID로 다른 사용자의 데이터 개수나 다음 ID를 추측할 수 없다(연속 정수 ID의 열거 문제). 서버가 여러 대여도 충돌 없이 만들 수 있다. 앞부분이 시각이라 무작위 UUID(v4)보다 인덱스에 순서대로 쌓여 쓰기 성능 저하가 적다. 애플리케이션 생성은 DB 버전과 무관하고 저장 전에 ID를 알 수 있다. 순서는 같은 밀리초 안이나 서버 간에 보장되지 않으므로 시각 컬럼으로 정한다 | 연속 정수 ID: 열거 가능 / UUIDv4: 인덱스 쓰기 분산으로 성능 저하 / DB 생성 UUIDv7: 운영 DB 버전에 의존 |
| DEC-ARCH-011 | 인증은 로그인 토큰 하나(256비트 난수, 서버에는 해시만 저장) + 요청마다 서버의 로그인 행 조회. 재발급(Refresh) 없음. 인증 필터는 Spring Security에 직접 만든 필터 하나로 구현 | 사용자 결정. 요구사항이 즉시 종료(auth BR-005, BR-011), 종료 사유 구분(auth ERR-005, 006, 008), 사용할 때마다 30일 연장(auth BR-006)을 요구해 어느 방식이든 요청마다 서버 조회가 필요하다. 서버 조회를 하면 JWT의 장점(조회 없는 검증)이 사라지고, 토큰 하나면 재발급 흐름이 필요 없다 | JWT + Refresh + 서버 조회: 서버 조회를 하면서 토큰 두 개와 재발급 흐름만 남아 가장 복잡 / JWT만(짧은 만료): 즉시 종료·사유 구분을 지킬 수 없음 |
| DEC-ARCH-012 | `users.id`는 예외적으로 DB가 UUIDv7을 생성한다 | 운영자가 SQL로 계정을 만들기 때문에(auth 요구사항 8.2) 애플리케이션이 ID를 만들 수 없다. 같은 UUIDv7 형식을 유지한다 | 운영자가 외부 도구로 UUID 생성 후 입력: 실수와 형식 불일치 위험 |
| DEC-ARCH-013 | 운영자 SQL로 바뀌는 데이터에 따른 규칙(PIN 변경 시 로그인 종료)은 DB 자동 동작(트리거)으로 보장한다. 그 밖의 규칙은 애플리케이션에 둔다 | 운영자가 SQL 한 줄을 빠뜨려도 규칙이 지켜진다(auth BR-011). 애플리케이션을 거치지 않는 변경은 애플리케이션이 알 수 없다 | 운영 절차에 로그인 종료 SQL을 함께 적기: 운영자가 빠뜨리면 규칙이 깨짐 / 요청마다 PIN 비교: 로그인 행에 PIN 정보를 따로 보관해야 함 |
| DEC-ARCH-014 | 사용자 파일은 오브젝트 저장소에 S3 API로 저장하고, 저장소는 공개하지 않는다. 앱은 API로 올리고 내려받는다 | 사용자 결정(제품은 1.4, v0.15에 교체됨 — DEC-ARCH-020). S3 API는 표준이라 저장소를 바꿔도 코드가 그대로다. 비공개로 두면 소유자 확인(7.4, workout-media BR-001)을 API 한 곳에서 할 수 있고, 저장소 주소를 외부에 열 필요가 없다. **한계:** 파일이 앱 서버를 거치므로 서버 대역폭과 메모리를 쓴다(스트리밍으로 처리). 부하가 커지면 서명된 임시 주소로 바꾼다 | 서명된 임시 주소(presigned URL)로 앱이 저장소와 직접 주고받기: 저장소를 외부에 공개해야 하고, 업로드 검증(형식·길이·미리보기)을 올린 뒤에 따로 해야 함 / 앱 서버 디스크: 서버를 늘리면 공유 불가 |
| DEC-ARCH-015 | 미리보기는 서버가 FFmpeg 명령으로 업로드 요청 안에서 만든다. FFmpeg는 앱 컨테이너의 기반 이미지에 넣는다 | 사용자 결정(서버가 만든다). 동영상 첫 장면, 동영상 길이, HEIC 사진을 한 도구로 다룰 수 있다. 업로드 요청 안에서 만들면 "올렸는데 미리보기가 없는" 중간 상태가 없다 | 별도 작업 큐에서 나중에 생성: 중간 상태와 재시도 처리가 필요 / JavaCV: 앱 이미지가 크게 늘고 arm64 확인 필요 |
| DEC-ARCH-016 | 파일은 먼저 저장하고 DB에 기록한다. 삭제는 DB 먼저, 파일은 커밋 후 작업으로. 파일 키는 `users/{userId}/` 아래 | DB와 오브젝트 저장소는 하나의 트랜잭션으로 묶을 수 없다. 이 순서면 실패해도 "DB가 가리키는데 파일이 없는" 상태가 생기지 않는다. 남을 수 있는 것은 DB가 가리키지 않는 파일뿐이라 사용자에게 보이지 않는다. 사용자 접두어 덕분에 계정 삭제 때 파일을 한 번에 지울 수 있다(7.7) | DB 먼저 기록 후 파일 저장: 저장 실패 시 DB가 없는 파일을 가리킴 / 주기적인 고아 파일 정리 작업: 지금 규모에 불필요한 작업 실행 환경 |
| DEC-ARCH-017 | 오브젝트 저장소 장애는 503 `SERVICE_UNAVAILABLE` | 입력이나 상태 문제가 아니라 다시 시도하면 되는 일시 장애라는 것을 앱이 구분할 수 있다(workout-media ERR-006) | 500: 예상하지 못한 오류(버그)와 구분되지 않음 |
| DEC-ARCH-018 | 서비스가 미리 넣는 기준 데이터(운동 부위·종목)의 행은 스키마 변경 스크립트가 넣고, ID는 DB가 UUIDv7로 만든다 (DEC-ARCH-012의 예외를 기준 데이터로 넓힘) | 애플리케이션을 거치지 않고 스크립트로 넣는 행이라 애플리케이션이 ID를 만들 수 없다. 같은 UUIDv7 형식을 유지한다 | 스크립트에 UUID를 직접 적기: 실수 위험, 형식 확인 어려움 |
| DEC-ARCH-019 | DB 자동 동작과 애플리케이션이 함께 행을 만드는 테이블은 ID에 DB 기본값(UUIDv7)을 두고, 애플리케이션은 자기가 만드는 행의 ID를 직접 만든다 | 자동 동작은 애플리케이션을 거치지 않아 ID를 받을 수 없다(DEC-ARCH-012와 같은 이유). 애플리케이션 쪽은 기존 규칙(저장 전 생성)을 그대로 쓴다. 두 쪽 모두 UUIDv7이다 | 애플리케이션 행도 DB 생성: 이 테이블만 저장 후 ID를 다시 읽는 방식이 되어 다른 테이블과 달라짐
| DEC-ARCH-020 | 오브젝트 저장소 제품을 SeaweedFS에서 RustFS 1.0.1로 바꾼다. 서버에는 RustFS 컨테이너 하나(단일 노드)를 띄운다 | 사용자 결정(2026-10-12). 운영 사용자와 저장된 파일이 없어 옮길 데이터가 없고, 서버에 기존 저장소도 없어 "이미 운영 중"이라는 SeaweedFS의 이점이 사라졌다. 서버 1대·소규모 파일에는 단일 바이너리에 접속 키를 환경 변수로만 주는 RustFS가 운영하기 단순하고, 테스트에서 S3 인증 설정 파일과 저장소 준비 대기 재시도가 필요 없어졌다. 앱 코드는 S3 API만 써서 바뀌지 않는다(DEC-ARCH-014). Apache 2.0. **한계:** 1.0 GA가 2026-09-16이라 운영 이력이 짧다. 버전을 `1.0.1`로 고정하고 올릴 때 파일 테스트를 다시 돌린다. 파일 백업은 D-TODO-ARCH-002 | SeaweedFS 유지: 서버 1대 규모에 master·volume·filer 구성과 S3 인증 설정 파일이 부담 / MinIO: 커뮤니티판 바이너리·이미지 배포 중단 / 앱 서버 디스크: 이미 만든 S3 경로를 다시 만들어야 하고 DEC-ARCH-014의 이유가 그대로 남음 |

## 부록 B. 설계 미결정 사항
- **D-TODO-ARCH-001** ~~운영 PostgreSQL 버전 결정~~ **결정됨 (v0.6): PostgreSQL 18.** 배포 compose와 테스트(Testcontainers)를 모두 `postgres:18`로 고정했다.
- **D-TODO-ARCH-002** 배포 환경 중 남은 것: DB와 오브젝트 저장소 파일의 백업과 암호화, 운영자 DB 접근 경로와 권한(DB 포트를 외부에 열지 않았으므로 서버 접속 후 `docker exec -it postgres psql -U workout -d workout` 등). 서버 1대 compose 배포와 Traefik TLS 종료는 결정됨(10.8). (영향: 2.4, 7.7, 9장)
- **D-TODO-ARCH-003** ~~Access/Refresh Token 만료 시간~~ **결정됨 (v0.4):** 로그인 토큰 하나, 마지막 사용 후 30일 (auth BR-006, DEC-ARCH-011)
- **D-TODO-ARCH-004** 부하 테스트 도구와 환경. (영향: 9장, 각 기능의 성능 NFR 확인 방법)
- **D-TODO-ARCH-005** ~~DDL 기반 jOOQ 코드 생성이 PostgreSQL 전용 구문을 읽지 못하면~~ **결정됨 (v0.8, 운동 기록 구현 시 확인):** 부분 인덱스·`uuidv7()` 기본값은 읽는다. `CREATE FUNCTION`(PL/pgSQL)은 Pro 전용이라 `[jooq ignore]` 주석으로 제외하고 `parseIgnoreComments`를 켠다. `timestamptz`는 forcedType으로 `Instant`. 설정은 `build.gradle.kts`의 `jooq` 블록 (영향: DEC-ARCH-002)
- **D-TODO-ARCH-006** ~~UUIDv7 생성 수단 확정~~ **결정됨 (인증 구현 시 확인):** Spring Boot 4.1.1에 포함된 Hibernate 7.4.5의 `UuidGenerator.Style.VERSION_7`을 쓴다. 라이브러리 추가 없음 (10.1)
- **D-TODO-ARCH-007** (v0.15에 RustFS 기준으로 다시 씀) 운영 서버의 RustFS 컨테이너: 네트워크 `rustfs`, 앱이 닿는 주소 `http://rustfs:9000`, 버킷 `workout`은 미리 만든다(`.env.example`, 10.8 compose에 반영됨). 접속 키는 RustFS의 `RUSTFS_ACCESS_KEY`·`RUSTFS_SECRET_KEY`로 정하고 서버 `.env`의 `STORAGE_S3_*`에 같은 값을 넣는다. 남은 일: 서버에 컨테이너를 띄우고 네트워크·버킷·키를 만드는 것. (영향: 2.4, 2.6, 10.7, 10.8)
- **D-TODO-ARCH-008** ~~FFmpeg를 넣은 기반 이미지: `eclipse-temurin:21-jre`에 배포판 FFmpeg를 설치한 `linux/arm64` 이미지를 만들어 GHCR에 올리는 방법(Jib은 패키지를 설치하지 못한다), 그 FFmpeg가 HEIC를 읽는지 확인(못 읽으면 libheif 추가), CI 에이전트에 테스트용 FFmpeg 설치. 개발 맥에는 Homebrew FFmpeg 9.0.2를 설치해 테스트를 통과했다(2026-10-09). (영향: 1.4, 10.1, 10.8, workout-media)~~ **결정됨 (v0.11):** Debian trixie 기반 이미지에 배포판 OpenJDK 21과 FFmpeg 7.1.5를 설치(`deploy/base-image/Dockerfile`). Nokia HEIF 샘플(타일 HEIC)로 미리보기 생성을 확인했다. 이미지 크기는 약 1GB(FFmpeg 의존성). 줄여야 하면 FFmpeg 정적 빌드로 바꾼다

## 부록 C. 요구사항 피드백
- ~~인증 요구사항 명세서가 없다~~ **해결 (v0.4):** `docs/requirements/auth.md` 작성됨.
