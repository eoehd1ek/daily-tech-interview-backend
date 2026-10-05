# MVP API Contract

## 1. 목적과 적용 범위

혼자 기술 면접을 준비하는 사용자가 질문을 선택하고 주관식 답변을 제출한 뒤, LLM 평가 점수와 피드백을 확인하는 흐름을 정의한다. 이 문서는 MVP API 계약이다. 질문 목록/상세 조회, 답변 제출·평가·완료 기록 저장, 결과 조회 및 공통 오류 처리가 구현되어 있다. 구현/검증 상태는 `TODO.md`를 참조한다. 외부 LLM을 대체한 실제 HTTP/commit 후 조회는 검증했으며 실제 공급자와 브라우저 연결은 미검증이다.

이번 MVP는 로그인과 사용자 구별 없이 동작한다. 같은 질문에 여러 번 답변할 수 있으며, 성공한 평가 결과를 저장하고 ID로 다시 조회한다.

`AGENTS.md`에 언급된 로그인 사용자 기록, 관리자 기능, 인증/인가는 이번 요청 범위에 포함하지 않는다. 질문/평가 기준 관리 API, 평가 기록 목록 API, 페이지네이션, 일일 제출 제한도 추가하지 않는다. 별도의 비동기 작업, 메시지 큐, 상태 조회 API는 사용하지 않는다.

## 2. 공통 규칙

| 항목 | 계약 |
| --- | --- |
| Base Path | `/api` |
| 요청/응답 형식 | 본문이 있는 요청과 응답은 `application/json` 사용 |
| JSON 필드명 | camelCase |
| ID | 양의 정수 JSON `number`, 범위 1~9,007,199,254,740,991 (JavaScript 안전 정수) |
| 점수 | 정수, 총점은 0~100 |
| 판정 | `"FAIL"`, `"RETRY"`, `"PASS"` 중 하나 |
| 생성 시각 | UTC ISO 8601 문자열. 예: `"2026-10-01T07:30:00Z"` |
| 인증 | 필요 없음 |
| 성공 응답 | 별도 공통 wrapper 없이 JSON 객체 또는 배열 반환 |
| 오류 응답 | 아래의 공통 `code`, `message` 객체 반환 |

성공 응답의 정의된 필드는 필수이며 `null`을 반환하지 않는다. 목록이 비어 있으면 `[]`을 반환한다. GET 요청에는 Request Body가 없다. 현재 정의한 API에는 Query Parameter가 없다.

경로 ID가 양의 정수가 아니거나 지원 범위를 벗어나면 `400 INVALID_REQUEST`, 유효한 ID지만 리소스가 없으면 `404`를 반환한다.

### CORS 및 환경별 Origin

- `/api/**`에는 Spring MVC 공통 CORS 정책을 적용한다. 개발 프론트엔드 Origin은 `http://localhost:5173`이며 `app.cors.allowed-origins` 설정으로 관리한다. 현재 `.env`와 `.env.example`에는 `CORS_ALLOWED_ORIGINS=http://localhost:5173`을 설정했다. 애플리케이션 자체의 기본 허용 목록은 비어 있으므로 미설정 시 교차 Origin을 허용하지 않는다.
- 배포 시 실행 환경변수 `CORS_ALLOWED_ORIGINS=https://frontend.example.com`으로 교체한다. 복수 Origin은 쉼표로 구분한다. 환경변수 변경 후 백엔드를 재시작하면 적용되며 코드 변경/재빌드는 필요하지 않다. Origin에는 경로나 끝의 `/` 없이 스킴/호스트/포트만 지정한다. localhost와 127.0.0.1, 다른 포트는 서로 다른 Origin이다. 와일드카드 허용은 사용하지 않는다.
- 허용 메서드는 GET/POST/OPTIONS, 요청 헤더는 Content-Type, 브라우저에 공개하는 응답 헤더는 Location이다. 쿠키 등의 credentials는 허용하지 않으며 현재 프론트에서는 credentials include를 사용하지 않는다. OPTIONS preflight는 MVC에서 처리하고 별도 Controller는 만들지 않는다. CORS 허용이 미구현 API의 존재를 의미하지는 않는다.
- 허용되지 않은 Origin/메서드/헤더는 MVC의 CORS 처리에서 거부하며 `403`과 CORS 헤더 미노출로 처리된다. 이 거부는 Controller의 API 오류 JSON 계약과 별개이며 브라우저에서는 응답 본문을 읽지 못할 수 있다. 허용 Origin의 정상/처리된 오류 응답에는 CORS 헤더를 제공한다.
- CORS는 브라우저 교차 Origin 정책이며 인증이나 일반 클라이언트의 접근 제한 기능이 아니다. 추후 Spring Security 적용 시 CORS 연동과 preflight 처리를 확인한다. 배포 프록시가 OPTIONS를 막거나 CORS 헤더를 중복 생성하지 않도록 확인한다. 운영 Origin과 실제 브라우저 연결은 배포 환경에서 별도 검증한다.

