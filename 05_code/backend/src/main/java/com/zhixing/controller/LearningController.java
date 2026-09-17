package com.zhixing.controller;

import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.LearningPageView;
import com.zhixing.model.LearningFiltersView;
import com.zhixing.model.LearningTopicView;
import com.zhixing.model.WordNotebookSummaryView;
import com.zhixing.service.LearningService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/learning")
public class LearningController {
    private final SessionService sessions;private final LearningService learning;
    public LearningController(SessionService sessions,LearningService learning){this.sessions=sessions;this.learning=learning;}

    @GetMapping("/contents")
    public LearningPageView contents(@RequestHeader(value="Authorization",required=false) String auth,
            @RequestParam(required=false) String type,@RequestParam(required=false) String topicId,
            @RequestParam(required=false) String difficulty,@RequestParam(required=false) String stage,
            @RequestParam(required=false,defaultValue="false") Boolean notebook,@RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword,@RequestParam(required=false,defaultValue="1") Integer page,
            @RequestParam(required=false,defaultValue="20") Integer pageSize){
        return learning.page(user(auth),type,topicId,difficulty,stage,notebook,status,keyword,page,pageSize);
    }
    @GetMapping("/filters") public LearningFiltersView filters(@RequestHeader(value="Authorization",required=false) String auth){user(auth);return learning.filters();}
    @GetMapping("/topics") public List<LearningTopicView> topics(@RequestHeader(value="Authorization",required=false) String auth){return learning.topics(user(auth));}
    @GetMapping("/notebook/summary") public WordNotebookSummaryView notebookSummary(@RequestHeader(value="Authorization",required=false) String auth){return learning.notebookSummary(user(auth));}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
