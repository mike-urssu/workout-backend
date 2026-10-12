# Let's Workout Backend

운동 기록 모바일 앱 **Let's Workout**의 백엔드 API 서버입니다.
모바일 앱이 유일한 클라이언트이며, HTTP/JSON REST API(`/api/v1/...`)를 제공합니다.

주요 기능:

- **인증** — 아이디 + PIN 로그인, 로그인 토큰 발급 (계정 발급·관리는 운영자가 DB에서 직접 SQL로 처리)
- **운동 세션** — 오늘의 운동 시작·진행·완료·취소
- **운동 종목 / 세트 기록** — 사용자별 종목 목록 관리(추가·수정·삭제·순서 변경), 세트 기록, 이전 기록 불러오기
- **운동 기록 달력** — 월별 지난 기록 조회·삭제
- **미디어** — 오운완 사진·동영상 업로드, 서버에서 미리보기 생성
- **통계** — 부위별 운동량 추이

## 기술 스택

| 구분 | 사용 기술 |
|-----|---------|
| 언어 / 런타임 | Kotlin 2.3, Java 21 |
| 프레임워크 | Spring Boot 4.1 (Web MVC, Security, Validation) |
| 데이터 접근 | Spring Data JPA(단순 CRUD) + jOOQ(조인·집계 쿼리) |
| DB / 마이그레이션 | PostgreSQL 18, Flyway |
| 파일 저장 | RustFS (S3 호환 API, AWS SDK v2) |
| 미디어 처리 | FFmpeg 7.1 (`ffmpeg`, `ffprobe`) |
| 테스트 | JUnit 5, Testcontainers (PostgreSQL, RustFS) |
| 빌드 / 배포 | Gradle, Jib, GHCR, Jenkins, Docker Compose, Traefik |

## 아키텍처

```
Mobile App ──HTTPS(Bearer token)──▶ Traefik ──▶ Backend API ──▶ PostgreSQL
                                                     │
                                                     ├──S3 API──▶ RustFS (외부 비공개)
                                                     └──────────▶ FFmpeg (미리보기 생성)
```

- 무상태 서버: 로그인 상태는 DB에 저장하고 요청마다 조회합니다.
- 스키마는 Flyway 스크립트(`src/main/resources/db/migration`)로만 변경하고, 앱은 시작 시 검증만 합니다.
- jOOQ 클래스는 빌드 시 Flyway 스크립트를 읽어 생성하므로 빌드에 DB가 필요 없습니다.
- 사용자 파일은 오브젝트 저장소의 `users/{userId}/` 아래에 저장하며, 앱을 통해서만 접근합니다.

### 패키지 구조

기능별 패키지 아래에 `controller` → `service` → `repository`, `domain` 계층을 둡니다.

```
src/main/kotlin/cloud/jjoon/workout/
├── auth/       인증 (로그인, 로그인 토큰)
├── session/    운동 세션, 세트 기록, 기록 달력
├── exercise/   운동 종목 관리
├── media/      사진·동영상, 미리보기
├── stats/      통계
└── common/     보안 필터, 에러 처리, 파일 저장소, 시계
```

### 문서

- `docs/requirements/` — 기능별 요구사항 명세서
- `docs/design/` — 설계 문서. 공통 설계는 [`docs/design/architecture.md`](docs/design/architecture.md)

## 필요한 인프라

| 구성 요소 | 로컬 개발 | 운영 |
|---------|---------|-----|
| JDK 21 | 필요 | 이미지에 포함 |
| Docker | 필요 (Testcontainers) | Docker Compose |
| PostgreSQL 18 | Testcontainers가 자동 실행 | 서버의 기존 `postgres` 컨테이너 |
| RustFS (S3) | Testcontainers가 자동 실행 | 서버의 `rustfs` 컨테이너 |
| FFmpeg 7.1+ | `PATH`에 필요 (미디어 기능·테스트) | 기반 이미지에 포함 |
| Traefik | — | HTTPS 종료, `workout-api.jjoon.cloud` 라우팅 |

## 실행 방법

### 로컬 실행

Docker가 실행 중이면 PostgreSQL과 RustFS 컨테이너를 자동으로 띄워 앱을 실행합니다.

```sh
./gradlew bootTestRun
```

앱은 `http://localhost:8080`에서 뜹니다. 회원가입 API는 없으므로 계정은 DB에 직접 넣습니다
(컨테이너 DB 포트는 매번 바뀌므로 `docker ps`로 확인).

```sql
INSERT INTO users (login_id, pin) VALUES ('demo.user', '123456');
```

직접 띄운 DB·저장소에 연결하려면 아래 환경 변수를 지정하고 `./gradlew bootRun`으로 실행합니다.

| 환경 변수 | 설명 |
|---------|-----|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL 접속 정보 |
| `STORAGE_S3_ENDPOINT` | S3 주소 (기본 `http://localhost:9000`) |
| `STORAGE_S3_BUCKET` | 버킷 (기본 `workout`) |
| `STORAGE_S3_ACCESS_KEY` / `STORAGE_S3_SECRET_KEY` | S3 인증 정보 |

### 테스트

```sh
./gradlew test
```

Docker와 `ffmpeg`가 필요합니다.

### 배포

`main` 브랜치에 푸시하면 Jenkins(`Jenkinsfile`)가 아래 순서로 배포합니다.

1. 테스트
2. 기반 이미지(`deploy/base-image`) 변경 시 재빌드·푸시
3. Jib으로 앱 이미지 빌드 → GHCR 푸시 (`<version>-<commit sha>`, `latest`)
4. SSH로 서버에 `deploy/compose.yaml` 전송 후 `docker compose up -d`
5. 헬스 체크 (`GET /api/v1/auth/session`이 401 응답)

서버의 DB·저장소 접속 정보는 `deploy/.env.example`을 참고해 서버에 `.env`로 한 번 만들어 둡니다.
