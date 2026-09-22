package com.zhixing.model;

import java.util.List;

/** 管理端「词书学习记录」详情：汇总 + 每日明细。 */
public class AdminStudyRecordDetailView {
    private AdminStudyRecordView record;
    private List<AdminStudyDayView> days;
    private Integer activeDays,roundNewWords;
    public AdminStudyRecordView getRecord(){return record;} public void setRecord(AdminStudyRecordView v){record=v;}
    public List<AdminStudyDayView> getDays(){return days;} public void setDays(List<AdminStudyDayView> v){days=v;}
    public Integer getActiveDays(){return activeDays;} public void setActiveDays(Integer v){activeDays=v;}
    public Integer getRoundNewWords(){return roundNewWords;} public void setRoundNewWords(Integer v){roundNewWords=v;}
}
