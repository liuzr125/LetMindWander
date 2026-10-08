package com.zhixing.controller;
import com.zhixing.service.AnnouncementService;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.config.AdminSessionFilter;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
@RestController @RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {
 private final AnnouncementService service;
 public AdminAnnouncementController(AnnouncementService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="50")int pageSize){return service.list(true,page,pageSize);}
 @GetMapping("/{id}")public Map<String,Object> detail(@PathVariable String id){return service.detail(id,true);}
 @PostMapping public Map<String,Object> create(@RequestBody Map<String,String> body,HttpServletRequest request){return service.save(actor(request),null,body);}
 @PutMapping("/{id}")public Map<String,Object> update(@PathVariable String id,@RequestBody Map<String,String> body,HttpServletRequest request){return service.save(actor(request),id,body);}
 private String actor(HttpServletRequest request){return ((AdminPrincipal)request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE)).getAccountId();}
}
