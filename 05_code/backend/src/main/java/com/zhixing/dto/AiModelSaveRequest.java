package com.zhixing.dto;

import javax.validation.constraints.*;
import java.math.BigDecimal;

public class AiModelSaveRequest {
    @NotBlank private String providerCode;
    @NotBlank private String modelCode;
    @NotBlank private String displayName;
    @NotBlank private String specification;
    @NotBlank private String baseUrl;
    @NotBlank private String apiKeyParamKey;
    private String apiKey;
    private boolean enabled;
    private boolean defaultModel;
    @Min(1) @Max(8192) private int maxOutputTokens=1200;
    @Min(5) @Max(120) private int timeoutSeconds=60;
    @NotBlank private String currency="CNY";
    @DecimalMin("0.0") private BigDecimal inputPerMillion=BigDecimal.ZERO;
    @DecimalMin("0.0") private BigDecimal outputPerMillion=BigDecimal.ZERO;
    private Integer expectedVersion;
    public String getProviderCode(){return providerCode;} public void setProviderCode(String v){providerCode=v;}
    public String getModelCode(){return modelCode;} public void setModelCode(String v){modelCode=v;}
    public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
    public String getSpecification(){return specification;} public void setSpecification(String v){specification=v;}
    public String getBaseUrl(){return baseUrl;} public void setBaseUrl(String v){baseUrl=v;}
    public String getApiKeyParamKey(){return apiKeyParamKey;} public void setApiKeyParamKey(String v){apiKeyParamKey=v;}
    public String getApiKey(){return apiKey;} public void setApiKey(String v){apiKey=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public boolean isDefaultModel(){return defaultModel;} public void setDefaultModel(boolean v){defaultModel=v;}
    public int getMaxOutputTokens(){return maxOutputTokens;} public void setMaxOutputTokens(int v){maxOutputTokens=v;}
    public int getTimeoutSeconds(){return timeoutSeconds;} public void setTimeoutSeconds(int v){timeoutSeconds=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public BigDecimal getInputPerMillion(){return inputPerMillion;} public void setInputPerMillion(BigDecimal v){inputPerMillion=v;}
    public BigDecimal getOutputPerMillion(){return outputPerMillion;} public void setOutputPerMillion(BigDecimal v){outputPerMillion=v;}
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
}
