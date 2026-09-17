package com.zhixing.model;

public class AdminUserLearningView {
    private AdminUserView account;
    private PlanView plan;
    private VocabularyBookProgressView vocabulary;
    private WordNotebookSummaryView notebook;
    private LearningPageView notebookItems;
    private TodayProgressView today;

    public AdminUserView getAccount(){return account;} public void setAccount(AdminUserView v){account=v;}
    public PlanView getPlan(){return plan;} public void setPlan(PlanView v){plan=v;}
    public VocabularyBookProgressView getVocabulary(){return vocabulary;} public void setVocabulary(VocabularyBookProgressView v){vocabulary=v;}
    public WordNotebookSummaryView getNotebook(){return notebook;} public void setNotebook(WordNotebookSummaryView v){notebook=v;}
    public LearningPageView getNotebookItems(){return notebookItems;} public void setNotebookItems(LearningPageView v){notebookItems=v;}
    public TodayProgressView getToday(){return today;} public void setToday(TodayProgressView v){today=v;}

    public static class TodayProgressView {
        private Integer totalCount,completedCount,doingCount;
        private Double completionRate;
        public Integer getTotalCount(){return totalCount;} public void setTotalCount(Integer v){totalCount=v;}
        public Integer getCompletedCount(){return completedCount;} public void setCompletedCount(Integer v){completedCount=v;}
        public Integer getDoingCount(){return doingCount;} public void setDoingCount(Integer v){doingCount=v;}
        public Double getCompletionRate(){return completionRate;} public void setCompletionRate(Double v){completionRate=v;}
    }
}
