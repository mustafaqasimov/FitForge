package com.mustafaqasimov.fitforge.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String trace = UUID.randomUUID().toString();
    long start = System.nanoTime();
    MDC.put("traceId", trace);
    response.setHeader("X-Trace-Id", trace);
    if (request.getRequestURI().startsWith("/api/v1/auth/"))
      response.setHeader("Cache-Control", "no-store");
    try {
      chain.doFilter(request, response);
    } finally {
      log.info(
          "traceId={} method={} route={} status={} latencyMs={}",
          trace,
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          (System.nanoTime() - start) / 1000000);
      MDC.remove("traceId");
    }
  }
}
