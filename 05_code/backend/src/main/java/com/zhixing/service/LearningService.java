package com.zhixing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.mapper.LearningContentMapper;
import com.zhixing.model.LearningFiltersView;
import com.zhixing.model.LearningListItemView;
import com.zhixing.model.LearningPageView;
import com.zhixing.model.LearningTopicView;
import com.zhixing.model.WordNotebookSummaryView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LearningService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final Set<String> TYPES=new HashSet<String>(Arrays.asList("tech","word","english_article"));
    private static final Pattern ARTICLE_NUMBER_BEFORE_COLON=Pattern.compile("(?<!\\d)(\\d+)(?=\\s*:)");
    private static final Pattern ANY_NUMBER=Pattern.compile("\\d+");
    private final LearningContentMapper learning;
    private final AppParameterService parameters;
    private final ObjectMapper json;
    private final com.zhixing.mapper.VocabularyBookMapper books;
    public LearningService(LearningContentMapper learning,AppParameterService parameters,ObjectMapper json,com.zhixing.mapper.VocabularyBookMapper books){this.learning=learning;this.parameters=parameters;this.json=json;this.books=books;}

    public LearningFiltersView filters(){
        Map<String,String> values=parameters.activeByPrefix("learning.");
        LearningFiltersView view=new LearningFiltersView();
        view.setDifficulties(withAll(options(values.get("learning.difficulties"),difficultyDefaults())));
        view.setStages(withAll(stageOptions(values.get("learning.stages"))));
        view.setNotebookStatuses(withAll(options(values.get("learning.notebook_statuses"),notebookStatusDefaults())));
        return view;
    }

    public LearningPageView page(String ownerId,String type,String topicId,String difficulty,String stage,Boolean notebook,
            String status,String keyword,Integer page,Integer pageSize){
        String safeType=clean(type);if(safeType.isEmpty())safeType="tech";
        if(!TYPES.contains(safeType))throw bad("INVALID_CONTENT_TYPE","内容类型不正确");
        LearningFiltersView filters=filters();
        String safeDifficulty=clean(difficulty);if(!optionValues(filters.getDifficulties()).contains(safeDifficulty))throw bad("INVALID_DIFFICULTY","难度筛选不正确");
        String safeStage=clean(stage);if(!optionValues(filters.getStages()).contains(safeStage))throw bad("INVALID_STAGE","学段筛选不正确");
        String safeStatus=clean(status);if(!optionValues(filters.getNotebookStatuses()).contains(safeStatus))throw bad("INVALID_NOTEBOOK_STATUS","生词本状态筛选不正确");
        boolean inNotebook=Boolean.TRUE.equals(notebook);
        if(inNotebook&&!"word".equals(safeType))throw bad("NOTEBOOK_WORD_ONLY","生词本只支持英语词条");
        int safePage=page==null?1:Math.max(1,page);int safeSize=pageSize==null?20:Math.max(1,Math.min(pageSize,50));
        boolean orderArticles="english_article".equals(safeType);int offset=(safePage-1)*safeSize;
        List<LearningListItemView> rows=learning.selectLearningPage(ownerId,safeType,clean(topicId),safeDifficulty,safeStage,
                inNotebook,safeStatus,clean(keyword),LocalDate.now(BUSINESS_ZONE),orderArticles,offset,safeSize+1);
        boolean hasMore;
        if(orderArticles){rows.sort(Comparator.comparingInt((LearningListItemView item)->articleNumber(item.getTitle()))
                    .thenComparing(item->item.getTitle()==null?"":item.getTitle())
                    .thenComparing(item->item.getContentId()==null?"":item.getContentId()));
            int from=Math.min(offset,rows.size()),to=Math.min(from+safeSize,rows.size());hasMore=to<rows.size();rows=new ArrayList<LearningListItemView>(rows.subList(from,to));}
        else{hasMore=rows.size()>safeSize;if(hasMore)rows.remove(rows.size()-1);}
        LearningPageView result=new LearningPageView();result.setPage(safePage);result.setPageSize(safeSize);result.setHasMore(hasMore);result.setItems(rows);return result;
    }

    public List<LearningTopicView> topics(String ownerId){return learning.selectTopics(ownerId);}
    public WordNotebookSummaryView notebookSummary(String ownerId){LocalDate today=LocalDate.now(BUSINESS_ZONE);return new WordNotebookSummaryView(learning.selectNotebookTotal(ownerId),learning.selectNotebookDue(ownerId,today));}
    private List<LearningFiltersView.OptionView> options(String raw,List<LearningFiltersView.OptionView> fallback){
        List<LearningFiltersView.OptionView> result=new ArrayList<LearningFiltersView.OptionView>();
        try{for(JsonNode node:json.readTree(raw)){String value=clean(node.path("value").asText("")),label=clean(node.path("label").asText(""));if(value.isEmpty()||label.isEmpty())continue;boolean exists=false;for(LearningFiltersView.OptionView item:result)if(item.getValue().equals(value))exists=true;if(!exists)result.add(new LearningFiltersView.OptionView(value,label));}}catch(Exception ignored){}
        return result.isEmpty()?fallback:result;
    }
    private List<LearningFiltersView.OptionView> withAll(List<LearningFiltersView.OptionView> values){List<LearningFiltersView.OptionView> result=new ArrayList<LearningFiltersView.OptionView>();result.add(new LearningFiltersView.OptionView("","全部"));result.addAll(values);return result;}
    private Set<String> optionValues(List<LearningFiltersView.OptionView> options){Set<String> values=new HashSet<String>();for(LearningFiltersView.OptionView option:options)values.add(option.getValue());return values;}
    private List<LearningFiltersView.OptionView> difficultyDefaults(){return Arrays.asList(new LearningFiltersView.OptionView("intro","入门"),new LearningFiltersView.OptionView("advanced","进阶"));}
    /**
     * 学段筛选的候选项直接来自可用词书：label=词书名，value=词书 level_code，
     * 选中后按该词书成员刷新词汇列表（与「选择学习词书」页、管理端词书页同一口径）。
     * 没有任何词书时回退到 learning.stages 参数 / 内置小学·初中·高中。
     */
    private List<LearningFiltersView.OptionView> stageOptions(String raw){
        List<LearningFiltersView.OptionView> result=new ArrayList<LearningFiltersView.OptionView>();
        try{
            for(com.zhixing.model.VocabularyBookView book:books.selectAvailable(null)){
                String level=clean(book.getLevelCode());
                if(level.isEmpty())continue;
                boolean exists=false;
                for(LearningFiltersView.OptionView item:result)if(item.getValue().equals(level))exists=true;
                if(exists)continue;
                String label=clean(book.getBookName());
                if(label.isEmpty())label=level;
                if(book.getWordCount()==null||book.getWordCount()<=0)label=label+"（准备中）";
                result.add(new LearningFiltersView.OptionView(level,label));
            }
        }catch(RuntimeException exception){result.clear();}
        return result.isEmpty()?options(raw,stageDefaults()):result;
    }
    private List<LearningFiltersView.OptionView> stageDefaults(){return Arrays.asList(new LearningFiltersView.OptionView("primary","小学"),new LearningFiltersView.OptionView("junior","初中"),new LearningFiltersView.OptionView("senior","高中"));}
    private List<LearningFiltersView.OptionView> notebookStatusDefaults(){return Arrays.asList(new LearningFiltersView.OptionView("review","待复习"),new LearningFiltersView.OptionView("familiar","已熟悉"));}
    private int articleNumber(String title){if(title==null)return Integer.MAX_VALUE;Matcher preferred=ARTICLE_NUMBER_BEFORE_COLON.matcher(title);
        if(preferred.find())return parseArticleNumber(preferred.group(1));Matcher fallback=ANY_NUMBER.matcher(title);return fallback.find()?parseArticleNumber(fallback.group()):Integer.MAX_VALUE;}
    private int parseArticleNumber(String value){try{return Integer.parseInt(value);}catch(NumberFormatException ignored){return Integer.MAX_VALUE;}}
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
    private String clean(String value){return value==null?"":value.trim();}
}
