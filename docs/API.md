# MVP API Contract

## 1. 목적과 적용 범위

혼자 기술 면접을 준비하는 사용자가 질문을 선택하고 주관식 답변을 제출한 뒤, LLM 평가 점수와 피드백을 확인하는 흐름을 정의한다. 이 문서는 MVP API 계약이다. 질문 목록/상세 조회, 답변 제출·평가·완료 기록 저장, 결과 조회 및 공통 오류 처리가 구현되어 있다. 구현/검증 상태는 `TODO.md`를 참조한다. 외부 LLM을 대체한 실제 HTTP/commit 후 조회는 검증했으며 실제 공급자와 브라우저 연결은 미검증이다.

현재 공개 Core Flow는 로그인과 사용자 구별 없이 동작한다. 같은 질문에 여러 번 답변할 수 있으며, 성공한 평가 결과를 저장하고 ID로 다시 조회한다. 후속 관리자 API에는 별도의 권한 검증이 필요하다.

1~8절의 공개 Core Flow 계약은 유지한다. 후속 관리자 질문 관리 계약 초안은 10절에 정의하며 아직 구현되지 않았다. 관리자 기능은 질문/평가 기준 생성·수정과 저장 전 평가 테스트로 한정한다. 로그인 사용자 기록, 사용자 평가 기록 목록, 질문 삭제, 검색/페이지네이션, 일일 제출 제한, 별도의 비동기 작업/메시지 큐/상태 조회 API는 이번 관리자 범위에 포함하지 않는다. 관측 가능성과 배포 파이프라인 구현은 별도 작업이다.

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
| 인증 | 현재 공개 API는 필요 없음. 10절의 관리자 API는 관리자 권한 필요(미구현) |
| 성공 응답 | 별도 공통 wrapper 없이 JSON 객체 또는 배열 반환 |
| 오류 응답 | 아래의 공통 `code`, `message` 객체 반환 |

성공 응답의 정의된 필드는 필수이며 `null`을 반환하지 않는다. 목록이 비어 있으면 `[]`을 반환한다. GET 요청에는 Request Body가 없다. 현재 정의한 API에는 Query Parameter가 없다.

경로 ID가 양의 정수가 아니거나 지원 범위를 벗어나면 `400 INVALID_REQUEST`, 유효한 ID지만 리소스가 없으면 `404`를 반환한다.

### CORS 및 환경별 Origin

- `/api/**`에는 Spring MVC 공통 CORS 정책을 적용한다. 개발 프론트엔드 Origin은 `http://localhost:5173`이며 `app.cors.allowed-origins` 설정으로 관리한다. 현재 `.env`와 `.env.example`에는 `CORS_ALLOWED_ORIGINS=http://localhost:5173`을 설정했다. 애플리케이션 자체의 기본 허용 목록은 비어 있으므로 미설정 시 교차 Origin을 허용하지 않는다.
- 배포 예정 주소는 프론트엔드 `https://tech.eoehd1ek.com`, 백엔드 `https://techapi.eoehd1ek.com`이다. 배포 시 실행 환경변수 `CORS_ALLOWED_ORIGINS=https://tech.eoehd1ek.com`으로 교체한다. 복수 Origin은 쉼표로 구분한다. 환경변수 변경 후 백엔드를 재시작하면 적용되며 코드 변경/재빌드는 필요하지 않다. Origin에는 경로나 끝의 `/` 없이 스킴/호스트/포트만 지정한다. localhost와 127.0.0.1, 다른 포트는 서로 다른 Origin이다. 와일드카드 허용은 사용하지 않는다.
- 허용 메서드는 GET/POST/OPTIONS, 요청 헤더는 Content-Type, 브라우저에 공개하는 응답 헤더는 Location이다. 쿠키 등의 credentials는 허용하지 않으며 현재 프론트에서는 credentials include를 사용하지 않는다. OPTIONS preflight는 MVC에서 처리하고 별도 Controller는 만들지 않는다. CORS 허용이 미구현 API의 존재를 의미하지는 않는다.
- 허용되지 않은 Origin/메서드/헤더는 MVC의 CORS 처리에서 거부하며 `403`과 CORS 헤더 미노출로 처리된다. 이 거부는 Controller의 API 오류 JSON 계약과 별개이며 브라우저에서는 응답 본문을 읽지 못할 수 있다. 허용 Origin의 정상/처리된 오류 응답에는 CORS 헤더를 제공한다.
- CORS는 브라우저 교차 Origin 정책이며 인증이나 일반 클라이언트의 접근 제한 기능이 아니다. 추후 Spring Security 적용 시 CORS 연동과 preflight 처리를 확인한다. 배포 프록시가 OPTIONS를 막거나 CORS 헤더를 중복 생성하지 않도록 확인한다. 운영 Origin과 실제 브라우저 연결은 배포 환경에서 별도 검증한다.
- 관리자 구현에서는 PUT 허용과 Spring Security의 CORS 연동이 추가로 필요하다. 인증 방식 확정 후 필요한 요청 헤더/credentials/CSRF 정책을 함께 갱신한다. 현재 설정이 이미 PUT이나 인증 헤더/쿠키를 지원한다는 의미는 아니다.

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

