package io.github.doubletree.iam.audit.infrastructure;

import io.github.doubletree.iam.audit.api.AuditRequestContext;
import io.github.doubletree.iam.audit.api.AuditRequestMetadata;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuditRequestContextFilter extends OncePerRequestFilter {

    static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = safeCorrelationId(request.getHeader(CORRELATION_HEADER));
        response.setHeader(CORRELATION_HEADER, correlationId);
        AuditRequestMetadata metadata = new AuditRequestMetadata(
                "HTTP",
                correlationId,
                request.getRemoteAddr(),
                limited(request.getHeader("User-Agent"), 1024));
        try (AuditRequestContext.Scope ignored = AuditRequestContext.open(metadata)) {
            filterChain.doFilter(request, response);
        }
    }

    private String safeCorrelationId(String value) {
        String limited = limited(value, 128);
        return limited != null && limited.matches("[A-Za-z0-9._:-]+")
                ? limited
                : UUID.randomUUID().toString();
    }

    private String limited(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.substring(0, Math.min(stripped.length(), maxLength));
    }
}
