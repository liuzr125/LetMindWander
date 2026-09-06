package com.zhixing.controller;

import com.zhixing.dto.ReviewFeedbackRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ReviewItemView;
import com.zhixing.service.ReviewService;
import com.zhixing.service.SessionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final SessionService sessions;private final ReviewService reviews;
    public ReviewController(SessionService sessions,ReviewService reviews){this.sessions=sessions;this.reviews=reviews;}
    @GetMapping("/queue") public List<ReviewItemView> queue(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date){return reviews.queue(user(auth),date);}
    @PostMapping("/{id}/feedback") public List<ReviewItemView> feedback(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestBody ReviewFeedbackRequest request){return reviews.feedback(user(auth),id,request);}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
