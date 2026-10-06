# Backend MVP TODO

## 작업 기준

1~9단계의 목표는 질문 조회부터 답변 제출, LLM 평가, 저장된 결과 조회까지의 Core User Flow 완성이다. 관리자 MVP 범위는 10~15단계와 `docs/API.md` 10절의 확정 계약으로 정의한다. 관리자 목록·상세·생성·수정·preview는 구현되었으며 프론트 실제 상세 연결은 미완료다. 체크박스는 구현과 검증을 마친 항목만 완료로 표시한다.

- 작업 전에 `AGENTS.md`와 `docs/API.md`, 관련 기존 코드를 읽는다.
- 1~9단계에는 관리자/인증 기능을 포함하지 않았다. 10~15단계는 로그인 없는 관리자 질문 생성·수정·저장 전 평가 테스트만 다룬다. Spring Security + 아이디·비밀번호 인증은 MVP 이후 별도 계획이다. 로그인 사용자 기록과 관리자 사용자 평가 기록 조회는 이번 범위에 포함하지 않는다.
- 기존 Kotlin, Java 25, Spring Boot 4, Spring AI 2, Spring Data JPA, PostgreSQL, Flyway 구성을 활용한다. 의존성이나 버전을 임의로 변경하지 않는다.
- Controller / Service / Repository를 기본으로 사용하고 API에 Entity를 직접 노출하지 않는다.
- 비즈니스 예외는 ApplicationException 또는 DomainException을 거쳐 BusinessException : RuntimeException을 상속하고 ErrorType/오류 코드/안전한 메시지를 정의한다. 예외에 HTTP 상태/Spring HTTP 의존성을 두지 않으며 GlobalExceptionHandler에서 ErrorType을 HTTP 상태로 매핑한다. 선택 조건 없는 @RestControllerAdvice를 유지하고 특정 Controller 전용 Advice는 사용하지 않는다.
- 기본 타입은 Kotlin/JPA 기본 매핑을 사용하며, 일반 문자열에 `columnDefinition`을 지정하지 않는다. `jsonb`처럼 특정 DB 타입이 꼭 필요한 경우에만 사용 이유를 확인한다. 실제 DB 타입은 Flyway에서 관리한다.
- `updatable = false`는 사용하지 않는다. 생성 시각을 포함한 필드의 변경 정책을 JPA 매핑의 업데이트 제한으로 강제하지 않는다.
- Flyway 파일명에는 항상 세 버전 요소를 명시한다: `V<major>.<minor>.<patch>__<description>.sql`. 스키마는 `V1.0.0`, 같은 주 버전의 초기 데이터는 `V1.0.1`로 작성한다. 이미 적용된 DB 이력을 자동 수정하지 않는다.
- 평가 기록의 `createdAt`은 JPA Auditing이 최초 영속화 시 기록하는 감사 정보다. 시간 관련 도메인 규칙에는 사용하지 않으며, 필요 시 `submittedAt` 등 별도 사건 시각을 추가한다. Auditing Entity 테스트에는 `@DataJpaTest`와 `@Import(JpaAuditingConfig::class)`를 사용한다.
- 테스트는 JUnit 5와 AssertJ의 `assertThat(actual).isEqualTo(expected)` 등 fluent assertion을 사용한다. 실제 DB Repository 테스트는 기존 Testcontainers JDBC 설정과 `@DataJpaTest`를 활용한다. 테스트 설정 파일에서 `test` 프로필을 활성화하므로 `@ActiveProfiles`와 불필요한 DB/Flyway 자동 설정 어노테이션을 추가하지 않는다.
- 테스트 메서드 이름은 검증할 동작과 기대 결과가 드러나는 한글 문장으로 작성한다. 모든 테스트 메서드에 `// given`, `// when`, `// then`을 순서대로 포함하여 데이터 준비, 검증 대상 호출, AssertJ 결과 검증을 구분한다.
- Mockito mock 설정은 `org.mockito.BDDMockito.given`과 `willReturn`을 사용하여 `// given` 절에 작성한다. `Mockito.when`과 `thenReturn`은 사용하지 않는다.
- Service는 `@ExtendWith(MockitoExtension::class)`와 mock Repository로 단위 테스트한다. Controller는 `@WebMvcTest`와 MockMvc, mock Service로 요청 매핑/상태/JSON 계약을 검증한다. 실제 네트워크 서버나 불필요한 전체 통합 테스트는 추가하지 않는다. Repository의 실제 DB 테스트와 각 계층의 책임을 분리한다.
- Repository 기능 테스트는 각 메서드에서 Entity를 직접 생성하고 `save` 또는 `saveAll`로 저장한 뒤, 생성된 ID로 검증 대상 메서드를 호출한다. 저장용 Entity와 조회 결과는 필드 값 또는 AssertJ `usingRecursiveComparison`으로 비교한다. 기본 트랜잭션 롤백으로 테스트를 격리하고 다른 테스트의 데이터/실행 순서나 Flyway 초기 데이터 값/고정 ID/전체 개수에 의존하지 않는다.
- Repository 기능 테스트는 필요한 Repository만 주입한다. EntityManager, TestEntityManager, Flyway, JdbcTemplate, SessionFactory 등 인프라 객체와 명시적 `flush`, `clear`, `saveAndFlush`는 사용하지 않는다. 같은 영속성 컨텍스트 내 비교이므로 별도 DB 재로딩/commit 검증으로 설명하지 않는다.
- 검증 대상은 실제 저장/조회 결과, 필터링, 정렬, 빈 결과 등 기능 동작이다. Flyway 버전/파일명, 초기 데이터, 시스템 카탈로그의 제약/인덱스, SQL 횟수 검증과 Hibernate 통계 설정은 추가하지 않는다. 향후 사용자 요청 없이 이러한 인프라 검증을 다시 추가하지 않는다.
- Entity 간 연관관계 매핑은 사용하지 않는다. 다른 모델의 참조는 `Long` ID 필드로만 저장하고, 관련 데이터가 필요하면 해당 Repository를 별도로 호출한다. 질문과 평가 기준은 질문 조회 1회와 기준 목록 조회 1회로 가져온다. 추후 성능 개선이 필요한 경우에만 명시적 조인 쿼리와 DTO 매핑을 검토한다.
- DB에는 외래 키 제약 조건, `ON DELETE CASCADE`, 배점 등 도메인 규칙을 검증하는 `CHECK` 및 트리거를 추가하지 않는다. 기본 PK, 필수 컬럼의 `NOT NULL`, 조회용 인덱스는 사용한다. 참조의 정합성은 데이터 입력 및 애플리케이션의 생성/변경 흐름에서 관리한다.
- 저장된 질문/평가 기준은 정상이라고 가정한다. 후속 관리자 생성·수정·평가 테스트의 외부 입력에는 기준 개수와 배점 검증을 동일하게 적용한다. 일반 사용자 질문 조회나 저장된 기준을 사용하는 LLM 호출 전에는 재검증하지 않는다. 개발자의 직접 DB 입력과 초기 데이터 마이그레이션도 같은 규칙을 지키는 것을 전제로 한다.
- 아래 각 번호를 한 번의 AI 작업 또는 작은 PR의 기본 단위로 삼는다. 기능 구현과 해당 기능의 기본 테스트를 같은 작업에서 수행한다.
- 구현하면서 계약 변경이 필요하면 임의로 변경하지 않고 먼저 확인한다. 승인된 변경은 `docs/API.md`와 함께 반영한다.
- 이번 관리자 MVP에는 인증/인가, Security 의존성, 계정/세션/토큰/CSRF 처리, 동시성 잠금/직렬화/버전 검사/snapshot 읽기를 추가하지 않는다. Redis, 캐싱, RAG, Embedding, Vector Store, 메시지 큐, Microservice, 불필요한 성능 최적화도 추가하지 않는다.
- 프론트엔드 화면 구현은 별도 저장소의 작업이다. 여기에는 백엔드 구현과 연결 검증에 필요한 항목만 둔다.

