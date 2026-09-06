package com.zhixing.controller;

import com.zhixing.dto.UpdateActionRequest;
import com.zhixing.model.ActionView;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.ActionService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actions")
public class ActionController {
    private final SessionService sessions; private final ActionService actions;
    public ActionController(SessionService sessions,ActionService actions){this.sessions=sessions;this.actions=actions;}
    @GetMapping("/{id}") public ActionView get(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return actions.get(user(auth),id);}
    @PutMapping("/{id}") public ActionView update(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestBody UpdateActionRequest request){return actions.update(user(auth),id,request);}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
