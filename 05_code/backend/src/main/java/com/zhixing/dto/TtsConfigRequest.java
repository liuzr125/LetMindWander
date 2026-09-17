package com.zhixing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class TtsConfigRequest {
    @NotBlank private String endpoint;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") private String voice;
    private Integer sampleRate;
    private String appKey;
    private String accessKeyId;
    private String accessKeySecret;
    public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
    public String getVoice(){return voice;} public void setVoice(String v){voice=v;}
    public Integer getSampleRate(){return sampleRate;} public void setSampleRate(Integer v){sampleRate=v;}
    public String getAppKey(){return appKey;} public void setAppKey(String v){appKey=v;}
    public String getAccessKeyId(){return accessKeyId;} public void setAccessKeyId(String v){accessKeyId=v;}
    public String getAccessKeySecret(){return accessKeySecret;} public void setAccessKeySecret(String v){accessKeySecret=v;}
}
