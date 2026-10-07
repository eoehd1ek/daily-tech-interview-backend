package com.eoehd1ek.tech.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

@WebMvcTest(ManagementSecurityConfigTest.TestEndpoints::class, properties = ["management.server.port=9091"])
@Import(SecurityConfig::class, ManagementSecurityConfig::class, ManagementSecurityConfigTest.TestEndpoints::class)
class ManagementSecurityConfigTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var mapper: ObjectMapper

    @MockitoBean
    private lateinit var users: UserDetailsService

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health", "/actuator/prometheus"])
    fun `수집용 GET은 관리 포트에서만 익명 접근을 허용한다`(path: String) {
        // given
        val internal = get(path).with { it.apply { localPort = 9091 } }
        val external = get(path).with { it.apply { localPort = 8080 } }

        // when
        val internalResponse = mvc.perform(internal).andReturn().response
        val externalResponse = mvc.perform(external).andReturn().response

        // then
        assertThat(internalResponse.status).isEqualTo(200)
        assertThat(externalResponse.status).isEqualTo(401)
        assertThat(mapper.readTree(externalResponse.contentAsByteArray).get("code").asString())
            .isEqualTo("AUTHENTICATION_REQUIRED")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/env", "/actuator/metrics", "/api/questions", "/api/admin/questions"])
    fun `관리 포트도 지정하지 않은 경로는 거부한다`(path: String) {
        // given
        val request = get(path).with { it.apply { localPort = 9091 } }

        // when
        val response = mvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `관리 포트에 쓰기 요청은 허용하지 않는다`() {
        // given
        val request = post("/actuator/prometheus").with { it.apply { localPort = 9091 } }

        // when
        val response = mvc.perform(request).andReturn().response

        // then
        assertThat(response.status).isEqualTo(403)
    }

    @RestController
    class TestEndpoints {
        @GetMapping("/actuator/health", "/actuator/prometheus", "/actuator/env", "/actuator/metrics",
            "/api/questions", "/api/admin/questions")
        fun get(): String = "ok"
    }
}
