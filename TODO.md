# Backend MVP TODO

## 작업 기준

목표는 질문 조회부터 답변 제출, LLM 평가, 저장된 결과 조회까지의 Core User Flow 완성이다. 아래 체크박스는 구현과 검증을 마친 항목만 완료로 표시한다.

- 작업 전에 `AGENTS.md`와 `docs/API.md`, 관련 기존 코드를 읽는다.
- 이번 MVP의 명시적 범위를 우선한다. `AGENTS.md`에 있는 로그인 사용자 기록, 관리자 기능, 인증/인가는 아래 작업에 포함하지 않는다.
- 기존 Kotlin, Java 25, Spring Boot 4, Spring AI 2, Spring Data JPA, PostgreSQL, Flyway 구성을 활용한다. 의존성이나 버전을 임의로 변경하지 않는다.
- Controller / Service / Repository를 기본으로 사용하고 API에 Entity를 직접 노출하지 않는다.
- 기본 타입은 Kotlin/JPA 기본 매핑을 사용하며, 일반 문자열에 `columnDefinition`을 지정하지 않는다. `jsonb`처럼 특정 DB 타입이 꼭 필요한 경우에만 사용 이유를 확인한다. 실제 DB 타입은 Flyway에서 관리한다.
- Flyway 파일명에는 항상 세 버전 요소를 명시한다: `V<major>.<minor>.<patch>__<description>.sql`. 스키마는 `V1.0.0`, 같은 주 버전의 초기 데이터는 `V1.0.1`로 작성한다. 이미 적용된 DB 이력을 자동 수정하지 않는다.
- 테스트는 JUnit 5와 AssertJ의 `assertThat(actual).isEqualTo(expected)` 등 fluent assertion을 사용한다. 실제 DB Repository 테스트는 기존 Testcontainers JDBC 설정과 `@DataJpaTest`를 활용한다. 테스트 설정 파일에서 `test` 프로필을 활성화하므로 `@ActiveProfiles`와 불필요한 DB/Flyway 자동 설정 어노테이션을 추가하지 않는다.
- 테스트 메서드 이름은 검증할 동작과 기대 결과가 드러나는 한글 문장으로 작성한다. 모든 테스트 메서드에 `// given`, `// when`, `// then`을 순서대로 포함하여 데이터 준비, 검증 대상 호출, AssertJ 결과 검증을 구분한다.
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

현재 Question/EvaluationCriterion 모델, Repository, 스키마/초기 데이터 마이그레이션과 PostgreSQL 테스트가 구현되어 있다. API는 아직 구현하지 않았다. 기존 Flyway 의존성을 사용해 스키마를 관리하며, 테스트에서는 `validate`로 마이그레이션과 Entity 매핑의 일치를 확인한다.

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

- [ ] `GET /api/questions` Controller / Service와 목록 Response DTO 구현.
- [ ] 전체 목록에 `id`, `title`만 반환하고 데이터가 없으면 `[]` 반환.
- [ ] 평가 기준/본문 비노출, 성공 응답 타입, 빈 목록 테스트.

완료 기준: 준비한 PostgreSQL 질문 데이터가 `200 OK` JSON 배열로 조회된다. 가능한 경우 프론트엔드와 연결해 DB → Spring → HTTP → 화면까지의 첫 연결을 검증한다. 질문 목록의 페이지네이션이나 정렬 기능은 추가하지 않는다. 평가 기준의 `displayOrder` 정렬과는 별개다.

## 3. 질문 상세 조회

- [ ] `GET /api/questions/{questionId}`와 상세 Response DTO 구현.
- [ ] `id`, `title`, `content`만 반환하고 평가 기준/배점은 비공개 유지.
- [ ] 잘못된 ID에 `400 INVALID_REQUEST`, 없는 질문에 `404 QUESTION_NOT_FOUND` 반환.
- [ ] 성공, 잘못된 ID, 없는 질문, 비공개 필드 미노출 테스트.

완료 기준: 선택한 질문의 본문을 조회할 수 있고 API 계약의 오류 형식과 일치한다. 초기 오류 변환은 필요한 범위만 구현하고 8단계에서 공통 처리를 완성한다.

