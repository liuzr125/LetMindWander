package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.ManagedTaskScheduler;
import com.zhixing.service.TaskScheduleSettings;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/tasks")
public class AdminTaskScheduleController {
    private final ManagedTaskScheduler scheduler;
    private final TaskScheduleSettings settings;
    private final AppProperties properties;

    public AdminTaskScheduleController(ManagedTaskScheduler scheduler,TaskScheduleSettings settings,AppProperties properties) {
        this.scheduler=scheduler;this.settings=settings;this.properties=properties;
    }

    @PutMapping("/{code}/schedule")
    public Map<String,Object> save(@RequestHeader(value="X-Admin-Token",required=false) String token,
                                   @PathVariable String code,@RequestBody Map<String,Object> body) {
        if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");
        Object cron=body==null?null:body.get("cronExpression");
        if(!(cron instanceof String))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TASK_CRON","执行计划必须是文本");
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("cronExpression",scheduler.update(code,(String)cron));
        result.put("timezone",TaskScheduleSettings.ZONE.getId());
        result.put("nextRunAt",settings.next(code,Instant.now()));
        return result;
    }
}
