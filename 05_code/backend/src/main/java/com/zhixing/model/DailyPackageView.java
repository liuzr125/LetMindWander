package com.zhixing.model;

import java.time.LocalDate;
import java.util.List;

public class DailyPackageView {
  private LocalDate businessDate;
  private Boolean active, temporary, nonActiveDay;
  private String state, message;
  private Integer originalCount, currentCount, doneCount;
  private Double completionRate;
  private Integer versionNo;

  public Integer getVersionNo() {
    return versionNo;
  }

  public void setVersionNo(Integer v) {
    versionNo = v;
  }

  private List<DailyTaskView> tasks;
  private Object gaps;
  private java.time.Instant wordsSelectedAt;
  private String wordsBookId;
  private String wordsBookName, wordsSelectionNote;
  private Integer wordsTargetCount, wordsUnavailableCount, pendingConsolidationCount;

  public String getWordsBookName() {
    return wordsBookName;
  }

  public void setWordsBookName(String v) {
    wordsBookName = v;
  }

  public String getWordsSelectionNote() {
    return wordsSelectionNote;
  }

  public void setWordsSelectionNote(String v) {
    wordsSelectionNote = v;
  }

  public Integer getWordsTargetCount() {
    return wordsTargetCount;
  }

  public void setWordsTargetCount(Integer v) {
    wordsTargetCount = v;
  }

  public Integer getWordsUnavailableCount() {
    return wordsUnavailableCount;
  }

  public void setWordsUnavailableCount(Integer v) {
    wordsUnavailableCount = v;
  }

  public Integer getPendingConsolidationCount() {
    return pendingConsolidationCount;
  }

  public void setPendingConsolidationCount(Integer v) {
    pendingConsolidationCount = v;
  }

  public java.time.Instant getWordsSelectedAt() {
    return wordsSelectedAt;
  }

  public void setWordsSelectedAt(java.time.Instant value) {
    wordsSelectedAt = value;
  }

  public String getWordsBookId() {
    return wordsBookId;
  }

  public void setWordsBookId(String value) {
    wordsBookId = value;
  }

  public LocalDate getBusinessDate() {
    return businessDate;
  }

  public void setBusinessDate(LocalDate v) {
    businessDate = v;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean v) {
    active = v;
  }

  public Boolean getTemporary() {
    return temporary;
  }

  public void setTemporary(Boolean v) {
    temporary = v;
  }

  public Boolean getNonActiveDay() {
    return nonActiveDay;
  }

  public void setNonActiveDay(Boolean v) {
    nonActiveDay = v;
  }

  public String getState() {
    return state;
  }

  public void setState(String v) {
    state = v;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String v) {
    message = v;
  }

  public Integer getOriginalCount() {
    return originalCount;
  }

  public void setOriginalCount(Integer v) {
    originalCount = v;
  }

  public Integer getCurrentCount() {
    return currentCount;
  }

  public void setCurrentCount(Integer v) {
    currentCount = v;
  }

  public Integer getDoneCount() {
    return doneCount;
  }

  public void setDoneCount(Integer v) {
    doneCount = v;
  }

  public Double getCompletionRate() {
    return completionRate;
  }

  public void setCompletionRate(Double v) {
    completionRate = v;
  }

  public List<DailyTaskView> getTasks() {
    return tasks;
  }

  public void setTasks(List<DailyTaskView> v) {
    tasks = v;
  }

  public Object getGaps() {
    return gaps;
  }

  public void setGaps(Object v) {
    gaps = v;
  }
}
