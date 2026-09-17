package com.zhixing.controller;
import com.zhixing.dto.UserFeedbackRequest;
import com.zhixing.model.*;
import com.zhixing.service.MineService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.*;

@RestController @RequestMapping("/api/mine")
public class MineController {
 private final SessionService sessions; private final MineService mine;
 public MineController(SessionService sessions,MineService mine){this.sessions=sessions;this.mine=mine;}
 private String user(String auth){return sessions.requireUser(auth).getUserId();}
 @GetMapping("/overview") public MineOverviewView overview(@RequestHeader(value="Authorization",required=false)String auth){return mine.overview(user(auth));}
 @GetMapping("/favorites") public List<FavoriteView> favorites(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(required=false)String type,@RequestParam(required=false)String query){return mine.favorites(user(auth),type,query);}
 @GetMapping("/privacy") public PrivacyView privacy(@RequestHeader(value="Authorization",required=false)String auth){return mine.privacy(user(auth));}
 @PostMapping("/exports") public PrivacyView export(@RequestHeader(value="Authorization",required=false)String auth){return mine.requestExport(user(auth));}
 @PostMapping("/deletions") public Map<String,Object> delete(@RequestHeader(value="Authorization",required=false)String auth){return mine.requestDeletion(user(auth));}
 @PostMapping("/feedback") public Map<String,Object> feedback(@RequestHeader(value="Authorization",required=false)String auth,@Valid @RequestBody UserFeedbackRequest request){return mine.feedback(user(auth),request);}
}
