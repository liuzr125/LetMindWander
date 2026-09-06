package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.time.LocalDate;

@TableName("daily_package")
public class DailyPackageEntity {
    @TableId private String id;
    @TableField("owner_id") private String ownerId;
    @TableField("business_date") private LocalDate businessDate;
    @TableField("plan_id") private String planId;
    private String state;
    @TableField("is_temporary") private Integer temporary;
    @TableField("version_no") private Integer versionNo;
    @TableField("budget_seconds") private Integer budgetSeconds;
    @TableField("original_count") private Integer originalCount;
    @TableField("current_count") private Integer currentCount;
    @TableField("final_done_count") private Integer finalDoneCount;
    @TableField("gap_json") private String gapJson;
    @TableField("activated_at") private Instant activatedAt;
    public String getId(){return id;} public void setId(String v){id=v;} public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;}
    public LocalDate getBusinessDate(){return businessDate;} public void setBusinessDate(LocalDate v){businessDate=v;} public String getPlanId(){return planId;} public void setPlanId(String v){planId=v;}
    public String getState(){return state;} public void setState(String v){state=v;} public Integer getTemporary(){return temporary;} public void setTemporary(Integer v){temporary=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;} public Integer getBudgetSeconds(){return budgetSeconds;} public void setBudgetSeconds(Integer v){budgetSeconds=v;}
    public Integer getOriginalCount(){return originalCount;} public void setOriginalCount(Integer v){originalCount=v;} public Integer getCurrentCount(){return currentCount;} public void setCurrentCount(Integer v){currentCount=v;}
    public Integer getFinalDoneCount(){return finalDoneCount;} public void setFinalDoneCount(Integer v){finalDoneCount=v;} public String getGapJson(){return gapJson;} public void setGapJson(String v){gapJson=v;}
    public Instant getActivatedAt(){return activatedAt;} public void setActivatedAt(Instant v){activatedAt=v;}
}
