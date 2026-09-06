package com.zhixing.controller;

import com.zhixing.dto.CreateTopicRequest;
import com.zhixing.dto.UpdatePlanRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.PlanView;
import com.zhixing.service.PlanService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** F03 计划设置：版本化保存，所有普通用户接口均按登录用户隔离。 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {
    private final SessionService sessions;
    private final PlanService plans;

    public PlanController(SessionService sessions, PlanService plans) { this.sessions = sessions; this.plans = plans; }

    @GetMapping
    public PlanView get(@RequestHeader(value = "Authorization", required = false) String authorization) { return plans.get(userId(authorization)); }

    @GetMapping("/history")
    public List<PlanView> history(@RequestHeader(value = "Authorization", required = false) String authorization) { return plans.history(userId(authorization)); }

    @PostMapping("/default")
    public PlanView initializeDefault(@RequestHeader(value = "Authorization", required = false) String authorization) { return plans.initializeDefault(userId(authorization)); }

    @PutMapping
    public PlanView update(@RequestHeader(value = "Authorization", required = false) String authorization,
                           @RequestBody UpdatePlanRequest request) { return plans.update(userId(authorization), request); }

    @GetMapping("/topics")
    public List<PlanView.TopicView> topics(@RequestHeader(value = "Authorization", required = false) String authorization) { return plans.availableTopics(userId(authorization)); }

    @PostMapping("/topics")
    public PlanView.TopicView createTopic(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody CreateTopicRequest request) { return plans.createTopic(userId(authorization), request); }

    private String userId(String authorization) { AuthenticatedSession session = sessions.requireUser(authorization); return session.getUserId(); }
}
