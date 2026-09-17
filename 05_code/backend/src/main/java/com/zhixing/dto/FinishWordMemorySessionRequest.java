package com.zhixing.dto;

public class FinishWordMemorySessionRequest {
    private Integer expectedVersion;
    private Boolean partial;
    public Integer getExpectedVersion(){return expectedVersion;} public void setExpectedVersion(Integer v){expectedVersion=v;}
    public Boolean getPartial(){return partial;} public void setPartial(Boolean v){partial=v;}
}
