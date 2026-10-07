# 프로젝트 목적

혼자 기술 면접을 준비하는 사용자를 위한 1일 1 기술면접 학습 플랫폼이다.

사용자는 기술 질문에 주관식으로 답변하고,
LLM이 사전에 정의된 평가 기준을 바탕으로 답변을 분석한다.

평가 결과로 다음 정보를 사용자에게 제공한다.

- 잘 설명한 부분
- 부족하거나 잘못 설명한 부분
- 개선할 부분
- 평가 점수

현재 IT 경진대회 제출을 위한 MVP를 개발하고 있다.
기능 범위 확대보다 핵심 사용자 흐름의 완성도와 안정성을 우선한다.

# Core User Flow

핵심 사용자 흐름은 다음과 같다.

질문 조회
→ 답변 작성
→ 답변 제출
→ LLM 평가
→ 점수 및 피드백 표시

이 흐름의 구현과 안정화를 다른 부가 기능보다 우선한다.

핵심 흐름이 완성되기 전에는 다음 기능을 임의로 추가하지 않는다.

- 복잡한 인증/인가
- 불필요한 캐싱
- 새로운 상태관리 라이브러리
- 디자인 시스템 구축
- RAG / Embedding / Vector Store
- 불필요한 성능 최적화

# MVP Scope

## 사용자

- 기술 질문 목록 조회
- 기술 질문 상세 조회
- 주관식 답변 제출
- LLM 기반 답변 평가
- 평가 결과 조회
- 로그인 사용자의 학습 기록 조회

## 관리자

- 기술 질문 관리
- 평가 기준 관리
- 사용자 평가 기록 조회

인증/인가는 핵심 기능 구현 이후 Spring Security를 사용하여 구현한다.

# Architecture

프론트엔드와 백엔드는 별도의 repository로 개발한다.

## Backend

- Java 25
- Kotlin
- Spring Boot 4
- Spring AI 2
- Spring Data JPA
- PostgreSQL

LLM 요청은 Spring AI를 통해 처리한다.
현재 비용 절감을 위한 연동 대상은 CODEX_LB의 `gpt-6-sol`이며 추론 기능은 사용하지 않는 방향이다. 모델/엔드포인트/키는 기존 `CHAT_MODEL`, `CHAT_BASE_URL`, `CHAT_API_KEY` 환경설정으로 관리하고 에이전트가 임의로 변경하지 않는다. OpenRouter는 향후 장애 대응 경로로 검토하며 현재 자동 fallback이 구현되어 있다는 의미는 아니다.

# 개발 원칙

- 경진대회 MVP이므로 확장성보다 구현 완성도를 우선한다.
- 한 번에 하나의 기능만 구현한다.
- 기존 프로젝트 구조와 코딩 스타일을 최대한 유지한다.
- 성능보다 코드 가독성과 유지보수성을 우선한다.
- 과도한 추상화를 만들지 않는다.
- 현재 요구사항에 필요하지 않은 기능을 미리 구현하지 않는다.
- 요청하지 않은 대규모 리팩터링을 하지 않는다.

핵심 사용자 흐름이 완성되기 전에는 다음 기능을 임의로 추가하지 않는다.

- RAG
- Embedding
- Vector Store
- Redis
- 복잡한 캐싱
- 새로운 상태 관리 라이브러리
- 별도의 디자인 시스템
- Microservice 구조
- 불필요한 성능 최적화

# 의존성 규칙

- 새로운 라이브러리와 dependency는 반드시 필요한 경우에만 추가한다.
- 새로운 dependency를 추가하기 전에 기존 기술 스택으로 해결 가능한지 확인한다.
- dependency가 필요한 경우 추가 이유를 먼저 설명한다.
- 기존 dependency의 버전을 임의로 변경하지 않는다.
- 사용하지 않는 dependency를 추가하지 않는다.

# Backend Rules

