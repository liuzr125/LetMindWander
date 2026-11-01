package com.zhixing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class ManagedTaskSchedulerTest {
  @Test
  void reschedulesImmediatelyWithoutInterruptingExistingExecution() {
    TaskScheduleSettings settings = mock(TaskScheduleSettings.class);
    when(settings.validate(anyString())).thenAnswer(call -> call.getArgument(0));
    TaskScheduler scheduler = mock(TaskScheduler.class);
    ScheduledFuture<?> old = mock(ScheduledFuture.class), updated = mock(ScheduledFuture.class);
    when(settings.cron("article")).thenReturn("0 30 22 * * *");
    when(settings.save("article", "0 0 19 * * *")).thenReturn("0 0 19 * * *");
    doReturn(old, updated).when(scheduler).schedule(any(Runnable.class), any(Trigger.class));
    ManagedTaskScheduler managed =
        new ManagedTaskScheduler(
            settings, scheduler, Collections.singletonMap("article", () -> {}));
    managed.start();
    managed.update("article", "0 0 19 * * *");
    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    verify(old).cancel(false);
    verify(old, never()).cancel(true);
    managed.update("article", "0 0 19 * * *");
    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    managed.destroy();
    verify(updated).cancel(false);
  }

  @Test
  void invalidSaveDoesNotReplaceTheRunningPlan() {
    TaskScheduleSettings settings = mock(TaskScheduleSettings.class);
    TaskScheduler scheduler = mock(TaskScheduler.class);
    when(settings.validate(anyString())).thenThrow(new IllegalArgumentException("invalid"));
    ManagedTaskScheduler managed =
        new ManagedTaskScheduler(
            settings, scheduler, Collections.singletonMap("article", () -> {}));
    assertThatThrownBy(() -> managed.update("article", "bad"))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(scheduler);
  }

  @Test
  void replacingPlanDoesNotOverlapAnInFlightRun() {
    TaskScheduleSettings settings = mock(TaskScheduleSettings.class);
    when(settings.validate(anyString())).thenAnswer(call -> call.getArgument(0));
    TaskScheduler scheduler = mock(TaskScheduler.class);
    when(settings.cron("article")).thenReturn("0 30 22 * * *");
    when(settings.save(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
    doReturn(mock(ScheduledFuture.class))
        .when(scheduler)
        .schedule(any(Runnable.class), any(Trigger.class));
    List<Runnable> callbacks = new ArrayList<>();
    AtomicInteger executions = new AtomicInteger();
    doAnswer(
            call -> {
              callbacks.add(call.getArgument(0));
              return mock(ScheduledFuture.class);
            })
        .when(scheduler)
        .schedule(any(Runnable.class), any(Trigger.class));
    ManagedTaskScheduler[] holder = new ManagedTaskScheduler[1];
    Runnable job =
        () -> {
          if (executions.incrementAndGet() == 1) {
            holder[0].update("article", "0 0 19 * * *");
            callbacks.get(1).run();
          }
        };
    holder[0] =
        new ManagedTaskScheduler(settings, scheduler, Collections.singletonMap("article", job));
    holder[0].start();
    callbacks.get(0).run();
    assertThat(executions.get()).isEqualTo(1);
    callbacks.get(1).run();
    assertThat(executions.get()).isEqualTo(2);
    holder[0].destroy();
  }

  @Test
  void rejectedRegistrationDoesNotWriteAndOldPlanRemainsExecutable() {
    Harness h = new Harness();
    doThrow(new java.util.concurrent.RejectedExecutionException("pool stopped"))
        .when(h.scheduler)
        .schedule(any(Runnable.class), any(Trigger.class));
    assertThatThrownBy(() -> h.managed.update("article", "0 0 19 * * *"))
        .isInstanceOf(com.zhixing.common.ApiException.class)
        .satisfies(
            error ->
                assertThat(((com.zhixing.common.ApiException) error).getCode())
                    .isEqualTo("TASK_SCHEDULE_UNAVAILABLE"));
    verify(h.settings, never()).save(anyString(), anyString());
    verify(h.futures.get(0), never()).cancel(anyBoolean());
    h.callbacks.get(0).run();
    assertThat(h.executions.get()).isEqualTo(1);
  }

  @Test
  void nullRegistrationDoesNotSaveConfiguration() {
    Harness h = new Harness();
    doReturn(null).when(h.scheduler).schedule(any(Runnable.class), any(Trigger.class));
    assertThatThrownBy(() -> h.managed.update("article", "0 0 19 * * *"))
        .isInstanceOf(com.zhixing.common.ApiException.class);
    verify(h.settings, never()).save(anyString(), anyString());
    h.callbacks.get(0).run();
    assertThat(h.executions.get()).isEqualTo(1);
  }

  @Test
  void persistenceFailureCancelsStagedPlanAndPreservesOriginalCallback() {
    Harness h = new Harness();
    when(h.settings.save(eq("article"), anyString()))
        .thenAnswer(
            call -> {
              h.callbacks.get(1).run(); // 尚未提交时，候选回调不应执行业务。
              assertThat(h.executions.get()).isZero();
              throw new IllegalStateException("database failure");
            });
    assertThatThrownBy(() -> h.managed.update("article", "0 0 19 * * *"))
        .isInstanceOf(com.zhixing.common.ApiException.class)
        .satisfies(
            error ->
                assertThat(((com.zhixing.common.ApiException) error).getCode())
                    .isEqualTo("TASK_SCHEDULE_SAVE_FAILED"));
    verify(h.futures.get(1)).cancel(false);
    verify(h.futures.get(0), never()).cancel(anyBoolean());
    h.callbacks.get(1).run();
    assertThat(h.executions.get()).isZero();
    h.callbacks.get(0).run();
    assertThat(h.executions.get()).isEqualTo(1);
  }

  @Test
  void onlyCommittedRegistrationCanExecuteEvenWhenOldCancellationThrows() {
    Harness h = new Harness();
    when(h.settings.save(eq("article"), anyString()))
        .thenAnswer(
            call -> {
              h.callbacks.get(1).run();
              assertThat(h.executions.get()).isZero();
              return call.getArgument(1);
            });
    when(h.futures.get(0).cancel(false)).thenThrow(new IllegalStateException("cannot cancel"));
    assertThat(h.managed.update("article", "0 0 19 * * *")).isEqualTo("0 0 19 * * *");
    h.callbacks.get(0).run();
    assertThat(h.executions.get()).isZero();
    h.callbacks.get(1).run();
    assertThat(h.executions.get()).isEqualTo(1);
  }

  @Test
  void cancelledCurrentHandleIsReplacedEvenWhenCronIsUnchanged() {
    Harness h = new Harness();
    when(h.futures.get(0).isCancelled()).thenReturn(true);
    h.managed.update("article", "0 30 22 * * *");
    verify(h.scheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    h.callbacks.get(0).run();
    h.callbacks.get(1).run();
    assertThat(h.executions.get()).isEqualTo(1);
  }

  @Test
  void shutdownAndUnknownTaskNeverSaveOrLeaveExecutableCallbacks() {
    Harness h = new Harness();
    assertThatThrownBy(() -> h.managed.update("missing", "0 0 19 * * *"))
        .isInstanceOf(com.zhixing.common.ApiException.class);
    h.managed.destroy();
    h.callbacks.get(0).run();
    assertThat(h.executions.get()).isZero();
    assertThatThrownBy(() -> h.managed.update("article", "0 0 19 * * *"))
        .isInstanceOf(com.zhixing.common.ApiException.class)
        .satisfies(
            error ->
                assertThat(((com.zhixing.common.ApiException) error).getCode())
                    .isEqualTo("TASK_SCHEDULER_STOPPED"));
    verify(h.settings, never()).save(anyString(), anyString());
  }

  @Test
  void failedIdempotentSaveDoesNotDiscardLivePlanAndCanBeRetried() {
    Harness h = new Harness();
    when(h.settings.save("article", "0 30 22 * * *"))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn("0 30 22 * * *");
    assertThatThrownBy(() -> h.managed.update("article", "0 30 22 * * *"))
        .isInstanceOf(com.zhixing.common.ApiException.class);
    verify(h.futures.get(0), never()).cancel(anyBoolean());
    h.callbacks.get(0).run();
    assertThat(h.executions.get()).isEqualTo(1);
    assertThat(h.managed.update("article", "0 30 22 * * *")).isEqualTo("0 30 22 * * *");
    verify(h.scheduler).schedule(any(Runnable.class), any(Trigger.class));
  }

  private static class Harness {
    final TaskScheduleSettings settings = mock(TaskScheduleSettings.class);
    final TaskScheduler scheduler = mock(TaskScheduler.class);
    final List<Runnable> callbacks = new ArrayList<>();
    final List<ScheduledFuture<?>> futures = new ArrayList<>();
    final AtomicInteger executions = new AtomicInteger();
    final ManagedTaskScheduler managed;

    Harness() {
      when(settings.cron("article")).thenReturn("0 30 22 * * *");
      when(settings.validate(anyString())).thenAnswer(call -> call.getArgument(0));
      when(settings.save(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
      doAnswer(
              call -> {
                callbacks.add(call.getArgument(0));
                ScheduledFuture<?> future = mock(ScheduledFuture.class);
                futures.add(future);
                return future;
              })
          .when(scheduler)
          .schedule(any(Runnable.class), any(Trigger.class));
      managed =
          new ManagedTaskScheduler(
              settings,
              scheduler,
              Collections.singletonMap("article", executions::incrementAndGet));
      managed.start();
    }
  }
}
