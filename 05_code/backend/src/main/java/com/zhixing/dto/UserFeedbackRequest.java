package com.zhixing.dto;
import javax.validation.constraints.NotBlank;
public class UserFeedbackRequest {
 @NotBlank(message="反馈内容不能为空") private String body; private String category,contentId,requestId;
 public String getBody(){return body;} public void setBody(String v){body=v;}
 public String getCategory(){return category;} public void setCategory(String v){category=v;}
 public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
 public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;}
}
