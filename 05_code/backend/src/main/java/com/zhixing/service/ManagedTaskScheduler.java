package com.zhixing.service;

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
  private final Map<String, ScheduledFuture<?>> futures = new HashMap<>();
  private final Map<String, String> plans = new HashMap<>();
  private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

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
        reschedule(code, settings.cron(code));
      } catch (RuntimeException error) {
        LOG.warn("Managed task scheduling failed: category={}", error.getClass().getSimpleName());
      }
    }
  }

  public synchronized String update(String code, String cron) {
    String saved = settings.save(code, cron);
    reschedule(code, saved);
    return saved;
  }

  private synchronized void reschedule(String code, String cron) {
    if (cron.equals(plans.get(code))) return;
    Runnable job = jobs.get(code);
    if (job == null) throw new IllegalArgumentException("Unknown managed task");
    AtomicBoolean guard = running.computeIfAbsent(code, key -> new AtomicBoolean());
    Runnable guarded =
        () -> {
          if (!guard.compareAndSet(false, true)) return;
          try {
            job.run();
          } finally {
            guard.set(false);
          }
        };
    ScheduledFuture<?> next =
        scheduler.schedule(
            guarded, new CronTrigger(cron, TimeZone.getTimeZone(TaskScheduleSettings.ZONE)));
    if (next == null) throw new IllegalStateException("执行计划无法调度");
    ScheduledFuture<?> previous = futures.put(code, next);
    plans.put(code, cron);
    if (previous != null) previous.cancel(false);
  }

  @Override
  public synchronized void destroy() {
    for (ScheduledFuture<?> future : futures.values()) future.cancel(false);
    if (scheduler instanceof ThreadPoolTaskScheduler)
      ((ThreadPoolTaskScheduler) scheduler).shutdown();
  }
}
