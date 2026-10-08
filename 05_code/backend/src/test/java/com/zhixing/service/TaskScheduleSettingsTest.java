package com.zhixing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.mapper.CollectionScheduleSettingsMapper;
import com.zhixing.mapper.ContentCollectionMapper;
import com.zhixing.model.CollectionScheduleView;
import java.time.Instant;
import java.util.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;

class TaskScheduleSettingsTest {
  @Test
  void parameterPlansPersistAndCalculateInBeijingTime() {
    AppParameterService parameters = mock(AppParameterService.class);
    Map<String, String> saved = new HashMap<>();
    when(parameters.optionalStored(anyString(), anyString()))
        .thenAnswer(call -> saved.getOrDefault(call.getArgument(0), call.getArgument(1)));
    doAnswer(
            call -> {
              saved.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(parameters)
        .saveValue(anyString(), anyString(), anyString());
    TaskScheduleSettings settings =
        new TaskScheduleSettings(
            parameters,
            new AppProperties(),
            mock(ContentCollectionMapper.class),
            mock(CollectionScheduleSettingsMapper.class),
            "0 0 22 * * *");
    assertThat(settings.cron("article")).isEqualTo("0 30 22 * * *");
    assertThat(settings.save("article", " 0   15 19 * * * ")).isEqualTo("0 15 19 * * *");
    assertThat(settings.next("article", Instant.parse("2026-10-08T10:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-08T11:15:00Z"));
    assertThat(settings.next("article", Instant.parse("2026-10-08T11:15:00Z")))
        .isEqualTo(Instant.parse("2026-10-09T11:15:00Z"));
    settings.save("pricing", "0 0 9 * * MON-FRI");
    assertThat(settings.cron("pricing")).isEqualTo("0 0 9 * * MON-FRI");
    verify(parameters, never()).saveValue(contains("enabled"), anyString(), anyString());
    TaskScheduleSettings restarted =
        new TaskScheduleSettings(
            parameters,
            new AppProperties(),
            mock(ContentCollectionMapper.class),
            mock(CollectionScheduleSettingsMapper.class),
            "0 0 22 * * *");
    assertThat(restarted.cron("article")).isEqualTo("0 15 19 * * *");
  }

  @Test
  void invalidAndUnknownPlansDoNotWrite() {
    AppParameterService parameters = mock(AppParameterService.class);
    CollectionScheduleSettingsMapper schedules = mock(CollectionScheduleSettingsMapper.class);
    TaskScheduleSettings settings =
        new TaskScheduleSettings(
            parameters,
            new AppProperties(),
            mock(ContentCollectionMapper.class),
            schedules,
            "0 0 22 * * *");
    for (String cron :
        Arrays.asList("", "0 22 * * *", "0 70 22 * * *", "0 0 22 31 FEB *", "@daily"))
      assertThatThrownBy(() -> settings.save("article", cron))
          .isInstanceOf(ApiException.class)
          .hasMessageContaining("Cron")
          .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("INVALID_TASK_CRON"));
    assertThatThrownBy(() -> settings.save("unknown", "0 0 22 * * *"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("TASK_NOT_FOUND"));
    verifyNoInteractions(parameters, schedules);
  }

  @Test
  void collectionPlanUpdatesExistingTableWithoutEnablingPausedTask() {
    EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    try {
      JdbcTemplate jdbc = new JdbcTemplate(db);
      jdbc.execute(
          "CREATE TABLE content_collection_schedule (id VARCHAR(32) PRIMARY KEY,cron_expression VARCHAR(80),timezone VARCHAR(40),state VARCHAR(16),next_run_at TIMESTAMP)");
      jdbc.update(
          "INSERT INTO content_collection_schedule VALUES('00000000000000000000000000000090','0 0 22 * * *','Asia/Shanghai','paused',NULL)");
      ContentCollectionMapper mapper = mock(ContentCollectionMapper.class);
      when(mapper.selectSchedule())
          .thenAnswer(
              call -> {
                CollectionScheduleView row = new CollectionScheduleView();
                row.setCronExpression(
                    jdbc.queryForObject(
                        "SELECT cron_expression FROM content_collection_schedule", String.class));
                return row;
              });
      Configuration configuration =
          new Configuration(new Environment("test", new JdbcTransactionFactory(), db));
      new XMLMapperBuilder(
              getClass().getResourceAsStream("/mapper/CollectionScheduleSettingsMapper.xml"),
              configuration,
              "mapper/CollectionScheduleSettingsMapper.xml",
              configuration.getSqlFragments())
          .parse();
      try (SqlSession session =
          new SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
        TaskScheduleSettings settings =
            new TaskScheduleSettings(
                mock(AppParameterService.class),
                new AppProperties(),
                mapper,
                session.getMapper(CollectionScheduleSettingsMapper.class),
                "0 0 22 * * *");
        settings.save("collection", "0 45 20 * * *");
        assertThat(settings.cron("collection")).isEqualTo("0 45 20 * * *");
        assertThat(
                jdbc.queryForObject("SELECT state FROM content_collection_schedule", String.class))
            .isEqualTo("paused");
        assertThat(
                jdbc.queryForObject(
                    "SELECT next_run_at FROM content_collection_schedule", Object.class))
            .isNull();
        jdbc.update("UPDATE content_collection_schedule SET state='active'");
        settings.save("collection", "0 30 21 * * *");
        assertThat(
                jdbc.queryForObject(
                    "SELECT next_run_at FROM content_collection_schedule", Object.class))
            .isNotNull();
      }
    } finally {
      db.shutdown();
    }
  }
}