현재 Question/EvaluationCriterion/EvaluationAttempt 모델과 Repository, 마이그레이션, JPA Auditing 및 PostgreSQL 테스트가 구현되어 있다. 질문 목록/상세, 답변 제출·평가·완료 기록 저장, 저장 결과 조회와 공통 `400`/`404`/`502`/`500` 처리가 구현되어 있다. 외부 LLM을 대체한 실제 HTTP/commit 후 재조회도 검증했다. 실제 브라우저와 유료 공급자 연결은 미검증이다. 스키마는 Flyway로 관리하고 테스트에서는 `validate`로 매핑을 확인한다.

관리자 후속 명세 정리(2026-10-06): `docs/API.md` 10절에 관리자 목록/상세/생성/전체 수정/비저장 평가 테스트 계약 초안을 추가했다. 관리자 API와 인증/인가 구현은 아직 없으며 프론트엔드 코드/문서는 이번 정리에서 변경하지 않는다. 관측 가능성, DB 모니터링, CI/CD, Docker/nginx/Cloudflare 배포는 별도 작업으로 분리한다.

사용자 결정 반영: 관리자 MVP는 인증 없이 구현한다. 제목 200/본문 10,000/기준 설명 항목당 1,000/테스트 답변 3,000 UTF-16 코드 단위, 기준 1~10개, 양의 정수 배점 합계 100으로 확정했다. 필드 위반은 400 INVALID_REQUEST, 전체 본문 초과는 배포 nginx의 413이다. 저장 즉시 공개하고 기존 평가/현재 제목 조회는 유지한다. 동시성 개선 및 서버 임시저장은 하지 않는다. 이 결정 반영은 기능 구현 완료를 의미하지 않는다.

## 1. Question 및 평가 기준 모델

- [x] Question(`id`, `title`, `content`)과 EvaluationCriterion(`id`, `questionId: Long`, `content`, `maxScore`, `displayOrder`) 모델 및 Repository 구현. Entity 간 연관관계 매핑은 사용하지 않음.
- [x] 기존 Flyway 구성을 이용해 테이블, PK, 필수 컬럼의 `NOT NULL`, `question_id` 조회 인덱스를 생성하는 `V1.0.0__create_question_and_evaluation_criterion.sql` 작성. 외래 키 제약 조건, `CHECK`, 도메인 검증 트리거, `ON DELETE CASCADE`는 사용하지 않음.
- [x] 평가 기준의 `displayOrder`를 저장하고 오름차순으로 조회. 같은 순서 값에서는 ID 오름차순으로 조회하되, 점수 대응은 순서가 아닌 평가 항목 ID로 유지. User 관계는 추가하지 않음.
- [x] 질문별 기준 1개 이상, 양의 정수 최대 배점, 최대 배점 합계 100을 데이터 작성 규칙으로 적용. 검증 코드와 실패 검증 테스트는 향후 관리자 질문 생성 기능에서 구현하며 이번 작업에는 포함하지 않음.
- [x] 같은 주 버전의 별도 Flyway 초기 데이터 마이그레이션 `V1.0.1__seed_initial_questions.sql`에 질문 `자기 소개`, 본문 `인사 후, 자신의 이름, 성별을 소개해주세요.`, 평가 기준 `인사` 50점 / `본인 이름` 30점 / `본인 성별` 20점과 `displayOrder` 1 / 2 / 3을 입력. 관리자 API는 만들지 않음.
- [x] 각 테스트에서 Repository로 직접 저장한 질문/기준과 조회 결과를 비교하고 질문 ID별 기준 필터링, `displayOrder` 및 동률 시 ID 정렬, 빈 결과를 테스트. 마이그레이션과 초기 데이터는 테스트 환경 자동 설정으로 적용하며 별도 assertion을 추가하지 않음.

완료 기준: Flyway 자동 설정으로 PostgreSQL 테스트 환경을 준비하고, 각 테스트가 직접 저장한 질문과 기준을 각각 Repository로 조회하여 기대 값과 비교한다. 기준은 해당 `questionId`로 필터링되고 `displayOrder` 및 동률 시 ID 순으로 반환되며, 기준이 없는 질문에는 빈 목록을 반환한다. Entity 간 연관관계 매핑이나 DB 외래 키/도메인 검증 제약은 사용하지 않는다. 마이그레이션 이력/초기 데이터 assertion과 잘못된 저장 기준의 런타임 탐지/차단은 완료 기준에 포함하지 않는다.

이전 검증 기록: Testcontainers PostgreSQL 18.6 Alpine에서 `./gradlew clean build`가 통과했으며 Entity의 `columnDefinition` 없이 기존 `TEXT` 컬럼에 대한 `ddl-auto: validate`도 통과했다. 당시 포함된 마이그레이션 이력/초기 데이터/시스템 카탈로그/SQL 횟수 assertion은 현재 Repository 기능 테스트에서 제거했다. 개발/배포 DB에는 접속하거나 데이터를 입력하지 않았다.

현재 검증 결과: Repository만 주입하는 한글 기능 테스트 4개(질문 저장/조회 값 비교, 기준 저장/조회 값 비교, 질문별 필터링/정렬, 기준 없는 질문의 빈 결과)와 context 테스트 1개가 통과했다. `// given`, `// when`, `// then`으로 각 테스트를 구분하고 EntityManager/Flyway/JdbcTemplate 및 명시적 flush/clear/SQL 통계 코드를 제거한 상태에서 `./gradlew clean build`가 통과했다. 기본 영속성 컨텍스트를 유지하므로 별도 DB 재로딩이나 commit 후 검증을 수행한 것은 아니다.

테스트 실행에는 Docker 호환 런타임이 필요하며 수동 PostgreSQL 실행이나 `TEST_DATABASE_*` 환경변수 설정은 필요하지 않다. `src/test/resources/application.yaml`이 `test` 프로필과 `jdbc:tc:postgresql:18.6-alpine:///tech_test?TC_DAEMON=true`를 설정한다. Testcontainers가 PostgreSQL을 자동 시작하며 LLM은 비활성화한다. 루트 `.env`는 테스트에서 가져오지 않으며 테스트 대상에 개발/운영 DB를 지정하지 않는다. Flyway가 테스트 DB에 스키마와 초기 데이터를 생성한다. Repository 테스트는 `@DataJpaTest`만 사용하고 assertion은 AssertJ로 작성한다.

## 2. 질문 목록 조회

- [x] `GET /api/questions` Controller / Service와 목록 Response DTO 구현.
- [x] 전체 목록에 `id`, `title`만 반환하고 데이터가 없으면 `[]` 반환.
- [x] 평가 기준/본문 비노출, 성공 응답 타입, 빈 목록 테스트. Service는 MockitoExtension 단위 테스트, Controller는 mock Service와 MockMvc로 검증.

완료 기준: Controller → Service → QuestionRepository 목록 조회 코드를 연결하고 계층별 테스트로 목록 변환, `200 OK` JSON 배열, `id`/`title`만 노출, 빈 배열 응답을 검증한다. 실제 네트워크/DB부터 API까지의 전체 연결과 프론트엔드 화면 연결은 별도 연결 검증으로 남긴다. 질문 목록의 페이지네이션이나 정렬 기능은 추가하지 않는다. 평가 기준의 `displayOrder` 정렬과는 별개다.

검증 결과: `./gradlew clean build`가 통과했다(Windows에서는 `gradlew.bat` 실행). Service 단위 테스트 2개, MockMvc Controller 테스트 2개, 기존 Repository 테스트 4개와 context 테스트 1개로 총 9개가 통과했다. 목록 순서는 계약에서 보장하지 않으므로 테스트는 순서에 의존하지 않는다. Controller 테스트에서는 배열 타입과 각 항목의 정수 ID/문자열 제목 및 정확히 두 공개 필드만 존재함을 확인했다. 새 의존성, 마이그레이션, 평가 기준 조회는 추가하지 않았다. 실제 서버/프론트엔드 연결과 DB 조회 오류의 공통 `500 INTERNAL_SERVER_ERROR` 응답 형식은 이번 검증 범위가 아니며 공통 예외 처리는 8단계에서 구현한다.

## 3. 질문 상세 조회

