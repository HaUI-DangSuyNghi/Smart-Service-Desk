package vn.haui.dx.core.identity;

import java.util.Map;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityApi {
    @GetMapping("/api/core/v1/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("sub", jwt.getSubject(), "groups", jwt.hasClaim("groups") ? jwt.getClaim("groups") : List.of(),
            "realmAccess", jwt.hasClaim("realm_access") ? jwt.getClaim("realm_access") : Map.of());
    }
}
