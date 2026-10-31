package vn.haui.dx.core.identity.internal;

import java.io.IOException;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.haui.dx.core.common.ErrorCode;
import vn.haui.dx.core.common.Problems;

@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper json) throws Exception {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        // 401: giữ header WWW-Authenticate chuẩn, thêm body lỗi chung
        AuthenticationEntryPoint unauthenticated = (request, response, error) -> {
            bearer.commence(request, response, error);
            write(json, request, response, ErrorCode.UNAUTHENTICATED, "Authentication required");
        };
        AccessDeniedHandler forbidden = (request, response, error) ->
            write(json, request, response, ErrorCode.FORBIDDEN, "Access denied");
        return http.csrf(csrf -> csrf.disable())
            .headers(headers -> headers.addObjectPostProcessor(
                new org.springframework.security.config.annotation.ObjectPostProcessor<org.springframework.security.web.header.HeaderWriterFilter>() {
                    @Override
                    public <Filter extends org.springframework.security.web.header.HeaderWriterFilter> Filter postProcess(Filter filter) {
                        filter.setShouldWriteHeadersEagerly(true);
                        return filter;
                    }
                }))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults())
                .authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden))
            .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthenticated).accessDeniedHandler(forbidden))
            .build();
    }

    private static void write(ObjectMapper json, HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
        throws IOException {
        ProblemDetail problem = Problems.of(code, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
