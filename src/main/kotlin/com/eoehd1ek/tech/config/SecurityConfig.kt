package com.eoehd1ek.tech.config

import com.eoehd1ek.tech.auth.infrastructure.SecurityErrorHandler
import com.eoehd1ek.tech.config.properties.AdminProperties
import com.eoehd1ek.tech.config.properties.CorsProperties
import jakarta.servlet.DispatcherType
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository
import org.springframework.security.web.savedrequest.NullRequestCache
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties::class, AdminProperties::class)
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(
        userDetailsService: UserDetailsService,
        encoder: PasswordEncoder
    ): AuthenticationManager {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(encoder)
        return ProviderManager(provider)
    }

    @Bean
    fun securityContextRepository() =
        HttpSessionSecurityContextRepository()

    @Bean
    fun csrfTokenRepository() =
        HttpSessionCsrfTokenRepository()

    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = properties.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "OPTIONS")
            allowedHeaders = listOf("Content-Type", "X-CSRF-TOKEN")
            exposedHeaders = listOf("Location")
            allowCredentials = true
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
        csrfTokenRepository: HttpSessionCsrfTokenRepository,
        securityContextRepository: HttpSessionSecurityContextRepository,
        mapper: ObjectMapper,
    ): SecurityFilterChain {
        val securityErrorHandler = SecurityErrorHandler(mapper)

        http
            .cors { it.configurationSource(corsConfigurationSource) }
            .csrf {
                it.csrfTokenRepository(csrfTokenRepository)
                    .ignoringRequestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher(
                            HttpMethod.POST,
                            "/api/questions/{questionId}/evaluation-attempts"
                        )
                    )
            }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .requestCache { it.requestCache(NullRequestCache()) }
            .sessionManagement { it.sessionFixation { fixation -> fixation.changeSessionId() } }
            .authorizeHttpRequests {
                it
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()

                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/questions",
                        "/api/questions/{questionId}",
                        "/api/evaluation-attempts/{attemptId}",
                        "/api/auth/csrf"
                    ).permitAll()

                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/questions/{questionId}/evaluation-attempts",
                        "/api/auth/login"
                    ).permitAll()

                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/auth/me"
                    ).authenticated()

                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/logout"
                    ).authenticated()

                    .requestMatchers(
                        "/api/admin/**"
                    ).hasRole("ADMIN")

                    .anyRequest()
                    .denyAll()
            }
            .exceptionHandling {
                it
                    .authenticationEntryPoint(securityErrorHandler)
                    .accessDeniedHandler(securityErrorHandler)
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
        return http.build()
    }
}
