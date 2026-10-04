package com.eoehd1ek.tech.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties::class)
class WebConfig(
    private val corsProperties: CorsProperties,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type")
            .exposedHeaders("Location")
            .allowCredentials(false)
    }
}
