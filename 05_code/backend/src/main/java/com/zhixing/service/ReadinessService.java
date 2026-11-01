package com.zhixing.service;

import java.sql.*;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** 有界、单飞的数据库就绪探测；不返回数据库配置、异常消息或供应商状态。 */
@Service
public class ReadinessService {
  private final DataSource dataSource;
  private final int timeoutMillis, cacheMillis;
  private final ExecutorService worker;
  private volatile boolean accepting;
  private Probe current;

  public ReadinessService(
      DataSource dataSource,
      @Value("${app.health.probe-timeout-ms:2000}") int timeoutMillis,
      @Value("${app.health.cache-ms:1000}") int cacheMillis) {
    if (timeoutMillis < 1 || timeoutMillis > 30000 || cacheMillis < 0 || cacheMillis > 60000)
      throw new IllegalArgumentException("健康检查超时或缓存配置不正确");
    this.dataSource = dataSource;
    this.timeoutMillis = timeoutMillis;
    this.cacheMillis = cacheMillis;
    this.worker =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            runnable -> {
              Thread thread = new Thread(runnable, "database-readiness");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  @EventListener(ApplicationReadyEvent.class)
  public void acceptTraffic() {
    accepting = true;
  }

  @EventListener(ContextClosedEvent.class)
  public void refuseTraffic() {
    accepting = false;
  }

  public boolean ready() {
    if (!accepting) return false;
    Probe probe = probe();
    try {
      return probe.result.get(timeoutMillis, TimeUnit.MILLISECONDS) && accepting;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException | TimeoutException error) {
      // 不取消共享探测：驱动可能不响应中断，重建探测反而可能耗尽连接和线程。
      return false;
    }
  }

  private synchronized Probe probe() {
    if (current != null
        && (!current.result.isDone()
            || System.nanoTime() - current.completedAt
                < TimeUnit.MILLISECONDS.toNanos(cacheMillis))) return current;
    Probe next = new Probe();
    current = next;
    try {
      worker.execute(
          () -> {
            boolean healthy = checkDatabase();
            next.completedAt = System.nanoTime();
            next.result.complete(healthy);
          });
    } catch (RejectedExecutionException error) {
      next.completedAt = System.nanoTime();
      next.result.complete(false);
    }
    return next;
  }

  private boolean checkDatabase() {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.setQueryTimeout(Math.max(1, (timeoutMillis + 999) / 1000));
      try (ResultSet result = statement.executeQuery("SELECT 1")) {
        return result.next() && result.getInt(1) == 1;
      }
    } catch (SQLException | RuntimeException error) {
      return false;
    }
  }

  @PreDestroy
  public void close() {
    refuseTraffic();
    worker.shutdownNow();
  }

  private static final class Probe {
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    volatile long completedAt;
  }
}
