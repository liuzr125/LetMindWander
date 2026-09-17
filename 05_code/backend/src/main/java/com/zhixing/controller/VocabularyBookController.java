package com.zhixing.controller;

import com.zhixing.dto.SelectVocabularyBookRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.VocabularyBookView;
import com.zhixing.model.VocabularyBookProgressView;
import com.zhixing.service.DailyTaskService;
import com.zhixing.service.SessionService;
import com.zhixing.service.VocabularyBookService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/vocabulary-books")
public class VocabularyBookController {
    private final SessionService sessions;
    private final VocabularyBookService books;
    private final DailyTaskService daily;
    public VocabularyBookController(SessionService sessions,VocabularyBookService books,DailyTaskService daily){this.sessions=sessions;this.books=books;this.daily=daily;}

    @GetMapping public List<VocabularyBookView> list(@RequestHeader(value="Authorization",required=false) String auth){return books.list(user(auth));}
    @GetMapping("/current") public VocabularyBookView current(@RequestHeader(value="Authorization",required=false) String auth){return books.current(user(auth));}
    @GetMapping("/current/progress") public VocabularyBookProgressView progress(@RequestHeader(value="Authorization",required=false) String auth,@RequestParam(required=false,defaultValue="all") String status,@RequestParam(required=false,defaultValue="1") Integer page,@RequestParam(required=false,defaultValue="20") Integer pageSize){return books.progress(user(auth),status,page,pageSize);}
    @PutMapping("/current") public VocabularyBookView select(@RequestHeader(value="Authorization",required=false) String auth,@Valid @RequestBody SelectVocabularyBookRequest request){String owner=user(auth);VocabularyBookView selected=books.select(owner,request);daily.refreshToday(owner);return selected;}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
