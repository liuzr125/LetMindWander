package com.zhixing.dto;
import javax.validation.constraints.NotBlank;
public class DecideFriendRequest {
 @NotBlank(message="处理决定不能为空") private String decision; private Integer expectedVersion;
 public String getDecision(){return decision;} public void setDecision(String v){decision=v;}
 public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
