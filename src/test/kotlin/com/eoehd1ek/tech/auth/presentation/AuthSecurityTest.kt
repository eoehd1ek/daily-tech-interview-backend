package com.eoehd1ek.tech.auth.presentation

import com.eoehd1ek.tech.account.domain.AccountRole
import com.eoehd1ek.tech.auth.infrastructure.AccountPrincipal
import com.eoehd1ek.tech.config.SecurityConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

@WebMvcTest(controllers = [AuthController::class, AuthSecurityTest.TestEndpoints::class], properties = [
    "app.cors.allowed-origins[0]=http://localhost:5173",
])
@Import(SecurityConfig::class, AuthSecurityTest.TestEndpoints::class)
class AuthSecurityTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @Autowired
    private lateinit var encoder: PasswordEncoder

    @MockitoBean
    private lateinit var users: UserDetailsService

    @Test
    fun `CSRF 조회 로그인 세션 재조회 로그아웃은 쿠키 세션으로 연결된다`() {
        // given
        given(users.loadUserByUsername("admin")).willAnswer {
            AccountPrincipal(1L, "admin", checkNotNull(encoder.encode("test-password")), AccountRole.ADMIN)
        }
        val initial = mvc.perform(get("/api/auth/csrf")).andReturn()
        val session = initial.request.getSession(false) as MockHttpSession
        val oldSessionId = session.id
        val csrf = mapper.readTree(initial.response.contentAsByteArray)

        // when
        val login = mvc.perform(post("/api/auth/login").session(session)
            .header(csrf.get("headerName").asString(), csrf.get("token").asString())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"loginId":"admin","password":"test-password"}""")).andReturn()
        val current = mvc.perform(get("/api/auth/me").session(session)).andReturn().response
        val authorized = mvc.perform(get("/api/admin/questions").session(session)).andReturn().response
        val refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn().response
        val freshToken = mapper.readTree(refreshed.contentAsByteArray)
        val oldToken = mvc.perform(post("/api/admin/questions").session(session)
            .header("X-CSRF-TOKEN", csrf.get("token").asString())).andReturn().response
        val logout = mvc.perform(post("/api/auth/logout").session(session)
            .header(freshToken.get("headerName").asString(), freshToken.get("token").asString())).andReturn().response
        val afterLogout = mvc.perform(get("/api/auth/me")).andReturn().response

        // then
        assertThat(initial.response.status).isEqualTo(200)
        assertThat(csrf.propertyNames()).containsExactlyInAnyOrder("headerName", "parameterName", "token")
        assertThat(csrf.get("headerName").asString()).isEqualTo("X-CSRF-TOKEN")
        assertThat(login.response.status).isEqualTo(200)
        assertThat(session.id).isNotEqualTo(oldSessionId)
        val expected = mapper.readTree("""{"id":1,"loginId":"admin","role":"ADMIN"}""")
        assertThat(mapper.readTree(login.response.contentAsByteArray)).isEqualTo(expected)
        assertThat(current.status).isEqualTo(200)
        assertThat(mapper.readTree(current.contentAsByteArray)).isEqualTo(expected)
        assertThat(authorized.status).isEqualTo(200)
        assertThat(oldToken.status).isEqualTo(403)
        assertThat(mapper.readTree(oldToken.contentAsByteArray).get("code").asString()).isEqualTo("INVALID_CSRF_TOKEN")
        assertThat(logout.status).isEqualTo(204)
        assertThat(logout.contentAsString).isEmpty()
        assertThat(session.isInvalid).isTrue()
        assertThat(logout.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch { it.startsWith("JSESSIONID=") && it.contains("Max-Age=0") }
        assertThat(afterLogout.status).isEqualTo(401)
        assertThat(login.response.contentAsString).doesNotContain("password", "hash", "test-password")
    }

    @ParameterizedTest
    @ValueSource(strings = ["admin", "missing"])
    fun `잘못된 비밀번호와 없는 계정은 같은 오류로 거부하고 세션에 로그인하지 않는다`(loginId: String) {
        // given
        if (loginId == "admin") {
            given(users.loadUserByUsername(loginId)).willReturn(
                AccountPrincipal(1L, loginId, checkNotNull(encoder.encode("test-password")), AccountRole.ADMIN),
            )
        } else {
            given(users.loadUserByUsername(loginId)).willThrow(UsernameNotFoundException("private detail"))
        }

        // when
        val result = mvc.perform(post("/api/auth/login").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"loginId":"$loginId","password":"wrong"}""")).andReturn().response

        // then
        assertThat(result.status).isEqualTo(401)
        assertThat(mapper.readTree(result.contentAsByteArray).get("code").asString()).isEqualTo("INVALID_CREDENTIALS")
        assertThat(result.contentAsString).doesNotContain("private detail", "password", "wrong")
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "{", "{\"loginId\":\" \",\"password\":\"x\"}", "{\"loginId\":\"admin\",\"password\":\"\"}"])
    fun `잘못된 로그인 본문은 요청 오류다`(body: String) {
        // given
        val request = post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)

        // when
        val response = mvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(400)
        assertThat(mapper.readTree(response.contentAsByteArray).get("code").asString()).isEqualTo("INVALID_REQUEST")
    }

    @Test
    fun `일반 사용자 API 네 개는 인증과 CSRF 없이 접근한다`() {
        // given
        val requests = listOf(get("/api/questions"), get("/api/questions/1"),
            post("/api/questions/1/evaluation-attempts"), get("/api/evaluation-attempts/1"))

        // when
        val statuses = requests.map { mvc.perform(it).andReturn().response.status }

        // then
        assertThat(statuses).containsExactly(200, 200, 200, 200)
    }

    @Test
    fun `관리자 모든 작업은 비인증을 거부하고 USER를 거부하며 ADMIN을 허용한다`() {
        // given
        val requests = listOf(get("/api/admin/questions"), get("/api/admin/questions/1"),
            post("/api/admin/questions"), put("/api/admin/questions/1"), post("/api/admin/questions/evaluation-preview"))

        // when
        val anonymous = requests.map { mvc.perform(it.with(csrf())).andReturn().response.status }
        val nonAdmin = requests.map { mvc.perform(it.with(csrf()).with(user("user").roles("USER"))).andReturn().response.status }
        val admin = requests.map { mvc.perform(it.with(csrf()).with(user("admin").roles("ADMIN"))).andReturn().response.status }

        // then
        assertThat(anonymous).containsOnly(401)
        assertThat(nonAdmin).containsOnly(403)
        assertThat(admin).containsOnly(200)
    }

    @Test
    fun `관리자 로그인 로그아웃 쓰기에 CSRF가 없으면 거부한다`() {
        // given
        val requests = listOf(post("/api/auth/login"), post("/api/auth/logout"),
            post("/api/admin/questions"), put("/api/admin/questions/1"), post("/api/admin/questions/evaluation-preview"))

        // when
        val responses = requests.map { mvc.perform(it.with(user("admin").roles("ADMIN"))).andReturn().response }

        // then
        responses.forEach {
            assertThat(it.status).isEqualTo(403)
            assertThat(mapper.readTree(it.contentAsByteArray).get("code").asString()).isEqualTo("INVALID_CSRF_TOKEN")
        }
    }

    @Test
    fun `목록의 다른 메서드와 새 경로는 ADMIN도 기본 차단된다`() {
        // given
        val requests = listOf(post("/api/questions"), get("/api/private"), get("/login"))

        // when
        val responses = requests.map { mvc.perform(it.with(csrf()).with(user("admin").roles("ADMIN"))).andReturn().response }

        // then
        responses.forEach {
            assertThat(it.status).isEqualTo(403)
            assertThat(mapper.readTree(it.contentAsByteArray).get("code").asString()).isEqualTo("ACCESS_DENIED")
        }
    }

    @Test
    fun `허용 Origin의 인증 권한 오류도 credentials CORS를 반환한다`() {
        // given
        val anonymous = get("/api/admin/questions").header(HttpHeaders.ORIGIN, "http://localhost:5173")
        val denied = get("/api/admin/questions").header(HttpHeaders.ORIGIN, "http://localhost:5173")
            .with(user("user").roles("USER"))

        // when
        val responses = listOf(anonymous, denied).map { mvc.perform(it).andReturn().response }

        // then
        assertThat(responses.map { it.status }).containsExactly(401, 403)
        responses.forEach {
            assertThat(it.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost:5173")
            assertThat(it.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true")
            assertThat(mapper.readTree(it.contentAsByteArray).propertyNames()).containsExactlyInAnyOrder("code", "message")
        }
    }

    @RestController
    class TestEndpoints {
        @GetMapping("/api/questions", "/api/questions/{id}", "/api/evaluation-attempts/{id}",
            "/api/admin/questions", "/api/admin/questions/{id}", "/api/private")
        fun get(): String = "ok"

        @PostMapping("/api/questions", "/api/questions/{id}/evaluation-attempts", "/api/admin/questions",
            "/api/admin/questions/evaluation-preview")
        fun post(): String = "ok"

        @PutMapping("/api/admin/questions/{id}")
        fun put(): String = "ok"
    }
}
