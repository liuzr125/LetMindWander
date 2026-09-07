package com.zhixing.model;

/** 词条/例句的发音条目：accent（uk/us）+ 可播放的音频地址。 */
public class PronunciationView {
    private String accent;
    private String phonetic;
    private String audioUrl;

    public String getAccent(){return accent;} public void setAccent(String v){accent=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getAudioUrl(){return audioUrl;} public void setAudioUrl(String v){audioUrl=v;}
}