### 공통 오류 형식

```json
{
  "code": "INVALID_REQUEST",
  "message": "답변은 공백이 아닌 문자열이어야 합니다."
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| code | string | 프론트엔드 분기용 오류 코드 |
| message | string | 사용자에게 표시 가능한 오류 설명 |

예시의 메시지 문구 자체는 고정 계약이 아니다. 프론트엔드는 메시지 문자열 대신 HTTP 상태와 `code`로 분기한다. 내부 예외, 스택 트레이스, API 키, LLM 원문 응답과 비공개 평가 기준은 오류 응답에 노출하지 않는다.

백엔드의 비즈니스 예외는 `BusinessException : RuntimeException`을 상속한다. `QuestionNotFoundException`은 상태 `404`, 코드 `QUESTION_NOT_FOUND`, 안전한 사용자 메시지를 정의한다. 모든 Controller에 적용되는 `GlobalExceptionHandler`의 `@RestControllerAdvice`에서 BusinessException, JSON 본문 파싱, DTO/입력 타입/메서드 검증 오류를 공통 변환한다. 특정 Controller 선택 조건은 사용하지 않으며 입력 검증 메시지는 질문에 한정하지 않는다. 반환값 검증, DB 및 예상치 못한 애플리케이션 오류는 안전한 `500 INTERNAL_SERVER_ERROR`로 처리한다. Spring의 HTTP 프로토콜 오류(예: 405/415)는 원래 상태 처리를 유지하며 이 문서의 API 오류 계약을 확대하지 않는다.

| HTTP 상태 | code | 발생 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 잘못된 JSON, 필수 답변 누락/잘못된 타입/빈 답변/길이 초과, 잘못된 경로 ID |
| 404 | QUESTION_NOT_FOUND | 질문이 존재하지 않음 |
| 404 | EVALUATION_ATTEMPT_NOT_FOUND | 평가 기록이 존재하지 않음 |
| 502 | LLM_EVALUATION_FAILED | 라이브러리 최종 호출 실패 또는 반환 응답 검증 실패. 로컬 검증 실패는 재호출하지 않음 |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류, DB 조회/저장 실패 |

## 3. API 목록

| 기능 | Method | Path | 성공 상태 |
| --- | --- | --- | --- |
| 질문 목록 조회 | GET | `/api/questions` | 200 OK |
| 질문 상세 조회 | GET | `/api/questions/{questionId}` | 200 OK |
| 답변 제출 및 평가 | POST | `/api/questions/{questionId}/evaluation-attempts` | 201 Created |
| 평가 결과 조회 | GET | `/api/evaluation-attempts/{attemptId}` | 200 OK |

## 4. 질문 목록 조회

```http
GET /api/questions
```

Request Body: 없음.

전체 질문의 ID와 제목만 반환한다. 본문, 평가 기준, 배점은 반환하지 않는다. 페이지네이션과 정렬 파라미터는 없으며 목록 순서는 현재 계약에서 보장하지 않는다.

### 성공 응답: 200 OK

```json
[
  {
    "id": 1,
    "title": "데이터베이스 인덱스"
  },
  {
    "id": 2,
    "title": "MySQL MVCC"
  },
  {
    "id": 3,
    "title": "Java GC"
  }
]
```

배열 원소의 필드: `id: number`, `title: string`.

주요 오류: `500 INTERNAL_SERVER_ERROR`. 질문이 없는 것은 오류가 아니며 `200`과 `[]`을 반환한다.

## 5. 질문 상세 조회

```http
GET /api/questions/{questionId}
```

Path Parameter: `questionId: number`, 질문 ID.

Request Body: 없음.

### 성공 응답: 200 OK

```json
{
  "id": 1,
  "title": "데이터베이스 인덱스",
  "content": "데이터베이스 인덱스가 무엇인지 설명해주세요."
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| id | number | 질문 ID |
| title | string | 질문 제목 |
| content | string | 사용자에게 보여줄 질문 본문 |

평가 항목 ID, 평가 기준 설명, 항목별 최대 배점, 평가 기준의 `displayOrder`는 반환하지 않는다.

주요 오류: `400 INVALID_REQUEST`, `404 QUESTION_NOT_FOUND`, `500 INTERNAL_SERVER_ERROR`.

```json
{
  "code": "QUESTION_NOT_FOUND",
  "message": "질문을 찾을 수 없습니다."
}
```

## 6. 답변 제출 및 평가

```http
POST /api/questions/{questionId}/evaluation-attempts
Content-Type: application/json
```

Path Parameter: `questionId: number`, 평가할 질문 ID.

### Request Body

```json
{
  "answer": "데이터베이스 인덱스는 조회 성능을 높이기 위한 자료구조입니다. B-Tree를 활용해 필요한 데이터를 빠르게 찾을 수 있지만 저장 공간과 데이터 변경 시 유지 비용이 발생합니다."
}
```

| 필드 | 타입 | 필수 | 검증 |
| --- | --- | --- | --- |
| answer | string | 예 | 누락, null, 문자열 외 타입, 빈 문자열, 공백만 있는 문자열 및 3,000 UTF-16 코드 단위 초과 거부 |

답변은 사용자가 제출한 원문을 저장하고 결과 조회에 반환한다. trim하거나 잘라 저장하지 않는다. 최대 길이는 3,000 UTF-16 코드 단위로 Kotlin `String.length`와 브라우저 `answer.length`/`maxLength`의 기준과 같다. 공백과 줄바꿈도 포함하며 보조 평면 이모지는 두 코드 단위로 계산한다. 전체 HTTP JSON 본문 바이트 제한은 아직 적용하지 않았고 배포 전 별도로 결정한다. 답변 길이 검증은 대용량 본문 수신을 제한하는 보호 장치가 아니다.

### 처리 흐름

1. 요청을 검증하고 QuestionRepository로 질문을 조회한다.
2. EvaluationCriterionRepository로 해당 `questionId`의 기준 목록을 `displayOrder` 오름차순으로 조회한다. 같은 순서 값에서는 ID 오름차순으로 조회한다.
3. 저장된 기준은 정상이라고 가정하고 개수/양의 배점/배점 합계를 재검증하지 않은 채 질문, 기준, 답변을 Spring AI를 통해 환경설정으로 지정한 LLM 엔드포인트(현재 CODEX_LB)에 전달한다.
4. LLM이 반환한 항목별 점수와 피드백을 파싱하고 검증한다.
5. 백엔드가 총점을 합산하고 FAIL/RETRY/PASS를 판정한다.
6. 완료된 평가 기록을 DB에 저장한다.
7. 저장 성공 후 평가 ID와 결과를 반환한다.

질문과 기준은 각각 한 번 조회하며 POST 응답의 제목은 이미 읽은 질문에서 가져온다. 제출 전체에 DB 트랜잭션을 적용하지 않아 LLM 대기 중 DB 트랜잭션을 유지하지 않는다. 완료 Entity만 `JpaRepository.save`의 짧은 쓰기 트랜잭션으로 저장하고 commit이 반환된 뒤 응답을 만든다. 반환 Entity의 ID와 Auditing 생성 시각이 누락되면 성공 대신 안전한 500을 반환한다.

하나의 동기식 HTTP 요청 안에서 평가와 저장을 완료한다. `202 Accepted`나 중간 작업 ID를 반환하지 않으며, 처리 중인 기록을 조회하는 API도 제공하지 않는다. 평가 또는 저장이 실패하면 성공 결과나 평가 ID를 반환하지 않고 완료된 평가 기록도 저장하지 않는다.

### 성공 응답: 201 Created

응답 헤더 예: `Location: /api/evaluation-attempts/42`.

POST와 결과 조회 GET은 같은 평가 결과 형식을 사용한다.

```json
{
  "id": 42,
  "questionId": 1,
  "questionTitle": "데이터베이스 인덱스",
  "answer": "데이터베이스 인덱스는 조회 성능을 높이기 위한 자료구조입니다. B-Tree를 활용해 필요한 데이터를 빠르게 찾을 수 있지만 저장 공간과 데이터 변경 시 유지 비용이 발생합니다.",
  "score": 82,
  "result": "PASS",
  "strengths": "인덱스의 목적과 조회 성능 향상, 데이터 변경 시 유지 비용을 잘 설명했습니다.",
  "weaknesses": "B-Tree 내부 탐색 과정에 대한 설명이 부족합니다.",
  "improvements": "페이지 단위 탐색 과정과 인덱스 선택이 조회 성능에 미치는 영향을 함께 설명하면 좋습니다.",
  "createdAt": "2026-10-01T07:30:00Z"
}
```

평가 결과 필드의 타입은 7절에 정의한다. 공개 응답에는 평가 기준과 항목별 배점/점수 목록을 추가하지 않는다.

주요 오류: `400 INVALID_REQUEST`, `404 QUESTION_NOT_FOUND`, `502 LLM_EVALUATION_FAILED`, `500 INTERNAL_SERVER_ERROR`.

```json
{
  "code": "LLM_EVALUATION_FAILED",
  "message": "평가 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
}
```

각 성공한 POST는 새로운 평가 기록을 생성한다. 같은 답변을 다시 제출해도 별도 평가이며, 중복 제거/멱등 키는 이번 MVP에 포함하지 않는다. 프론트엔드는 요청 중 제출 버튼을 비활성화하고 오류 시 작성한 답변을 유지한다. 네트워크 단절로 응답을 받지 못하더라도 서버 저장이 완료되었을 수 있으므로 POST를 자동 재전송하지 않는다.

## 7. 평가 결과 조회

```http
GET /api/evaluation-attempts/{attemptId}
```

Path Parameter: `attemptId: number`, 저장된 평가 기록 ID.

Request Body: 없음.

### 성공 응답: 200 OK

6절의 성공 응답과 동일한 필드와 타입의 JSON 객체를 반환한다. DB에서 저장된 결과를 읽으며 LLM을 다시 호출하거나 점수/판정을 재계산하지 않는다. 저장된 `questionId`로 현재 질문 제목을 조회하며, 기록의 참조 질문이 없다면 `QUESTION_NOT_FOUND` 404가 아닌 안전한 `500 INTERNAL_SERVER_ERROR`로 처리한다. 프론트엔드 결과 페이지(`/results/{attemptId}`)를 새로고침해도 이 API로 결과를 복원한다. 해당 페이지 경로는 프론트엔드 경로이며 백엔드 API가 아니다.

POST의 `createdAt`은 저장 반환 Entity의 Auditing 값, GET은 PostgreSQL에서 읽은 값이다. PostgreSQL의 마이크로초 정밀도 때문에 소수 초 표현에 미세한 차이가 있을 수 있으며 동일한 JSON 본문 바이트를 보장하는 것은 아니다. 두 응답 모두 필수 UTC ISO 8601 문자열이다.

```json
{
  "id": 42,
  "questionId": 1,
  "questionTitle": "데이터베이스 인덱스",
  "answer": "데이터베이스 인덱스는 조회 성능을 높이기 위한 자료구조입니다. B-Tree를 활용해 필요한 데이터를 빠르게 찾을 수 있지만 저장 공간과 데이터 변경 시 유지 비용이 발생합니다.",
  "score": 82,
  "result": "PASS",
  "strengths": "인덱스의 목적과 조회 성능 향상, 데이터 변경 시 유지 비용을 잘 설명했습니다.",
  "weaknesses": "B-Tree 내부 탐색 과정에 대한 설명이 부족합니다.",
  "improvements": "페이지 단위 탐색 과정과 인덱스 선택이 조회 성능에 미치는 영향을 함께 설명하면 좋습니다.",
  "createdAt": "2026-10-01T07:30:00Z"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| id | number | 평가 기록 ID |
| questionId | number | 평가 대상 질문 ID |
| questionTitle | string | 질문 제목 |
| answer | string | 제출한 답변 원문 |
| score | number | 백엔드가 계산한 총점, 0~100 정수 |
| result | `"FAIL" \| "RETRY" \| "PASS"` | 백엔드 판정 |
| strengths | string | 잘 설명한 부분 |
| weaknesses | string | 부족하거나 잘못 설명한 부분 |
| improvements | string | 개선할 부분 |
| createdAt | string | JPA Auditing이 기록한 평가 기록의 감사용 생성 시각, UTC ISO 8601 |

주요 오류: `400 INVALID_REQUEST`, `404 EVALUATION_ATTEMPT_NOT_FOUND`, `500 INTERNAL_SERVER_ERROR`.

```json
{
  "code": "EVALUATION_ATTEMPT_NOT_FOUND",
  "message": "평가 결과를 찾을 수 없습니다."
}
```

인증과 소유자 검증이 없으므로 평가 ID를 아는 누구나 답변과 결과를 조회할 수 있다. 개인 전용 기록이나 접근 보호를 보장하지 않으며, 사용자에게 민감한 개인정보 입력을 유도하지 않는다.

## 8. 백엔드 내부 평가 계약

이 절은 백엔드와 LLM 사이의 내부 계약이며 프론트엔드 API가 아니다.

### 최소 데이터 모델

| 모델 | 최소 데이터 및 ID 참조 |
| --- | --- |
| Question | id, title, content |
| EvaluationCriterion | id, questionId, content(자연어 평가 기준), maxScore, displayOrder |
| EvaluationAttempt | id, questionId, answer, score, result, strengths, weaknesses, improvements, createdAt |

Question 하나에 1개 이상의 EvaluationCriterion과 여러 EvaluationAttempt가 ID로 연결된다. User 관계는 추가하지 않는다. `EvaluationCriterion.displayOrder`는 정수 필드이며 평가 기준 조회/프롬프트 나열 순서에 사용한다. 오름차순으로 조회하고 같은 값에서는 ID 오름차순으로 조회한다. 질문 목록의 정렬이나 공개 응답 필드에는 영향을 주지 않는다. 점수는 여전히 순서가 아닌 `criterionId`로 대응시킨다. 항목별 평가 결과를 별도로 영속화하는 것은 현재 필수 요구사항이 아니다.

### 영속화 및 조회 정책

- Entity 간 연관관계 매핑은 사용하지 않는다. `@ManyToOne`, `@OneToMany` 등 대신 `EvaluationCriterion.questionId`와 `EvaluationAttempt.questionId`를 `Long` 필드로 저장한다.
- 관련 데이터가 필요하면 해당 모델의 Repository를 별도로 호출한다. 질문과 평가 기준은 질문 조회 1회와 기준 목록 조회 1회로 가져오며, 평가 기록의 질문 정보도 저장된 `questionId`로 QuestionRepository에서 조회한다. 불필요한 기준 조회는 질문 목록/상세 API에 추가하지 않는다.
- 추후 성능 개선이 필요한 경우 명시적 조인 쿼리와 DTO 매핑을 검토하며, 이를 위해 Entity 간 연관관계 매핑을 도입하지 않는다.
- 프로젝트의 DB 스키마에는 외래 키 제약 조건, `ON DELETE CASCADE`, 배점 등 도메인 규칙을 강제하는 `CHECK` 및 트리거를 사용하지 않는다. PK, 필수 컬럼의 `NOT NULL`, `question_id` 조회 인덱스는 사용한다. 참조 정합성은 데이터 입력 및 애플리케이션의 생성/변경 흐름에서 관리한다.
- 일반 문자열 등 기본 타입은 Kotlin/JPA 기본 매핑을 사용한다. `columnDefinition`은 `jsonb`처럼 특정 DB 타입이 기능상 꼭 필요한 경우에만 사용하며 일반 문자열에는 지정하지 않는다. DB 컬럼 타입은 Flyway 마이그레이션에서 관리한다.
- Flyway 파일명은 `V<major>.<minor>.<patch>__<description>.sql` 형식으로 항상 세 버전 요소를 명시한다. 이미 적용된 마이그레이션의 변경이나 DB 이력 수정은 별도 확인 없이 수행하지 않는다.

### 평가 기록 저장 및 감사 정책

- 평가 기록 스키마는 `V2.0.0__create_evaluation_attempt.sql`로 추가한다. `EvaluationAttemptRepository`의 기본 저장/ID 조회를 사용하며, 같은 질문의 복수 기록을 허용한다. 평가 기록 초기 데이터나 이력 목록 API는 추가하지 않는다.
- 답변은 공백과 줄바꿈을 포함한 원문을 보존한다. 총점과 세 종류 종합 피드백을 저장하고, `EvaluationResult`의 FAIL/RETRY/PASS는 JPA `EnumType.STRING`으로 저장한다. 점수/판정 계산과 LLM 응답 검증은 평가 로직에서 수행하며 DB CHECK나 DB 전용 enum은 사용하지 않는다.
- 생성 시각은 JPA Auditing의 `@CreatedDate`와 AuditingEntityListener로 최초 영속화 시 기록한다. Kotlin 타입은 `Instant?`로 저장 전에는 null일 수 있지만 저장된 기록의 `created_at`은 NOT NULL이다. `updatable = false`는 사용하지 않으며 필드 변경 정책을 JPA 매핑으로 제한하지 않는다. PostgreSQL 컬럼은 TIMESTAMP WITH TIME ZONE으로 관리하고 Entity에 `columnDefinition`을 지정하지 않는다. DB 기본값/트리거와 직접 `Instant.now()` Entity 콜백은 사용하지 않는다.
- `createdAt`은 감사 정보이며 답변 제출 시각, LLM 평가 시작/종료 시각, 조회 만료의 기준을 의미하지 않는다. 시간 기반 도메인 규칙이 생기면 `submittedAt` 또는 `evaluatedAt` 등 의미가 분명한 별도 필드를 추가한다. 공개 응답의 `createdAt` 이름과 UTC ISO 8601 형식은 유지한다.
- 현재는 생성 시각만 적용하고 공통 BaseEntity, 수정 시각, 사용자 감사 정보 및 기존 질문 모델의 감사 필드는 추가하지 않는다. 질문 제목은 기록에 스냅샷으로 저장하지 않고 `questionId`로 현재 질문을 별도 조회한다.

### 평가 기준 작성 및 검증 정책

각 질문에는 평가 기준이 1개 이상 필요하다. `maxScore`는 양의 정수이고 질문별 합계는 정확히 100이어야 한다. 검증은 향후 관리자 페이지에서 질문을 생성하는 애플리케이션 처리에서만 수행한다. 관리자 페이지/API와 해당 검증 코드는 현재 MVP 작업 범위에 포함하지 않는다.

저장된 질문/평가 기준은 정상이라고 가정한다. 질문 조회와 LLM 호출 전에는 기준 개수, 양의 배점, 배점 합계를 재검증하지 않으며, 잘못된 기준을 사전에 탐지하여 `500`으로 변환하는 흐름도 구현하지 않는다. Entity 생성자나 영속화 콜백에도 이 검증을 넣지 않는다. 개발자가 직접 DB에 입력하거나 Flyway로 초기 데이터를 입력할 때에는 작성자가 같은 규칙 및 참조 정합성을 지킨다. 이 정책은 저장 기준에 대한 것이며, 외부 입력인 사용자 요청과 LLM 응답 검증은 유지한다.

### 확정된 초기 데이터

Core Flow 연결 검증용 데이터는 스키마 마이그레이션 `V1.0.0__create_question_and_evaluation_criterion.sql`과 분리하되 같은 주 버전의 Flyway 초기 데이터 마이그레이션 `V1.0.1__seed_initial_questions.sql`로 입력한다. 관리자 API나 별도 입력 스크립트는 추가하지 않는다.

- 질문 제목: `자기 소개`
- 질문 본문: `인사 후, 자신의 이름, 성별을 소개해주세요.`

| 평가 기준 content | maxScore | displayOrder |
| --- | --- | --- |
| 인사 | 50 | 1 |
| 본인 이름 | 30 | 2 |
| 본인 성별 | 20 | 3 |

기준의 `questionId`는 초기 질문의 실제 ID를 참조하고 초기 데이터의 배점 합계는 100이다. Repository 기능 테스트는 이 초기 데이터에 의존하지 않고 각 테스트에서 직접 Entity를 생성/저장한 뒤 조회 결과를 비교한다. 마이그레이션 이력/초기 데이터의 별도 assertion은 작성하지 않는다. 이 질문은 개발 연결 검증용이며 답변에는 가상의 이름/성별을 사용하고 실제 개인정보 입력을 유도하지 않는다. 문서의 기술 질문 API/LLM 예시는 계약 설명용이며 별도의 초기 데이터가 아니다.

### LLM 응답 예시

```json
{
  "criteria": [
    {
      "criterionId": 1,
      "score": 25,
      "feedback": "인덱스의 목적과 기본 개념을 대체로 정확히 설명했습니다."
    },
    {
      "criterionId": 2,
      "score": 20,
      "feedback": "B-Tree를 언급했지만 내부 탐색 과정 설명이 부족합니다."
    },
    {
      "criterionId": 3,
      "score": 37,
      "feedback": "조회 이점과 저장 공간 및 데이터 변경 비용을 잘 설명했습니다."
    }
  ],
  "strengths": "인덱스의 목적과 조회 성능 향상, 데이터 변경 시 유지 비용을 잘 설명했습니다.",
  "weaknesses": "B-Tree 내부 탐색 과정에 대한 설명이 부족합니다.",
  "improvements": "페이지 단위 탐색 과정과 인덱스 선택이 조회 성능에 미치는 영향을 함께 설명하면 좋습니다."
}
```

위 예시의 항목별 최대 배점은 각각 30, 30, 40이고 합산 점수는 82다. `criterionId`는 실제 DB에서 조회해 전달한 ID여야 한다. 각 항목의 `feedback`은 내부 검증에 사용하고, 공개 결과에는 세 종류의 종합 피드백 문자열만 반환한다.

### LLM 응답 검증과 판정

1. 응답이 기대한 JSON 구조와 타입을 만족하는지 검증한다.
2. 해당 질문의 모든 평가 항목이 정확히 한 번씩 존재해야 한다. 누락, 중복, 다른 질문의 ID, 알 수 없는 ID를 거부한다.
3. 각 점수는 정수이며 `0 <= score <= maxScore`여야 한다. 잘못된 값을 반올림하거나 상한으로 보정해 통과시키지 않는다.
4. 항목별 `feedback`과 `strengths`, `weaknesses`, `improvements`는 공백이 아닌 문자열이어야 한다. 설명할 내용이 없으면 그 사실을 문장으로 작성하도록 요청한다.
5. 검증된 항목 점수만 합산한다. 총점이나 판정이 LLM 응답에 추가되어도 이를 사용하지 않는다.
6. 백엔드에서 아래 구간으로 판정한다.

| 총점 | result |
| --- | --- |
| 0~49 | FAIL |
| 50~79 | RETRY |
| 80~100 | PASS |

**LLM은 항목별 점수와 피드백을 생성하고, 총점과 판정은 백엔드가 결정한다.** 사용자 답변은 평가 대상 데이터로 취급하며, 답변 속 명령으로 평가 기준이나 출력 형식을 변경하지 않도록 프롬프트를 구성한다.

### LLM 실패와 재시도

- 현재 연동 대상은 CODEX_LB의 `gpt-6-sol`이며 추론 기능은 사용하지 않는 방향이다. 모델/엔드포인트/키는 기존 `CHAT_MODEL`, `CHAT_BASE_URL`, `CHAT_API_KEY`를 사용하며 사용자가 관리한다. 에이전트는 이 값을 변경하지 않는다. OpenRouter는 향후 장애 대응 경로로 검토하지만 자동 fallback은 현재 구현/검증 대상이 아니다.
- Service는 LLM 클라이언트를 한 번 호출하며 애플리케이션의 timeout/재시도 반복/설정을 두지 않는다. Spring AI 2.0.1 기본 auto-configuration을 사용한다. 해당 버전 기본값은 timeout 60초와 SDK 추가 재시도 최대 3회이며 최초 포함 최대 4 SDK 시도와 backoff가 발생할 수 있다. 이는 전체 요청 60초 제한이나 실제 HTTP 요청 수의 엄격한 상한이 아니다.
- SDK 재시도는 전송 오류와 408/409/429/5xx 등의 재시도 가능한 상태에 적용되며 공급자 헤더 및 기본 HTTP 동작의 영향을 받는다. 라이브러리가 최종 오류를 반환하면 안전한 `502 LLM_EVALUATION_FAILED`로 변환한다.
- 직접 OpenAiChatModel.call(Prompt)의 네이티브 JSON Schema 요청은 응답 JSON 불일치 교정 재시도를 자동 활성화하지 않는다. StructuredOutputValidationAdvisor는 별도 opt-in ChatClient 기능이며 현재 사용하지 않는다. 로컬 JSON 파싱/ID/점수/피드백 검증 실패는 재호출 없이 502로 종료하고 기록 저장/점수 보정을 하지 않는다.
- 잘못된 요청, 없는 질문, DB 조회/저장 실패는 LLM 재시도 대상이 아니다. DB 저장 실패를 이유로 LLM을 다시 호출하지 않는다. 저장 기준 오류를 탐지하는 별도 검증 흐름은 추가하지 않는다.
- timeout/max-retries는 코드/YAML로 덮어쓰지 않는다. 커스텀 HTTP 취소/재전송 제어, 전체 deadline, 수동 retry 및 교정 Advisor도 추가하지 않는다.
- 프론트는 제출 후 180초에 응답이 없으면 대기를 종료하고 답변을 유지한 채 수동 재시도를 안내한다. 180초는 프론트 UX 정책이며 서버 취소/실패 확정/전체 처리 상한이 아니다. 기본 retry와 backoff로 서버 처리 시간이 이를 초과할 수 있고, 재제출은 중복 평가/기록/비용을 유발할 수 있다. POST 자동 재전송은 하지 않는다. 프론트 구현은 후속 연결 작업이며 이번에는 백엔드/문서만 변경한다. 실제 프록시 timeout과 SSE는 별도 검토한다.
- 출력 요청 방식은 네이티브 `json_schema`로 확정했다. 미지원 시 프롬프트 기반 JSON이나 다른 공급자로 자동 전환하지 않으며 기존 서버 응답 검증은 유지한다.
- 이번 작업과 자동 테스트에서는 실제 LLM을 호출하지 않는다. Spring AI 호출을 mock/로컬 대체로 검증하며 CODEX_LB의 네이티브 JSON Schema 지원과 실제 Spring AI 연동, 평가 품질은 후속 검토 대상으로 남긴다. 지원을 확인한 것으로 표시하거나 미지원 시 임의로 출력 계약을 완화하지 않는다. 비용이 발생하는 실제 호출은 추후 별도 승인 후 수행한다.

### 내부 평가 구현 상태

내부 EvaluationService와 기본 Spring AI 클라이언트, 엄격한 JSON 검증/합산/판정은 구현되었다. app.evaluation 설정, EvaluationConfig/EvaluationProperties, 호출별 timeout 인자, Service 재시도 루프는 제거했다. endpoint/model/key의 기존 환경변수 매핑은 유지한다.

전체 평가 작업은 DB 트랜잭션으로 감싸지 않고 현재 호출 스레드에서 동기 수행한다. LlmEvaluationClient는 기본 OpenAiChatModel을 직접 주입하고 기존 옵션에서 네이티브 Schema만 설정한다. 라이브러리 최종 호출 실패/빈 응답은 클라이언트에서, 로컬 검증 실패는 Service에서 안전한 평가 실패 예외로 변환한다. 질문 없음/DB 오류는 그대로 전달한다. 새로운 abstraction/wrapper는 없다.

테스트는 모델 결과를 mock하여 애플리케이션의 프롬프트/Schema/Service/오류 계약을 확인하고 로컬 JSON 검증을 직접 테스트한다. 라이브러리 timeout/retry 내부를 재구현/테스트하지 않으며 실제 CODEX_LB/OpenRouter 호출도 하지 않는다. 기본 전송의 즉시 취소/원격 생성 중단은 보장하지 않는다. 공개 POST는 검증된 완료 결과만 저장하며 DB 저장 실패 시 LLM을 재호출하지 않는다. 프론트 180초 대기 종료/취소 뒤에도 서버가 저장할 수 있고 재제출은 별도 평가/기록이다.

## 9. 구현 전 확인할 사항

아래는 이번 문서에서 임의의 수치나 운영 정책으로 확정하지 않았다. 관련 작업에 착수하기 전에 결정하고 필요하면 계약과 TODO를 함께 갱신한다.

- 전체 JSON 본문 바이트 상한, 거부 HTTP 상태/계약 및 프록시 제한. 답변의 3,000 UTF-16 길이 제한과 별도이며 배포 전 결정한다.
- 프론트 180초 대기 종료/답변 유지/수동 재시도 안내 구현과 실제 공급자/프록시 연결. 서버 계속 처리 및 중복 제출 위험을 검증한다.
- CODEX_LB/gpt-6-sol과 Spring AI의 실제 연동/Structured Output 지원 및 품질 검증 방법. 모델/엔드포인트/키는 기존 환경변수로 사용자가 관리하며 이번에는 실제 호출하지 않는다. OpenRouter 장애 대응은 향후 검토 대상으로 현재 자동 전환하지 않는다.
- 실제 배포 프론트엔드 Origin. 개발 Origin `http://localhost:5173`과 CORS 정책은 2절에 확정했으며 운영 허용 목록은 환경변수로 지정한다.
- 익명 답변/평가 기록의 보관 기간과 운영상 정리 정책. 삭제 API나 자동 정리 작업은 현재 범위에 추가하지 않는다.
