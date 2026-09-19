package com.zhixing.model;

import java.math.BigDecimal;

public class AiRuntimeModel {
    public String id,providerCode,modelCode,displayName,specification,baseUrl,apiKeyParamKey,priceId,currency,pricingJson;
    public int maxOutputTokens,timeoutSeconds,versionNo;
    public BigDecimal inputPerMillion,outputPerMillion;
}