- [x] `GET /api/questions/{questionId}`와 상세 Response DTO 구현.
- [x] `id`, `title`, `content`만 반환하고 평가 기준/배점은 비공개 유지.
- [x] 잘못된 ID에 `400 INVALID_REQUEST`, 없는 질문에 `404 QUESTION_NOT_FOUND` 반환.
- [x] 성공, 잘못된 ID, 없는 질문, 비공개 필드 미노출 테스트.

완료 기준: 선택한 질문의 본문을 조회할 수 있고 API 계약의 오류 형식과 일치한다. 초기 오류 변환은 필요한 범위만 구현하고 8단계에서 공통 처리를 완성한다.

구현/검증 결과: 기존 `findById`와 읽기 전용 Service에서 상세 DTO로 변환한다. Controller의 Long 경로 변수에 `@Min(1)`/`@Max(9_007_199_254_740_991L)`을 적용한다. `QuestionNotFoundException`은 공통 `BusinessException`을 상속하며 Controller 제한 없는 `GlobalExceptionHandler`에서 타입 변환/입력 범위 검증 오류와 BusinessException을 `code`, `message` JSON으로 변환한다. 반환값 검증 실패는 `400`이 아닌 안전한 `500`으로 구분한다. 평가 기준 조회/검증, 새 의존성, 마이그레이션은 추가하지 않았다. Service는 MockitoExtension과 BDDMockito.given, Controller는 WebMvcTest/MockMvc로 검증하며 한글 이름과 given/when/then, AssertJ 규칙을 유지한다.

`./gradlew clean build`의 첫 실행은 Docker 엔진 미실행으로 기존 Repository/context 테스트 5개가 실패했다. Docker Desktop을 시작한 뒤 재실행하여 총 21개(Service 4개, Controller 12개, Repository 4개, context 1개)가 모두 통과했다. 상세 성공/없는 질문, ID 0/음수/지원 범위 초과/문자/소수/Long 오버플로, 양 끝 경계값, 잘못된 ID의 Service 미호출, 상세/오류의 정확한 공개 필드와 기존 목록 회귀를 확인했다. 실제 서버/프론트엔드 연결과 공통 DB `500` 처리 완성은 이번 범위에 포함하지 않는다.

## 4. 평가 기록 모델

- [x] EvaluationAttempt의 `questionId: Long` 참조 필드, Repository 및 `V2.0.0__create_evaluation_attempt.sql` 구현. Question Entity 매핑과 DB 외래 키 제약 조건은 사용하지 않음.
- [x] 제출 답변 원문, 총점, 문자열 enum 판정, 세 종류 피드백과 JPA Auditing의 생성 시각 저장.
- [x] User 관계 없이 완료된 결과 필드를 가진 평가 기록을 저장하고 같은 질문의 복수 평가 허용. 실패/중간 결과를 생성하지 않는 Service 연결은 후속 평가/제출 작업에서 구현.
- [x] 저장/조회 값 비교, 질문 ID로 QuestionRepository 별도 조회, 복수 기록, Auditing 생성 시각 설정 및 답변 공백/줄바꿈 보존 테스트.

완료 기준: 평가 결과를 저장하고 ID로 다시 읽을 수 있다. 항목별 점수 이력, 진행 상태, 실패 작업 테이블은 필수 모델로 추가하지 않는다.

구현 결과: `evaluation` 패키지의 EvaluationAttempt/EvaluationResult/EvaluationAttemptRepository와 `config/JpaAuditingConfig`를 추가했다. `createdAt: Instant?`는 `@CreatedDate`와 AuditingEntityListener로 설정하며 생성자 입력, 직접 시각 생성, 수정 시각, 공통 BaseEntity는 추가하지 않는다. 감사 시각은 제출/평가 이벤트 시각이 아니며 시간 기반 도메인 규칙과 분리한다. 총점/판정 계산은 5단계에서 구현하고 Entity에는 도메인 검증이나 계산 로직을 추가하지 않았다. 스키마는 TEXT 답변/피드백, VARCHAR 판정, TIMESTAMP WITH TIME ZONE 생성 시각과 필수 NOT NULL/PK/question_id 인덱스를 사용하며 FK/CHECK/DB enum/default/trigger/cascade는 사용하지 않는다. 질문 제목 스냅샷은 저장하지 않으며 후속 API에서 questionId로 현재 질문을 조회한다.

검증 결과: Testcontainers PostgreSQL 18.6 Alpine에서 `./gradlew clean build`가 성공했고 전체 30개 테스트가 통과했다. 새 Repository 테스트 5개는 Repository 두 개만 주입하고 직접 데이터를 생성/저장한다. `@Import(JpaAuditingConfig::class)`로 감사 설정만 활성화하며 EntityManager/flush/clear와 인프라 assertion은 추가하지 않았다. 기존 규칙대로 같은 영속성 컨텍스트에서 비교하므로 commit 이후 DB 재로딩을 검증한 것은 아니다. 일반 타입 매핑과 새 스키마의 `ddl-auto: validate`도 통과했다. 개발/운영 DB에는 마이그레이션을 적용하지 않았다.

## 5. LLM 연동 및 평가

- [x] 기존 Spring AI 설정을 활용하여 CODEX_LB의 `gpt-6-sol`을 위한 환경설정 기반 클라이언트 구현. 실제 공급자 호환성은 미검증. 추론 옵션은 추가하지 않았으며 `CHAT_MODEL`, `CHAT_BASE_URL`, `CHAT_API_KEY`는 사용자가 관리하는 기존 값을 유지. 실제 키는 저장소에 커밋하지 않음.
- [x] DB의 질문/평가 기준과 사용자 답변으로 평가 프롬프트 구성. 답변 속 명령을 평가 규칙으로 취급하지 않음.
- [x] 저장된 평가 기준은 정상이라고 가정하고 LLM 호출 전에 기준 개수/양의 배점/배점 합계를 재검증하지 않음. LLM 응답 검증은 별도로 유지.
- [x] `docs/API.md` 내부 LLM 계약의 항목별 점수/피드백 및 종합 피드백을 네이티브 `json_schema` Structured Output으로 수신/파싱. 실제 공급자 지원은 미검증으로 기록하고 다른 출력 방식으로 자동 fallback하지 않음.
- [x] 항목 누락/중복/알 수 없는 ID, 점수 타입/범위, 필수 피드백 검증.
- [x] 백엔드에서 점수 합산 및 FAIL/RETRY/PASS 판정 구현. LLM의 총점/판정 값은 사용하지 않음.
- [x] LLM 호출부를 테스트에서 대체해 정상/잘못된 응답과 판정 경계값 테스트.
- [x] Spring AI 2.0.1 기본 timeout/SDK 재시도 사용. 애플리케이션의 호출 반복/timeout 옵션/설정 클래스는 제거하고 반환된 응답만 검증. 라이브러리 구현을 재현하는 테스트는 작성하지 않음.
- [ ] 후속 검토: Spring AI와 CODEX_LB의 실제 연동/Structured Output 지원 및 실모델 품질 확인 방법 결정. 이번에는 실제 LLM 호출 없이 테스트하며 비용 발생 검증은 별도 승인 후 수행.

완료 기준: 유효한 내부 평가 결과만 생성하고 판정 경계값이 계약과 일치하며 잘못된 점수를 보정/저장하지 않는다. 라이브러리 기본 timeout/재시도를 사용하고 Service는 한 번의 모델 호출 결과를 검증한다. 호출 및 검증 오류는 안전한 502로 처리한다. 실제 외부 호출 없이 mock으로 검증하고 실제 공급자 호환성/품질은 미검증으로 기록한다. OpenRouter 전환/SSE는 이번 범위가 아니다.

구현 결과: EvaluationService가 질문/기준을 조회하고 LlmEvaluationClient를 한 번 호출하여 반환 문자열을 검증/합산/판정한다. 모델 호출 실패와 빈 응답은 클라이언트에서, 로컬 검증 실패는 Service에서 LlmEvaluationFailedException으로 변환하여 안전한 502를 반환한다. 질문 없음/DB 오류는 그대로 전달한다. 서비스 전체의 DB 트랜잭션, 재시도 루프, deadline/Executor/Future는 없다. 기록 저장/공개 평가 API는 추가하지 않았다.