각 질문에는 평가 기준이 1개 이상 필요하다. `maxScore`는 양의 정수이고 질문별 합계는 정확히 100이어야 한다. 후속 관리자 생성·수정·평가 테스트 요청에는 같은 작성 규칙을 적용한다(10절). 관리자 API와 해당 입력 검증은 아직 구현되지 않았다.

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
- 배포 주소는 2절에 확정했으며 운영 CORS/HTTPS/프록시 연결은 미검증이다. 관리자 인증 방식과 CORS/CSRF 정책, 관리자 입력 크기 상한은 10절의 구현 전 결정 사항이다.
- 익명 답변/평가 기록의 보관 기간과 운영상 정리 정책. 삭제 API나 자동 정리 작업은 현재 범위에 추가하지 않는다.

## 10. 관리자 질문 관리 계약 초안 (미구현)

이 절은 프론트엔드와 백엔드의 후속 작업 분리를 위한 계약 초안이다. 현재 호출 가능한 API가 아니며, 아래 결정 사항을 확정한 뒤 구현한다. 공개 API의 경로/응답 필드는 변경하지 않는다. 이번 문서 정리는 인증 코드, 관리자 API, 프론트엔드 화면을 구현하거나 실제 LLM을 호출하는 작업이 아니다.

### 10.1. 화면 및 동작 범위

- 관리자 목록에서 새 질문 작성 또는 기존 질문 수정을 선택한다. 프론트엔드 경로 초안은 `/admin/questions`, `/admin/questions/new`, `/admin/questions/:questionId/edit`이며 백엔드 API 경로와 별개다.
- 생성/수정 화면에 질문 제목, 질문 본문, 평가 기준 목록을 둔다. 기준별 자연어 설명과 최대 배점을 입력하고, 항목 추가/제거/순서 변경과 배점 합계 100 안내를 제공한다.
- 같은 화면의 별도 테스트 영역에서 답변을 입력하고 현재 폼 내용으로 평가를 요청한다. DB에 저장되지 않은 신규 질문이나 저장 이후 변경된 내용도 테스트할 수 있어야 한다.
- 테스트 결과는 일반 사용자와 동일한 총점, 판정, 잘 설명한 부분, 부족하거나 잘못 설명한 부분, 개선할 부분을 표시한다. 항목별 점수 공개나 평가 이력 화면은 추가하지 않는다.
- 테스트와 저장은 별도 버튼/요청이다. 테스트 성공이 저장의 선행 조건은 아니며, 저장은 LLM을 호출하지 않는다. 테스트 답변과 결과도 저장 대상이 아니다.
- 저장 성공(commit) 후 질문은 일반 사용자 목록/상세에서 즉시 조회 대상이 된다. 별도 초안 저장, 공개 상태, 발행 버튼, 예약 공개는 두지 않는다. 이미 열려 있는 사용자 화면은 다음 GET에서 변경을 확인하며 실시간 갱신은 제공하지 않는다.
- 편집 내용 또는 테스트 답변이 바뀌면 이전 결과를 현재 내용의 결과로 표시하지 않는다. 테스트 중 입력 잠금과 중복 요청 방지, 오래된 응답 무시를 적용하고 테스트가 대기 중인 동안 저장도 잠근다.
- 저장 중에는 편집/저장/테스트를 잠가 전송한 내용과 화면 내용이 달라지지 않게 한다. 저장 성공 후 반환된 상세를 편집 상태의 기준으로 사용하고, 저장 실패 시 현재 입력을 지우거나 상세 GET으로 덮지 않는다. 새로고침/화면 이탈 후 미저장 입력 복원이나 자동 저장은 이번 범위에 포함하지 않는다.
- 저장/테스트 오류 시 모든 입력을 보존한다. 180초 테스트 대기 종료는 서버 취소가 아니며, POST 자동 재전송은 하지 않는다. 재테스트는 기록을 만들지는 않지만 추가 LLM 비용이 발생할 수 있다.

