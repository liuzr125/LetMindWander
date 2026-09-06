package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("app_parameter")
public class AppParameterEntity {
    @TableId private String id;
    @TableField("param_key") private String paramKey;
    @TableField("param_value") private String paramValue;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getParamKey() { return paramKey; } public void setParamKey(String value) { paramKey = value; }
    public String getParamValue() { return paramValue; } public void setParamValue(String value) { paramValue = value; }
}
