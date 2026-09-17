package com.zhixing.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ContentDetailView {
    private String contentId, versionId, contentType, title, summary, body, difficulty, stage;
    private String wordTerm, phonetic, meaning, exampleText, exampleTranslation;
    private String originUrl, originAuthor, licenseSnapshot, sourceName, sourceType, sourceUrl;
    private String articleAudioAssetId,articleAudioUrl,articleAudioVoice;
    private Instant originPublishedAt;
    private String articleBlocksJson;
    private Integer estimatedSeconds, familiarityPercent, recordVersion;
    private Boolean understood, favorite, inReview, inWordBook;
    private List<String> topics = new ArrayList<String>();
    private List<PronunciationView> pronunciations = new ArrayList<PronunciationView>();
    private List<WordSenseView> senses = new ArrayList<WordSenseView>();
    private List<ArticleBlockView> articleBlocks = new ArrayList<ArticleBlockView>();
    public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
    public String getVersionId(){return versionId;} public void setVersionId(String v){versionId=v;}
    public String getContentType(){return contentType;} public void setContentType(String v){contentType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getBody(){return body;} public void setBody(String v){body=v;}
    public String getDifficulty(){return difficulty;} public void setDifficulty(String v){difficulty=v;}
    public String getStage(){return stage;} public void setStage(String v){stage=v;}
    public String getWordTerm(){return wordTerm;} public void setWordTerm(String v){wordTerm=v;}
    public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
    public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    public String getExampleText(){return exampleText;} public void setExampleText(String v){exampleText=v;}
    public String getExampleTranslation(){return exampleTranslation;} public void setExampleTranslation(String v){exampleTranslation=v;}
    public String getOriginUrl(){return originUrl;} public void setOriginUrl(String v){originUrl=v;}
    public String getOriginAuthor(){return originAuthor;} public void setOriginAuthor(String v){originAuthor=v;}
    public String getLicenseSnapshot(){return licenseSnapshot;} public void setLicenseSnapshot(String v){licenseSnapshot=v;}
    public String getSourceName(){return sourceName;} public void setSourceName(String v){sourceName=v;}
    public String getSourceType(){return sourceType;} public void setSourceType(String v){sourceType=v;}
    public String getSourceUrl(){return sourceUrl;} public void setSourceUrl(String v){sourceUrl=v;}
    public String getArticleAudioUrl(){return articleAudioUrl;} public void setArticleAudioUrl(String v){articleAudioUrl=v;}
    public String getArticleAudioVoice(){return articleAudioVoice;} public void setArticleAudioVoice(String v){articleAudioVoice=v;}
    @com.fasterxml.jackson.annotation.JsonIgnore public String getArticleAudioAssetId(){return articleAudioAssetId;} public void setArticleAudioAssetId(String v){articleAudioAssetId=v;}
    public Instant getOriginPublishedAt(){return originPublishedAt;} public void setOriginPublishedAt(Instant v){originPublishedAt=v;}
    public Integer getEstimatedSeconds(){return estimatedSeconds;} public void setEstimatedSeconds(Integer v){estimatedSeconds=v;}
    public Integer getFamiliarityPercent(){return familiarityPercent;} public void setFamiliarityPercent(Integer v){familiarityPercent=v;}
    public Integer getRecordVersion(){return recordVersion;} public void setRecordVersion(Integer v){recordVersion=v;}
    @com.fasterxml.jackson.annotation.JsonIgnore public String getArticleBlocksJson(){return articleBlocksJson;} public void setArticleBlocksJson(String v){articleBlocksJson=v;}
    public List<ArticleBlockView> getArticleBlocks(){return articleBlocks;} public void setArticleBlocks(List<ArticleBlockView> v){articleBlocks=v;}
    public Boolean getUnderstood(){return understood;} public void setUnderstood(Boolean v){understood=v;}
    public Boolean getFavorite(){return favorite;} public void setFavorite(Boolean v){favorite=v;}
    public Boolean getInReview(){return inReview;} public void setInReview(Boolean v){inReview=v;}
    public Boolean getInWordBook(){return inWordBook;} public void setInWordBook(Boolean v){inWordBook=v;}
    public List<String> getTopics(){return topics;} public void setTopics(List<String> v){topics=v;}
    public List<PronunciationView> getPronunciations(){return pronunciations;} public void setPronunciations(List<PronunciationView> v){pronunciations=v;}
    public List<WordSenseView> getSenses(){return senses;} public void setSenses(List<WordSenseView> v){senses=v;}

    public static class WordSenseView {
        private String id, partOfSpeech, meaning;
        private Integer sortNo;
        private List<WordExampleView> examples = new ArrayList<WordExampleView>();
        private List<PronunciationView> pronunciations = new ArrayList<PronunciationView>();
        public String getId(){return id;} public void setId(String v){id=v;}
        public String getPartOfSpeech(){return partOfSpeech;} public void setPartOfSpeech(String v){partOfSpeech=v;}
        public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
        public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
        public List<WordExampleView> getExamples(){return examples;} public void setExamples(List<WordExampleView> v){examples=v;}
        public List<PronunciationView> getPronunciations(){return pronunciations;} public void setPronunciations(List<PronunciationView> v){pronunciations=v;}
    }

    public static class WordExampleView {
        private String id, sentence, translation;
        private Integer sortNo;
        private List<PronunciationView> pronunciations = new ArrayList<PronunciationView>();
        public String getId(){return id;} public void setId(String v){id=v;}
        public String getSentence(){return sentence;} public void setSentence(String v){sentence=v;}
        public String getTranslation(){return translation;} public void setTranslation(String v){translation=v;}
        public Integer getSortNo(){return sortNo;} public void setSortNo(Integer v){sortNo=v;}
        public List<PronunciationView> getPronunciations(){return pronunciations;} public void setPronunciations(List<PronunciationView> v){pronunciations=v;}
    }

    public static class ArticleBlockView {
        private String paragraphId,text,translation;
        private List<ArticleWordView> words=new ArrayList<ArticleWordView>();
        private List<ArticleTokenView> tokens=new ArrayList<ArticleTokenView>();
        public String getParagraphId(){return paragraphId;} public void setParagraphId(String v){paragraphId=v;}
        public String getText(){return text;} public void setText(String v){text=v;}
        public String getTranslation(){return translation;} public void setTranslation(String v){translation=v;}
        public List<ArticleWordView> getWords(){return words;} public void setWords(List<ArticleWordView> v){words=v;}
        public List<ArticleTokenView> getTokens(){return tokens;} public void setTokens(List<ArticleTokenView> v){tokens=v;}
    }

    public static class ArticleWordView {
        private String contentId,term,meaning;
        public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
        public String getTerm(){return term;} public void setTerm(String v){term=v;}
        public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
    }

    public static class ArticleTokenView {
        private String text,contentId,speechKey,phonetic,meaning,audioUrl;
        private boolean word,known;
        public String getText(){return text;} public void setText(String v){text=v;}
        public String getContentId(){return contentId;} public void setContentId(String v){contentId=v;}
        public String getSpeechKey(){return speechKey;} public void setSpeechKey(String v){speechKey=v;}
        public String getPhonetic(){return phonetic;} public void setPhonetic(String v){phonetic=v;}
        public String getMeaning(){return meaning;} public void setMeaning(String v){meaning=v;}
        public String getAudioUrl(){return audioUrl;} public void setAudioUrl(String v){audioUrl=v;}
        public boolean isWord(){return word;} public void setWord(boolean v){word=v;}
        public boolean isKnown(){return known;} public void setKnown(boolean v){known=v;}
    }
}
