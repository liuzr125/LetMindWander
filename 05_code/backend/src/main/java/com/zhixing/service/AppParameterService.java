package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.entity.AppParameterEntity;
import com.zhixing.mapper.AppParameterMapper;
import com.zhixing.config.AppProperties;
import com.zhixing.common.CryptoUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/** 运行参数从 app_parameter 读取；不提供任何对外查询接口，避免凭据随 API 暴露。 */
@Service
public class AppParameterService {
    public static final String NLS_TEMPORARY_TOKEN = "ALIYUN_NLS_TEMPORARY_TOKEN";

    /** Only an active, non-deleted encrypted database value is usable; no environment/cache fallback. */
    public String requiredStoredSecret(String key) {
        AppParameterEntity parameter = parameters.selectActiveByKey(key);
        if (parameter == null || parameter.getParamValue() == null || parameter.getParamValue().trim().isEmpty())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PARAMETER_NOT_CONFIGURED", "服务参数尚未完成配置");
        if (!Integer.valueOf(1).equals(parameter.getIsSecret()) || !parameter.getParamValue().startsWith("enc:v1:"))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CREDENTIAL_KEY_UNAVAILABLE", "敏感参数必须重新加密保存");
        try { return CryptoUtils.open(parameter.getParamValue(), properties.getAi().getCredentialEncryptionKey()); }
        catch (IllegalStateException e) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CREDENTIAL_KEY_UNAVAILABLE", "密钥解密配置不可用"); }
    }
    public boolean storedSecretConfigured(String key) {
        try { requiredStoredSecret(key); return true; } catch (ApiException e) { return false; }
    }
    public void softDeleteByKey(String key) {
        AppParameterEntity parameter = parameters.selectByKeyIncludingDeleted(key);
        if (parameter != null && !Integer.valueOf(1).equals(parameter.getDelIs())) adminSoftDelete(parameter.getId());
    }

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
    /** 管理端可修改的运行开关以数据库为显式覆盖值，服务配置仅作为尚未保存时的默认值。 */
    public String optionalStored(String key,String fallback){
        AppParameterEntity parameter=parameters.selectActiveByKey(key);
        return parameter==null||parameter.getParamValue()==null||parameter.getParamValue().trim().isEmpty()?fallback:parameter.getParamValue().trim();
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
        AppParameterEntity existing=parameters.selectByKeyIncludingDeleted(key);
        if(existing==null){AppParameterEntity entity=new AppParameterEntity();entity.setId(CryptoUtils.randomId());entity.setParamKey(key);entity.setParamValue(CryptoUtils.seal(value.trim(),master));entity.setIsSecret(1);entity.setDescription("AI provider credential");entity.setState("active");entity.setDelIs(0);entity.setVersionNo(1);parameters.insert(entity);}
        else{existing.setParamValue(CryptoUtils.seal(value.trim(),master));existing.setIsSecret(1);existing.setState("active");existing.setDelIs(0);existing.setVersionNo(existing.getVersionNo()==null?1:existing.getVersionNo()+1);parameters.updateById(existing);}
    }
    public void saveValue(String key,String value,String description){
        if(key==null||value==null||value.trim().isEmpty())return;
        AppParameterEntity existing=parameters.selectByKeyIncludingDeleted(key);
        if(existing==null){AppParameterEntity entity=new AppParameterEntity();entity.setId(CryptoUtils.randomId());entity.setParamKey(key);entity.setParamValue(value.trim());entity.setIsSecret(0);entity.setDescription(description);entity.setState("active");entity.setDelIs(0);entity.setVersionNo(1);parameters.insert(entity);}
        else{existing.setParamValue(value.trim());existing.setIsSecret(0);existing.setDescription(description);existing.setState("active");existing.setDelIs(0);existing.setVersionNo(existing.getVersionNo()==null?1:existing.getVersionNo()+1);parameters.updateById(existing);}
    }
    /** 管理端列表刻意不下发敏感原文；凭据仅能在编辑时覆盖，不能被读回。 */
    public List<Map<String,Object>> adminList(String keyword){String query=keyword==null?"":keyword.trim();List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(AppParameterEntity parameter:parameters.selectVisibleForAdmin(query)){Map<String,Object> item=new LinkedHashMap<String,Object>();boolean secret=parameter.getIsSecret()!=null&&parameter.getIsSecret()==1;item.put("id",parameter.getId());item.put("paramKey",parameter.getParamKey());item.put("paramValue",secret?null:parameter.getParamValue());item.put("valueDisplay",secret?"已加密保存":parameter.getParamValue());item.put("isSecret",secret);item.put("valueConfigured",parameter.getParamValue()!=null&&!parameter.getParamValue().trim().isEmpty());item.put("description",parameter.getDescription());item.put("state",parameter.getState());item.put("versionNo",parameter.getVersionNo());result.add(item);}return result;}
    public Map<String,Object> adminPage(String keyword,Integer rawPage,Integer rawPageSize){int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?10:rawPageSize;if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 个参数");String query=keyword==null?"":keyword.trim();if(query.length()>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_KEYWORD","搜索词最多 100 个字符");int total=parameters.countVisibleForAdmin(query),totalPages=total==0?0:(total+pageSize-1)/pageSize;List<Map<String,Object>> items=new ArrayList<Map<String,Object>>();for(AppParameterEntity parameter:parameters.selectVisiblePageForAdmin(query,(page-1)*pageSize,pageSize))items.add(adminItem(parameter));Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("total",total);result.put("page",page);result.put("pageSize",pageSize);result.put("totalPages",totalPages);result.put("keyword",query);result.put("items",items);return result;}
    /** 新增遇到同名软删除记录时复用该记录并恢复，保持 param_key 的全局唯一约束。 */
    public Map<String,Object> adminCreate(Map<String,Object> input){String key=parameterKey(input.get("paramKey"));AppParameterEntity existing=parameters.selectByKeyIncludingDeleted(key);if(existing!=null&&!(existing.getDelIs()!=null&&existing.getDelIs()==1))throw new ApiException(HttpStatus.CONFLICT,"PARAMETER_KEY_EXISTS","参数键已存在");return adminSave(existing,input,key);}
    public Map<String,Object> adminUpdate(String id,Map<String,Object> input){AppParameterEntity existing=parameters.selectById(id);if(existing==null||existing.getDelIs()!=null&&existing.getDelIs()==1)throw new ApiException(HttpStatus.NOT_FOUND,"PARAMETER_NOT_FOUND","系统参数不存在");String key=parameterKey(input.get("paramKey"));if(!key.equals(existing.getParamKey()))throw new ApiException(HttpStatus.BAD_REQUEST,"PARAMETER_KEY_IMMUTABLE","参数键创建后不可修改");return adminSave(existing,input,key);}
    public void adminSoftDelete(String id){AppParameterEntity existing=parameters.selectById(id);if(existing==null||existing.getDelIs()!=null&&existing.getDelIs()==1)throw new ApiException(HttpStatus.NOT_FOUND,"PARAMETER_NOT_FOUND","系统参数不存在");existing.setDelIs(1);existing.setState("inactive");existing.setVersionNo(existing.getVersionNo()==null?1:existing.getVersionNo()+1);parameters.updateById(existing);}
    private Map<String,Object> adminSave(AppParameterEntity existing,Map<String,Object> input,String key){boolean secret=NLS_TEMPORARY_TOKEN.equalsIgnoreCase(key)||bool(input.get("isSecret"));String description=text(input.get("description"),200,"参数说明");String state=choice(input.get("state"),Arrays.asList("active","inactive"),"状态");String entered=nullable(input.get("paramValue"));if(existing==null){if(entered==null)throw new ApiException(HttpStatus.BAD_REQUEST,"PARAMETER_VALUE_REQUIRED","参数值不能为空");existing=new AppParameterEntity();existing.setId(CryptoUtils.randomId());existing.setParamKey(key);existing.setVersionNo(1);}else existing.setVersionNo(existing.getVersionNo()==null?1:existing.getVersionNo()+1);
        if(entered!=null){if(NLS_TEMPORARY_TOKEN.equalsIgnoreCase(key)&&(entered.length()<16||entered.length()>4096))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_NLS_TEMPORARY_TOKEN","请输入有效的临时 NLS Token（16 至 4096 字符）");existing.setParamValue(secret?seal(entered):entered);}else if(existing.getParamValue()==null||!secret||existing.getIsSecret()==null||existing.getIsSecret()!=1)throw new ApiException(HttpStatus.BAD_REQUEST,"PARAMETER_VALUE_REQUIRED","非敏感参数或切换密钥类型时必须填写参数值");
        existing.setIsSecret(secret?1:0);existing.setDescription(description);existing.setState(state);existing.setDelIs(0);if(parameters.selectById(existing.getId())==null)parameters.insert(existing);else parameters.updateById(existing);return adminItem(existing);}
    private Map<String,Object> adminItem(AppParameterEntity parameter){boolean secret=parameter.getIsSecret()!=null&&parameter.getIsSecret()==1;Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("id",parameter.getId());item.put("paramKey",parameter.getParamKey());item.put("paramValue",secret?null:parameter.getParamValue());item.put("valueDisplay",secret?"已加密保存":parameter.getParamValue());item.put("isSecret",secret);item.put("valueConfigured",parameter.getParamValue()!=null&&!parameter.getParamValue().trim().isEmpty());item.put("description",parameter.getDescription());item.put("state",parameter.getState());item.put("versionNo",parameter.getVersionNo());return item;}
    private String parameterKey(Object value){String key=nullable(value);if(key==null||!key.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}"))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER_KEY","参数键须以字母开头，且只包含字母、数字、下划线、点或连字符");return key;}
    private String text(Object value,int max,String label){String result=nullable(value);if(result==null||result.length()>max)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER_FIELD",label+"不能为空且长度不能超过 "+max);return result;}
    private String choice(Object value,List<String> allowed,String label){String result=nullable(value);if(result==null||!allowed.contains(result))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER_FIELD",label+"不正确");return result;}
    private String nullable(Object value){String result=value==null?null:String.valueOf(value).trim();return result==null||result.isEmpty()?null:result;}
    private boolean bool(Object value){return value instanceof Boolean?(Boolean)value:"true".equalsIgnoreCase(String.valueOf(value));}
    private String seal(String value){String master=properties.getAi().getCredentialEncryptionKey();if(master==null||master.trim().length()<16)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CREDENTIAL_ENCRYPTION_NOT_CONFIGURED","请先在服务端配置 AI_CREDENTIAL_ENCRYPTION_KEY（至少 16 位）");return CryptoUtils.seal(value,master);}
    private String environment(String key){
        String value=System.getenv(key);
        if(value!=null&&!value.trim().isEmpty())return value;
        return System.getenv(key.toUpperCase(java.util.Locale.ROOT).replace('.','_'));
    }
}
