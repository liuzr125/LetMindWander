package com.zhixing.controller;

import com.zhixing.dto.JournalSaveRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.JournalView;
import com.zhixing.service.JournalService;
import com.zhixing.service.SessionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/journals")
public class JournalController {
    private final SessionService sessions; private final JournalService journals;
    public JournalController(SessionService sessions,JournalService journals){this.sessions=sessions;this.journals=journals;}
    @GetMapping public List<JournalView> history(@RequestHeader(value="Authorization",required=false)String auth){return journals.history(user(auth));}
    @GetMapping("/{date}") public JournalView get(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date){return journals.get(user(auth),date);}
    @PutMapping("/{date}") public JournalView save(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestBody JournalSaveRequest r){return journals.save(user(auth),date,r,false);}
    @PostMapping("/{date}/submit") public JournalView submit(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestBody JournalSaveRequest r){return journals.save(user(auth),date,r,true);}
    private String user(String auth){AuthenticatedSession s=sessions.requireUser(auth);return s.getUserId();}
}
