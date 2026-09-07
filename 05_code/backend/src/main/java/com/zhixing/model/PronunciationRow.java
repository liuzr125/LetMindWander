package com.zhixing.model;

/** Mapper 层读取 pronunciation 表行的中间结构，用于按 sense/example 归属分组。 */
public class PronunciationRow {
    private String senseId;
    private String exampleId;
    private String accent;
    private String phonetic;
    private String assetId;

    public String getSenseId(){return senseId;} public void setSenseId(String v){senseId=v;}
    public String getExampleId(){return exampleId;} public void setExampleId(String v){exampleId=v;}
    public String getAccent(){return accent;} public void setAccent(String v){accent=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getAssetId(){return assetId;} public void setAssetId(String v){assetId=v;}
}
