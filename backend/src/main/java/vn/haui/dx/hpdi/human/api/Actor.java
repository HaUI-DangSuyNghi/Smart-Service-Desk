package vn.haui.dx.hpdi.human.api;
import java.util.Set;
/** Trusted workspace context, derived from verified identity; realm admin grants no workspace access. */
public record Actor(String subject, String workspace, Set<String> roles, Set<String> capabilities) {
    public Actor { roles = Set.copyOf(roles); capabilities = Set.copyOf(capabilities); }
    public boolean hasRole(String... anyOf) { return java.util.Arrays.stream(anyOf).anyMatch(roles::contains); }
}
