package com.zhixing.service;

import com.zhixing.mapper.AdminContentMapper;
import com.zhixing.model.ContentCoverageView;
import com.zhixing.model.AdminTechnicalContentPageView;
import com.zhixing.model.AdminTechnicalContentView;
import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AdminContentService {
    private final AdminContentMapper contents;
    public AdminContentService(AdminContentMapper contents){this.contents=contents;}
    public ContentCoverageView coverage(){ContentCoverageView view=new ContentCoverageView();view.setPublishedTotal(contents.countPublished());view.setSourceCount(contents.countSources());view.setTechnicalTopics(contents.selectTechnicalTopics());view.setWordStages(contents.selectWordStages());List<ContentCoverageView.CoverageItem> articles=contents.selectArticleDifficulties();for(ContentCoverageView.CoverageItem item:articles){if("intro".equals(item.getName()))item.setName("入门");else if("advanced".equals(item.getName()))item.setName("进阶");}view.setArticleDifficulties(articles);return view;}
    public AdminTechnicalContentPageView technical(String rawTopic,String rawKeyword,Integer rawPage,Integer rawPageSize){
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条内容");
        String topic=clean(rawTopic),keyword=clean(rawKeyword);
        if(topic.length()>50||keyword.length()>80)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","主题或搜索关键词过长");
        int total=contents.countTechnical(topic,keyword),totalPages=total==0?0:(total+pageSize-1)/pageSize;
        List<AdminTechnicalContentView> items=contents.selectTechnical(topic,keyword,(page-1)*pageSize,pageSize);
        for(AdminTechnicalContentView item:items)item.setTopics(contents.selectTechnicalTopicNames(item.getVersionId()));
        AdminTechnicalContentPageView result=new AdminTechnicalContentPageView();result.setTotal(total);result.setPage(page);result.setPageSize(pageSize);result.setTotalPages(totalPages);result.setTopic(topic);result.setKeyword(keyword);result.setItems(items);return result;
    }
    public AdminTechnicalContentView technicalDetail(String contentId){AdminTechnicalContentView detail=contents.selectTechnicalDetail(contentId);if(detail==null)throw new ApiException(HttpStatus.NOT_FOUND,"TECH_CONTENT_NOT_FOUND","技术知识不存在或未发布");detail.setTopics(contents.selectTechnicalTopicNames(detail.getVersionId()));return detail;}
    public AdminTechnicalContentPageView articles(String rawDifficulty,String rawKeyword,Integer rawPage,Integer rawPageSize){
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条内容");
        String difficulty=clean(rawDifficulty).toLowerCase(java.util.Locale.ROOT),keyword=clean(rawKeyword);
        if(!difficulty.isEmpty()&&!"intro".equals(difficulty)&&!"advanced".equals(difficulty))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DIFFICULTY","短文难度只能是 intro 或 advanced");
        if(keyword.length()>80)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","搜索关键词过长");
        int total=contents.countArticles(difficulty,keyword),totalPages=total==0?0:(total+pageSize-1)/pageSize;
        AdminTechnicalContentPageView result=new AdminTechnicalContentPageView();result.setTotal(total);result.setPage(page);result.setPageSize(pageSize);result.setTotalPages(totalPages);result.setTopic(difficulty);result.setKeyword(keyword);result.setItems(contents.selectArticles(difficulty,keyword,(page-1)*pageSize,pageSize));return result;
    }
    public AdminTechnicalContentView articleDetail(String contentId){AdminTechnicalContentView detail=contents.selectArticleDetail(contentId);if(detail==null)throw new ApiException(HttpStatus.NOT_FOUND,"ARTICLE_CONTENT_NOT_FOUND","英语短文不存在或未发布");return detail;}
    private String clean(String value){return value==null?"":value.trim();}
}