설정: timeout/max-retries 및 app.evaluation 설정을 제거했고 EvaluationConfig/EvaluationProperties/LlmCallException도 제거했다. 기본 자동 설정의 모델을 직접 주입하며 기존 옵션에서 Schema만 설정한다. 2.0.1 기본 timeout은 60초, SDK 추가 재시도는 최대 3회(최초 포함 최대 4 SDK 시도)이며 전체 deadline은 아니다. 직접 call()은 응답 JSON 교정 Advisor를 실행하지 않아 로컬 검증 실패 시 재호출하지 않는다. 기존 모델/endpoint/key는 유지하고 chat=none context 테스트의 모델은 @MockitoBean으로 대체한다.

이전 커스텀 시간/전송 및 수동 재시도 검증 기록은 현재 정책의 보장이 아니다. 라이브러리 자체 timeout/retry 테스트는 제거했고 실제 모델 호출 없이 반환값 mock으로 Service와 오류 계약을 검증한다. JSON 타입/ID/점수/피드백 검증은 애플리케이션 규칙이므로 유지한다.

테스트 구성: Service는 정상 결과, 잘못된 응답의 즉시 실패, 라이브러리 최종 호출 실패, 질문 없음, DB 오류를 mock으로 검증한다. 수동 retry/interrupt/timeout 테스트는 제거했다. 클라이언트의 Schema/프롬프트/빈 응답/호출 실패와 JSON 검증, 공통 502 및 기존 API/DB 회귀는 유지한다. 실제 공급자나 라이브러리 retry 동작을 검증한 것으로 기록하지 않는다.

검증 결과: 라이브러리 기본값으로 정리한 뒤 `./gradlew clean build`가 성공했고 전체 146개 테스트가 통과했다. Service 테스트는 11개에서 7개로 줄었으며 실제 외부 LLM 호출/새 의존성/호환 계층/프론트 코드 변경은 없다.

프론트 정책: 180초 무응답 시 대기를 종료하고 답변을 유지하며 수동 재시도를 안내한다. 이는 UX상의 실패 간주이며 서버 취소/실패 확정이 아니다. SDK 기본 retry/backoff 때문에 서버는 계속 처리할 수 있어 재제출로 중복 평가/기록/비용이 발생할 수 있다. POST 자동 재전송은 하지 않는다. 프론트 구현과 실제 공급자/프록시 시간 동작은 후속 검증하며 SSE는 별도 작업이다.

## 6. 답변 제출 및 평가 API

- [x] 승인된 최대 3,000 UTF-16 코드 단위 답변 길이를 Request DTO와 계약에 반영하고 입력 경계 검증.
- [ ] 배포 후속 작업: nginx에서 전체 JSON 본문 크기를 제한하고 초과 시 413 반환. 상한 수치는 배포 때 확정(256 KiB 권장안). 백엔드 커스텀 본문 제한 Filter/Wrapper나 JSON에 적용되지 않는 Tomcat 폼/multipart 설정은 추가하지 않음. 답변 길이 검증은 대용량 HTTP 본문 수신 제한이 아님.
- [x] `POST /api/questions/{questionId}/evaluation-attempts` 구현.
- [x] 요청 검증 → QuestionRepository로 질문 조회 → EvaluationCriterionRepository로 기준 목록 조회 → 평가 → LLM 응답 검증/합산/판정 → 완료 기록 저장을 하나의 동기식 요청으로 연결. 저장 기준의 도메인 규칙은 재검증하지 않음.
- [x] DB 저장이 완료된 뒤 `201 Created`, `Location` 헤더 및 계약의 평가 결과 DTO 반환.
- [x] 실패 결과/중간 기록은 저장하지 않고 POST마다 별도 평가를 생성.
- [x] 성공 반환 ID/감사 시각, 같은 질문 재제출, 잘못된 답변/없는 질문/평가 실패/저장 실패 계층별 테스트. 실제 commit 후 HTTP 재조회는 9단계에서 별도 검증.

완료 기준: 사용자 답변을 제출하면 저장된 결과 ID와 점수/피드백을 얻는다. 누락/null/문자열 외 타입/빈 문자열/공백/3,000 UTF-16 코드 단위 초과 답변은 LLM 호출 전에 `400`으로 거부한다. 원문을 trim하거나 자르지 않으며 처리 중 오류가 나면 성공 응답을 반환하지 않는다. 계층별 mock 및 Repository 롤백 테스트만으로 commit 후 재조회나 브라우저 흐름을 검증했다고 표시하지 않는다.

POST 단계 검증(2026-10-05): Windows `.\gradlew.bat clean build` 성공, 전체 196개 테스트 통과. 제출 Service 11개, Controller 35개, 공통 Handler 16개와 기존 질문/엄격한 LLM 검증/CORS/Repository/context 회귀를 확인했다. 답변 필드 한정 Jackson 3 역직렬화로 숫자/boolean String coercion을 차단한다. Service는 질문/기준 각 1회 조회 후 검증된 완료 기록만 save하며 LLM 대기 중 외부 DB 트랜잭션은 없다. mock save 반환의 ID/감사 시각을 사용하고 누락 시 500으로 처리한다. 405/415의 원래 상태도 보존한다. 실제 외부 LLM/개발 DB/브라우저 또는 commit 후 HTTP GET은 이 단계에서 검증하지 않았다.

## 7. 평가 결과 조회

- [x] `GET /api/evaluation-attempts/{attemptId}` 구현.
- [x] POST와 같은 결과 형식으로 질문 ID/현재 제목, 답변 원문, 저장된 점수/판정, 피드백, 생성 시각 반환.
- [x] 잘못된 ID와 없는 평가 기록의 `400`/`404` 처리. 참조 질문 누락/DB 오류는 안전한 500.
- [x] 저장 결과 조회, 오류 응답, 기준 조회/LLM 재호출/점수 재계산 없음 테스트.

완료 기준: 제출로 생성된 평가 ID를 조회하면 저장된 결과를 복원할 수 있다. 결과 화면 새로고침에 필요한 데이터를 제공하며 평가 기록 목록이나 사용자 기록 기능은 추가하지 않는다.

GET 단계 검증(2026-10-05): `.\gradlew.bat clean build` 성공, 전체 216개 테스트 통과. EvaluationService 16개와 EvaluationAttemptController 49개로 POST 회귀/GET 성공·ID 경계·404·DB/참조 500을 검증했다. GET 메서드에만 짧은 readOnly 트랜잭션을 적용한다. 제목 스냅샷이나 연관관계/새 스키마/의존성은 추가하지 않았다.

## 8. LLM 실패 및 공통 예외 처리

Service/Controller 책임 정리: submit → submitAnswer, preview → previewEvaluation, EvaluationAttemptRequest → SubmitAnswerRequest로 변경한다. 나머지 HTTP DTO 이름과 API 형식은 유지한다. submitAnswer/getAttempt는 Application의 EvaluationAttemptResult, previewEvaluation은 title/content/criteria Spec/answer를 받아 EvaluatedAnswerResult를 반환한다. 두 Controller가 요청·응답 DTO를 변환하고 preview 기준 정렬/임시 ID 매핑을 수행한다. EvaluationService에서 presentation DTO 의존성을 제거하며 Factory/Mapper나 새로운 의존성은 추가하지 않는다.

책임 정리 검증: 기존 Service/두 Controller 테스트를 새 Application 결과·메서드명에 맞춰 수정하고 preview 정렬/임시 ID 변환을 기존 성공 테스트에서 확인했다. Service 테스트는 presentation DTO를 사용하지 않고 전달한 기준 Spec을 그대로 평가하는 동작을 확인한다. `.\gradlew.bat clean build`가 통과했고 구 메서드 호출/요청 클래스명/EvaluationService의 presentation 참조가 남아 있지 않다. HTTP 경로·JSON·상태·Location/검증/저장·비저장 정책은 유지하며 실제 LLM은 호출하지 않았다.

