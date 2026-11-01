package com.zhixing.service;

import com.zhixing.common.ApiException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/** Replaces fixed annotations; replacing a plan never interrupts a running job. */
@Service
public class ManagedTaskScheduler implements DisposableBean {
  private static final Logger LOG = LoggerFactory.getLogger(ManagedTaskScheduler.class);
  private final TaskScheduleSettings settings;
  private final TaskScheduler scheduler;
  private final Map<String, Runnable> jobs;
  private final Map<String, Registration> active = new HashMap<>();
  private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();
  private boolean stopped;

  @Autowired
  public ManagedTaskScheduler(
      TaskScheduleSettings settings,
      ContentCollectionService collection,
      DailyEnglishArticleService articles,
      AiOfficialPricingService pricing) {
    this.settings = settings;
    ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
    pool.setPoolSize(3);
    pool.setThreadNamePrefix("managed-task-");
    // Third-party job failures may carry authenticated URLs; keep only the exception category.
    pool.setErrorHandler(
        error ->
            LOG.error(
                "Managed task execution failed: category={}", error.getClass().getSimpleName()));
    pool.initialize();
    this.scheduler = pool;
    this.jobs = new LinkedHashMap<>();
    jobs.put("collection", collection::scheduledCollection);
    jobs.put("article", articles::scheduledGeneration);
    jobs.put("pricing", pricing::scheduledRefresh);
  }

  ManagedTaskScheduler(
      TaskScheduleSettings settings, TaskScheduler scheduler, Map<String, Runnable> jobs) {
    this.settings = settings;
    this.scheduler = scheduler;
    this.jobs = jobs;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void start() {
    for (String code : jobs.keySet()) {
      try {
        restore(code, settings.cron(code));
      } catch (RuntimeException error) {
        LOG.warn("Managed task scheduling failed: category={}", error.getClass().getSimpleName());
      }
    }
  }

  public synchronized String update(String code, String cron) {
    requireAvailable(code);
    String validated = settings.validate(cron);
    Registration current = active.get(code);
    if (current != null
        && validated.equals(current.cron)
        && !current.future.isCancelled()
        && !current.future.isDone()) {
      persist(code, validated);
      return validated;
    }
    Registration staged = prepare(code, validated);
    try {
      // save() 的事务提交成功后才发布新注册；新回调在此之前不会执行业务。
      persist(code, validated);
    } catch (RuntimeException error) {
      cancel(staged);
      throw error;
    }
    activate(staged);
    return validated;
  }

  private void persist(String code, String cron) {
    try {
      settings.save(code, cron);
    } catch (RuntimeException error) {
      if (error instanceof ApiException) throw error;
      LOG.warn(
          "Managed task plan persistence failed: category={}", error.getClass().getSimpleName());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "TASK_SCHEDULE_SAVE_FAILED", "执行计划保存失败，已保留原计划，请稍后重试");
    }
  }

  private synchronized void restore(String code, String cron) {
    requireAvailable(code);
    if (active.containsKey(code)) return;
    activate(prepare(code, cron));
  }

  private void requireAvailable(String code) {
    if (!jobs.containsKey(code))
      throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "定时任务不存在");
    if (stopped)
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "TASK_SCHEDULER_STOPPED", "调度器已停止，执行计划未更新");
  }

  private Registration prepare(String code, String cron) {
    Runnable job = jobs.get(code);
    Registration registration = new Registration(code, cron);
    AtomicBoolean guard = running.computeIfAbsent(code, key -> new AtomicBoolean());
    Runnable guarded =
        () -> {
          synchronized (ManagedTaskScheduler.this) {
            if (active.get(code) != registration || stopped || !guard.compareAndSet(false, true))
              return;
          }
          try {
            job.run();
          } finally {
            guard.set(false);
          }
        };
    try {
      registration.future =
          scheduler.schedule(
              guarded, new CronTrigger(cron, TimeZone.getTimeZone(TaskScheduleSettings.ZONE)));
      if (registration.future == null
          || registration.future.isCancelled()
          || registration.future.isDone()) throw new IllegalStateException("调度器未接受执行计划");
      return registration;
    } catch (RuntimeException error) {
      cancel(registration);
      LOG.warn("Managed task registration failed: category={}", error.getClass().getSimpleName());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "TASK_SCHEDULE_UNAVAILABLE", "执行计划未更新，已保留原计划，请稍后重试");
    }
  }

  private void activate(Registration registration) {
    Registration previous = active.put(registration.code, registration);
    cancel(previous);
  }

  private void cancel(Registration registration) {
    if (registration == null || registration.future == null) return;
    try {
      registration.future.cancel(false);
    } catch (RuntimeException error) {
      // 回调还必须匹配 active 注册；取消失败不会让旧计划恢复执行。
      LOG.warn("Managed task cancellation failed: category={}", error.getClass().getSimpleName());
    }
  }

  private static final class Registration {
    final String code, cron;
    ScheduledFuture<?> future;

    Registration(String code, String cron) {
      this.code = code;
      this.cron = cron;
    }
  }

  @Override
  public synchronized void destroy() {
    stopped = true;
    for (Registration registration : active.values()) cancel(registration);
    active.clear();
    if (scheduler instanceof ThreadPoolTaskScheduler)
      ((ThreadPoolTaskScheduler) scheduler).shutdown();
  }
}
