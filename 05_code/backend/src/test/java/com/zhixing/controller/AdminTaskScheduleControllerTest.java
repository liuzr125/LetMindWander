package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.service.ManagedTaskScheduler;
import com.zhixing.service.TaskScheduleSettings;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminTaskScheduleControllerTest {
    @Test void savesWithAdminAuthorizationAndReturnsEffectivePlan() {
        ManagedTaskScheduler scheduler=mock(ManagedTaskScheduler.class);TaskScheduleSettings settings=mock(TaskScheduleSettings.class);
        when(scheduler.update("article","0 0 19 * * *")).thenReturn("0 0 19 * * *");
        AdminTaskScheduleController controller=new AdminTaskScheduleController(scheduler,settings,new AppProperties());
        Map<String,Object> result=controller.save("dev-admin-token","article",Collections.singletonMap("cronExpression","0 0 19 * * *"));
        assertThat(result).containsEntry("cronExpression","0 0 19 * * *").containsEntry("timezone","Asia/Shanghai");
        verify(settings).next(eq("article"),any());
    }

    @Test void rejectsUnauthorizedOrNonTextInputBeforeAnyWrite() {
        ManagedTaskScheduler scheduler=mock(ManagedTaskScheduler.class);TaskScheduleSettings settings=mock(TaskScheduleSettings.class);
        AdminTaskScheduleController controller=new AdminTaskScheduleController(scheduler,settings,new AppProperties());
        assertThatThrownBy(()->controller.save(null,"article",Collections.singletonMap("cronExpression","0 0 19 * * *")))
                .isInstanceOf(ApiException.class).satisfies(e->assertThat(((ApiException)e).getCode()).isEqualTo("ADMIN_UNAUTHORIZED"));
        assertThatThrownBy(()->controller.save("dev-admin-token","article",Collections.singletonMap("cronExpression",19)))
                .isInstanceOf(ApiException.class).satisfies(e->assertThat(((ApiException)e).getCode()).isEqualTo("INVALID_TASK_CRON"));
        verifyNoInteractions(scheduler,settings);
    }
}
