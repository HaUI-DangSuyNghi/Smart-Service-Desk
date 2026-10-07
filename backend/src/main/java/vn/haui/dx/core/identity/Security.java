package vn.haui.dx.core.identity;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@Configuration
public class Security {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults())).build();
    }
    public static void workspace(Jwt jwt, String workspace) {
        List<String> groups = jwt.getClaimAsStringList("groups");
        if (groups == null || !groups.contains("/" + workspace)) throw new ResponseStatusException(FORBIDDEN);
    }
    public static void role(Jwt jwt, String role) {
        Map<String, Object> access = jwt.getClaim("realm_access");
        if (access == null || !(access.get("roles") instanceof List<?> roles) || !roles.contains(role))
            throw new ResponseStatusException(FORBIDDEN);
    }
}
