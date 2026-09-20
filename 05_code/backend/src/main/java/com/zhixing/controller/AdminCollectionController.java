package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.CollectionRunView;
import com.zhixing.model.CollectionScheduleView;
import com.zhixing.model.ArticleGenerationRunView;
import com.zhixing.model.ArticleGenerationStatusView;
import com.zhixing.service.ContentCollectionService;
import com.zhixing.service.DailyEnglishArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/collection")
public class AdminCollectionController {
    private final ContentCollectionService collection; private final DailyEnglishArticleService articles; private final AppProperties properties;
    public AdminCollectionController(ContentCollectionService collection,DailyEnglishArticleService articles,AppProperties properties){this.collection=collection;this.articles=articles;this.properties=properties;}
    @GetMapping("/schedule") public CollectionScheduleView schedule(@RequestHeader(value="X-Admin-Token",required=false)String token){require(token);CollectionScheduleView schedule=collection.schedule();if(schedule==null)throw new ApiException(HttpStatus.NOT_FOUND,"COLLECTION_SCHEDULE_MISSING","请先执行数据库定时采集脚本");return schedule;}
    @GetMapping("/runs") public List<CollectionRunView> runs(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(value="limit",defaultValue="15")int limit){require(token);return collection.runs(limit);}
    @GetMapping("/runs/{runId}/detail") public Map<String,Object> runDetail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String runId){require(token);try{return collection.runDetail(runId);}catch(IllegalArgumentException exception){throw new ApiException(HttpStatus.NOT_FOUND,"COLLECTION_RUN_NOT_FOUND",exception.getMessage());}catch(IllegalStateException exception){throw new ApiException(HttpStatus.CONFLICT,"COLLECTION_UNAVAILABLE",exception.getMessage());}}
    @PostMapping("/run") public CollectionRunView run(@RequestHeader(value="X-Admin-Token",required=false)String token){require(token);try{return collection.runManually();}catch(IllegalStateException exception){throw new ApiException(HttpStatus.CONFLICT,"COLLECTION_UNAVAILABLE",exception.getMessage());}}
    @GetMapping("/article-generation/status") public ArticleGenerationStatusView articleStatus(@RequestHeader(value="X-Admin-Token",required=false)String token){require(token);return articles.status();}
    @GetMapping("/article-generation/runs") public List<ArticleGenerationRunView> articleRuns(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(value="limit",defaultValue="12")int limit){require(token);return articles.runs(limit);}
    @GetMapping("/article-generation/runs/{runId}/detail") public Map<String,Object> articleRunDetail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String runId){require(token);return articles.runDetail(runId);}
    @PutMapping("/article-generation/settings") public ArticleGenerationStatusView articleSettings(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,Object> body){require(token);Object raw=body==null?null:body.get("perBookCount");try{return articles.updatePerBookCount(Integer.parseInt(String.valueOf(raw)));}catch(NumberFormatException exception){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ARTICLE_COUNT","请输入 1 至 20 的整数");}}
    @PutMapping("/article-generation/state") public ArticleGenerationStatusView articleState(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,Object> body){require(token);Object raw=body==null?null:body.get("enabled");if(!(raw instanceof Boolean))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ARTICLE_STATE","任务状态必须是布尔值");return articles.updateEnabled((Boolean)raw);}
    @PostMapping("/article-generation/run") public ArticleGenerationRunView articleRun(@RequestHeader(value="X-Admin-Token",required=false)String token){require(token);try{return articles.runManually();}catch(IllegalStateException exception){throw new ApiException(HttpStatus.CONFLICT,"ARTICLE_GENERATION_UNAVAILABLE",exception.getMessage());}}
    private void require(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
