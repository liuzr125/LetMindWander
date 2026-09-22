package com.zhixing.model;

import java.time.Instant;

/** 「重新学习」记录：某轮里的一次重置（时间、重置词数、取消的复习排期数）。 */
public class AdminStudyResetView {
    private Instant resetAt;
    private Integer resetCount,pausedReviewCount;
    public Instant getResetAt(){return resetAt;} public void setResetAt(Instant v){resetAt=v;}
    public Integer getResetCount(){return resetCount;} public void setResetCount(Integer v){resetCount=v;}
    public Integer getPausedReviewCount(){return pausedReviewCount;} public void setPausedReviewCount(Integer v){pausedReviewCount=v;}
}