- Controller / Service / Repository 계층을 기본 구조로 사용한다.
- Controller에 비즈니스 로직을 작성하지 않는다.
- JPA Entity를 API request 또는 response로 직접 사용하지 않는다.
- API에는 Request / Response DTO를 사용한다.
- 비즈니스 예외는 역할에 따라 `ApplicationException`(조회/유스케이스/외부 의존성 실패) 또는 `DomainException`(도메인 규칙 위반)을 상속한다. 두 계층의 공통 부모는 `BusinessException : RuntimeException`이다. 예외에는 `ErrorType`, 오류 코드, 안전한 공개 메시지만 정의하고 HTTP 상태나 Spring HTTP 타입을 참조하지 않는다. `GlobalExceptionHandler`가 ErrorType을 HTTP 상태로 매핑하고 `code`, `message` 응답으로 변환한다. 내부 정보나 원본 DB/LLM 예외 메시지를 공개 메시지에 넣지 않는다.
- 공통 예외 처리는 Controller 선택 조건이 없는 `@RestControllerAdvice`의 `GlobalExceptionHandler`에서 수행한다. `assignableTypes` 등으로 특정 Controller에 한정하거나 기능별로 같은 예외 처리를 중복하지 않는다. 입력 타입/범위 검증 오류는 `400 INVALID_REQUEST`, 반환값 검증 오류는 서버 오류로 구분한다. 아직 필요한 범위만 처리하며 알 수 없는 예외/DB/LLM 오류의 전체 처리는 해당 기능 작업에서 확장한다.
- 예외 핸들러는 `ResponseEntity<ErrorResponse>`를 반환하고 메서드 본문에서 HTTP 상태를 지정한다. `@ResponseStatus`와 혼용하지 않는다. 동일한 오류 본문은 공통으로 분리하여 재사용하고, 다른 예외 핸들러 메서드를 응답 생성 용도로 호출하지 않는다. 오류 본문은 API 계약의 `code`, `message` 두 필드를 유지한다.
- Spring Data JPA를 기본 데이터 접근 방식으로 사용한다.
- native query는 명확한 이유가 있는 경우에만 사용한다.
- 일반 문자열 등 기본 타입은 Kotlin/JPA 기본 매핑을 사용하고 `@Column(columnDefinition = ...)`으로 DB 타입을 고정하지 않는다. `jsonb`처럼 기능상 특정 DB 타입이 꼭 필요한 경우에만 이유를 확인한 뒤 사용한다. 실제 컬럼 타입은 Flyway에서 관리한다.
- Flyway 파일명은 항상 `V<major>.<minor>.<patch>__<description>.sql` 형식으로 세 버전 요소를 명시한다. 예: `V1.0.0__create_question_and_evaluation_criterion.sql`, `V1.0.1__seed_initial_questions.sql`. 이미 적용된 마이그레이션의 내용/이름을 임의로 변경하거나 운영 DB의 이력을 자동 수정하지 않는다.
- `@Column(updatable = false)`는 사용하지 않는다. 필드의 변경 정책은 필요한 애플리케이션 로직에서 관리하며 JPA 매핑으로 업데이트를 막지 않는다.
- 감사 정보인 생성 시각은 JPA Auditing(`@EnableJpaAuditing`, `AuditingEntityListener`, `@CreatedDate`)으로 관리한다. 평가 기록의 `createdAt: Instant?`는 최초 영속화 시 설정한다. 직접 `Instant.now()`를 호출하는 Entity 콜백이나 DB 기본값/트리거로 중복 관리하지 않는다. 생성 전에는 null일 수 있지만 저장된 기록에서는 필수 값이다.
- `createdAt`은 감사 정보이며 제출/평가 시각이나 만료 등 도메인 규칙에 사용하지 않는다. 시간 기반 규칙이 필요해지면 `submittedAt`, `evaluatedAt`처럼 사건을 표현하는 별도 필드를 해당 기능에서 추가한다. 현재는 공통 BaseEntity, 수정 시각, 사용자 감사 정보 및 기존 질문 모델의 감사 필드를 추가하지 않는다.
- CORS는 config/SecurityConfig의 CorsConfigurationSource에서 /api/**에 적용하고 MVC WebConfig/@CrossOrigin을 중복 추가하지 않는다. 기존 app.cors.allowed-origins/CORS_ALLOWED_ORIGINS의 명시적 Origin만 허용하며 기본 목록은 비어 있다. GET/POST/PUT/OPTIONS, Content-Type/X-CSRF-TOKEN, Location 공개, credentials=true다.
- Security는 공개 API 네 개와 인증 API의 필요한 메서드만 허용하고 /api/admin/**는 ADMIN 역할, 나머지는 기본 거부한다. 공개 답변 제출 POST만 CSRF 예외이며 로그인/로그아웃/관리자 쓰기는 세션 CSRF를 요구한다. 로그인은 JSON/서버 메모리 세션, 초기 관리자는 환경변수로 최초 생성/BCrypt 저장하고 기존 ADMIN의 비밀번호는 덮어쓰지 않는다. API 계약의 401/403 JSON을 유지하며 formLogin/httpBasic/remember-me는 추가하지 않는다.
- 메트릭 단계는 Actuator/Prometheus Registry로 관리 포트9091의 health/prometheus GET만 내부 수집에 허용한다. ManagementSecurityConfig는 실제 수신 포트를 검사하며 공개 API 포트의 /actuator를 허용하지 않는다. 관리 포트는 host publish/공개 nginx에 연결하지 않는다. metrics/tag에는 비밀값/답변/ID/원문 URI를 넣지 않는다. 기존 개발 DB Compose와 별도의 compose.metrics.yml을 사용하고 Loki/Tempo는 후속 단계다.
- 개발 Origin은 http://localhost:5173, 운영은 환경변수로 교체하고 재시작한다. Origin에 경로/끝의 슬래시를 넣지 않고 CORS를 인증 대신 사용하지 않는다. 세션 쿠키는 HttpOnly/SameSite=Lax/Domain 미지정이며 운영 HTTPS에서 SESSION_COOKIE_SECURE=true를 설정한다. 관리자 ID/비밀번호/해시/CSRF/세션을 로그·응답에 노출하지 않는다. 프록시 CORS 중복과 직접 원본 접근을 별도로 확인한다.

# Backend Tests

- 테스트는 JUnit 5의 `@Test`와 AssertJ의 `assertThat(actual).isEqualTo(expected)` 등 fluent assertion으로 읽기 쉽게 작성한다. 값 비교에는 `kotlin.test.assertEquals`나 JUnit assertion 대신 AssertJ를 사용한다. 컬렉션은 `containsExactly`, `hasSize`, `isEmpty`, `allSatisfy` 등으로 의도를 드러낸다.
- 테스트 메서드 이름은 Kotlin 백틱을 사용한 한글 문장으로 작성하고 검증할 동작과 기대 결과를 표현한다. 예: `질문 ID로 해당 질문의 평가 기준만 조회한다`.
- 각 테스트 메서드는 `// given`, `// when`, `// then` 주석 절을 순서대로 포함한다. `given`에서는 입력과 필요한 데이터를 준비하고, `when`에서는 검증 대상 메서드를 호출하며, `then`에서는 기대 결과를 AssertJ로 검증한다.
- Mockito mock의 동작 설정은 `org.mockito.BDDMockito.given`을 사용한다. 예: `given(questionRepository.findAll()).willReturn(questions)`. `Mockito.when`과 `thenReturn` 대신 BDD 방식으로 작성하고 mock 설정은 `// given` 절에 둔다.
- Service 테스트는 `@ExtendWith(MockitoExtension::class)` 기반 단위 테스트로 작성한다. Repository 등 협력 객체는 `@Mock`으로 대체하고 실제 Service를 검증한다. Spring 컨텍스트나 실제 DB는 사용하지 않는다.
- Controller 테스트는 `@WebMvcTest(대상Controller::class)`와 MockMvc를 사용하고 Service는 `@MockitoBean`으로 대체한다. URL 매핑, HTTP 상태, 콘텐츠 타입과 JSON 응답 계약을 검증하며 실제 서버 포트나 네트워크 요청은 사용하지 않는다. `@MockMvc` 어노테이션은 없으며 MockMvc는 주입받는 테스트 도구다. 계층 검증만으로 충분한 작업에는 `@SpringBootTest` 기반 전체 통합 테스트를 추가하지 않는다.
- Repository 테스트는 각 메서드의 `given`에서 필요한 Entity를 직접 생성하고 Repository의 `save` 또는 `saveAll`로 저장한다. `when`에서 검증 대상 Repository 메서드를 호출하고 `then`에서 저장용 Entity와 조회 Entity의 필드 값 또는 반환 목록을 비교한다. Entity의 참조 동일성에 의존하는 `isSameAs`나 기본 `equals` 비교 대신 필드 assertion 또는 AssertJ `usingRecursiveComparison`을 사용한다. 다른 테스트의 데이터나 실행 순서, Flyway 초기 데이터의 값/고정 ID/전체 개수에 의존하지 않는다. 생성된 ID로 자신의 데이터를 조회하고 기본 트랜잭션 롤백으로 격리한다.
- Repository 테스트에는 검증에 필요한 Repository만 주입한다. EntityManager, TestEntityManager, Flyway, JdbcTemplate, SessionFactory 등 인프라 객체를 주입하거나 사용하지 않으며 명시적 `flush`, `clear`, `saveAndFlush`를 추가하지 않는다. 같은 영속성 컨텍스트에서 조회한 Entity는 캐시된 객체일 수 있으므로 이 테스트를 별도 DB 재로딩이나 commit 후 영속화 검증으로 설명하지 않는다.
- Repository 테스트는 실제 사용하는 저장/조회 메서드의 반환 값, 질문별 필터링, 정렬, 빈 결과 등 기능 동작을 검증한다. Flyway 버전/파일명, 초기 데이터 내용, 시스템 카탈로그의 제약/인덱스, SQL 횟수 검증은 추가하지 않는다. 향후 인프라 검증이 필요하더라도 사용자 요청 없이 이러한 코드나 의존을 다시 추가하지 않는다.
- 실제 PostgreSQL이 필요한 Repository 테스트는 기본적으로 `@DataJpaTest`만 사용한다. `src/test/resources/application.yaml`의 `jdbc:tc:postgresql:18.6-alpine` 설정으로 Testcontainers가 DB를 시작하며, 개발/운영 DB나 수동으로 띄운 DB에 연결하지 않는다. 실행 환경에는 Docker 호환 런타임이 필요하다.
- JPA Auditing을 사용하는 Entity의 Repository 테스트에는 `@Import(JpaAuditingConfig::class)`를 함께 사용하여 제한된 JPA 테스트 컨텍스트에서도 감사 기능을 활성화한다. Repository만 주입하는 규칙은 유지하며 감사 설정 import를 이유로 인프라 객체나 전체 SpringBootTest를 추가하지 않는다.
- 테스트 설정 파일이 `spring.profiles.active=test`를 선언하므로 테스트 코드에 `@ActiveProfiles`를 추가하지 않는다. 현재 구성에서는 DB 대체 방지와 Flyway를 위한 `@AutoConfigureTestDatabase` 및 `@ImportAutoConfiguration(FlywayAutoConfiguration::class)`도 추가하지 않는다.
- JPA 테스트 쓰기는 기본 트랜잭션 롤백을 유지한다. Flyway 마이그레이션과 초기 데이터는 테스트 환경의 자동 설정으로 적용하고 `ddl-auto: validate`로 Entity 매핑을 확인한다. 테스트 코드에서 이를 직접 제어하거나 검증하지 않는다. Hibernate 통계 수집과 `generate_statistics` 설정은 추가하지 않는다.

# AI Evaluation

- 기술 질문과 평가 기준은 데이터베이스에서 조회한다.
- 질문 ID로 필요한 평가 기준을 직접 조회할 수 있으므로 현재 MVP에서는 RAG를 사용하지 않는다.
- LLM 응답은 가능한 한 Structured Output으로 받는다.
- LLM에게 자유롭게 최종 점수를 결정하도록 하지 않는다.
- 평가 항목에 대한 LLM의 판단과 실제 점수 계산 로직을 가능한 한 분리한다.
- LLM 응답은 신뢰할 수 없는 외부 입력으로 취급하고 파싱 실패 및 잘못된 응답을 처리한다.
- LLM 평가는 Spring AI 2.0.1 기본 auto-configuration의 OpenAiChatModel을 직접 주입하여 동기 호출한다. timeout/최대 재시도는 라이브러리 기본값(2.0.1: timeout 60초, SDK 추가 재시도 최대 3회)을 사용하며 코드/YAML로 덮어쓰지 않는다. Service는 모델을 한 번 호출하고 반환값을 검증하며 별도 재시도 루프/속성/시간 계산/커스텀 HTTP/내부 자동 설정 API는 추가하지 않는다. 호출 실패나 응답 검증 실패는 안전한 `502 LLM_EVALUATION_FAILED`로 처리한다.
- 직접 OpenAiChatModel.call()과 네이티브 Schema만으로 JSON 불일치 자동 교정 재시도는 실행되지 않는다. 별도 StructuredOutputValidationAdvisor는 opt-in이며 현재 사용하지 않는다. SDK 기본 재시도는 전송/상태 오류에 대한 것이고 로컬 ID/점수/피드백 검증 실패는 즉시 종료한다. 테스트는 모델/클라이언트 결과를 mock하여 서비스·응답 계약을 확인하며 라이브러리 재시도/timeout 자체를 재구현하거나 테스트하지 않는다.
- 프론트는 제출 후 180초에 응답이 없으면 대기를 종료하고 답변을 유지한 채 수동 재시도를 안내한다. 이는 클라이언트 UX 정책이고 서버 실패/취소/180초 종료를 보장하지 않는다. 서버의 SDK 재시도는 180초를 넘길 수 있으며 재제출은 중복 처리/기록/비용 위험이 있다. POST 자동 재전송은 하지 않고 후속 연결 작업에서 구현/검증한다. 추후 SSE는 별도 작업이다. `chat=none` 전체 컨텍스트 테스트는 `@MockitoBean OpenAiChatModel`로 대체하고 운영 호환 레이어를 만들지 않는다.
- 출력 요청은 네이티브 `json_schema`를 사용한다. 공급자의 실제 지원은 미검증으로 기록하고 미지원 오류에서 프롬프트 기반 JSON이나 다른 공급자로 자동 fallback하지 않는다. 서버의 응답 타입/점수/피드백 검증은 유지한다.
- 이번 LLM 연동 작업과 자동 테스트에서는 실제 LLM을 호출하지 않는다. 호출부를 mock 또는 로컬 대체로 검증하며, CODEX_LB의 Structured Output 호환성과 Spring AI 실제 연동 검증은 후속 TODO로 남긴다. 실제 호출은 추후 별도 승인과 비용 확인 후 수행한다.

# API Contract

- 프론트엔드와 백엔드 사이의 API 형식은 백엔드 레포지토리의 docs/API.md를 기준으로 한다.
- API 구현을 변경하면서 request 또는 response 형식이 변경되는 경우 docs/API.md도 함께 수정한다.
- AI는 기존 API contract를 임의로 변경하지 않는다.

# AI Coding Rules

작업을 시작하기 전에:

1. 관련 파일과 기존 구현을 먼저 확인한다.
2. 구현하려는 기능의 현재 구조를 파악한다.
3. 변경하거나 생성할 파일을 확인한다.
4. 기존 구조와 충돌하지 않는지 확인한다.

구현할 때:

1. 요청받은 기능의 범위만 수정한다.
2. 관련 없는 파일을 수정하지 않는다.
3. 기존 동작을 불필요하게 변경하지 않는다.
4. 과도한 abstraction이나 미래 확장용 코드를 추가하지 않는다.
5. 확실하지 않은 요구사항을 임의로 확대 해석하지 않는다.

작업 후:

1. 빌드 오류를 확인한다.
2. 가능한 경우 테스트와 lint를 실행한다.
3. 변경된 파일과 구현 내용을 요약한다.
4. 불필요하게 변경된 코드가 없는지 확인한다.
5. 남아 있는 문제나 검증하지 못한 부분이 있다면 명확하게 설명한다.
6. 빌드 또는 테스트 실패를 숨기거나 임의로 무시하지 않는다.

# Documentation

- AGENTS.md: AI coding agent가 따라야 하는 프로젝트 규칙
- TODO.md: 구현해야 할 작업과 우선순위
- docs/API.md: 백엔드 레포지토리의 API contract

불필요한 문서 수정을 하지 않는다.