## 4. 평가 기록 모델

- [ ] EvaluationAttempt의 `questionId: Long` 참조 필드, Repository 및 Flyway 마이그레이션 구현. Question Entity 매핑과 DB 외래 키 제약 조건은 사용하지 않음.
- [ ] 제출 답변 원문, 총점, 판정, 세 종류 피드백, 서버 생성 시각 저장.
- [ ] User 관계 없이 완료된 평가만 저장하고 같은 질문의 복수 평가 허용.
- [ ] 저장 후 재조회, 질문 ID 참조와 QuestionRepository 별도 조회, 생성 시각 및 필수 값 영속화 테스트.

완료 기준: 평가 결과를 저장하고 ID로 다시 읽을 수 있다. 항목별 점수 이력, 진행 상태, 실패 작업 테이블은 필수 모델로 추가하지 않는다.

## 5. LLM 연동 및 평가

- [ ] 기존 Spring AI와 OpenRouter 설정을 확인하고 사용할 모델/키 결정. 실제 키는 저장소에 커밋하지 않음.
- [ ] DB의 질문/평가 기준과 사용자 답변으로 평가 프롬프트 구성. 답변 속 명령을 평가 규칙으로 취급하지 않음.
- [ ] 저장된 평가 기준은 정상이라고 가정하고 LLM 호출 전에 기준 개수/양의 배점/배점 합계를 재검증하지 않음. LLM 응답 검증은 별도로 유지.
- [ ] `docs/API.md` 내부 LLM 계약의 항목별 점수/피드백 및 종합 피드백을 Structured Output으로 수신/파싱.
- [ ] 항목 누락/중복/알 수 없는 ID, 점수 타입/범위, 필수 피드백 검증.
- [ ] 백엔드에서 점수 합산 및 FAIL/RETRY/PASS 판정 구현. LLM의 총점/판정 값은 사용하지 않음.
- [ ] LLM 호출부를 테스트에서 대체해 정상/잘못된 응답과 판정 경계값 테스트.

완료 기준: 유효한 내부 평가 결과만 생성한다. 점수 0, 49, 50, 79, 80, 100의 판정이 계약과 일치하며 잘못된 점수를 보정하거나 저장하지 않는다. 실제 외부 호출은 모델/키 설정 후 별도 수동 확인하고 자동 테스트는 외부 호출에 의존하지 않는다.

## 6. 답변 제출 및 평가 API

- [ ] 답변 최대 길이/요청 크기 제한을 확인하고 Request DTO 검증 규칙과 계약에 반영.
- [ ] `POST /api/questions/{questionId}/evaluation-attempts` 구현.
- [ ] 요청 검증 → QuestionRepository로 질문 조회 → EvaluationCriterionRepository로 기준 목록 조회 → 평가 → LLM 응답 검증/합산/판정 → 완료 기록 저장을 하나의 동기식 요청으로 연결. 저장 기준의 도메인 규칙은 재검증하지 않음.
- [ ] DB 저장이 완료된 뒤 `201 Created`, `Location` 헤더 및 계약의 평가 결과 DTO 반환.
- [ ] 실패 결과/중간 기록은 저장하지 않고 POST마다 별도 평가를 생성.
- [ ] 성공 및 저장 후 재조회, 같은 질문 재제출, 잘못된 답변/없는 질문/평가 실패/저장 실패 테스트.

완료 기준: 사용자 답변을 제출하면 저장된 결과 ID와 점수/피드백을 얻는다. 누락/null/문자열 외 타입/빈 문자열/공백 답변은 LLM 호출 전에 `400`으로 거부한다. 처리 중 오류가 나면 성공 응답을 반환하지 않는다.

## 7. 평가 결과 조회

- [ ] `GET /api/evaluation-attempts/{attemptId}` 구현.
- [ ] POST와 같은 결과 형식으로 질문 ID/제목, 답변 원문, 점수/판정, 피드백, 생성 시각 반환.
- [ ] 잘못된 ID와 없는 평가 기록의 `400`/`404` 처리.
- [ ] 저장 결과 조회, 오류 응답, LLM 재호출 없음 테스트.