평가 책임 분리(2026-10-06): 기존 EvaluationResponseValidator를 제거하고 JSON 파싱/형식 검증은 infrastructure.llm.LlmEvaluationResponseParser, 기준 개수/ID/중복/점수 범위는 application.validation.EvaluationProviderResultValidator, 점수 판정은 domain.EvaluationResult.fromScore로 나눴다. EvaluationCriterionSpec와 EvaluationProviderResult는 application.model에, EvaluationProvider는 application.port에 둔다. Service는 raw JSON/Jackson/JsonNode/Spring AI/LLM 구현을 모르고 검증 후 점수 합산만 수행한다. LlmEvaluationClient가 Provider를 구현하고 파싱 실패를 안전한 LlmEvaluationFailedException으로 변환한다. port.out/port.out.model이나 LLM parser/exception 하위 패키지는 사용하지 않고 작은 인터페이스 하나만 유지한다. 총 배점 100인 정상 저장 기준 전제와 공개 API/preview 계약/기존 모델 설정·Schema·timeout/retry는 유지한다. Factory/Calculator/Mapper나 새 의존성은 추가하지 않았다.

책임 분리 검증: 기존 Validator 테스트를 parser/application.validation/domain 테스트로 이관하고 Service mock은 정규화된 Provider 결과를 반환하도록 수정했다. LLM 클라이언트의 파싱 실패→안전한 평가 실패 테스트도 추가했다. 최초 compileKotlin에서 Jackson JsonNode의 map 메서드와 Kotlin 컬렉션 map의 충돌로 타입 오류가 발생해 배열을 toList로 변환 후 매핑하도록 수정했다. 이후 `.\gradlew.bat clean build`와 기존 mock 모델 기반 Core Flow가 통과했다. Application의 Jackson/JsonNode/Spring AI/LLM 구현 참조는 없으며 실제 LLM은 호출하지 않았다.

예외 계층 후속 정리: 마지막 커밋의 BusinessExceptionNew/ApplicationException/DomainException/ErrorType을 사용한다. 질문/평가 기록 없음과 LLM 평가 실패는 ApplicationException이며 도메인 규칙 위반에만 DomainException을 사용한다. 구 BusinessException과 HTTP 상태 의존성은 제거하고 Handler에서만 400/404/409/502를 매핑한다. 내부 InvalidLlmResponseException은 후속 책임 분리에서 LlmEvaluationClient가 안전한 평가 실패로 변환하도록 옮겼다. 아래 초기 구현 기록의 구 계층 설명은 당시 상태이며 현재 API 계약은 유지한다.

후속 검증: `.\gradlew.bat clean build`가 통과했다. 공통 Handler 테스트의 ApplicationException/CONFLICT와 DomainException/INVALID_INPUT 매핑, 기존 질문·평가 기록 404/LLM 실패 502/내부 오류 500/405·415 보존 회귀를 확인했다. src의 구 BusinessException 참조와 예외 클래스의 Spring HTTP 의존성은 없다. 새로운 의존성/HTTP 계약 변경/실제 LLM 호출은 추가하지 않았다.

- [x] `BusinessException : RuntimeException`과 이를 상속하는 `QuestionNotFoundException`, Controller 선택 조건 없는 `GlobalExceptionHandler`로 공통 예외 처리 기반 구현. BusinessException/입력 타입/메서드 검증 오류만 우선 처리하며 전체 DB/LLM/예상치 못한 오류 처리는 아래 항목에서 완성.

기반 검증 결과: `./gradlew clean build`가 통과했으며 전체 25개 테스트가 성공했다. 추가 MockMvc 테스트 4개에서 질문 외 Controller의 BusinessException 상태/코드/메시지 전달, 입력 타입 및 범위 오류의 공통 `400`, 반환값 검증 실패의 안전한 `500`을 확인했다. 기존 질문 목록/상세의 `400`/`404` 계약은 유지하며 아직 아래 재시도 및 전체 예외 처리 항목을 완료한 것은 아니다.

- [ ] 라이브러리 기본 timeout/전송 retry와 프론트 180초 대기 종료/수동 재시도 UX를 실제 공급자/프록시와 연결 검증.
- [x] 애플리케이션의 timeout/횟수 설정과 수동 재시도 제거. Spring AI 기본 SDK 정책에 맡기고 JSON 검증 실패는 즉시 종료. opt-in 교정 Advisor는 추가하지 않음.
- [x] 라이브러리 최종 실패와 응답 검증 실패에 `502 LLM_EVALUATION_FAILED` 반환, 잘못된 요청/없는 질문/DB 오류는 애플리케이션에서 재호출하지 않음. 저장 기준 오류를 탐지하는 별도 검증 흐름은 추가하지 않음. 라이브러리 retry 내부/실제 소진은 검증 대상이 아님.
- [x] `400`/`404`/`502`/`500` 응답을 계약의 공통 `code`, `message` 형식으로 통일. 내부 정보/키/평가 기준은 응답에 숨김.
- [x] 모델 반환값/최종 실패를 mock한 Service/Controller 계약 및 DB 저장 실패 시 LLM 재호출 없음 테스트. 라이브러리 retry 내부는 별도 재구현/테스트하지 않음.

완료 기준: 애플리케이션 재시도 없이 라이브러리 최종 실패/응답 검증 실패를 안전한 오류로 처리하고 실패 기록은 저장하지 않는다. 프론트는 180초 무응답 시 답변 유지/수동 재시도 안내를 제공하되 서버 실패 확정으로 간주하지 않는다. 실제 공급자/프록시 연결과 중복 제출 위험을 확인한다.

## 9. 테스트 및 Core Flow 검증

- [x] 기존 테스트 설정과 Docker 호환 런타임으로 `.\gradlew.bat clean build` 실행 및 전체 216개 통과 확인. 개발/운영 DB나 .env는 사용하지 않음.
- [x] 기존 Testcontainers/Flyway 자동 설정 아래 질문/기준 조회, 외부 모델 대체 평가 및 실제 commit 후 별도 HTTP GET 연결 테스트. 마이그레이션 이력/인프라 assertion 및 Repository flush/clear 없음.
- [x] 네 API의 JSON 타입/필수 필드/상태 코드/오류 코드가 `docs/API.md`와 일치하는지 계층별 테스트로 확인.
- [ ] 후속 검토/별도 승인 후 실제 환경의 유효한 LLM 응답과 Spring AI/CODEX_LB 호환성을 수동 확인. 이번에는 실제 LLM 호출 없이 제어 가능한 대체로 타임아웃/잘못된 응답/재시도 실패를 검증.
- [ ] 목록 → 상세 → 답변 제출 → 결과 표시 → 결과 재조회 흐름을 프론트엔드와 연결해 확인.
- [ ] 프론트엔드와 중복 제출 방지, 분석 중 표시, 오류 시 답변 유지, 결과 새로고침 복원, POST 자동 재전송 방지를 확인.
- [ ] 프론트 180초 대기 종료 후 답변 유지/수동 재시도 안내 구현과 확인. 이전 서버 요청이 진행 중일 수 있어 중복 평가/비용 위험을 공유하며 백엔드 취소/중복 방지 기능은 임의로 추가하지 않음.
- [x] `CORS_ALLOWED_ORIGINS` 기반 `/api/**` CORS 설정과 개발 Origin `http://localhost:5173` 적용, MockMvc 허용/거부/preflight 검증. 배포 Origin과 실제 브라우저/프록시 연결은 아래 별도 항목에서 확인.
- [ ] 실제 개발/배포 프론트엔드와 브라우저 CORS 연결 및 운영 Origin/프록시 설정 검증.
- [x] 공개 API의 정확한 필드 검증으로 평가 기준 비노출 확인. 결과 조회에 인증/소유자 보호가 없고 ID를 아는 누구나 답변/결과를 읽을 수 있음을 API 문서에 공유.

완료 기준: 정상 경로와 핵심 실패 경로가 검증되고 결과 페이지를 다시 열어도 저장된 평가를 조회할 수 있다. 실제 호출/배포 연결 등 확인하지 못한 항목은 완료로 표시하지 않는다.

