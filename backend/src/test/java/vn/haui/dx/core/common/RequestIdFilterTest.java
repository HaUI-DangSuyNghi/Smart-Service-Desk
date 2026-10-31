package vn.haui.dx.core.common;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {
    private static MockHttpServletResponse run(MockHttpServletRequest request, String[] seenInMdc) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, new MockFilterChain() {
            @Override public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInMdc[0] = MDC.get(RequestContext.REQUEST_ID_ATTRIBUTE);
            }
        });
        return response;
    }

    @Test void keepsValidGatewayId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "7C9E6679-7425-40DE-944B-E07FC1F90AE7");
        String[] mdc = new String[1];
        var response = run(request, mdc);
        assertEquals("7c9e6679-7425-40de-944b-e07fc1f90ae7", response.getHeader(RequestIdFilter.HEADER));
        assertEquals(response.getHeader(RequestIdFilter.HEADER), request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE));
        assertEquals(response.getHeader(RequestIdFilter.HEADER), mdc[0]);
        assertNull(MDC.get(RequestContext.REQUEST_ID_ATTRIBUTE), "MDC must be cleared after the request");
    }

    @Test void replacesMissingOrInvalidId() throws Exception {
        for (String header : new String[] {null, "abc", "1-1-1-1-1", "<script>", "7c9e6679-7425-40de-944b-e07fc1f90ae7\nX: y"}) {
            var request = new MockHttpServletRequest();
            if (header != null) request.addHeader(RequestIdFilter.HEADER, header);
            String id = run(request, new String[1]).getHeader(RequestIdFilter.HEADER);
            assertNotEquals(header, id);
            assertDoesNotThrow(() -> UUID.fromString(id));
        }
    }
}