### 10.2. 관리자 API 목록

모든 `/api/admin/**` 요청은 서버에서 관리자 권한을 검사한다. 화면에서 버튼을 숨기거나 CORS/Cloudflare 프록시를 사용하는 것은 권한 검증의 대체가 아니다. 미인증/비관리자 요청은 DB 변경이나 LLM 호출 전에 거부한다. 로그인/세션/토큰 발급 API는 아직 계약에 포함하지 않으며 인증 방식 확정 후 추가한다.

| 기능 | Method | Path | 성공 상태 | 본문 |
| --- | --- | --- | --- | --- |
| 관리자 질문 목록 | GET | `/api/admin/questions` | 200 OK | `{ id, title }[]` |
| 수정용 질문 상세 | GET | `/api/admin/questions/{questionId}` | 200 OK | 질문과 평가 기준 |
| 질문 생성 | POST | `/api/admin/questions` | 201 Created | 저장된 관리자 상세 |
| 질문 전체 수정 | PUT | `/api/admin/questions/{questionId}` | 200 OK | 저장된 관리자 상세 |
| 저장 전 평가 테스트 | POST | `/api/admin/questions/evaluation-preview` | 200 OK | 비저장 평가 결과 |

관리자 목록은 전체 질문을 반환하고 빈 목록은 `[]`이다. 순서 보장, Query Parameter, 페이지네이션은 없다. 관리자 상세 조회와 PUT의 경로 ID는 2절의 안전 정수 규칙을 사용하며 없는 질문은 `404 QUESTION_NOT_FOUND`다. PUT은 신규 생성(upsert)을 하지 않는다.

### 10.3. 질문 생성·수정 요청

POST 생성과 PUT 수정은 같은 전체 본문을 사용한다. 일부 필드만 수정하는 PATCH나 평가 기준 단독 CRUD API는 제공하지 않는다.

```json
{
  "title": "데이터베이스 인덱스",
  "content": "데이터베이스 인덱스가 무엇인지 설명해주세요.",
  "criteria": [
    { "content": "인덱스의 목적과 기본 개념을 설명한다.", "maxScore": 30 },
    { "content": "B-Tree 탐색 과정을 설명한다.", "maxScore": 30 },
    { "content": "조회 이점과 저장 공간 및 변경 비용을 설명한다.", "maxScore": 40 }
  ]
}
```

| 필드 | 타입 | 검증/정책 |
| --- | --- | --- |
| title | string | 필수, null/문자열 외 타입/빈 문자열/공백만 입력 거부 |
| content | string | 필수, null/문자열 외 타입/빈 문자열/공백만 입력 거부 |
| criteria | array | 필수, null/배열 외 타입/빈 목록/null 항목 거부 |
| criteria[].content | string | 필수, null/문자열 외 타입/빈 문자열/공백만 입력 거부 |
| criteria[].maxScore | number | 필수, 양의 정수 1~100. 소수/문자열/boolean 거부, 목록 합계 정확히 100 |

