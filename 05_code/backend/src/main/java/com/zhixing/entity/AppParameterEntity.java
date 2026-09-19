package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("app_parameter")
public class AppParameterEntity {
    @TableId private String id;
    @TableField("param_key") private String paramKey;
    @TableField("param_value") private String paramValue;
    @TableField("is_secret") private Integer isSecret;
    private String description;
    private String state;
    @TableField("del_is") private Integer delIs;
    @TableField("version_no") private Integer versionNo;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getParamKey() { return paramKey; } public void setParamKey(String value) { paramKey = value; }
    public String getParamValue() { return paramValue; } public void setParamValue(String value) { paramValue = value; }
    public Integer getIsSecret(){return isSecret;} public void setIsSecret(Integer v){isSecret=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public String getState(){return state;} public void setState(String v){state=v;}
    public Integer getDelIs(){return delIs;} public void setDelIs(Integer v){delIs=v;}
    public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;}
}
