package com.ssafy.upcycle.common.security.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = RestAccessDeniedHandlerTests.FilterDeniedController.class)
@Import({
        RestAccessDeniedHandler.class,
        RestAccessDeniedHandlerTests.FilterDeniedSecurityConfiguration.class
})
class RestAccessDeniedHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestAccessDeniedHandler accessDeniedHandler;

    @Test
    @WithMockUser(roles = "USER")
    void filterChainAccessDenialUsesCommonForbiddenResponse() throws Exception {
        mockMvc.perform(get("/test/security/filter-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void unrelatedExceptionPassesThroughResolverWithoutWritingResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(accessDeniedHandler.resolveException(
                        request, response, new Object(), new IllegalStateException("unrelated")))
                .isNull();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @RestController
    static class FilterDeniedController {

        @GetMapping("/test/security/filter-denied")
        String endpoint() {
            return "denied";
        }
    }

    @TestConfiguration
    @EnableWebSecurity
    static class FilterDeniedSecurityConfiguration {

        @Bean
        SecurityFilterChain filterDeniedSecurityFilterChain(
                HttpSecurity http,
                RestAccessDeniedHandler accessDeniedHandler) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/test/security/filter-denied").hasRole("ADMIN")
                            .anyRequest().permitAll())
                    .exceptionHandling(exceptions ->
                            exceptions.accessDeniedHandler(accessDeniedHandler));
            return http.build();
        }
    }
}