양의 정수 배점과 합계 100 규칙에 의해 유효한 기준 개수는 1~100개다. 문자열은 공백 검증만 수행하고 저장/테스트 시 원문을 trim하거나 자르지 않는다. title/content/기준 content의 최대 길이와 전체 JSON 바이트 상한은 아직 미정이며 10.7에서 확정한다. 프론트만 검증하는 것이 아니라 서버에서도 같은 규칙을 적용한다. 잘못된 입력은 저장/LLM 호출 전에 `400 INVALID_REQUEST`로 거부하고, 오류 본문에 입력 원문이나 비공개 기준을 반사하지 않는다.

요청은 기준 `id`, `questionId`, `displayOrder`를 지정하지 않는다. 서버가 `criteria` 배열 순서대로 `displayOrder`를 1부터 부여한다. PUT은 질문 ID를 유지하고 제목/본문/전체 기준 목록을 교체한다. 기존 기준은 제거하고 새 ID를 발급하므로 기준 ID의 유지나 이전 ID와의 대응은 보장하지 않는다. 기준 ID는 영속 모델/내부 평가용이며 프론트 수정 요청에서 사용하지 않는다.

### 10.4. 관리자 상세 및 저장 성공 응답

GET 상세, POST 생성, PUT 수정은 아래 같은 객체를 반환한다. POST에는 `Location: /api/admin/questions/1` 헤더를 포함한다. PUT은 `Location`을 요구하지 않는다. 모든 필드는 필수이며 null을 반환하지 않는다.

```json
{
  "id": 1,
  "title": "데이터베이스 인덱스",
  "content": "데이터베이스 인덱스가 무엇인지 설명해주세요.",
  "criteria": [
    { "id": 11, "content": "인덱스의 목적과 기본 개념을 설명한다.", "maxScore": 30, "displayOrder": 1 },
    { "id": 12, "content": "B-Tree 탐색 과정을 설명한다.", "maxScore": 30, "displayOrder": 2 },
    { "id": 13, "content": "조회 이점과 저장 공간 및 변경 비용을 설명한다.", "maxScore": 40, "displayOrder": 3 }
  ]
}
```

`id`와 `criteria[].id`는 2절의 ID 규칙, 문자열 필드는 JSON string, `maxScore`와 `displayOrder`는 정수 number다. 기준은 `displayOrder`, 동률 시 ID 오름차순으로 반환한다. 새로 저장한 기준의 순서는 요청 배열과 같으며, 기존 초기 데이터 등은 저장된 순서를 그대로 반환한다. `criteria[].questionId`는 중복 참조이므로 반환하지 않는다.

질문과 기준 저장/교체는 하나의 짧은 쓰기 트랜잭션으로 처리한다. 어떤 저장 단계든 실패하면 전체 롤백하며, 기준 일부만 반영한 성공 응답이나 질문만 공개된 상태를 만들지 않는다. 같은 질문의 동시 저장은 직렬화하여 서로 다른 요청의 기준이 섞이지 않게 한다. MVP는 마지막으로 성공한 저장을 적용하며 버전 기반 충돌 감지/409/편집 잠금 UI는 이번 범위에 추가하지 않는다. 앞서 저장한 내용을 나중 저장이 덮을 수 있음을 관리자에게 안내한다.

공개 질문 목록/상세에는 여전히 `criteria`를 반환하지 않는다. 질문 수정은 기존 EvaluationAttempt의 답변/점수/판정/피드백/생성 시각을 변경하거나 재평가하지 않는다. 기존 결과 GET의 `questionTitle`은 현재 질문 제목이므로 제목 수정 후 달라질 수 있다. 과거 질문/기준 스냅샷이나 버전 이력은 이번 범위에 추가하지 않는다.

