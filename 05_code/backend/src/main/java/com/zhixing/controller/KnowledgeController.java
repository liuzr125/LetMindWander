package com.zhixing.controller;

import com.zhixing.dto.*;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.KnowledgeDetailView;
import com.zhixing.model.KnowledgeFriendView;
import com.zhixing.model.KnowledgeListItemView;
import com.zhixing.service.KnowledgeService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final SessionService sessions;private final KnowledgeService knowledge;
    public KnowledgeController(SessionService sessions,KnowledgeService knowledge){this.sessions=sessions;this.knowledge=knowledge;}

    @GetMapping public List<KnowledgeListItemView> list(@RequestHeader(value="Authorization",required=false) String auth,
            @RequestParam(required=false,defaultValue="all") String type,@RequestParam(required=false) String query,
            @RequestParam(required=false) String learningStatus,@RequestParam(required=false) String verificationStatus,
            @RequestParam(required=false,defaultValue="20") Integer limit){return knowledge.list(user(auth),type,query,learningStatus,verificationStatus,limit);}
    @GetMapping("/tags") public List<String> tags(@RequestHeader(value="Authorization",required=false) String auth){return knowledge.tags(user(auth));}
    @GetMapping("/friends") public List<KnowledgeFriendView> friends(@RequestHeader(value="Authorization",required=false) String auth){return knowledge.friends(user(auth));}
    @GetMapping("/{id}") public KnowledgeDetailView get(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id){return knowledge.get(user(auth),id);}
    @PostMapping public KnowledgeDetailView create(@RequestHeader(value="Authorization",required=false) String auth,@RequestBody KnowledgeSaveRequest request){return knowledge.create(user(auth),request);}
    @PutMapping("/{id}") public KnowledgeDetailView update(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody KnowledgeSaveRequest request){return knowledge.update(user(auth),id,request);}
    @PutMapping("/{id}/verification") public KnowledgeDetailView verify(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody KnowledgeVerificationRequest request){return knowledge.verify(user(auth),id,request);}
    @PutMapping("/{id}/visibility") public KnowledgeDetailView visibility(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody KnowledgeVisibilityRequest request){return knowledge.visibility(user(auth),id,request);}
    @PostMapping("/{id}/review") public KnowledgeDetailView review(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestBody(required=false) KnowledgeReviewRequest request){return knowledge.review(user(auth),id,request);}
    @DeleteMapping("/{id}") public void delete(@RequestHeader(value="Authorization",required=false) String auth,@PathVariable String id,@RequestParam Integer expectedVersion){knowledge.delete(user(auth),id,expectedVersion);}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
