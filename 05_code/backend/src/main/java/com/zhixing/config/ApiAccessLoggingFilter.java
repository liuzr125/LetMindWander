package com.zhixing.config;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 访问日志只记录请求关联号、接口模板、状态与耗时；不采集正文、查询值、凭据或用户信息。 */
@Component
public class ApiAccessLoggingFilter extends OncePerRequestFilter {
  private static final Logger ACCESS_LOG = LoggerFactory.getLogger("API_ACCESS");

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // 关联号由服务端生成，避免将客户端传入的个人信息或换行写入日志。
    String previous = MDC.get("requestId");
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    long startedAt = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      Object route =
          request.getAttribute(
              org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      ACCESS_LOG.info(
          "api_access requestId={} method={} route={} status={} elapsedMs={}",
          requestId,
          request.getMethod(),
          route == null ? "unmatched" : route,
          response.getStatus(),
          (System.nanoTime() - startedAt) / 1_000_000L);
      if (previous == null) MDC.remove("requestId");
      else MDC.put("requestId", previous);
    }
  }
}