연결 검증(2026-10-05): `EvaluationCoreFlowTest`는 RANDOM_PORT 실제 서버 + Testcontainers PostgreSQL + 테스트 전용 `@MockitoBean OpenAiChatModel`을 사용한다. 테스트 전체 트랜잭션 없이 직접 생성한 질문/기준으로 HTTP 목록 → 상세 → POST 201/Location → GET → 반복 GET을 확인하고 동일 답변 재제출의 새 ID/기록과 모델 호출 2회를 검증했다. Repository 테스트와 별개로 실제 commit 이후 요청에서 읽는 검증이다. 첫 실행은 POST Auditing 나노초와 PostgreSQL GET 마이크로초의 차이로 본문 전체 일치 assertion이 실패했다. 시각을 변경하지 않고 UTC 시각 차이 1마이크로초 미만과 나머지 필드 동일성/반복 GET 동일성을 분리한 후 통과했다.

프론트 파일 변경 없이 기존 `npm run test:run -- src/pages/QuestionAnswerPage.test.tsx src/pages/EvaluationResultPage.test.tsx`를 실행해 30개가 통과했다. 오류 후 답변 유지, 진행 중 단일 POST, 결과 GET 실패/재시도 시 POST 재전송 없음의 기존 jsdom/MSW 검증이며 실제 백엔드/브라우저 연결 성공을 뜻하지 않는다. 현재 브라우저 제어 도구와 프로젝트 E2E 실행 설정이 없어 실제 URL 직접 접근/새로고침·브라우저 CORS는 미검증으로 남긴다. 새 브라우저 도구/의존성이나 프론트 코드/문서는 추가·변경하지 않았다. front/TODO.md의 오래된 서버 deadline 설명 정정은 프론트 후속 작업이며, 실모델/품질/운영 설정/전체 본문 제한도 미완료 상태를 유지한다.

### CORS 설정 및 검증

- WebConfig/CorsProperties와 `app.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:}`를 추가했다. 빈 설정은 교차 Origin을 허용하지 않으며 허용 메서드는 GET/POST/OPTIONS, 요청 헤더는 Content-Type, 공개 응답 헤더는 Location이다. credentials와 와일드카드 Origin은 사용하지 않는다. Spring Security나 새 의존성은 추가하지 않았다.
- 로컬 `.env`와 `.env.example`의 CORS 항목은 `CORS_ALLOWED_ORIGINS=http://localhost:5173`이다. 다른 환경에서는 실행 환경변수로 정확한 Origin을 지정한다. 복수 Origin은 쉼표로 구분한다. 예: `CORS_ALLOWED_ORIGINS=https://frontend.example.com,https://preview.example.com`. 변경 후 백엔드를 재시작하며 코드 수정/재빌드는 필요하지 않다. 경로와 끝의 `/`는 넣지 않는다. localhost와 127.0.0.1, 서로 다른 포트는 별도 Origin이다.
- 초기 CORS 구현에서 `./gradlew clean build`가 성공했고 당시 전체 41개 테스트가 통과했다. 현재 중복 WebConfigOriginTest는 제거하고 WebConfigTest의 indexed property(`app.cors.allowed-origins[0]`, `[1]`)로 CorsProperties 바인딩과 MVC 정책 적용을 검증한다. 테스트에서 환경변수/placeholder는 재정의하지 않으며 production 환경변수 매핑은 유지한다. 별도 WebConfigWithoutOriginsTest는 Origin 설정을 아예 선언하지 않아 빈 목록 기본값의 `403`/허용 헤더 없음/Service 미호출을 확인한다. preflight 메서드 헤더는 쉼표 분리 후 trim하여 비교한다. CORS 테스트 11개와 기존 질문 Controller 테스트 12개, 총 23개가 통과했다. 여러 Origin 허용/미등록 Origin 거부, 정상 및 잘못된 메서드/헤더 preflight, 404의 CORS 헤더, Location 공개, credentials 미허용과 Origin 없는 요청 검증을 유지한다. 거부/preflight에는 Service 미호출을 확인한다. POST preflight는 현재 목록 경로의 정책 검증이며 평가 제출 API 구현이나 실제 브라우저/프론트엔드 연결 검증은 수행하지 않았다.
- Spring MVC의 CORS 거부는 Controller 호출 전 `403`으로 처리되며 API의 BusinessException JSON 응답과 별개다. 추후 Security 적용 시 CORS 연동과 preflight 인증 제외를 확인하고, 배포 프록시가 OPTIONS를 차단하거나 CORS 헤더를 중복 추가하지 않도록 확인한다. 현재 프론트 요청에는 credentials include가 필요하지 않다.

## 10. 관리자 MVP 범위 및 확정 계약

사용자와 결정한 기준은 `docs/API.md` 10절이다. 아래 정책은 구현 선행 미정 사항이 아니라 확정 범위이며 공개 네 API의 기존 계약은 유지한다.

- 로그인/인증/인가 없는 MVP를 만든다. Spring Security + 아이디·비밀번호는 후속 계획으로만 남긴다. 인증/인가 없는 외부 노출 시 누구나 기준 조회·질문 변경·유료 테스트 호출이 가능하므로 인증 전에는 로컬/접근 제한 환경에서 검증한다. CORS/Cloudflare 프록시는 접근 보호가 아니다.
- 제목 200/본문 10,000/기준 설명 항목당 1,000/테스트 답변 3,000 UTF-16 코드 단위, 기준 1~10개, maxScore 양의 정수/합계 100. 원문을 잘라 저장하지 않고 필드 위반은 400 INVALID_REQUEST다. 기존 TEXT 컬럼을 유지한다.
- 생성/수정은 일반 쓰기 트랜잭션으로 처리하고 후속 저장으로 이전 내용을 덮는다. 별도 동시성 제어/요청 도착 순서 보장/혼합 읽기 방지/snapshot 읽기/409/버전 이력은 추가하지 않는다. 운영 중 필요해지면 개선한다.
- 저장 즉시 공개하며 기준은 전체 교체하고 프론트가 displayOrder를 명시적으로 전송한다. 서버는 필수/중복만 확인하고 값을 그대로 사용하며 양수/연속 번호 규칙은 추가하지 않는다. 기존 평가 기록은 보존하고 제목은 현재 값으로 읽는 기존 구현을 유지한다. 초안/발행/삭제/과거 평가 스냅샷은 추가하지 않는다.
- preview는 일반 사용자와 같은 총점/판정/종합 피드백만 반환하며 비저장이다. 항목별 결과 API/화면은 추가하지 않는다. 미저장 입력 보호와 저장 후 이동은 프론트 담당이며 백엔드 임시저장 API는 없다.

위 계약으로 프론트 작업을 진행할 수 있다. 전체 본문 크기 수치/실제 프록시 설정은 배포 책임으로 분리하며 인증 상세 계획도 현재 MVP 구현의 선행 조건이 아니다. 관리자 목록·상세·생성·수정·preview는 구현되었다.

## 11. 관리자 PUT CORS 지원

목적: 기존 CORS 정책에 전체 수정의 PUT 메서드만 추가한다. Security나 로그인 기능은 구현하지 않는다.

- [x] 공통 CORS 허용 메서드에 PUT을 추가한다. 허용 Origin/Content-Type/Location/credentials 미허용 정책은 유지하고 인증 헤더/쿠키/CSRF 처리는 추가하지 않는다.
- [x] PUT preflight/허용·거부 Origin/기존 GET·POST 회귀를 MockMvc로 검증한다. 인증/인가 테스트나 401/403 애플리케이션 계약은 추가하지 않는다.

완료 기준: 허용 Origin의 브라우저에서 PUT 요청이 가능하고 기존 CORS 계약은 유지된다. 이 작업으로 관리자 접근 보호가 생기는 것은 아니다.

## 12. 관리자 질문 목록/상세 및 입력 검증

목적: 수정 화면에 필요한 비공개 기준 조회와 생성/수정/테스트의 동일한 입력 규칙을 준비한다.

