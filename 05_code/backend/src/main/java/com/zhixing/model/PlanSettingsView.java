package com.zhixing.model;

import java.util.ArrayList;
import java.util.List;

/** 仅暴露计划页面可用的非敏感数据字典，不开放通用参数查询。 */
public class PlanSettingsView {
    private Integer dailyBudgetMin,dailyBudgetMax,dailyBudgetStep;
    private List<Integer> dailyBudgetPresets=new ArrayList<Integer>();
    private List<OptionView> weekdays=new ArrayList<OptionView>(),difficulties=new ArrayList<OptionView>();
    private Integer techCountMax,newWordCountMax,reviewLimitMax;
    private Integer defaultDailyBudgetMin,defaultWeekdaysMask,defaultTechCount,defaultNewWordCount,defaultReviewLimit;
    private String defaultDifficulty;
    private Boolean defaultJournalEnabled,defaultReviewEnabled;
    private Integer techEstimateSeconds,wordEstimateSeconds,journalEstimateSeconds,reviewEstimateSeconds;

    public Integer getDailyBudgetMin(){return dailyBudgetMin;} public void setDailyBudgetMin(Integer v){dailyBudgetMin=v;}
    public Integer getDailyBudgetMax(){return dailyBudgetMax;} public void setDailyBudgetMax(Integer v){dailyBudgetMax=v;}
    public Integer getDailyBudgetStep(){return dailyBudgetStep;} public void setDailyBudgetStep(Integer v){dailyBudgetStep=v;}
    public List<Integer> getDailyBudgetPresets(){return dailyBudgetPresets;} public void setDailyBudgetPresets(List<Integer> v){dailyBudgetPresets=v;}
    public List<OptionView> getWeekdays(){return weekdays;} public void setWeekdays(List<OptionView> v){weekdays=v;}
    public List<OptionView> getDifficulties(){return difficulties;} public void setDifficulties(List<OptionView> v){difficulties=v;}
    public Integer getTechCountMax(){return techCountMax;} public void setTechCountMax(Integer v){techCountMax=v;}
    public Integer getNewWordCountMax(){return newWordCountMax;} public void setNewWordCountMax(Integer v){newWordCountMax=v;}
    public Integer getReviewLimitMax(){return reviewLimitMax;} public void setReviewLimitMax(Integer v){reviewLimitMax=v;}
    public Integer getDefaultDailyBudgetMin(){return defaultDailyBudgetMin;} public void setDefaultDailyBudgetMin(Integer v){defaultDailyBudgetMin=v;}
    public Integer getDefaultWeekdaysMask(){return defaultWeekdaysMask;} public void setDefaultWeekdaysMask(Integer v){defaultWeekdaysMask=v;}
    public Integer getDefaultTechCount(){return defaultTechCount;} public void setDefaultTechCount(Integer v){defaultTechCount=v;}
    public Integer getDefaultNewWordCount(){return defaultNewWordCount;} public void setDefaultNewWordCount(Integer v){defaultNewWordCount=v;}
    public Integer getDefaultReviewLimit(){return defaultReviewLimit;} public void setDefaultReviewLimit(Integer v){defaultReviewLimit=v;}
    public String getDefaultDifficulty(){return defaultDifficulty;} public void setDefaultDifficulty(String v){defaultDifficulty=v;}
    public Boolean getDefaultJournalEnabled(){return defaultJournalEnabled;} public void setDefaultJournalEnabled(Boolean v){defaultJournalEnabled=v;}
    public Boolean getDefaultReviewEnabled(){return defaultReviewEnabled;} public void setDefaultReviewEnabled(Boolean v){defaultReviewEnabled=v;}
    public Integer getTechEstimateSeconds(){return techEstimateSeconds;} public void setTechEstimateSeconds(Integer v){techEstimateSeconds=v;}
    public Integer getWordEstimateSeconds(){return wordEstimateSeconds;} public void setWordEstimateSeconds(Integer v){wordEstimateSeconds=v;}
    public Integer getJournalEstimateSeconds(){return journalEstimateSeconds;} public void setJournalEstimateSeconds(Integer v){journalEstimateSeconds=v;}
    public Integer getReviewEstimateSeconds(){return reviewEstimateSeconds;} public void setReviewEstimateSeconds(Integer v){reviewEstimateSeconds=v;}

    public static class OptionView {
        private String value,label;
        public OptionView(){}
        public OptionView(String value,String label){this.value=value;this.label=label;}
        public String getValue(){return value;} public void setValue(String v){value=v;}
        public String getLabel(){return label;} public void setLabel(String v){label=v;}
    }
}