완료 기준: 제출로 생성된 평가 ID를 조회하면 저장된 결과를 복원할 수 있다. 결과 화면 새로고침에 필요한 데이터를 제공하며 평가 기록 목록이나 사용자 기록 기능은 추가하지 않는다.

## 8. LLM 실패 및 공통 예외 처리

- [ ] 재시도 기본 횟수/간격, 개별 호출 및 전체 요청 시간 제한을 확인하고 환경설정으로 반영.
- [ ] 최초 호출 + N회 재시도 규칙 구현. Spring AI/HTTP 클라이언트의 중첩 재시도가 없는지 확인.
- [ ] 호출 오류/타임아웃/파싱 실패/평가 응답 검증 실패를 정해진 횟수만큼 재시도.
- [ ] 재시도 소진 시 `502 LLM_EVALUATION_FAILED` 반환, 잘못된 요청/없는 질문/DB 오류는 LLM 재시도에서 제외. 저장 기준 오류를 탐지하는 별도 검증 흐름은 추가하지 않음.
- [ ] `400`/`404`/`502`/`500` 응답을 계약의 공통 `code`, `message` 형식으로 통일. 내부 정보/키/평가 기준은 응답에 숨김.
- [ ] 재시도 후 성공, 모든 시도 실패, 재시도 0회, 중첩 호출 없음, DB 저장 실패 시 LLM 재호출 없음 테스트.

완료 기준: 실제 호출 횟수가 최대 N+1을 초과하지 않는다. 최종 실패 시 완료된 평가 기록이 남지 않으며 프론트엔드가 안전한 오류 메시지를 표시할 수 있다. 동기식 전체 평가 시간 예산과 프론트엔드/프록시의 타임아웃 설정을 연결 검증 시 확인한다.

## 9. 테스트 및 Core Flow 검증

- [ ] 기존 `./gradlew test` 실행에 필요한 DB/환경설정을 준비하고 빌드와 테스트 결과 확인. 실패하면 원인과 미검증 범위를 기록.
- [ ] PostgreSQL 마이그레이션, 질문/기준 조회, 평가 저장/재조회 통합 테스트.
- [ ] 네 API의 JSON 타입/필수 필드/상태 코드/오류 코드가 `docs/API.md`와 일치하는지 확인.
- [ ] 실제 환경의 유효한 LLM 응답을 수동 확인하고, 제어 가능한 테스트로 타임아웃/잘못된 응답/재시도 실패를 검증.
- [ ] 목록 → 상세 → 답변 제출 → 결과 표시 → 결과 재조회 흐름을 프론트엔드와 연결해 확인.
- [ ] 프론트엔드와 중복 제출 방지, 분석 중 표시, 오류 시 답변 유지, 결과 새로고침 복원, POST 자동 재전송 방지를 확인.
- [ ] 배포/개발 Origin을 확인하고 필요한 경우에만 해당 Origin에 대한 CORS 설정 및 연결 검증.
- [ ] 공개 API에 평가 기준이 노출되지 않는지, 결과 조회에 인증/소유자 보호가 없다는 제한이 공유되었는지 확인.

완료 기준: 정상 경로와 핵심 실패 경로가 검증되고 결과 페이지를 다시 열어도 저장된 평가를 조회할 수 있다. 실제 호출/배포 연결 등 확인하지 못한 항목은 완료로 표시하지 않는다.

## 구현 전 결정 필요

`docs/API.md`의 미확정 사항을 참조한다. 답변 길이 제한, 재시도/시간 예산, 실제 LLM 모델, 프론트엔드 Origin, 익명 기록 보관 정책은 임의로 확정하지 않는다. 초기 질문/기준과 Flyway 입력 방식은 1단계에 기재한 내용으로 확정했다. 관련 작업 전에 필요한 결정만 확인하며, 이를 이유로 새 API나 범위 밖 기능을 추가하지 않는다.
