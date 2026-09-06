package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.entity.AppParameterEntity;
import com.zhixing.mapper.AppParameterMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 运行参数从 app_parameter 读取；不提供任何对外查询接口，避免凭据随 API 暴露。 */
@Service
public class AppParameterService {
    private final AppParameterMapper parameters;
    public AppParameterService(AppParameterMapper parameters) { this.parameters = parameters; }
    public String required(String key) {
        AppParameterEntity parameter = parameters.selectActiveByKey(key);
        if (parameter == null || parameter.getParamValue() == null || parameter.getParamValue().trim().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PARAMETER_NOT_CONFIGURED", "服务参数尚未完成配置");
        }
        return parameter.getParamValue();
    }
    public String optional(String key, String fallback) {
        AppParameterEntity parameter = parameters.selectActiveByKey(key);
        return parameter == null || parameter.getParamValue() == null || parameter.getParamValue().trim().isEmpty() ? fallback : parameter.getParamValue();
    }
}
