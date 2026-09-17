package com.zhixing.model;
public class MineOverviewView {
 private ProfileView profile; private Integer planMinutes,weekdaysMask,aiUsed,aiLimit,pendingFriendRequests,activeSchedules,todayUpdates;
 private String planDifficulty,planTopics;
 public ProfileView getProfile(){return profile;} public void setProfile(ProfileView v){profile=v;}
 public Integer getPlanMinutes(){return planMinutes;} public void setPlanMinutes(Integer v){planMinutes=v;}
 public Integer getWeekdaysMask(){return weekdaysMask;} public void setWeekdaysMask(Integer v){weekdaysMask=v;}
 public String getPlanDifficulty(){return planDifficulty;} public void setPlanDifficulty(String v){planDifficulty=v;}
 public String getPlanTopics(){return planTopics;} public void setPlanTopics(String v){planTopics=v;}
 public Integer getAiUsed(){return aiUsed;} public void setAiUsed(Integer v){aiUsed=v;}
 public Integer getAiLimit(){return aiLimit;} public void setAiLimit(Integer v){aiLimit=v;}
 public Integer getPendingFriendRequests(){return pendingFriendRequests;} public void setPendingFriendRequests(Integer v){pendingFriendRequests=v;}
 public Integer getActiveSchedules(){return activeSchedules;} public void setActiveSchedules(Integer v){activeSchedules=v;}
 public Integer getTodayUpdates(){return todayUpdates;} public void setTodayUpdates(Integer v){todayUpdates=v;}
}