- [x] `GET /api/admin/questions`는 별도 AdminQuestionController에서 기존 QuestionService와 QuestionResponse를 재사용해 `{ id, title }[]`를 반환한다. 정렬/검색/페이지네이션/인증/기준 조회는 추가하지 않는다.
- [x] 상세 GET은 `id`, `title`, `content`, `criteria[{ id, content, maxScore, displayOrder }]`를 반환한다. 공개 상세 DTO를 확장하지 않는다.
- [x] 상세 GET의 ID 검증/없는 질문 404/기준 정렬과 DB 오류 공통 처리를 구현한다. 평가 기준은 일반 사용자 목록/상세 DTO에 추가하지 않는다. 인증 전에는 관리자 상세 자체도 접근 보호가 없음을 계약에 명시한다.
- [x] 생성/수정이 공유하는 AdminQuestionRequest 입력 검증을 구현한다. 기본 Jackson 변환 후 필수 비공백 문자열/제목 200·본문 10,000·기준 설명 1,000 UTF-16 상한/기준 1~10개/양의 정수 배점/합계 100을 검증하고 문자열 원문은 보존한다. 관리자 커스텀 역직렬화/별도 coercion 설정은 사용하지 않는다. preview 연결은 14단계에서 수행한다.
- [x] 사용자 계약 변경: 생성/수정/preview의 기준에 displayOrder를 필수로 받는다. 기본 Jackson 변환 후 같은 요청 내 중복은 400 INVALID_REQUEST, 0·음수·불연속 값은 허용하며 값을 재번호화하지 않는다. 저장 응답/preview 프롬프트는 displayOrder 오름차순이다. 기준 ID/questionId는 요구하지 않는다.
- 프론트 연결 정책 확인: 생성·수정·preview 모두 공통 AdminCriterionRequest의 displayOrder: Int를 이미 받는다. 프론트는 1 이상의 중복 없는 값을 명시적으로 보낸다. 서버는 전달 값을 그대로 저장/정렬하며 기존 중복 검증만 유지한다. 프론트 양수 정책에 맞춘 별도 서버 @Min 제약이나 배열 기반 재번호화는 추가하지 않는다.
- [x] 생성/수정 Controller 테스트로 ID 경계/404/500과 입력 누락/null/Jackson 변환 불가 타입/공백/배점 0·음수/합계 부족·초과/각 문자열 상한과 상한+1/기준 0·1·10·11개 경계를 검증한다. 기본 변환이 허용하는 타입 coercion을 거부하는 테스트는 제거했다.
- [x] 관리자 목록의 id/title만 반환, 빈 목록, 내부 오류 500을 Controller 테스트 3개로 검증했다. 기존 조회 Service 테스트를 중복 추가하지 않는다.
- [x] 관리자 상세 GET 조회 필드/ID 경계/404/500을 기본 Service/Controller 테스트로 검증한다.

관리자 상세 GET 검증(2026-10-06): AdminQuestionService.getQuestion에 짧은 readOnly 트랜잭션으로 질문과 기준 조회를 추가했다. Application의 AdminQuestionDetailResult는 질문/기준을 묶고 Controller가 기존 응답 DTO로 변환한다. 생성·수정의 기존 변환 구조나 일반 사용자 API는 바꾸지 않았다. Service 정상 조회/없는 질문과 Controller 정확한 필드/정렬/ID 양 끝 및 잘못된 ID/404/안전한 500을 검증했다. `.\gradlew.bat clean build`가 통과했다. 새로운 의존성/마이그레이션/인증/동시성/LLM 호출/통합 테스트/프론트 코드 변경은 없다. 실제 브라우저의 목록→수정/수정 URL 직접 접근·새로고침/CORS는 서버 반영 후 재검증해야 하며 완료 체크하지 않는다.

관리자 목록/패키지 정리 검증: question도 evaluation처럼 presentation(Controller/request/response), application(Service/exception), domain(Entity), infrastructure/persistence(Repository)로 이동했다. 관리자/일반 사용자 Controller는 각각 별도 클래스이며 목록 조회 구현/DTO만 재사용한다. 질문 테스트는 main에 대응하는 presentation/application/infrastructure/persistence로 이동하고 평가/CORS 테스트는 변경된 질문 import만 수정했다. `.\gradlew.bat clean build`가 통과했다. 관리자 상세 GET/새 의존성/인증/실제 LLM/브라우저 연동/프론트 코드 변경은 없다.

완료 기준: 관리자 상세 형식으로 질문과 기준을 읽고 같은 작성 규칙을 저장과 테스트에서 재사용할 수 있다. 인증 보호는 없다. Entity 검증/DB CHECK/외래 키 제약으로 규칙을 옮기거나 공개 조회에서 저장 기준을 재검증하지 않는다.

## 13. 질문 생성·전체 수정 및 공개 반영

목적: 하나의 저장 요청으로 질문과 기준을 함께 반영하고 일반 사용자 조회에 공개한다. LLM 호출은 하지 않는다.

- [x] `POST /api/admin/questions`에 title/content/criteria 전체 입력을 받아 질문/기준을 저장한다. commit 후 `201 Created`, 관리자 상세 DTO, 관리자 상세 `Location` 헤더를 반환한다.
- [x] `PUT /api/admin/questions/{questionId}`는 기존 질문 ID를 유지하고 제목/본문/기준 전체를 교체한다. 기준 ID는 새로 발급하며 없는 질문은 생성하지 않고 404를 반환한다. 성공은 `200 OK`와 관리자 상세 DTO다.
- [x] 생성/수정의 전체 쓰기에 @Transactional을 적용했다. FK/cascade 없이 기존 기준 삭제와 새 기준 저장을 애플리케이션에서 관리한다. 별도의 동시성 잠금/직렬화/버전 검사/혼합 방지 로직은 없다. 실제 DB 실패를 주입한 롤백 검증은 15단계의 미완료 항목으로 남긴다.
- [x] 기존 EvaluationAttempt는 수정/삭제/재평가하지 않는다. 질문 저장 후 다음 공개 목록/상세 GET에 반영하고 기준은 여전히 숨긴다. 별도 초안/발행/삭제 기능은 추가하지 않는다.
- [x] Service/Controller/Repository 기능 테스트로 생성/전체 교체/기준 제거/순서/404/저장 실패의 안전한 오류를 검증하고, 기존 연결 테스트에 생성·수정 후 공개 조회/평가 기록 보존 확인을 추가했다. 동시성 테스트/snapshot 읽기 변경은 없으며 mock만으로 실제 DB 롤백 검증 완료라고 표시하지 않는다.

완료 기준: 저장 성공 후 질문과 전체 기준이 반영되며 부분 저장은 공개되지 않는다. 저장에 LLM 비용이 들지 않고 기존 평가 결과는 유지된다. 응답 유실 시 자동 재전송하지 않고 목록/상세를 조회해 저장 여부를 확인한다.

생성·수정 검증(2026-10-06): AdminQuestionController/Service와 요청·응답 DTO를 추가하고 Question.title/content를 수정 가능하게 했다. 기준 교체는 deleteAllByQuestionId와 saveAll, 순서는 요청 배열 기준이다. 새로운 의존성/마이그레이션/인증/동시성/LLM 호출/프론트 변경은 없다. 관리자 GET/preview는 미구현이며 생성 Location의 관리자 상세 GET도 후속 작업이다. 최초 빌드는 JSON LongNode/IntNode 비교 차이로 성공 응답 테스트 2개가 실패했다. 같은 JSON 파싱 기준으로 테스트를 수정한 뒤 `.\gradlew.bat clean build`가 통과했다. 입력 검증/CRUD/CORS와 Testcontainers PostgreSQL commit 후 공개 조회 및 기존 평가 보존을 확인했다. 실제 브라우저/배포와 DB 실패 주입 롤백 검증은 하지 않았다.

후속 단순화: 관리자 QuestionStringDeserializer/CriterionScoreDeserializer와 JsonDeserialize를 제거해 기본 Jackson 변환을 사용한다. AdminCriterionRequest/AdminCriterionResponse는 각각 별도 파일로 분리했다. Question의 title/content setter는 protected로 제한하고 Service/테스트는 changeTitle/changeContent로 수정한다. JPA all-open 설정 때문에 private setter는 컴파일되지 않아 기존 ID와 같은 protected 접근을 사용했다. 변경 후 `.\gradlew.bat clean build`가 통과했다. 일반 사용자 답변 역직렬화와 기존 길이/배점 규칙은 유지한다.

## 14. 저장 전 비저장 평가 테스트 API

목적: 신규/수정 폼의 현재 질문/기준/답변으로 일반 사용자 평가 결과를 미리 확인한다.

