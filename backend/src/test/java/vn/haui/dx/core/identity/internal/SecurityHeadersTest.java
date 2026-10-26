package vn.haui.dx.core.identity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityHeadersTest {
    @TestConfiguration
    @EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {
        @Bean ObjectMapper json() { return new ObjectMapper(); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
    }

    @Test void headersArePresentBeforeDownstreamCodeCanFail() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var filter = context.getBean(SecurityFilterChain.class).getFilters().stream()
                .filter(HeaderWriterFilter.class::isInstance).map(HeaderWriterFilter.class::cast).findFirst().orElseThrow();
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/protected"), response, (request, output) -> {
                assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
                assertEquals("DENY", response.getHeader("X-Frame-Options"));
                assertTrue(response.getHeader("Cache-Control").contains("no-store"));
                response.sendError(500);
            });
            assertEquals(500, response.getStatus());
            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        }
    }
}
