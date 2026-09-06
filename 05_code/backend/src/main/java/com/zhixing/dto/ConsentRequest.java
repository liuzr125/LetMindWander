package com.zhixing.dto;

import javax.validation.constraints.NotBlank;

public class ConsentRequest {
    @NotBlank(message = "同意用途不能为空")
    private String purpose;
    @NotBlank(message = "文本版本不能为空")
    private String documentVersion;
    @NotBlank(message = "决定不能为空")
    private String decision;

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
}
