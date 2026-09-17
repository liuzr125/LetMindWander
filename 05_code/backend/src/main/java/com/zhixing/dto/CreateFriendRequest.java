package com.zhixing.dto;
import javax.validation.constraints.NotBlank;
public class CreateFriendRequest {
 @NotBlank(message="目标用户不能为空") private String targetUserId;
 @NotBlank(message="申请备注不能为空") private String remark;
 public String getTargetUserId(){return targetUserId;} public void setTargetUserId(String v){targetUserId=v;}
 public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
