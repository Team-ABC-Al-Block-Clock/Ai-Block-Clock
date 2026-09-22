package com.aiblockclock.api.config;

import com.aiblockclock.support.response.ApiResult;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.UnannotatedProtectedController.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void CurrentUser가_없는_API도_기본적으로_인증이_필요하다() throws Exception {
        mvc.perform(get("/api/v1/security-test/unannotated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @RestController
    static class UnannotatedProtectedController {

        @GetMapping("/api/v1/security-test/unannotated")
        ApiResult<Void> unannotated() {
            return ApiResult.success();
        }
    }
}
