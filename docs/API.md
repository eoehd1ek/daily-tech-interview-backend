# MVP API Contract

## 1. 목적과 적용 범위

혼자 기술 면접을 준비하는 사용자가 질문을 선택하고 주관식 답변을 제출한 뒤, LLM 평가 점수와 피드백을 확인하는 흐름을 정의한다. 이 문서는 구현 예정인 계약이며 현재 API가 구현되어 있다는 의미는 아니다.

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

| HTTP 상태 | code | 발생 조건 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | 잘못된 JSON, 필수 답변 누락/잘못된 타입/빈 답변, 잘못된 경로 ID |
| 404 | QUESTION_NOT_FOUND | 질문이 존재하지 않음 |
| 404 | EVALUATION_ATTEMPT_NOT_FOUND | 평가 기록이 존재하지 않음 |
| 502 | LLM_EVALUATION_FAILED | LLM 호출 또는 응답 검증이 모든 재시도 후에도 실패 |
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
| answer | string | 예 | 누락, null, 문자열 외 타입, 빈 문자열, 공백만 있는 문자열 거부 |

답변은 사용자가 제출한 원문을 저장하고 결과 조회에 반환한다. 공백 검증을 이유로 원문을 임의로 변경하지 않는다. 최대 답변 길이는 아직 확정하지 않았으며, 구현 전에 결정할 사항이다.

### 처리 흐름

1. 요청을 검증하고 QuestionRepository로 질문을 조회한다.
2. EvaluationCriterionRepository로 해당 `questionId`의 기준 목록을 `displayOrder` 오름차순으로 조회한다. 같은 순서 값에서는 ID 오름차순으로 조회한다.
3. 저장된 기준은 정상이라고 가정하고 개수/양의 배점/배점 합계를 재검증하지 않은 채 질문, 기준, 답변을 Spring AI를 통해 OpenRouter에 전달한다.
4. LLM이 반환한 항목별 점수와 피드백을 파싱하고 검증한다.
5. 백엔드가 총점을 합산하고 FAIL/RETRY/PASS를 판정한다.
6. 완료된 평가 기록을 DB에 저장한다.
7. 저장 성공 후 평가 ID와 결과를 반환한다.

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

6절의 성공 응답과 동일한 JSON 객체를 반환한다. DB에서 저장된 결과를 읽으며 LLM을 다시 호출하지 않는다. 프론트엔드 결과 페이지(`/results/{attemptId}`)를 새로고침해도 이 API로 결과를 복원한다. 해당 페이지 경로는 프론트엔드 경로이며 백엔드 API가 아니다.

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
| createdAt | string | 서버에서 기록한 평가 생성 시각, UTC ISO 8601 |

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

- 재시도 횟수는 환경설정으로 관리한다. 제안 설정명은 `LLM_MAX_RETRIES`이며 0 이상의 정수다. 아직 기존 설정에 추가되어 있지 않다.
- 값 N은 최초 호출을 제외한 추가 재시도 횟수다. 예를 들어 N=2이면 최대 3회 호출한다. 이 예시는 기본값 확정을 의미하지 않는다.
- 호출 오류, 타임아웃, JSON 파싱 실패, 점수/피드백 검증 실패는 유효한 평가를 얻지 못한 실패로 취급해 설정 범위 안에서 재시도한다.
- 모든 시도가 실패하면 `502 LLM_EVALUATION_FAILED`를 반환한다. 검증 실패 결과를 저장하거나 임의 점수로 대체하지 않는다.
- 잘못된 요청, 없는 질문, DB 조회/저장 실패는 LLM 재시도 대상이 아니다. DB 저장 실패를 이유로 LLM을 다시 호출하지 않는다. 저장 기준 오류를 탐지하는 별도 검증 흐름은 추가하지 않는다.
- Spring AI/HTTP 클라이언트의 기존 재시도 설정을 확인해 중첩 재시도로 실제 호출 횟수가 계약을 초과하지 않도록 한다.
- 개별 호출 및 전체 요청에 유한한 시간 제한을 둔다. 정확한 시간 제한, 재시도 기본 횟수/간격은 구현 전에 확정한다. 프론트엔드와 배포 프록시 시간 제한도 동기식 평가 시간 예산에 맞춰 확인한다.

## 9. 구현 전 확인할 사항

아래는 이번 문서에서 임의의 수치나 운영 정책으로 확정하지 않았다. 관련 작업에 착수하기 전에 결정하고 필요하면 계약과 TODO를 함께 갱신한다.

- 답변의 최대 길이와 그에 맞는 요청 크기 제한.
- LLM 재시도 기본 횟수, 재시도 간격, 개별 호출/전체 요청 시간 제한.
- 실제 배포에 사용할 OpenRouter 모델과 키. 기존 `CHAT_MODEL` 설정을 사용하되 예시 값을 확정 모델로 간주하지 않는다.
- 개발/배포 프론트엔드 Origin 및 필요한 CORS 허용 값.
- 익명 답변/평가 기록의 보관 기간과 운영상 정리 정책. 삭제 API나 자동 정리 작업은 현재 범위에 추가하지 않는다.