질문과 기준을 두 번 읽는 관리자 상세/사용자 평가는 동시 수정이 있어도 같은 저장본의 내용을 읽어야 한다. 단순히 쓰기만 원자화하거나 기본 readOnly 트랜잭션만 붙이는 것으로 두 SELECT의 일관성이 자동 보장되는 것은 아니다. 구현 시 짧은 일관된 읽기로 질문/기준을 메모리 입력값으로 확보하고 트랜잭션을 끝낸 뒤 LLM을 호출한다. 진행 중 평가는 읽은 입력값으로 완료하고, 수정 저장 후 새 평가는 변경된 내용을 사용한다. LLM 대기 동안 DB 트랜잭션/행 잠금을 유지하지 않는다.

### 10.5. 저장 전 평가 테스트

```http
POST /api/admin/questions/evaluation-preview
Content-Type: application/json
```

```json
{
  "title": "데이터베이스 인덱스",
  "content": "데이터베이스 인덱스가 무엇인지 설명해주세요.",
  "criteria": [
    { "content": "인덱스의 목적과 기본 개념을 설명한다.", "maxScore": 30 },
    { "content": "B-Tree 탐색 과정을 설명한다.", "maxScore": 30 },
    { "content": "조회 이점과 저장 공간 및 변경 비용을 설명한다.", "maxScore": 40 }
  ],
  "answer": "인덱스는 조회 성능을 높이지만 저장 공간과 데이터 변경 시 유지 비용이 발생합니다."
}
```

질문/기준은 10.3과 동일하게 검증하고 `answer`는 6절의 공백/타입/3,000 UTF-16 규칙을 적용한다. questionId/기준 DB ID는 받지 않으며 생성/수정 화면 모두 같은 API를 사용한다. 기존 질문 수정 화면에서도 DB의 기존 내용이 아닌 요청한 현재 폼 내용으로 평가한다.

서버는 요청 배열에 임시 criterionId(1부터)를 부여해 기존 프롬프트/네이티브 Schema/항목별 검증/합산/판정 로직을 재사용한다. 이 ID는 요청 내부에서만 쓰고 DB ID로 저장하거나 공개하지 않는다. 일반 사용자 평가와 동일한 모델/설정/판정 경계값을 사용하되 LLM의 비결정성 때문에 같은 답변의 점수/피드백 문자열까지 동일함을 보장하지 않는다. 관리자 전용 느슨한 응답 검증이나 별도 채점 로직은 만들지 않는다.

성공 응답은 `200 OK`이며 아래 필드만 반환한다. `questionTitle`, `answer`는 요청 원문, 점수/판정/피드백 타입과 의미는 7절과 같다.

```json
{
  "questionTitle": "데이터베이스 인덱스",
  "answer": "인덱스는 조회 성능을 높이지만 저장 공간과 데이터 변경 시 유지 비용이 발생합니다.",
  "score": 65,
  "result": "RETRY",
  "strengths": "조회 이점과 유지 비용을 설명했습니다.",
  "weaknesses": "B-Tree 탐색 과정 설명이 부족합니다.",
  "improvements": "인덱스 구조와 탐색 과정을 함께 설명하면 좋습니다."
}
```

질문/평가 기준/평가 기록에 어떤 쓰기도 하지 않으며 질문 존재 여부를 DB에서 조회할 필요도 없다. `id`, `questionId`, `createdAt`, `Location`은 없고 결과 재조회 API/결과 페이지 URL도 만들지 않는다. 응답은 관리자 편집 화면 안에서만 표시한다. 테스트 요청이나 결과를 애플리케이션 로그에 원문으로 남기지 않는다.

기존 SDK timeout/retry 정책과 180초 화면 대기 종료 정책을 따른다. LLM 오류/잘못된 응답은 저장 없이 `502 LLM_EVALUATION_FAILED`로 종료한다. 테스트 실패는 편집 내용을 저장하지 못한다는 의미가 아니며 테스트와 저장 오류 상태를 분리한다.

### 10.6. 관리자 오류와 권한 계약 초안

공통 오류는 기존 `{ code, message }` 두 필드를 사용하고 fieldErrors 같은 새 wrapper는 추가하지 않는다. 아래 401/403은 신규 계약이며 구현 시 Security 필터의 응답도 공통 JSON 형식으로 맞춘다. CORS 계층의 거부와는 별개다.

