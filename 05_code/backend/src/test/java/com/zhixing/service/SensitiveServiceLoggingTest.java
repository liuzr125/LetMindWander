package com.zhixing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

class SensitiveServiceLoggingTest {
  @Test
  void mockSmsKeepsCodesAndMobileNumbersOutOfDiagnosticLogs() {
    AppProperties properties = new AppProperties();
    properties.getSms().setMockEnabled(true);
    properties.getSms().setFixedCode("654321");
    SmsVerificationService service = new SmsVerificationService(properties);
    List<ILoggingEvent> events =
        capture(
            SmsVerificationService.class,
            () -> {
              assertThat(service.send("synthetic-ticket", "13800009999").getCode())
                  .isEqualTo("654321");
              assertThatThrownBy(
                      () -> service.verifyAndConsume("synthetic-ticket", "13800009999", "wrong"))
                  .isInstanceOf(ApiException.class);
              service.verifyAndConsume("synthetic-ticket", "13800009999", "654321");
            });
    assertThat(events).hasSize(3);
    for (ILoggingEvent event : events) {
      assertThat(event.getFormattedMessage())
          .doesNotContain("654321", "13800009999", "9999", "synthetic-ticket");
      assertThat(event.getThrowableProxy()).isNull();
    }
  }

  @Test
  void wechatProviderMessagesAreNotCopiedToLogs() {
    AppParameterService parameters = mock(AppParameterService.class);
    when(parameters.required(anyString())).thenReturn("synthetic-provider-secret");
    RestTemplate rest = mock(RestTemplate.class);
    when(rest.getForObject(anyString(), eq(String.class)))
        .thenReturn("{\"errcode\":40029,\"errmsg\":\"synthetic-provider-secret\"}");
    AppProperties properties = new AppProperties();
    properties.getWechat().setMockEnabled(false);
    WechatGateway gateway = new WechatGateway(properties, parameters, rest, new ObjectMapper());
    List<ILoggingEvent> events =
        capture(
            WechatGateway.class,
            () ->
                assertThatThrownBy(() -> gateway.exchangeCode("synthetic-code"))
                    .isInstanceOf(ApiException.class));
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage()).isEqualTo("WeChat code exchange rejected");
    assertThat(events.get(0).getThrowableProxy()).isNull();
    verify(rest).getForObject(anyString(), eq(String.class));
  }

  @Test
  void schedulerSetupFailuresLogCategoryWithoutExceptionDetails() {
    TaskScheduleSettings settings = mock(TaskScheduleSettings.class);
    when(settings.cron("article"))
        .thenThrow(new IllegalArgumentException("synthetic-provider-secret"));
    ManagedTaskScheduler scheduler =
        new ManagedTaskScheduler(
            settings, mock(TaskScheduler.class), Collections.singletonMap("article", () -> {}));
    List<ILoggingEvent> events = capture(ManagedTaskScheduler.class, scheduler::start);
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage())
        .contains("IllegalArgumentException")
        .doesNotContain("synthetic-provider-secret");
    assertThat(events.get(0).getThrowableProxy()).isNull();
  }

  private List<ILoggingEvent> capture(Class<?> source, Runnable operation) {
    Logger logger = (Logger) LoggerFactory.getLogger(source);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      operation.run();
      return appender.list;
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
