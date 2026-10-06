package com.eoehd1ek.tech.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.admin")
class AdminProperties(
    val loginId: String = "",
    val password: String = "",
)
