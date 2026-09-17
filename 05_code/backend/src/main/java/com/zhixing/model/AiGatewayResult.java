package com.zhixing.model;

import java.util.Map;

public class AiGatewayResult {
    public String answer,providerRequestId;
    public int inputTokens,outputTokens;
    public Map<String,Object> usage;
}