| HTTP 상태 | code | 적용 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 잘못된 ID/JSON/질문·기준·답변 타입 또는 작성 규칙 위반 |
| 401 | AUTHENTICATION_REQUIRED | 관리자 API에 인증 없이 접근하거나 인증이 유효하지 않음 |
| 403 | ACCESS_DENIED | 인증되었으나 관리자 권한 없음 |
| 404 | QUESTION_NOT_FOUND | 관리자 상세/수정 대상 질문 없음 |
| 502 | LLM_EVALUATION_FAILED | 평가 테스트의 최종 모델 호출 실패/응답 검증 실패 |
| 500 | INTERNAL_SERVER_ERROR | DB 또는 예상치 못한 내부 오류 |

GET/저장 API는 LLM을 호출하지 않으므로 502를 사용하지 않는다. preview는 DB 리소스 조회가 아니므로 QUESTION_NOT_FOUND를 사용하지 않는다. 405/415는 기존 HTTP 상태 보존 정책을 따른다. 오류 메시지에 기준/프롬프트/답변/인증 정보/원본 공급자 오류를 노출하지 않는다.

저장 응답이 유실되면 성공 여부가 불확실할 수 있다. 자동 POST/PUT 재전송 대신 관리자 목록/상세 재조회로 확인한다. POST 재시도는 새 질문을 만들 수 있고 PUT 재시도도 다른 편집을 덮거나 기준 ID를 재발급할 수 있으므로 멱등 키/중복 제거를 구현했다고 가정하지 않는다.

### 10.7. 구현 전 결정 및 병렬 작업 경계

- 관리자 인증 방식/관리자 식별 및 초기 권한 부여 방법을 확정한다. Spring Security로 서버 권한 검증을 구현하며, 방식에 따라 로그인/로그아웃 계약, 프론트 인증 전달, 쿠키/CSRF 또는 인증 헤더 및 CORS 정책을 함께 확정한다. 프론트 번들에 관리자 비밀키를 넣지 않는다. 인증 미구현 관리자 쓰기/유료 테스트 API를 운영에 공개하지 않는다.
- 제목/본문/각 기준 설명의 최대 UTF-16 길이와 전체 JSON 바이트 상한, 초과 상태/오류 계약을 확정한다. 현재 세 문자열 컬럼은 모두 TEXT이므로 DB에 255자 제한이 있는 것으로 가정하지 않는다. 추가 스키마 변경이 정말 필요한 경우에만 새 Flyway를 작성한다. 입력을 잘라 저장하거나 임의의 상한을 프론트에 먼저 적용하지 않는다.
- 동시 쓰기 직렬화 및 질문/기준의 일관된 읽기 방법을 기존 JPA/PostgreSQL 안에서 결정하고 해당 동작을 검증한다. API 버전 필드나 범용 편집 이력 시스템은 추가하지 않는다.
- 위 결정 후 API 계약/인증 타입 담당자가 공통 클라이언트·타입·MSW fixture를 먼저 준비한다. 이후 관리자 목록/진입 화면과 생성·수정 공통 편집 화면을 별도 담당자로 병렬 구현한다. 생성/수정/테스트는 같은 폼 상태와 오류 보존 정책을 공유하므로 편집 화면 파일을 서로 다른 담당자가 동시에 수정하지 않는다.
- 라우팅/공통 헤더/인증 가드/공유 API 파일은 한 명의 통합 담당자가 수정한다. 백엔드 미완료 동안에는 명시적 MSW 응답으로 개발하고, 서버 구현 이후 관리자 권한·CORS·저장 후 공개 목록 반영·preview 비저장·오래된 응답 무시를 실제 연결 검증한다.
- 실모델 평가 품질 검증과 Cloudflare/nginx 프록시 제한 확인은 별도 승인/배포 검증이다. mock 통과를 실제 공급자 지원이나 운영 연결 완료로 표시하지 않는다.
