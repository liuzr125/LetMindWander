package com.zhixing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown=true)
public class AiAskRequest {
    @NotBlank(message="请输入你想问的问题")
    @Size(max=8000,message="问题最多 8000 个字符")
    private String question;
    public String getQuestion(){return question;} public void setQuestion(String v){question=v;}
}
