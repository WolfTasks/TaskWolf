package com.taskowolf.i18n

import com.taskowolf.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.junit.jupiter.api.Test

class AuthErrorLocalizationTest : IntegrationTestBase() {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun register(email: String, lang: String) = mockMvc.perform(
        post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Language", lang)
            .content("""{"email":"$email","displayName":"User","password":"password123"}""")
    )

    @Test
    fun `duplicate registration returns localized german conflict`() {
        register("dupe-de@test.com", "en").andExpect(status().isCreated)
        register("dupe-de@test.com", "de")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("E-Mail bereits registriert: dupe-de@test.com"))
    }

    @Test
    fun `duplicate registration returns english by default`() {
        register("dupe-en@test.com", "en").andExpect(status().isCreated)
        register("dupe-en@test.com", "en")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Email already registered: dupe-en@test.com"))
    }
}
