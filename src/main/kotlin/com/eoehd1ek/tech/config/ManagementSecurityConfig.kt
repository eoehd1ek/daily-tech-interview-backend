package com.eoehd1ek.tech.config

import com.eoehd1ek.tech.auth.infrastructure.SecurityErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RequestMatcher
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
class ManagementSecurityConfig {
    @Bean
    @Order(1)
    fun managementSecurityFilterChain(http: HttpSecurity, environment: Environment, mapper: ObjectMapper): SecurityFilterChain {
        val errors = SecurityErrorHandler(mapper)
        // The main API port must never inherit the collector's anonymous access.
        val managementPort = RequestMatcher { request ->
            val port = environment.getProperty("local.management.port", Int::class.java)
                ?: environment.getProperty("management.server.port", Int::class.java, 9091)
            request.localPort == port
        }
        http
            .securityMatcher(managementPort)
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                    .anyRequest().denyAll()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .exceptionHandling { it.authenticationEntryPoint(errors).accessDeniedHandler(errors) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
        return http.build()
    }
}
