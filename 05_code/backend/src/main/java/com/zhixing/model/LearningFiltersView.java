package com.zhixing.model;

import java.util.ArrayList;
import java.util.List;

/** 学习页可公开的筛选数据字典。 */
public class LearningFiltersView {
    private List<OptionView> difficulties=new ArrayList<OptionView>();
    private List<OptionView> stages=new ArrayList<OptionView>();
    private List<OptionView> notebookStatuses=new ArrayList<OptionView>();

    public List<OptionView> getDifficulties(){return difficulties;}
    public void setDifficulties(List<OptionView> value){difficulties=value;}
    public List<OptionView> getStages(){return stages;}
    public void setStages(List<OptionView> value){stages=value;}
    public List<OptionView> getNotebookStatuses(){return notebookStatuses;}
    public void setNotebookStatuses(List<OptionView> value){notebookStatuses=value;}

    public static class OptionView {
        private String value,label;
        public OptionView(){}
        public OptionView(String value,String label){this.value=value;this.label=label;}
        public String getValue(){return value;}
        public void setValue(String value){this.value=value;}
        public String getLabel(){return label;}
        public void setLabel(String label){this.label=label;}
    }
}
