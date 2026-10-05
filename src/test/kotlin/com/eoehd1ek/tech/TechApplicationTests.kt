package com.eoehd1ek.tech

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class TechApplicationTests {

    @MockitoBean
    private lateinit var chatModel: OpenAiChatModel

    @Test
    fun contextLoads() {
    }

}
