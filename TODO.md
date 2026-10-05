# Backend MVP TODO

## 작업 기준

목표는 질문 조회부터 답변 제출, LLM 평가, 저장된 결과 조회까지의 Core User Flow 완성이다. 아래 체크박스는 구현과 검증을 마친 항목만 완료로 표시한다.

- 작업 전에 `AGENTS.md`와 `docs/API.md`, 관련 기존 코드를 읽는다.
- 이번 MVP의 명시적 범위를 우선한다. `AGENTS.md`에 있는 로그인 사용자 기록, 관리자 기능, 인증/인가는 아래 작업에 포함하지 않는다.
- 기존 Kotlin, Java 25, Spring Boot 4, Spring AI 2, Spring Data JPA, PostgreSQL, Flyway 구성을 활용한다. 의존성이나 버전을 임의로 변경하지 않는다.
- Controller / Service / Repository를 기본으로 사용하고 API에 Entity를 직접 노출하지 않는다.
- 비즈니스 예외는 `BusinessException : RuntimeException`을 상속하고 HTTP 상태/오류 코드/안전한 사용자 메시지를 정의한다. 선택 조건 없는 `@RestControllerAdvice`의 `GlobalExceptionHandler`에서 공통 처리하며 특정 Controller 전용 Advice는 사용하지 않는다. 알 수 없는 예외/DB/LLM 오류의 전체 처리는 8단계에서 완성한다.
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
- 저장된 질문/평가 기준은 정상이라고 가정한다. 평가 기준 개수와 배점 검증은 향후 관리자 페이지의 질문 생성 처리에서만 수행하며, 질문 조회나 LLM 호출 전에는 재검증하지 않는다. 개발자의 직접 DB 입력과 초기 데이터 마이그레이션도 같은 규칙을 지키는 것을 전제로 한다.
- 아래 각 번호를 한 번의 AI 작업 또는 작은 PR의 기본 단위로 삼는다. 기능 구현과 해당 기능의 기본 테스트를 같은 작업에서 수행한다.
- 구현하면서 계약 변경이 필요하면 임의로 변경하지 않고 먼저 확인한다. 승인된 변경은 `docs/API.md`와 함께 반영한다.
- 인증/인가, Redis, 캐싱, RAG, Embedding, Vector Store, 메시지 큐, Microservice, 불필요한 성능 최적화는 추가하지 않는다.
- 프론트엔드 화면 구현은 별도 저장소의 작업이다. 여기에는 백엔드 구현과 연결 검증에 필요한 항목만 둔다.

현재 Question/EvaluationCriterion/EvaluationAttempt 모델과 Repository, 마이그레이션, JPA Auditing 및 PostgreSQL 테스트가 구현되어 있다. 질문 목록/상세, 답변 제출·평가·완료 기록 저장, 저장 결과 조회와 공통 `400`/`404`/`502`/`500` 처리가 구현되어 있다. 외부 LLM을 대체한 실제 HTTP/commit 후 재조회도 검증했다. 실제 브라우저와 유료 공급자 연결은 미검증이다. 스키마는 Flyway로 관리하고 테스트에서는 `validate`로 매핑을 확인한다.

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
- [ ] 배포 전 후속 작업: 전체 JSON 본문 바이트 상한과 거부 상태/계약 결정 및 적용. 답변 길이 검증은 대용량 HTTP 본문 수신 제한이 아님.
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

## 구현 전 결정 필요

`docs/API.md`의 미확정 사항을 참조한다. 답변 최대 길이는 3,000 UTF-16 코드 단위로 승인되었다. 전체 JSON 본문 크기 제한, 운영 Origin, 기록 보관 정책은 별도 결정하며 임의로 확정하지 않는다. 현재 CODEX_LB/gpt-6-sol, Spring AI 기본 timeout/retry, 네이티브 Schema, 실제 호출 검증 보류와 프론트 180초 대기 종료 방향은 확정했다. 모델/endpoint 환경변수는 변경하지 않는다. 개발 Origin/CORS와 초기 데이터는 해당 단계에 기재했으며 범위 밖 기능을 추가하지 않는다.
