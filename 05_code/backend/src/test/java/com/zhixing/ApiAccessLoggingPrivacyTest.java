package com.zhixing;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zhixing.config.ApiAccessLoggingFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class ApiAccessLoggingPrivacyTest {
  @Test
  void logsOnlyTemplateStatusTimingAndGeneratedCorrelation() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger("API_ACCESS");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    MDC.put("requestId", "outer-correlation");
    try {
      MockHttpServletRequest request =
          new MockHttpServletRequest("POST", "/api/profile/private-id");
      request.addHeader("Authorization", "Bearer secret-token");
      request.addParameter("query", "private-search");
      request.setContent(
          "{\"password\":\"private-password\",\"name\":\"private-name\"}".getBytes("UTF-8"));
      request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/profile/{id}");
      MockHttpServletResponse response = new MockHttpServletResponse();
      new ApiAccessLoggingFilter()
          .doFilter(
              request,
              response,
              (req, res) -> {
                assertNotEquals("outer-correlation", MDC.get("requestId"));
                ((javax.servlet.http.HttpServletResponse) res).setStatus(409);
              });
      String message = appender.list.get(0).getFormattedMessage();
      assertTrue(message.contains("route=/api/profile/{id}"));
      assertTrue(message.contains("status=409"));
      assertTrue(message.contains("elapsedMs="));
      for (String secret :
          new String[] {
            "private-id", "secret-token", "private-search", "private-password", "private-name"
          }) {
        assertFalse(message.contains(secret));
      }
      assertEquals("outer-correlation", MDC.get("requestId"));
    } finally {
      logger.detachAppender(appender);
      MDC.remove("requestId");
    }
  }
}
