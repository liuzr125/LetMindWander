package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.TtsConfigRequest;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AliyunTtsService {
    private static final String APP_KEY="ALIYUN_NLS_APP_KEY",AK_ID="ALIYUN_AK_ID",AK_SECRET="ALIYUN_AK_SECRET";
    private static final String PREVIEW_TEXT="The quick brown fox jumps over the lazy dog.";
    private static final Map<String,VoiceDefinition> VOCABULARY_VOICES=voiceCatalog();
    private final JdbcTemplate jdbc;private final AppParameterService parameters;private final MediaService media;private final RestTemplate http;private final AppProperties properties;
    private final Map<String,Object> locks=new ConcurrentHashMap<String,Object>();
    private volatile String token;private volatile long tokenExpiresAt;
    public AliyunTtsService(JdbcTemplate jdbc,AppParameterService parameters,MediaService media,RestTemplate http,AppProperties properties){this.jdbc=jdbc;this.parameters=parameters;this.media=media;this.http=http;this.properties=properties;}

    public Map<String,Object> adminConfig(){Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("provider","aliyun_nls");result.put("endpoint",parameters.optional("tts.aliyun.endpoint","https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts"));result.put("voice",parameters.optional("tts.aliyun.voice","aixia"));result.put("sampleRate",integer(parameters.optional("tts.aliyun.sample_rate","16000"),16000));result.put("appKeyConfigured",parameters.configured(APP_KEY));result.put("accessKeyIdConfigured",parameters.configured(AK_ID));result.put("accessKeySecretConfigured",parameters.configured(AK_SECRET));result.put("temporaryTokenProductionEnabled",parameters.storedSecretConfigured(AppParameterService.NLS_TEMPORARY_TOKEN));result.put("productionCredentialMode",parameters.storedSecretConfigured(AppParameterService.NLS_TEMPORARY_TOKEN)?"temporary_nls_token":"access_key");result.put("ready",configured());result.put("logs",jdbc.queryForList("SELECT id,target_type,target_id,provider_code,voice,char_count,state,provider_request_id,asset_id,error_code,created_at FROM tts_usage_log ORDER BY created_at DESC LIMIT 100"));return result;}

    @Transactional
    public Map<String,Object> saveConfig(TtsConfigRequest request){String endpoint=clean(request.getEndpoint());if(!endpoint.matches("https://nls-gateway-[a-z0-9-]+\\.aliyuncs\\.com/(stream|rest)/.*"))throw bad("INVALID_TTS_ENDPOINT","只允许阿里云 NLS HTTPS 地址");int sample=request.getSampleRate()==null?16000:request.getSampleRate();if(sample!=8000&&sample!=16000)throw bad("INVALID_TTS_SAMPLE_RATE","采样率只能是 8000 或 16000");parameters.saveValue("tts.aliyun.endpoint",endpoint,"Aliyun NLS TTS endpoint");parameters.saveValue("tts.aliyun.voice",clean(request.getVoice()),"Aliyun NLS English voice");parameters.saveValue("tts.aliyun.sample_rate",String.valueOf(sample),"Aliyun NLS sample rate");parameters.saveSecret(APP_KEY,request.getAppKey());parameters.saveSecret(AK_ID,request.getAccessKeyId());parameters.saveSecret(AK_SECRET,request.getAccessKeySecret());token=null;tokenExpiresAt=0;return adminConfig();}

    public boolean configured(){return parameters.configured(APP_KEY)&&(parameters.storedSecretConfigured(AppParameterService.NLS_TEMPORARY_TOKEN)||(parameters.configured(AK_ID)&&parameters.configured(AK_SECRET)));}

    /** 只获取 NLS Token，不合成、不写 OSS、不产生 TTS 用量，用于管理员定位凭据与 RAM 授权问题。 */
    public Map<String,Object> connectionTest(){Map<String,Object> result=new LinkedHashMap<String,Object>();if(parameters.storedSecretConfigured(AppParameterService.NLS_TEMPORARY_TOKEN)){result.put("ready",true);result.put("code","TTS_TEMPORARY_TOKEN_PRODUCTION_READY");result.put("message","系统参数已保存临时 NLS Token（此处未校验有效期），可尝试生成音频");return result;}try{accessToken();result.put("ready",true);result.put("code","TTS_CONNECTION_READY");result.put("message","阿里云 NLS Token 获取成功，可生成音频");}catch(ApiException e){result.put("ready",false);result.put("code",e.getCode());result.put("message",e.getMessage());}return result;}
    public Map<String,Object> productionStatus(){Map<String,Object> result=new LinkedHashMap<String,Object>();boolean temporary=parameters.storedSecretConfigured(AppParameterService.NLS_TEMPORARY_TOKEN);result.put("temporaryTokenProductionEnabled",temporary);result.put("productionCredentialMode",temporary?"temporary_nls_token":"access_key");result.put("message",temporary?"当前使用系统参数中的临时 NLS Token 生成并保存音频":"当前使用已保存 AccessKey 自动获取 NLS Token");return result;}

    /**
     * 用实际保存的 AccessKey 调用 STS，比较其父账号 UID；输入值只参与本次比对，不写入数据库。
     * ISI 不提供可按 AppKey 查询其拥有者账号的公开接口，因此同时验证该 AccessKey 是否可申请当前 NLS Token。
     */
    public Map<String,Object> accountCheck(String expectedAccountId){String expected=clean(expectedAccountId).replaceAll("[^0-9]","");if(expected.length()<6)throw bad("INVALID_ALIYUN_ACCOUNT_ID","请输入阿里云控制台中的账号 UID");Map<String,Object> result=new LinkedHashMap<String,Object>();try{Map<?,?> identity=callerIdentity();String actual=String.valueOf(identity.get("AccountId"));boolean matches=CryptoUtils.constantTimeEquals(actual,expected);result.put("accountIdMasked",maskAccountId(actual));result.put("identityType",String.valueOf(identity.get("IdentityType")));result.put("accountMatched",matches);if(!matches){result.put("ready",false);result.put("code","TTS_ACCOUNT_MISMATCH");result.put("message","当前保存的 AccessKey 不属于输入的阿里云账号 UID，请重新核对并录入对应 RAM 用户的 AccessKey");return result;}try{accessToken();result.put("ready",true);result.put("code","TTS_ACCOUNT_AND_TOKEN_READY");result.put("message","AccessKey 账号 UID 匹配，且已成功获取 NLS Token");}catch(ApiException e){result.put("ready",false);result.put("code",e.getCode());result.put("message","AccessKey 账号 UID 匹配，但 NLS Token 校验失败："+e.getMessage());}return result;}catch(ApiException e){result.put("ready",false);result.put("code",e.getCode());result.put("message",e.getMessage());return result;}}

    /** 词库批次只暴露已按英语口音分组的阿里云 NLS 预设音色，避免任意参数进入任务快照。 */
    public List<Map<String,Object>> vocabularyVoices(){List<Map<String,Object>> result=new ArrayList<Map<String,Object>>();for(VoiceDefinition voice:VOCABULARY_VOICES.values()){Map<String,Object> item=new LinkedHashMap<String,Object>();item.put("code",voice.code);item.put("name",voice.name);item.put("accent",voice.accent);item.put("gender",voice.gender);item.put("label",voice.name+" · "+voice.gender);result.add(item);}return result;}
    public String vocabularyVoice(Object requested,String accent,String fallback){String code=clean(requested==null?fallback:String.valueOf(requested)).toLowerCase(Locale.ROOT);VoiceDefinition voice=requiredVocabularyVoice(code);if(!accent.equals(voice.accent))throw bad("INVALID_VOCABULARY_ACCENT","所选音色与目标口音不匹配");return voice.code;}

    /** 试听仅在内存中合成并以内联音频返回，不创建 media_asset、TTS 任务或审计发布数据。 */
    public Map<String,Object> previewVocabularyVoice(String voice,Integer sampleRate){VoiceDefinition selected=requiredVocabularyVoice(voice);int rate=validSampleRate(sampleRate==null?16000:sampleRate);Synthesis audio=synthesize(PREVIEW_TEXT,selected.code,rate);Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("voice",selected.code);result.put("sampleRate",rate);result.put("audioDataUrl","data:audio/mpeg;base64,"+Base64.getEncoder().encodeToString(audio.bytes));return result;}

    /** 临时 NLS Token 仅用于管理员本次试听，不持久化、不中转给小程序；会产生一次实际语音合成用量。 */
    public Map<String,Object> previewWithTemporaryToken(String nlsToken,String text,String voice,Integer sampleRate){String temporary=clean(nlsToken);if(temporary.length()<16)throw bad("INVALID_NLS_TEMPORARY_TOKEN","请输入有效的 NLS 临时 Token");String content=clean(text);if(content.isEmpty())throw bad("TTS_TEXT_EMPTY","请输入试听文本");if(content.codePointCount(0,content.length())>300)throw bad("TTS_TEXT_TOO_LONG","试听文本不能超过 300 个字符");int rate=validSampleRate(sampleRate==null?16000:sampleRate);String selected=clean(voice);if(selected.isEmpty())selected=parameters.optional("tts.aliyun.voice","aixia");try{Synthesis audio=synthesizeWithToken(content,selected,rate,temporary);Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("ready",true);result.put("productionEnabled",false);result.put("message","临时 NLS Token 试听成功；如需小程序使用，请点击“保存 Token 到系统参数”");result.put("audioDataUrl","data:audio/mpeg;base64,"+Base64.getEncoder().encodeToString(audio.bytes));return result;}catch(RestClientResponseException e){throw ttsProviderFailure(providerCode(e.getResponseBodyAsString()),providerMessage(e.getResponseBodyAsString()));}}
    /** 启用后，实际“生成音频”任务使用此临时 Token，并继续按既有规则保存音频文件及其资源记录。 */
    @Transactional
    public Map<String,Object> enableTemporaryTokenProduction(String nlsToken) {
        String temporary = clean(nlsToken);
        if (temporary.length() < 16 || temporary.length() > 4096) throw bad("INVALID_NLS_TEMPORARY_TOKEN", "请输入有效的临时 NLS Token");
        parameters.saveSecret(AppParameterService.NLS_TEMPORARY_TOKEN, temporary);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("enabled", true);
        result.put("message", "临时 NLS Token 已加密保存到系统参数；小程序生成音频时由服务端读取。Token 过期后请更新。");
        return result;
    }
    public Map<String,Object> disableTemporaryTokenProduction() {
        parameters.softDeleteByKey(AppParameterService.NLS_TEMPORARY_TOKEN);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("enabled", false);
        result.put("message", "临时 Token 参数已软删除；已有音频仍可播放，小程序生成新音频需重新配置。");
        return result;
    }

    /** 供词库批处理使用：先合成到内存，再以稳定 key 直接写入 OSS。 */
    public String synthesizeVocabularyAudio(String text,String voice,String purpose,String objectKey,String originUrl,String licenseNote){
        return synthesizeVocabularyAudio(text,voice,purpose,objectKey,originUrl,licenseNote,integer(parameters.optional("tts.aliyun.sample_rate","16000"),16000));
    }
    public String synthesizeVocabularyAudio(String text,String voice,String purpose,String objectKey,String originUrl,String licenseNote,int sampleRate){
        if(text==null||text.trim().isEmpty())throw bad("TTS_TEXT_EMPTY","语音文本不能为空");
        if(text.codePointCount(0,text.length())>300)throw bad("TTS_TEXT_TOO_LONG","单词或例句超过 300 字符，需要先拆分");
        Synthesis audio=synthesize(text,requiredVocabularyVoice(voice).code,validSampleRate(sampleRate));
        return media.storeVocabularyAudio(purpose,objectKey,audio.bytes,originUrl,licenseNote);
    }

    public Map<String,Object> speech(String ownerId, String contentId) {
        return speech(ownerId, contentId, null);
    }

    /** Explicit request credential: never falls back to AccessKey or a shared temporary session. */
    public Map<String,Object> speechWithTemporaryToken(String ownerId, String contentId, String nlsToken) {
        String temporary = clean(nlsToken);
        if (temporary.length() < 16 || temporary.length() > 4096)
            throw bad("INVALID_NLS_TEMPORARY_TOKEN", "请在生成音频窗口输入有效的临时 NLS Token");
        return speech(ownerId, contentId, temporary);
    }

    private Map<String,Object> speech(String ownerId, String contentId, String explicitToken) {
        Map<String,Object> row = content(contentId);
        String type = string(row, "content_type");
        boolean article = "english_article".equals(type), glossary = "glossary_word".equals(type);
        if (!article && !"word".equals(type) && !glossary)
            throw bad("TTS_TARGET_UNSUPPORTED", "只支持英语短文和单词朗读");
        String versionId = string(row, "version_id"), targetId = glossary ? string(row, "glossary_id") : contentId;
        String logType = article ? "english_article" : "word", voice = parameters.optional("tts.aliyun.voice", "aixia");
        String cached = cached(row, type, versionId, voice);
        if (cached != null) return speechResult(cached, voice, true, article ? string(row, "article_audio_asset_id") : null);
        Object lock = locks.computeIfAbsent(type + ":" + versionId + ":" + voice, key -> new Object());
        synchronized (lock) {
            try {
                row = content(contentId);
                cached = cached(row, type, versionId, voice);
                if (cached != null) return speechResult(cached, voice, true, article ? string(row, "article_audio_asset_id") : null);
                String text = article ? string(row, "body") : string(row, "word_term");
                if (text == null || text.trim().isEmpty()) throw bad("TTS_TEXT_EMPTY", "朗读文本为空");
                int chars = text.codePointCount(0, text.length());
                if (chars > 300) throw bad("TTS_TEXT_TOO_LONG", "当前短文超过 300 字符，请在管理端拆分后再生成音频");
                Synthesis audio = explicitToken == null ? synthesizeStoredToken(text, voice) : synthesizeExplicitToken(text, voice, explicitToken);
                String assetId = media.storePublicAudio(article ? "article_audio" : "word_audio", "tts/" + type,
                        audio.bytes, "audio/mpeg", "aliyun_nls", "Aliyun NLS generated speech");
                if (article) jdbc.update("UPDATE content_version SET article_audio_asset_id=?,article_audio_voice=?,article_audio_generated_at=CURRENT_TIMESTAMP WHERE id=?", assetId, voice, versionId);
                else if (glossary) jdbc.update("UPDATE article_word_glossary SET audio_asset_id=?,audio_voice=?,audio_generated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", assetId, voice, versionId);
                else {
                    String target = "tts:" + shortHash(voice);
                    jdbc.update("DELETE FROM pronunciation WHERE content_version_id=? AND target_key=?", versionId, target);
                    jdbc.update("INSERT INTO pronunciation(id,content_version_id,target_key,accent,asset_id,state) VALUES(?,?,?,?,?,'ready')", CryptoUtils.randomId(), versionId, target, "tts", assetId);
                }
                log(ownerId, logType, targetId, versionId, voice, chars, "succeeded", audio.requestId, assetId, null);
                return speechResult(media.referenceUrl(assetId), voice, false, assetId);
            } catch (ApiException e) {
                log(ownerId, logType, targetId, versionId, voice, 0, "failed", null, null, e.getCode());
                throw e;
            } catch (RuntimeException e) {
                log(ownerId, logType, targetId, versionId, voice, 0, "failed", null, null, "TTS_PROVIDER_ERROR");
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TTS_PROVIDER_ERROR", "语音合成失败，请稍后重试");
            } finally {
                locks.remove(type + ":" + versionId + ":" + voice, lock);
            }
        }
    }

    private Map<String,Object> speechResult(String url, String voice, boolean cached, String assetId) {
        Map<String,Object> response = result(url, voice, cached);
        response.put("assetId", assetId);
        return response;
    }

    private Synthesis synthesizeExplicitToken(String text, String voice, String temporary) {
        int rate = validSampleRate(integer(parameters.optional("tts.aliyun.sample_rate", "16000"), 16000));
        try {
            return synthesizeWithToken(text, voice, rate, temporary);
        } catch (RestClientResponseException e) {
            throw ttsProviderFailure(providerCode(e.getResponseBodyAsString()), providerMessage(e.getResponseBodyAsString()));
        }
    }

    private Synthesis synthesize(String text,String voice){return synthesize(text,voice,integer(parameters.optional("tts.aliyun.sample_rate","16000"),16000));}
    private Synthesis synthesize(String text,String voice,int sampleRate) {
        if (properties.getTts().isMockEnabled()) return synthesizeWithToken(text, voice, sampleRate, "mock");
        String credential = parameters.storedSecretConfigured(AppParameterService.NLS_TEMPORARY_TOKEN)
                ? parameters.requiredStoredSecret(AppParameterService.NLS_TEMPORARY_TOKEN) : accessToken();
        try { return synthesizeWithToken(text, voice, sampleRate, credential); }
        catch (RestClientResponseException e) { throw ttsProviderFailure(providerCode(e.getResponseBodyAsString()), providerMessage(e.getResponseBodyAsString())); }
    }

    private Synthesis synthesizeStoredToken(String text, String voice) {
        if (properties.getTts().isMockEnabled()) return synthesize(text, voice);
        String credential;
        try { credential = parameters.requiredStoredSecret(AppParameterService.NLS_TEMPORARY_TOKEN); }
        catch (ApiException e) {
            if ("PARAMETER_NOT_CONFIGURED".equals(e.getCode()))
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_TEMPORARY_TOKEN_NOT_CONFIGURED",
                        "语音临时 Token 尚未配置，请管理员在系统参数中保存 ALIYUN_NLS_TEMPORARY_TOKEN");
            throw e;
        }
        try { return synthesizeExplicitToken(text, voice, credential); }
        catch (ApiException e) {
            if ("TTS_TEMPORARY_TOKEN_REJECTED".equals(e.getCode()))
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_TEMPORARY_TOKEN_REJECTED",
                        "语音临时 Token 已失效或与 AppKey 不匹配，请管理员更新系统参数中的临时 Token");
            throw e;
        }
    }
    private Synthesis synthesizeWithToken(String text,String voice,int sampleRate,String nlsToken){if(properties.getTts().isMockEnabled())return new Synthesis("mock" ,("ID3"+text).getBytes(StandardCharsets.UTF_8));if(!parameters.configured(APP_KEY))throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_NOT_CONFIGURED","请先在 Web 管理端配置阿里云 TTS 项目 AppKey");Map<String,Object> body=new LinkedHashMap<String,Object>();body.put("appkey",parameters.required(APP_KEY));body.put("token",nlsToken);body.put("text",text);body.put("format","mp3");body.put("sample_rate",sampleRate);body.put("voice",voice);body.put("speech_rate",0);HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);ResponseEntity<byte[]> response=http.exchange(parameters.optional("tts.aliyun.endpoint","https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts"),HttpMethod.POST,new HttpEntity<Map<String,Object>>(body,headers),byte[].class);MediaType type=response.getHeaders().getContentType();if(!response.getStatusCode().is2xxSuccessful()||type==null||!"audio".equalsIgnoreCase(type.getType())||response.getBody()==null||response.getBody().length==0)throw ttsProviderFailure(providerCode(response.getBody()==null?null:new String(response.getBody(),StandardCharsets.UTF_8)),providerMessage(response.getBody()==null?null:new String(response.getBody(),StandardCharsets.UTF_8)));return new Synthesis(response.getHeaders().getFirst("task_id"),response.getBody());}
    /**
     * NLS SDK 在 Token 接口返回错误时仅记录日志、不会抛出包含响应体的异常，导致上层只能得到空 Token。
     * 这里按阿里云 RPC 签名规范通过 HTTPS 请求，既避免明文 HTTP，又能把安全的错误码反馈给管理员。
     */
    private synchronized String accessToken(){long now=Instant.now().getEpochSecond();if(token!=null&&tokenExpiresAt-now>300)return token;try{Map<String,String> query=new TreeMap<String,String>();query.put("AccessKeyId",parameters.required(AK_ID));query.put("Action","CreateToken");query.put("Format","JSON");query.put("RegionId","cn-shanghai");query.put("SignatureMethod","HMAC-SHA1");query.put("SignatureNonce",UUID.randomUUID().toString());query.put("SignatureVersion","1.0");query.put("Timestamp",DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()));query.put("Version","2019-02-28");String canonical=canonicalQuery(query);String signature=percentEncode(Base64.getEncoder().encodeToString(hmacSha1("GET&%2F&"+percentEncode(canonical),parameters.required(AK_SECRET)+"&")));URI uri=URI.create("https://nls-meta.cn-shanghai.aliyuncs.com/?Signature="+signature+"&"+canonical);ResponseEntity<Map> response=http.exchange(uri,HttpMethod.GET,new HttpEntity<Void>(headers()),Map.class);Map<?,?> body=response.getBody();Object tokenNode=body==null?null:body.get("Token");if(!(tokenNode instanceof Map))throw tokenFailure(providerCode(body),providerMessage(body),null);Map<?,?> tokenMap=(Map<?,?>)tokenNode;Object id=tokenMap.get("Id"),expiry=tokenMap.get("ExpireTime");if(id==null||String.valueOf(id).trim().isEmpty())throw tokenFailure(providerCode(body),providerMessage(body),null);token=String.valueOf(id);tokenExpiresAt=expiry instanceof Number?((Number)expiry).longValue():Long.parseLong(String.valueOf(expiry));return token;}catch(ApiException e){throw e;}catch(RestClientResponseException e){throw tokenFailure(providerCode(e.getResponseBodyAsString()),providerMessage(e.getResponseBodyAsString()),e);}catch(Exception e){throw tokenFailure(null,null,e);}}
    private Map<?,?> callerIdentity(){try{Map<?,?> body=signedRpc("sts.cn-hangzhou.aliyuncs.com","cn-hangzhou","2015-04-01","GetCallerIdentity");if(body.get("AccountId")==null)throw tokenFailure(providerCode(body),providerMessage(body),null);return body;}catch(ApiException e){throw e;}catch(RestClientResponseException e){throw tokenFailure(providerCode(e.getResponseBodyAsString()),providerMessage(e.getResponseBodyAsString()),e);}catch(Exception e){throw tokenFailure(null,null,e);}}
    private Map<?,?> signedRpc(String domain,String region,String version,String action){Map<String,String> query=new TreeMap<String,String>();query.put("AccessKeyId",parameters.required(AK_ID));query.put("Action",action);query.put("Format","JSON");query.put("RegionId",region);query.put("SignatureMethod","HMAC-SHA1");query.put("SignatureNonce",UUID.randomUUID().toString());query.put("SignatureVersion","1.0");query.put("Timestamp",DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()));query.put("Version",version);String canonical=canonicalQuery(query);String signature=percentEncode(Base64.getEncoder().encodeToString(hmacSha1("GET&%2F&"+percentEncode(canonical),parameters.required(AK_SECRET)+"&")));ResponseEntity<Map> response=http.exchange(URI.create("https://"+domain+"/?Signature="+signature+"&"+canonical),HttpMethod.GET,new HttpEntity<Void>(headers()),Map.class);return response.getBody();}
    private HttpHeaders headers(){HttpHeaders headers=new HttpHeaders();headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));return headers;}
    private String canonicalQuery(Map<String,String> values){List<String> parts=new ArrayList<String>();for(Map.Entry<String,String> entry:values.entrySet())parts.add(percentEncode(entry.getKey())+"="+percentEncode(entry.getValue()));return String.join("&",parts);}
    private String percentEncode(String value){try{return java.net.URLEncoder.encode(value,"UTF-8").replace("+","%20").replace("*","%2A").replace("%7E","~");}catch(Exception e){throw new IllegalStateException("UTF-8 unavailable",e);}}
    private byte[] hmacSha1(String text,String secret){try{Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("Cannot sign NLS token request",e);}}
    private String providerCode(Object source){String value=jsonField(source,"Code");if(value==null)value=jsonField(source,"status");return value==null?null:value.replaceAll("[^A-Za-z0-9._-]","");}
    private String providerMessage(Object source){String value=jsonField(source,"Message");if(value==null)value=jsonField(source,"status_text");return value==null?null:value.replaceAll("[\\r\\n]"," ").trim();}
    @SuppressWarnings("unchecked") private String jsonField(Object source,String key){if(source instanceof Map){Object value=((Map<?,?>)source).get(key);return value==null?null:String.valueOf(value);}if(source==null)return null;java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(String.valueOf(source));return matcher.find()?matcher.group(1):null;}
    private ApiException tokenFailure(String providerCode,String providerMessage,Exception error){String details=((providerCode==null?"":providerCode)+" "+(providerMessage==null?"":providerMessage)+" "+(error==null?"":String.valueOf(error.getMessage()))).toLowerCase(Locale.ROOT);String suffix=providerCode==null||providerCode.isEmpty()?"":"（阿里云返回码："+providerCode+"）";if(details.contains("40020503")||details.contains("no permission")||details.contains("permission denied")||details.contains("accessdenied")||details.contains("forbidden.ram"))return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_NLS_PERMISSION_DENIED","RAM 用户缺少 NLS Token 权限，请授予 AliyunNLSFullAccess 后重试"+suffix);if(details.contains("invalidaccesskeyid")||details.contains("invalid accesskey"))return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_ACCESS_KEY_INVALID","AccessKey ID 无效或已禁用，请使用当前 RAM 用户的启用中 AccessKey"+suffix);if(details.contains("signaturedoesnotmatch")||details.contains("signature"))return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_ACCESS_KEY_SECRET_INVALID","AccessKey Secret 与 AccessKey ID 不匹配，请重新录入该 RAM 用户创建时的原始 Secret"+suffix);if(details.contains("requesttime")||details.contains("timestamp"))return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_REQUEST_TIME_INVALID","后端服务器时间异常，请校准系统时间后重试"+suffix);if(details.contains("unknownhost")||details.contains("connect")||details.contains("timeout"))return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_TOKEN_UNREACHABLE","无法连接阿里云 NLS Token 服务，请检查后端网络与 DNS");return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"TTS_TOKEN_FAILED","阿里云 Token 请求被拒绝；请核对 RAM 权限、AccessKey 状态以及 AppKey 是否属于同一阿里云账号"+suffix);}
    private ApiException ttsProviderFailure(String providerCode,String providerMessage){String suffix=providerCode==null||providerCode.isEmpty()?"":"（阿里云返回码："+providerCode+"）";String details=((providerCode==null?"":providerCode)+" "+(providerMessage==null?"":providerMessage)).toLowerCase(Locale.ROOT);if(details.contains("token")||details.contains("auth"))return new ApiException(HttpStatus.BAD_GATEWAY,"TTS_TEMPORARY_TOKEN_REJECTED","临时 NLS Token 无效、过期或与项目 AppKey 不匹配"+suffix);if(details.contains("appkey")||details.contains("app key"))return new ApiException(HttpStatus.BAD_GATEWAY,"TTS_APP_KEY_REJECTED","项目 AppKey 无效或不属于该 NLS 服务账号"+suffix);return new ApiException(HttpStatus.BAD_GATEWAY,"TTS_PROVIDER_REJECTED","阿里云未接受语音合成请求，请检查临时 Token、AppKey 和语音服务配置"+suffix);}
    private String maskAccountId(String value){if(value==null||value.length()<8)return "已识别";return value.substring(0,4)+"****"+value.substring(value.length()-4);}
    private Map<String,Object> content(String id){if(id!=null&&id.startsWith("glossary:")){String term=id.substring("glossary:".length()).toLowerCase(Locale.ROOT);if(!term.matches("[a-z]+"))throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","短文词汇不存在");List<Map<String,Object>> glossary=jdbc.queryForList("SELECT 'glossary_word' content_type,id version_id,id glossary_id,term word_term,audio_asset_id glossary_audio_asset_id,audio_voice glossary_audio_voice FROM article_word_glossary WHERE term=?",term);if(glossary.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","短文词汇不存在");return glossary.get(0);}List<Map<String,Object>> rows=jdbc.queryForList("SELECT lc.content_type,cv.id version_id,cv.body,cv.word_term,cv.article_audio_asset_id,cv.article_audio_voice FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.id=? AND lc.state='published'",id);if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","学习内容不存在");return rows.get(0);}
    private String cached(Map<String,Object> row,String type,String versionId,String voice){String assetId=null;if("english_article".equals(type)&&voice.equals(string(row,"article_audio_voice")))assetId=string(row,"article_audio_asset_id");if("glossary_word".equals(type)&&voice.equals(string(row,"glossary_audio_voice")))assetId=string(row,"glossary_audio_asset_id");if("word".equals(type)){String target="tts:"+shortHash(voice);List<String> ids=jdbc.query("SELECT p.asset_id FROM pronunciation p JOIN media_asset m ON m.id=p.asset_id AND m.state='ready' WHERE p.content_version_id=? AND p.target_key=? AND p.state='ready' LIMIT 1",(rs,n)->rs.getString(1),versionId,target);if(!ids.isEmpty())assetId=ids.get(0);}if(assetId==null)return null;Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM media_asset WHERE id=? AND state='ready'",Integer.class,assetId);return count!=null&&count>0?media.referenceUrl(assetId):null;}
    private void log(String owner,String type,String target,String version,String voice,int chars,String state,String request,String asset,String error){jdbc.update("INSERT INTO tts_usage_log(id,owner_id,target_type,target_id,content_version_id,provider_code,voice,char_count,state,provider_request_id,asset_id,error_code) VALUES(?,?,?,?,?,'aliyun_nls',?,?,?,?,?,?)",CryptoUtils.randomId(),owner,type,target,version,voice,chars,state,request,asset,error);}
    private Map<String,Object> result(String url,String voice,boolean cached){Map<String,Object> r=new LinkedHashMap<String,Object>();r.put("audioUrl",url);r.put("voice",voice);r.put("cached",cached);return r;}
    private static Map<String,VoiceDefinition> voiceCatalog(){Map<String,VoiceDefinition> voices=new LinkedHashMap<String,VoiceDefinition>();addVoice(voices,"eva","Eva","us","美式女声");addVoice(voices,"andy","Andy","us","美式男声");addVoice(voices,"luna","Luna","uk","英式女声");addVoice(voices,"emily","Emily","uk","英式女声");addVoice(voices,"wendy","Wendy","uk","英式女声");addVoice(voices,"olivia","Olivia","uk","英式女声");addVoice(voices,"luca","Luca","uk","英式男声");addVoice(voices,"eric","Eric","uk","英式男声");addVoice(voices,"william","William","uk","英式男声");return Collections.unmodifiableMap(voices);}
    private static void addVoice(Map<String,VoiceDefinition> voices,String code,String name,String accent,String gender){voices.put(code,new VoiceDefinition(code,name,accent,gender));}
    private VoiceDefinition requiredVocabularyVoice(String value){VoiceDefinition voice=VOCABULARY_VOICES.get(clean(value).toLowerCase(Locale.ROOT));if(voice==null)throw bad("UNSUPPORTED_VOCABULARY_VOICE","请选择阿里云 NLS 支持的英语音色");return voice;}
    private int validSampleRate(int sampleRate){if(sampleRate!=8000&&sampleRate!=16000)throw bad("INVALID_TTS_SAMPLE_RATE","采样率只能是 8000 或 16000");return sampleRate;}
    private String shortHash(String value){byte[] bytes=CryptoUtils.sha256(value);StringBuilder hex=new StringBuilder();for(int i=0;i<6;i++)hex.append(String.format("%02x",bytes[i]&255));return hex.toString();}private Object raw(Map<String,Object> row,String key){Object value=row.get(key);return value==null?row.get(key.toUpperCase()):value;}private String string(Map<String,Object> row,String key){Object value=raw(row,key);return value==null?null:String.valueOf(value);}private int integer(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception e){return fallback;}}private String clean(String value){return value==null?"":value.trim();}private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}private static class VoiceDefinition{final String code,name,accent,gender;VoiceDefinition(String code,String name,String accent,String gender){this.code=code;this.name=name;this.accent=accent;this.gender=gender;}}private static class Synthesis{final String requestId;final byte[] bytes;Synthesis(String requestId,byte[] bytes){this.requestId=requestId;this.bytes=bytes;}}
}
