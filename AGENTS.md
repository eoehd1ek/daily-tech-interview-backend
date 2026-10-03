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
LLM 제공자는 OpenRouter를 사용한다.

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
- Spring Data JPA를 기본 데이터 접근 방식으로 사용한다.
- native query는 명확한 이유가 있는 경우에만 사용한다.
- 일반 문자열 등 기본 타입은 Kotlin/JPA 기본 매핑을 사용하고 `@Column(columnDefinition = ...)`으로 DB 타입을 고정하지 않는다. `jsonb`처럼 기능상 특정 DB 타입이 꼭 필요한 경우에만 이유를 확인한 뒤 사용한다. 실제 컬럼 타입은 Flyway에서 관리한다.
- Flyway 파일명은 항상 `V<major>.<minor>.<patch>__<description>.sql` 형식으로 세 버전 요소를 명시한다. 예: `V1.0.0__create_question_and_evaluation_criterion.sql`, `V1.0.1__seed_initial_questions.sql`. 이미 적용된 마이그레이션의 내용/이름을 임의로 변경하거나 운영 DB의 이력을 자동 수정하지 않는다.

# Backend Tests

- 테스트는 JUnit 5의 `@Test`와 AssertJ의 `assertThat(actual).isEqualTo(expected)` 등 fluent assertion으로 읽기 쉽게 작성한다. 값 비교에는 `kotlin.test.assertEquals`나 JUnit assertion 대신 AssertJ를 사용한다. 컬렉션은 `containsExactly`, `hasSize`, `isEmpty`, `allSatisfy` 등으로 의도를 드러낸다.
- 테스트 메서드 이름은 Kotlin 백틱을 사용한 한글 문장으로 작성하고 검증할 동작과 기대 결과를 표현한다. 예: `질문 ID로 해당 질문의 평가 기준만 조회한다`.
- 각 테스트 메서드는 `// given`, `// when`, `// then` 주석 절을 순서대로 포함한다. `given`에서는 입력과 필요한 데이터를 준비하고, `when`에서는 검증 대상 메서드를 호출하며, `then`에서는 기대 결과를 AssertJ로 검증한다.
- Repository 테스트는 각 메서드의 `given`에서 필요한 Entity를 직접 생성하고 Repository의 `save` 또는 `saveAll`로 저장한다. `when`에서 검증 대상 Repository 메서드를 호출하고 `then`에서 저장용 Entity와 조회 Entity의 필드 값 또는 반환 목록을 비교한다. Entity의 참조 동일성에 의존하는 `isSameAs`나 기본 `equals` 비교 대신 필드 assertion 또는 AssertJ `usingRecursiveComparison`을 사용한다. 다른 테스트의 데이터나 실행 순서, Flyway 초기 데이터의 값/고정 ID/전체 개수에 의존하지 않는다. 생성된 ID로 자신의 데이터를 조회하고 기본 트랜잭션 롤백으로 격리한다.
- Repository 테스트에는 검증에 필요한 Repository만 주입한다. EntityManager, TestEntityManager, Flyway, JdbcTemplate, SessionFactory 등 인프라 객체를 주입하거나 사용하지 않으며 명시적 `flush`, `clear`, `saveAndFlush`를 추가하지 않는다. 같은 영속성 컨텍스트에서 조회한 Entity는 캐시된 객체일 수 있으므로 이 테스트를 별도 DB 재로딩이나 commit 후 영속화 검증으로 설명하지 않는다.
- Repository 테스트는 실제 사용하는 저장/조회 메서드의 반환 값, 질문별 필터링, 정렬, 빈 결과 등 기능 동작을 검증한다. Flyway 버전/파일명, 초기 데이터 내용, 시스템 카탈로그의 제약/인덱스, SQL 횟수 검증은 추가하지 않는다. 향후 인프라 검증이 필요하더라도 사용자 요청 없이 이러한 코드나 의존을 다시 추가하지 않는다.
- 실제 PostgreSQL이 필요한 Repository 테스트는 기본적으로 `@DataJpaTest`만 사용한다. `src/test/resources/application.yaml`의 `jdbc:tc:postgresql:18.6-alpine` 설정으로 Testcontainers가 DB를 시작하며, 개발/운영 DB나 수동으로 띄운 DB에 연결하지 않는다. 실행 환경에는 Docker 호환 런타임이 필요하다.
- 테스트 설정 파일이 `spring.profiles.active=test`를 선언하므로 테스트 코드에 `@ActiveProfiles`를 추가하지 않는다. 현재 구성에서는 DB 대체 방지와 Flyway를 위한 `@AutoConfigureTestDatabase` 및 `@ImportAutoConfiguration(FlywayAutoConfiguration::class)`도 추가하지 않는다.
- JPA 테스트 쓰기는 기본 트랜잭션 롤백을 유지한다. Flyway 마이그레이션과 초기 데이터는 테스트 환경의 자동 설정으로 적용하고 `ddl-auto: validate`로 Entity 매핑을 확인한다. 테스트 코드에서 이를 직접 제어하거나 검증하지 않는다. Hibernate 통계 수집과 `generate_statistics` 설정은 추가하지 않는다.

# AI Evaluation

- 기술 질문과 평가 기준은 데이터베이스에서 조회한다.
- 질문 ID로 필요한 평가 기준을 직접 조회할 수 있으므로 현재 MVP에서는 RAG를 사용하지 않는다.
- LLM 응답은 가능한 한 Structured Output으로 받는다.
- LLM에게 자유롭게 최종 점수를 결정하도록 하지 않는다.
- 평가 항목에 대한 LLM의 판단과 실제 점수 계산 로직을 가능한 한 분리한다.
- LLM 응답은 신뢰할 수 없는 외부 입력으로 취급하고 파싱 실패 및 잘못된 응답을 처리한다.

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
