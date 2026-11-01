package com.zhixing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;

class ReadinessServiceTest {
  @Test
  void refusesBeforeStartupAndAfterShutdownWithoutAccessingDatabase() {
    DataSource dataSource = mock(DataSource.class);
    ReadinessService service = new ReadinessService(dataSource, 100, 1000);
    try {
      assertThat(service.ready()).isFalse();
      service.acceptTraffic();
      service.refuseTraffic();
      assertThat(service.ready()).isFalse();
      verifyNoInteractions(dataSource);
    } finally {
      service.close();
    }
    assertThat(service.ready()).isFalse();
  }

  @Test
  void healthyDatabaseIsCachedAndProbeNeverChangesBusinessData() {
    EmbeddedDatabase db =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    ReadinessService service = new ReadinessService(db, 1000, 1000);
    try {
      JdbcTemplate jdbc = new JdbcTemplate(db);
      jdbc.execute("CREATE TABLE preserved_data(id INT)");
      jdbc.update("INSERT INTO preserved_data VALUES(1)");
      service.acceptTraffic();
      assertThat(service.ready()).isTrue();
      assertThat(service.ready()).isTrue();
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preserved_data", Integer.class))
          .isEqualTo(1);
    } finally {
      service.close();
      db.shutdown();
    }
  }

  @Test
  void databaseFailureReturnsDownAndLaterProbeCanRecover() throws Exception {
    DataSource ds = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet result = mock(ResultSet.class);
    when(ds.getConnection())
        .thenThrow(new SQLException("secret-host password=hidden"))
        .thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("SELECT 1")).thenReturn(result);
    when(result.next()).thenReturn(true);
    when(result.getInt(1)).thenReturn(1);
    ReadinessService service = new ReadinessService(ds, 500, 0);
    try {
      service.acceptTraffic();
      assertThat(service.ready()).isFalse();
      assertThat(service.ready()).isTrue();
      verify(statement).setQueryTimeout(1);
      verify(result).close();
      verify(statement).close();
      verify(connection).close();
    } finally {
      service.close();
    }
  }

  @Test
  void successfulProbeCacheAvoidsOpeningMoreConnections() throws Exception {
    DataSource ds = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    ResultSet result = mock(ResultSet.class);
    when(ds.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("SELECT 1")).thenReturn(result);
    when(result.next()).thenReturn(true);
    when(result.getInt(1)).thenReturn(1);
    ReadinessService service = new ReadinessService(ds, 1000, 60000);
    try {
      service.acceptTraffic();
      for (int i = 0; i < 10; i++) assertThat(service.ready()).isTrue();
      verify(ds).getConnection();
    } finally {
      service.close();
    }
  }

  @Test
  void blockedConnectionHasBoundedCallerWaitAndConcurrentRequestsShareOneProbe() throws Exception {
    DataSource ds = mock(DataSource.class);
    CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    when(ds.getConnection())
        .thenAnswer(
            call -> {
              started.countDown();
              release.await();
              throw new SQLException("unavailable");
            });
    ReadinessService service = new ReadinessService(ds, 50, 1000);
    ExecutorService clients = Executors.newFixedThreadPool(8);
    try {
      service.acceptTraffic();
      Future<Boolean> first = clients.submit(service::ready);
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(first.get(2, TimeUnit.SECONDS)).isFalse();
      List<Future<Boolean>> pending = new ArrayList<>();
      for (int i = 0; i < 8; i++) pending.add(clients.submit(service::ready));
      for (Future<Boolean> request : pending)
        assertThat(request.get(2, TimeUnit.SECONDS)).isFalse();
      verify(ds).getConnection();
    } finally {
      release.countDown();
      service.close();
      clients.shutdownNow();
    }
  }

  @Test
  void queryFailureClosesResourcesAndReturnsDown() throws Exception {
    DataSource ds = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Statement statement = mock(Statement.class);
    when(ds.getConnection()).thenReturn(connection);
    when(connection.createStatement()).thenReturn(statement);
    when(statement.executeQuery("SELECT 1"))
        .thenThrow(new SQLException("private database details"));
    ReadinessService service = new ReadinessService(ds, 1000, 1000);
    try {
      service.acceptTraffic();
      assertThat(service.ready()).isFalse();
      verify(statement).close();
      verify(connection).close();
    } finally {
      service.close();
    }
  }
}
