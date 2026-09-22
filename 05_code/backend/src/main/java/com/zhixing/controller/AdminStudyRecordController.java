package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminStudyRecordDetailView;
import com.zhixing.model.AdminStudyRecordPageView;
import com.zhixing.model.AdminStudyWordPageView;
import com.zhixing.service.AdminStudyRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 管理端「词书学习记录」：每个用户每本英语词书的学习记录、每日明细与已学词条。 */
@RestController
@RequestMapping("/api/admin/study-records")
public class AdminStudyRecordController {
    private final AdminStudyRecordService records;private final AppProperties properties;
    public AdminStudyRecordController(AdminStudyRecordService records,AppProperties properties){this.records=records;this.properties=properties;}
    @GetMapping public AdminStudyRecordPageView page(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize,@RequestParam(required=false)String keyword,@RequestParam(required=false)String bookId,@RequestParam(required=false)String dateFrom,@RequestParam(required=false)String dateTo){
        admin(token);return records.page(page,pageSize,keyword,bookId,dateFrom,dateTo);}
    @GetMapping("/books") public List<Map<String,Object>> books(@RequestHeader(value="X-Admin-Token",required=false)String token){admin(token);return records.books();}
    @GetMapping("/{ownerId}/{bookId}") public AdminStudyRecordDetailView detail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String ownerId,@PathVariable String bookId){admin(token);return records.detail(ownerId,bookId);}
    @GetMapping("/{ownerId}/{bookId}/words") public AdminStudyWordPageView words(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String ownerId,@PathVariable String bookId,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize,@RequestParam(defaultValue="all")String status){
        admin(token);return records.words(ownerId,bookId,page,pageSize,status);}
    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
