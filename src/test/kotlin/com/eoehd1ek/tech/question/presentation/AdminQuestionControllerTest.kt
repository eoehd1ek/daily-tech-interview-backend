package com.eoehd1ek.tech.question.presentation

import com.eoehd1ek.tech.config.SecurityConfig
import com.eoehd1ek.tech.question.application.AdminQuestionService
import com.eoehd1ek.tech.question.application.QuestionService
import com.eoehd1ek.tech.question.application.exception.QuestionNotFoundException
import com.eoehd1ek.tech.question.application.result.AdminQuestionDetailResult
import com.eoehd1ek.tech.question.domain.EvaluationCriterion
import com.eoehd1ek.tech.question.domain.Question
import com.eoehd1ek.tech.question.domain.QuestionCategory
import com.eoehd1ek.tech.question.presentation.request.AdminCriterionRequest
import com.eoehd1ek.tech.question.presentation.request.AdminQuestionRequest
import com.eoehd1ek.tech.question.presentation.response.AdminCriterionResponse
import com.eoehd1ek.tech.question.presentation.response.AdminQuestionResponse
import com.eoehd1ek.tech.question.presentation.response.QuestionResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import tools.jackson.databind.ObjectMapper

@WebMvcTest(AdminQuestionController::class)
@Import(SecurityConfig::class)
@WithMockUser(roles = ["ADMIN"])
class AdminQuestionControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var service: AdminQuestionService

    @MockitoBean
    private lateinit var questionService: QuestionService

    @MockitoBean
    private lateinit var userDetailsService: UserDetailsService

    @ParameterizedTest
    @ValueSource(longs = [1L, 9_007_199_254_740_991L])
    fun `관리자 상세는 안전 정수 ID를 허용하고 질문과 기준을 순서대로 반환한다`(questionId: Long) {
        // given
        val question = Question(" 제목 ", "본문\n원문")
        ReflectionTestUtils.setField(question, "id", questionId)
        val criteria = listOf(
            EvaluationCriterion(questionId, "나중 기준", 40, 20),
            EvaluationCriterion(questionId, "먼저 기준", 60, 1),
        )
        criteria.forEachIndexed { index, criterion ->
            ReflectionTestUtils.setField(criterion, "id", 20L + index)
        }
        val categories = listOf(QuestionCategory.FRONTEND, QuestionCategory.BACKEND)
        given(service.getQuestion(questionId)).willReturn(AdminQuestionDetailResult(question, criteria, categories))
        val expected = AdminQuestionResponse(
            questionId, question.title, question.content, listOf(
                AdminCriterionResponse(21L, "먼저 기준", 60, 1),
                AdminCriterionResponse(20L, "나중 기준", 40, 20),
            ), categories
        )

        // when
        val response = mockMvc.perform(get("/api/admin/questions/$questionId")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body).isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(expected)))
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "title", "content", "criteria", "categories")
        assertThat(body.get("categories").toList().map { it.asString() }).containsExactly("FRONTEND", "BACKEND")
        body.get("criteria").forEach {
            assertThat(it.propertyNames()).containsExactlyInAnyOrder("id", "content", "maxScore", "displayOrder")
        }
        verify(service).getQuestion(questionId)
        verifyNoInteractions(questionService)
    }

    @ParameterizedTest
    @MethodSource("categorySelections")
    fun `관리자 상세는 미분류와 단일 및 복수 카테고리를 배열로 반환한다`(categories: List<QuestionCategory>) {
        // given
        val question = Question("제목", "본문")
        ReflectionTestUtils.setField(question, "id", 10L)
        val criterion = EvaluationCriterion(10L, "기준", 100, 1)
        ReflectionTestUtils.setField(criterion, "id", 20L)
        val canonicalCategories = categories.sortedBy { it.ordinal }
        given(service.getQuestion(10L))
            .willReturn(AdminQuestionDetailResult(question, listOf(criterion), canonicalCategories))

        // when
        val response = mockMvc.perform(get("/api/admin/questions/10")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "title", "content", "criteria", "categories")
        assertThat(body.get("categories").isArray).isTrue()
        assertThat(body.get("categories").toList().map { it.asString() })
            .containsExactlyElementsOf(canonicalCategories.map { it.name })
        verify(service).getQuestion(10L)
        verifyNoInteractions(questionService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "abc", "1.5", "9223372036854775808"])
    fun `잘못된 관리자 상세 ID는 Service 호출 없이 요청 오류를 반환한다`(questionId: String) {
        // given
        val request = get("/api/admin/questions/$questionId")

        // when
        val response = mockMvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INVALID_REQUEST")
        verifyNoInteractions(service, questionService)
    }

    @Test
    fun `없는 관리자 상세는 질문 없음 오류를 반환한다`() {
        // given
        given(service.getQuestion(10L)).willThrow(QuestionNotFoundException())

        // when
        val response = mockMvc.perform(get("/api/admin/questions/10")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(404)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("QUESTION_NOT_FOUND")
    }

    @Test
    fun `관리자 상세 조회 실패는 안전한 서버 오류를 반환한다`() {
        // given
        given(service.getQuestion(10L)).willThrow(IllegalStateException("private database details"))

        // when
        val response = mockMvc.perform(get("/api/admin/questions/10")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(500)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(response.contentAsString).doesNotContain("private database details")
    }

    @Test
    fun `관리자 목록은 질문 ID 제목과 미분류 단일 복수 카테고리를 JSON 배열로 반환한다`() {
        // given
        val questions = listOf(
            QuestionResponse(10L, "미분류 질문", emptyList()),
            QuestionResponse(20L, "단일 분류 질문", listOf(QuestionCategory.BACKEND)),
            QuestionResponse(30L, "복수 분류 질문", listOf(QuestionCategory.FRONTEND, QuestionCategory.BACKEND)),
        )
        given(questionService.getQuestions()).willReturn(questions)

        // when
        val response = mockMvc.perform(get("/api/admin/questions")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body).isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(questions)))
        body.forEach {
            assertThat(it.propertyNames()).containsExactlyInAnyOrder("id", "title", "categories")
            assertThat(it.get("categories").isArray).isTrue()
        }
        assertThat(body.get(0).get("categories").size()).isZero()
        assertThat(body.get(1).get("categories").toList().map { it.asString() }).containsExactly("BACKEND")
        assertThat(body.get(2).get("categories").toList().map { it.asString() }).containsExactly("FRONTEND", "BACKEND")
        verify(questionService).getQuestions()
        verifyNoInteractions(service)
    }

    @Test
    fun `관리자 목록에 질문이 없으면 빈 배열을 반환한다`() {
        // given
        given(questionService.getQuestions()).willReturn(emptyList())

        // when
        val response = mockMvc.perform(get("/api/admin/questions")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(objectMapper.readTree(response.contentAsByteArray).isArray).isTrue()
        assertThat(objectMapper.readTree(response.contentAsByteArray).size()).isZero()
        verifyNoInteractions(service)
    }

    @Test
    fun `관리자 목록 조회 오류는 안전한 서버 오류를 반환한다`() {
        // given
        given(questionService.getQuestions()).willThrow(IllegalStateException("private database details"))

        // when
        val response = mockMvc.perform(get("/api/admin/questions")).andReturn().response

        // then
        assertThat(response.status).isEqualTo(500)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(response.contentAsString).doesNotContain("private database details")
        verifyNoInteractions(service)
    }

    @ParameterizedTest
    @MethodSource("categorySelections")
    fun `관리자 질문 생성은 카테고리를 전달하고 고정 순서의 상세와 생성 상태 및 Location을 반환한다`(categories: List<QuestionCategory>) {
        // given
        val request = AdminQuestionRequest(" 제목 ", "본문\n원문", listOf(AdminCriterionRequest("기준", 100, 1)), categories)
        val canonicalCategories = categories.sortedBy { it.ordinal }
        val expected = AdminQuestionResponse(
            10L, request.title, request.content,
            listOf(AdminCriterionResponse(20L, "기준", 100, 1)), canonicalCategories
        )
        given(service.create(request)).willReturn(expected)

        // when
        val response = mockMvc.perform(
            post("/api/admin/questions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request))
        )
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(201)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(response.getHeader("Location")).isEqualTo("/api/admin/questions/10")
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body).isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(expected)))
        assertThat(body.get("categories").isArray).isTrue()
        assertThat(body.get("categories").toList().map { it.asString() })
            .containsExactlyElementsOf(canonicalCategories.map { it.name })
        verify(service).create(request)
        verifyNoInteractions(questionService)
    }

    @ParameterizedTest
    @MethodSource("categorySelections")
    fun `질문 수정은 입력 상한과 카테고리 교체 및 해제를 허용하고 고정 순서로 반환한다`(categories: List<QuestionCategory>) {
        // given
        val request = AdminQuestionRequest(
            "가".repeat(200), "나".repeat(10000),
            List(10) { AdminCriterionRequest("다".repeat(1000), 10, it + 1) }, categories
        )
        val canonicalCategories = categories.sortedBy { it.ordinal }
        val expected = AdminQuestionResponse(
            10L, request.title, request.content,
            request.criteria.mapIndexed { index, item ->
                AdminCriterionResponse(20L + index, requireNotNull(item).content, item.maxScore, index + 1)
            }, canonicalCategories
        )
        given(service.update(10L, request)).willReturn(expected)

        // when
        val response = mockMvc.perform(
            put("/api/admin/questions/10")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request))
        )
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(200)
        assertThat(MediaType.parseMediaType(requireNotNull(response.contentType))).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(response.getHeader("Location")).isNull()
        val body = objectMapper.readTree(response.contentAsByteArray)
        assertThat(body).isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(expected)))
        assertThat(body.get("categories").isArray).isTrue()
        assertThat(body.get("categories").toList().map { it.asString() })
            .containsExactlyElementsOf(canonicalCategories.map { it.name })
        verify(service).update(10L, request)
        verifyNoInteractions(questionService)
    }

    @ParameterizedTest
    @MethodSource("invalidBodies", "invalidCategoryBodies")
    fun `잘못된 질문 입력은 생성과 수정 모두 Service 호출 없이 거부한다`(body: String) {
        // given
        val requests = listOf(post("/api/admin/questions"), put("/api/admin/questions/10"))

        // when
        val responses = requests.map {
            mockMvc.perform(it.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().response
        }

        // then
        responses.forEach {
            assertThat(it.status).isEqualTo(400)
            val json = objectMapper.readTree(it.contentAsByteArray)
            assertThat(json.propertyNames()).containsExactlyInAnyOrder("code", "message")
            assertThat(json.get("code").asString()).isEqualTo("INVALID_REQUEST")
        }
        verifyNoInteractions(service, questionService)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "9007199254740992", "abc"])
    fun `잘못된 수정 ID는 Service 호출 없이 요청 오류를 반환한다`(id: String) {
        // given
        val body =
            """{"title":"제목","content":"본문","criteria":[{"content":"기준","maxScore":100,"displayOrder":1}],"categories":[]}"""

        // when
        val response = mockMvc.perform(
            put("/api/admin/questions/$id")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)
        ).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INVALID_REQUEST")
        verifyNoInteractions(service)
    }

    @Test
    fun `존재하지 않는 질문 수정은 질문 없음 오류를 반환한다`() {
        // given
        val request = AdminQuestionRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 1)), emptyList())
        given(service.update(10L, request)).willThrow(QuestionNotFoundException())

        // when
        val response = mockMvc.perform(
            put("/api/admin/questions/10")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request))
        )
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(404)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("QUESTION_NOT_FOUND")
    }

    @Test
    fun `저장 실패는 안전한 서버 오류를 반환한다`() {
        // given
        val request = AdminQuestionRequest("제목", "본문", listOf(AdminCriterionRequest("기준", 100, 1)), emptyList())
        given(service.create(request)).willThrow(IllegalStateException("private database details"))

        // when
        val response = mockMvc.perform(
            post("/api/admin/questions")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request))
        )
            .andReturn().response

        // then
        assertThat(response.status).isEqualTo(500)
        assertThat(objectMapper.readTree(response.contentAsByteArray).get("code").asString())
            .isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(response.contentAsString).doesNotContain("private database details")
    }

    companion object {
        @JvmStatic
        fun categorySelections(): List<Arguments> = listOf(
            emptyList(),
            listOf(QuestionCategory.FRONTEND),
            listOf(QuestionCategory.BACKEND),
            listOf(QuestionCategory.FRONTEND, QuestionCategory.BACKEND),
            listOf(QuestionCategory.BACKEND, QuestionCategory.FRONTEND),
        ).map { Arguments.of(it) }

        @JvmStatic
        fun invalidCategoryBodies(): List<String> {
            val mapper = ObjectMapper()
            val valid = mapOf(
                "title" to "제목",
                "content" to "본문",
                "criteria" to listOf(mapOf("content" to "기준", "maxScore" to 100, "displayOrder" to 1)),
                "categories" to emptyList<String>(),
            )
            val invalidCategories = listOf(
                null,
                "FRONTEND",
                emptyMap<String, String>(),
                mapOf("category" to "FRONTEND"),
                0,
                1,
                true,
                false,
                listOf(null),
                listOf(0),
                listOf(1),
                listOf(-1),
                listOf(0.0),
                listOf(true),
                listOf(false),
                listOf(mapOf("category" to "FRONTEND")),
                listOf(listOf("FRONTEND")),
                listOf("0"),
                listOf("1"),
                listOf("COMMON"),
                listOf("frontend"),
                listOf("backend"),
                listOf("Frontend"),
                listOf(" FRONTEND "),
                listOf("BACKEND "),
                listOf(""),
                listOf(" "),
                listOf("FRONTEND", null),
                listOf("FRONTEND", 1),
                listOf("FRONTEND", "COMMON"),
                listOf("FRONTEND", "FRONTEND"),
                listOf("BACKEND", "BACKEND"),
                List(6) { if (it % 2 == 0) "FRONTEND" else "BACKEND" },
                List(7) { "FRONTEND" },
            )
            val bodies = listOf(valid - "categories") + invalidCategories.map { valid + ("categories" to it) }
            return bodies.map(mapper::writeValueAsString)
        }

        @JvmStatic
        fun invalidBodies(): List<String> {
            val mapper = ObjectMapper()
            val criterion = mapOf("content" to "기준", "maxScore" to 100, "displayOrder" to 1)
            val valid = mapOf(
                "title" to "제목", "content" to "본문", "criteria" to listOf(criterion),
                "categories" to emptyList<String>()
            )
            val bodies = mutableListOf<Map<String, Any?>>()
            for ((field, limit) in listOf("title" to 200, "content" to 10000)) {
                bodies += valid - field
                for (value in listOf(null, " ", emptyMap<String, String>(), "가".repeat(limit + 1))) {
                    bodies += valid + (field to value)
                }
            }
            bodies += valid - "criteria"
            for (value in listOf(null, "invalid", emptyList<Any>(), listOf(null), List(11) { criterion })) {
                bodies += valid + ("criteria" to value)
            }
            bodies += valid + ("criteria" to listOf(criterion - "content"))
            for (value in listOf(null, "", " ", emptyMap<String, String>(), "가".repeat(1001))) {
                bodies += valid + ("criteria" to listOf(criterion + ("content" to value)))
            }
            bodies += valid + ("criteria" to listOf(criterion - "maxScore"))
            for (value in listOf(null, 0, -1, 99, 101, "invalid", true)) {
                bodies += valid + ("criteria" to listOf(criterion + ("maxScore" to value)))
            }
            bodies += valid + ("criteria" to listOf(criterion, criterion))
            bodies += valid + ("criteria" to listOf(criterion - "displayOrder"))
            bodies += valid + ("criteria" to listOf(criterion + ("displayOrder" to null)))
            bodies += valid + ("criteria" to listOf(
                criterion + ("maxScore" to 50), criterion + ("maxScore" to 50),
            ))
            return bodies.map(mapper::writeValueAsString)
        }
    }
}
