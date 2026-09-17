package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.entity.AppParameterEntity;
import com.zhixing.mapper.AppParameterMapper;
import com.zhixing.config.AppProperties;
import com.zhixing.common.CryptoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 运行参数从 app_parameter 读取；不提供任何对外查询接口，避免凭据随 API 暴露。 */
@Service
public class AppParameterService {
    private final AppParameterMapper parameters;
    private final AppProperties properties;
    public AppParameterService(AppParameterMapper parameters,AppProperties properties) { this.parameters = parameters;this.properties=properties; }
    public String required(String key) {
        String environment=environment(key);
        if(environment!=null&&!environment.trim().isEmpty())return environment.trim();
        AppParameterEntity parameter = parameters.selectActiveByKey(key);
        if (parameter == null || parameter.getParamValue() == null || parameter.getParamValue().trim().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PARAMETER_NOT_CONFIGURED", "服务参数尚未完成配置");
        }
        try{return parameter.getIsSecret()!=null&&parameter.getIsSecret()==1?CryptoUtils.open(parameter.getParamValue(),properties.getAi().getCredentialEncryptionKey()):parameter.getParamValue();}
        catch(IllegalStateException exception){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CREDENTIAL_KEY_UNAVAILABLE","密钥解密配置不可用");}
    }
    public String optional(String key, String fallback) {
        String environment=environment(key);
        if(environment!=null&&!environment.trim().isEmpty())return environment.trim();
        AppParameterEntity parameter = parameters.selectActiveByKey(key);
        return parameter == null || parameter.getParamValue() == null || parameter.getParamValue().trim().isEmpty() ? fallback : parameter.getParamValue();
    }
    public Map<String,String> activeByPrefix(String prefix) {
        Map<String,String> result=new LinkedHashMap<String,String>();
        for(AppParameterEntity parameter:parameters.selectActiveByPrefix(prefix)) {
            if(parameter.getParamKey()!=null&&parameter.getParamValue()!=null)result.put(parameter.getParamKey(),parameter.getParamValue());
        }
        return result;
    }
    public boolean configured(String key){
        String environment=environment(key);if(environment!=null&&!environment.trim().isEmpty())return true;
        AppParameterEntity parameter=parameters.selectActiveByKey(key);if(parameter==null||parameter.getParamValue()==null||parameter.getParamValue().trim().isEmpty())return false;
        if(parameter.getIsSecret()!=null&&parameter.getIsSecret()==1&&parameter.getParamValue().startsWith("enc:v1:")){try{CryptoUtils.open(parameter.getParamValue(),properties.getAi().getCredentialEncryptionKey());}catch(Exception exception){return false;}}
        return true;
    }
    public void saveSecret(String key,String value){
        if(value==null||value.trim().isEmpty())return;
        String master=properties.getAi().getCredentialEncryptionKey();
        if(master==null||master.trim().length()<16)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CREDENTIAL_ENCRYPTION_NOT_CONFIGURED","请先在服务端配置 AI_CREDENTIAL_ENCRYPTION_KEY（至少 16 位）");
        AppParameterEntity existing=parameters.selectActiveByKey(key);
        if(existing==null){AppParameterEntity entity=new AppParameterEntity();entity.setId(CryptoUtils.randomId());entity.setParamKey(key);entity.setParamValue(CryptoUtils.seal(value.trim(),master));entity.setIsSecret(1);entity.setDescription("AI provider credential");entity.setState("active");entity.setVersionNo(1);parameters.insert(entity);}
        else{existing.setParamValue(CryptoUtils.seal(value.trim(),master));existing.setIsSecret(1);existing.setState("active");existing.setVersionNo(existing.getVersionNo()==null?1:existing.getVersionNo()+1);parameters.updateById(existing);}
    }
    public void saveValue(String key,String value,String description){
        if(key==null||value==null||value.trim().isEmpty())return;
        AppParameterEntity existing=parameters.selectActiveByKey(key);
        if(existing==null){AppParameterEntity entity=new AppParameterEntity();entity.setId(CryptoUtils.randomId());entity.setParamKey(key);entity.setParamValue(value.trim());entity.setIsSecret(0);entity.setDescription(description);entity.setState("active");entity.setVersionNo(1);parameters.insert(entity);}
        else{existing.setParamValue(value.trim());existing.setIsSecret(0);existing.setDescription(description);existing.setState("active");existing.setVersionNo(existing.getVersionNo()==null?1:existing.getVersionNo()+1);parameters.updateById(existing);}
    }
    private String environment(String key){
        String value=System.getenv(key);
        if(value!=null&&!value.trim().isEmpty())return value;
        return System.getenv(key.toUpperCase(java.util.Locale.ROOT).replace('.','_'));
    }
}
