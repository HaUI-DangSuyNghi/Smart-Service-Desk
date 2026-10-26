package vn.haui.dx.core.identity.internal;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.haui.dx.core.common.CoreException;
import vn.haui.dx.core.identity.api.RequirePermission;
import vn.haui.dx.core.identity.api.WorkspacePermissions;

@Configuration
public class EndUserPermissionConfiguration implements WebMvcConfigurer {
    private final WorkspacePermissions permissions;

    public EndUserPermissionConfiguration(WorkspacePermissions permissions) { this.permissions = permissions; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (handler instanceof HandlerMethod method) {
                    var required = method.getMethodAnnotation(RequirePermission.class);
                    if (required != null) {
                        var variables = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                        if (variables == null || !(variables.get("workspace") instanceof String workspace))
                            throw CoreException.forbidden("Workspace required");
                        permissions.require(workspace, required.value());
                    }
                }
                return true;
            }
        });
    }
}
