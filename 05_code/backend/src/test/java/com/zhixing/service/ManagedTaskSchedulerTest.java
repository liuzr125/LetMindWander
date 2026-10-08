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
    when(settings.save(anyString(), anyString()))
        .thenThrow(new IllegalArgumentException("invalid"));
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
}
