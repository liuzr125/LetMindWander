package com.zhixing.model;
import java.time.Instant;
public class PrivacyView {
 private Boolean aiConsent,followRecordingConsent; private String exportId,exportState,deletionId,deletionState; private Instant exportExpiresAt;
 public Boolean getAiConsent(){return aiConsent;} public void setAiConsent(Boolean v){aiConsent=v;}
 public Boolean getFollowRecordingConsent(){return followRecordingConsent;} public void setFollowRecordingConsent(Boolean v){followRecordingConsent=v;}
 public String getExportId(){return exportId;} public void setExportId(String v){exportId=v;}
 public String getExportState(){return exportState;} public void setExportState(String v){exportState=v;}
 public Instant getExportExpiresAt(){return exportExpiresAt;} public void setExportExpiresAt(Instant v){exportExpiresAt=v;}
 public String getDeletionId(){return deletionId;} public void setDeletionId(String v){deletionId=v;}
 public String getDeletionState(){return deletionState;} public void setDeletionState(String v){deletionState=v;}
}
