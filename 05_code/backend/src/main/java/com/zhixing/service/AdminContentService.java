package com.zhixing.service;

import com.zhixing.mapper.AdminContentMapper;
import com.zhixing.model.ContentCoverageView;
import com.zhixing.model.AdminTechnicalContentPageView;
import com.zhixing.model.AdminTechnicalContentView;
import com.zhixing.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AdminContentService {
    private static final Pattern ARTICLE_NUMBER_BEFORE_COLON=Pattern.compile("(?<!\\d)(\\d+)(?=\\s*:)");
    private static final Pattern ANY_NUMBER=Pattern.compile("\\d+");
    private final AdminContentMapper contents;
    private final MediaService media;
    public AdminContentService(AdminContentMapper contents,MediaService media){this.contents=contents;this.media=media;}
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
    public AdminTechnicalContentPageView articles(String rawDifficulty,String rawBookId,String rawKeyword,Integer rawPage,Integer rawPageSize){
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条内容");
        String difficulty=clean(rawDifficulty).toLowerCase(java.util.Locale.ROOT),bookId=clean(rawBookId),keyword=clean(rawKeyword);
        if(!difficulty.isEmpty()&&!"intro".equals(difficulty)&&!"advanced".equals(difficulty))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DIFFICULTY","短文难度只能是 intro 或 advanced");
        if(keyword.length()>80)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FILTER","搜索关键词过长");
        if(bookId.length()>32)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_BOOK_FILTER","词书筛选值不正确");
        List<AdminTechnicalContentView> all=contents.selectArticles(difficulty,bookId,keyword);
        all.sort(Comparator.comparingInt((AdminTechnicalContentView item)->articleNumber(item.getTitle()))
                .thenComparing(item->item.getTitle()==null?"":item.getTitle())
                .thenComparing(item->item.getContentId()==null?"":item.getContentId()));
        int total=all.size(),totalPages=total==0?0:(total+pageSize-1)/pageSize;
        int from=Math.min((page-1)*pageSize,total),to=Math.min(from+pageSize,total);
        List<AdminTechnicalContentView> items=all.subList(from,to);for(AdminTechnicalContentView item:items)attachArticleAudio(item);
        AdminTechnicalContentPageView result=new AdminTechnicalContentPageView();result.setTotal(total);result.setPage(page);result.setPageSize(pageSize);result.setTotalPages(totalPages);result.setTopic(difficulty);result.setKeyword(keyword);result.setItems(items);return result;
    }
    public AdminTechnicalContentView articleDetail(String contentId){AdminTechnicalContentView detail=contents.selectArticleDetail(contentId);if(detail==null)throw new ApiException(HttpStatus.NOT_FOUND,"ARTICLE_CONTENT_NOT_FOUND","英语短文不存在或未发布");attachArticleAudio(detail);return detail;}
    private void attachArticleAudio(AdminTechnicalContentView item){if(item.getArticleAudioAssetId()!=null&&!item.getArticleAudioAssetId().trim().isEmpty())item.setArticleAudioUrl(media.referenceUrl(item.getArticleAudioAssetId()));}
    private int articleNumber(String title){
        if(title==null)return Integer.MAX_VALUE;
        Matcher preferred=ARTICLE_NUMBER_BEFORE_COLON.matcher(title);
        if(preferred.find())return parseArticleNumber(preferred.group(1));
        Matcher fallback=ANY_NUMBER.matcher(title);
        return fallback.find()?parseArticleNumber(fallback.group()):Integer.MAX_VALUE;
    }
    private int parseArticleNumber(String value){try{return Integer.parseInt(value);}catch(NumberFormatException ignored){return Integer.MAX_VALUE;}}
    private String clean(String value){return value==null?"":value.trim();}
}