- [x] `POST /api/admin/questions/evaluation-preview`는 title/content/criteria(각 displayOrder 포함)/answer를 받는다. 생성·수정과 같은 길이/기준 개수/배점 합계/순서 중복 검증과 답변 비공백/3,000 UTF-16 제한을 적용한다. answer도 기본 Jackson 변환을 사용하며 커스텀 역직렬화는 추가하지 않는다. questionId나 DB 기준 ID는 요구하지 않는다.
- [x] 요청 기준을 displayOrder로 정렬하고 내부 입력 DTO에 임시 ID를 부여해 기존 프롬프트/Structured Output/엄격한 LLM 응답 검증/합산/FAIL·RETRY·PASS 판정 경로를 재사용한다. Entity에 임시 DB ID를 넣지 않고 평가 함수만 최소 분리하며 별도 채점 구현을 만들지 않는다.
- [x] `200 OK`와 questionTitle/answer/score/result/strengths/weaknesses/improvements만 반환한다. 평가 id/questionId/createdAt/Location과 저장 결과 URL은 제공하지 않는다.
- [x] 질문/기준/평가 기록 Repository에 쓰지 않고 저장 질문도 읽지 않는다. preview 성공/실패 모두 Repository를 사용하지 않으며 LLM 대기 중 DB 트랜잭션을 유지하지 않는다.
- [x] 기존 모델 환경설정/SDK timeout·retry를 유지한다. LLM 실패/잘못된 응답은 502이며 재호출/점수 보정/자동 fallback은 추가하지 않는다. 요청/프롬프트/답변/결과 원문을 애플리케이션 로그에 남기지 않는다.
- [x] 실제 모델 대신 mock으로 요청 내용/지정 순서/임시 ID 매칭/Repository 미사용/정상 결과/입력 오류/502/정확한 응답 필드를 검증했다. 채점 경계는 기존 validator 회귀 테스트를 그대로 사용하며 중복 추가하지 않는다.

완료 기준: 아직 저장하지 않은 질문과 수정 중 내용도 테스트할 수 있고 어떤 질문/평가 기록도 생기지 않는다. 저장 성공이나 유료 공급자 호환성/평가 품질까지 검증한 것으로 표시하지 않는다.

displayOrder/preview 검증(2026-10-06): 요청 순서는 프론트 지정 값으로 변경했고 저장/응답/preview 정렬 및 중복/누락/null 거부를 확인했다. 순서 양수/연속 번호 제약은 없다. EvaluationCriterionInput으로 LLM/validator의 Entity 의존성만 제거하고 기존 프롬프트·검증·판정을 재사용한다. preview 클래스는 각각 별도 파일이며 기본 Jackson을 사용한다. `.\gradlew.bat clean build`가 통과했고 기존 Testcontainers 생성·수정 연결도 새 순서 계약으로 통과했다. 새로운 의존성/마이그레이션/인증/동시성/재시도/실제 모델 호출/프론트 변경은 없다. preview 전후 실제 DB 비교/브라우저/배포 검증은 아직 하지 않았다.

## 15. 관리자 연결 검증 및 프론트엔드 병렬 작업 인계

목적: 새 관리자 흐름과 기존 공개 흐름을 함께 검증하고 문서 계약을 프론트 구현에 인계한다.

- [x] 생성·수정 구현 후 기존 Docker/Testcontainers 환경에서 `.\gradlew.bat clean build`를 실행했다. 개발/운영 DB 및 실제 LLM은 사용하지 않았다. GET/preview 구현 후에도 다시 전체 검증한다.
- [ ] 실제 commit 이후 별도 요청으로 관리자 생성 → 공개 목록/상세 → 관리자 상세 → 전체 수정 → 변경 내용 조회를 확인한다. 테스트 전용 모델로 preview 전후 질문/기준/평가 기록이 동일한 것도 검증한다.
- [ ] PostgreSQL 환경에서 실패 시 질문/기준 전체 롤백을 기능 동작으로 확인한다. 동시성/혼합 읽기 테스트나 SQL 횟수/시스템 카탈로그/마이그레이션 이력 assertion은 추가하지 않는다.
- [ ] 인증 없는 관리자 GET/POST/PUT/preview와 공개 네 API 회귀 및 PUT/preflight CORS를 검증한다. 인증/권한 거부 테스트는 후속 작업이다. 브라우저/운영 프록시는 mock/MockMvc 성공과 별도로 검증한다.
- [ ] API 타입 담당자가 기존 axios 클라이언트의 관리자 DTO/요청 함수/MSW fixture를 먼저 준비한 후 목록 담당과 생성·수정 공통 편집 화면 담당을 병렬 배정한다. App 라우팅/헤더/공유 API 파일은 한 통합 담당자가 소유한다. 인증 전달/인증 가드는 추가하지 않는다.
- [ ] 편집 화면 담당은 기준 추가·제거·순서/합계 안내, 저장·테스트 분리, 테스트 결과 무효화, 중복 요청 잠금, 오류 후 모든 입력 유지, 180초 대기 종료 후 오래된 응답 무시를 함께 구현한다. 생성/수정 화면은 같은 편집 로직으로 담당 범위를 나누지 않는다.
- [ ] 프론트 mock 작업과 서버 구현은 확정 계약으로 병렬 진행하되 실제 연결은 서버 완료 후 확인한다. 저장 후 메인 목록 반영은 새 GET 기준이며 실시간 갱신/캐싱/전역 상태를 추가하지 않는다.
- [ ] 미저장 입력 보호/저장 후 이동은 프론트 담당으로 인계한다. 백엔드는 서버 초안/임시저장/자동 저장 기능을 구현하지 않는다.
- [ ] 배포 작업에서 `https://tech.eoehd1ek.com` → `https://techapi.eoehd1ek.com`의 HTTPS/CORS 및 nginx 전체 본문 제한/413, Cloudflare/nginx 동기 평가 요청 제한을 확인한다. nginx 우회 Tomcat 접근은 차단하고 413 오류의 브라우저 CORS도 별도로 검증한다. 관측 가능성과 CI/CD 구성은 이 기능 작업에서 구현하지 않는다.

완료 기준: 관리자 저장 결과는 공개 API에 반영되고 테스트 결과는 DB에 남지 않으며, 저장/테스트가 같은 평가 규칙을 사용한다. mock/실제 DB/브라우저/실모델/운영 검증 결과를 구분해 기록한다. 프론트 작업은 이번 명세 정리에서 시작하지 않았다.

## MVP 이후 관리자 인증 계획

- [ ] Spring Security + 아이디·비밀번호로 관리자 인증/인가를 구현한다. 이번 MVP 선행 작업으로 수행하거나 미리 의존성을 추가하지 않는다.
- [ ] 계정 초기 등록/비밀번호 해시/로그인·로그아웃/세션·CSRF/CORS/401·403 API 계약을 후속 인증 작업에서 확정하고 문서를 갱신한다. 프론트 번들에 비밀키를 넣지 않는다.
- [ ] 관리자 조회/쓰기/유료 preview의 서버 권한 검증과 인증 없는 접근 거부를 테스트한다. 관리자 기능을 보호 없이 인터넷에 공개하지 않도록 배포 범위를 검토한다.

## 배포 및 후속 확인 사항

`docs/API.md` 9절과 10.7~10.8을 참조한다. 관리자 입력 길이/개수와 인증 생략/후속 아이디·비밀번호 인증 방향은 확정했다. 전체 본문은 nginx에서 제한하고 초과 시 413이며, 상한 수치(256 KiB 권장안)는 배포 작업에서 확정한다. JSON 크기 제한을 위해 Tomcat maxPostSize/maxSwallowSize/multipart 설정이나 커스텀 Filter를 추가하지 않는다. 익명 기록 보관 정책은 별도 결정한다. 운영 주소는 프론트 `https://tech.eoehd1ek.com`, 백엔드 `https://techapi.eoehd1ek.com`이며 배포 연결은 미검증이다. CODEX_LB/gpt-6-sol, 기존 모델/endpoint 환경변수, Spring AI 기본 timeout/retry, 네이티브 Schema, 실제 호출 검증 보류와 프론트 180초 대기 종료 방향은 유지한다.
