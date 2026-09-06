package com.zhixing.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("package_revision")
public class PackageRevisionEntity {
 @TableId private String id; @TableField("owner_id") private String ownerId; @TableField("package_id") private String packageId; @TableField("version_no") private Integer versionNo; private String reason; @TableField("before_json") private String beforeJson; @TableField("after_json") private String afterJson;
 public String getId(){return id;} public void setId(String v){id=v;} public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;} public String getPackageId(){return packageId;} public void setPackageId(String v){packageId=v;} public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;} public String getReason(){return reason;} public void setReason(String v){reason=v;} public String getBeforeJson(){return beforeJson;} public void setBeforeJson(String v){beforeJson=v;} public String getAfterJson(){return afterJson;} public void setAfterJson(String v){afterJson=v;}
}
