package com.zhixing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String privacyVersion = "PRIVACY_V1";
    private String adminToken = "dev-admin-token";
    private String adminPrincipalId = "00000000000000000000000000000002";
    private Duration sessionTtl = Duration.ofDays(30);
    private Duration registrationTtl = Duration.ofMinutes(10);
    private String registrationStore = "memory";
    private List<String> allowedOrigins = new ArrayList<String>();
    private final Wechat wechat = new Wechat();
    private final Sms sms = new Sms();
    private final WordMemory wordMemory = new WordMemory();
    private final Ai ai = new Ai();
    private final Tts tts = new Tts();

    public String getPrivacyVersion() { return privacyVersion; }
    public void setPrivacyVersion(String privacyVersion) { this.privacyVersion = privacyVersion; }
    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
    public String getAdminPrincipalId() { return adminPrincipalId; }
    public void setAdminPrincipalId(String adminPrincipalId) { this.adminPrincipalId = adminPrincipalId; }
    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
    public Duration getRegistrationTtl() { return registrationTtl; }
    public void setRegistrationTtl(Duration registrationTtl) { this.registrationTtl = registrationTtl; }
    public String getRegistrationStore() { return registrationStore; }
    public void setRegistrationStore(String registrationStore) { this.registrationStore = registrationStore; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public Wechat getWechat() { return wechat; }
    public Sms getSms() { return sms; }
    public WordMemory getWordMemory() { return wordMemory; }
    public Ai getAi() { return ai; }
    public Tts getTts() { return tts; }

    public static class Wechat {
        private boolean mockEnabled = true;
        private String appId = "wx-dev-app";
        private String appSecret = "";

        public boolean isMockEnabled() { return mockEnabled; }
        public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    }

    public static class Sms {
        private boolean mockEnabled;
        private Duration codeTtl = Duration.ofMinutes(5);
        private Duration resendInterval = Duration.ofSeconds(60);
        private int maxAttempts = 5;
        private String fixedCode = "";

        public boolean isMockEnabled() { return mockEnabled; }
        public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
        public Duration getCodeTtl() { return codeTtl; }
        public void setCodeTtl(Duration codeTtl) { this.codeTtl = codeTtl; }
        public Duration getResendInterval() { return resendInterval; }
        public void setResendInterval(Duration resendInterval) { this.resendInterval = resendInterval; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public String getFixedCode() { return fixedCode; }
        public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }
    }

    public static class WordMemory {
        private boolean enabled = true;
        private boolean listeningEnabled;
        private boolean aiEnabled;
        private boolean stableLabelEnabled;
        public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
        public boolean isListeningEnabled(){return listeningEnabled;} public void setListeningEnabled(boolean v){listeningEnabled=v;}
        public boolean isAiEnabled(){return aiEnabled;} public void setAiEnabled(boolean v){aiEnabled=v;}
        public boolean isStableLabelEnabled(){return stableLabelEnabled;} public void setStableLabelEnabled(boolean v){stableLabelEnabled=v;}
    }

    public static class Ai {
        private boolean mockEnabled;
        private int personalDailyLimit = 10;
        private int globalDailyLimit = 200;
        private int personalConcurrency = 1;
        private int globalConcurrency = 2;
        private String consentVersion = "AI_SEND_V1";
        private String credentialEncryptionKey = "";
        public boolean isMockEnabled(){return mockEnabled;} public void setMockEnabled(boolean v){mockEnabled=v;}
        public int getPersonalDailyLimit(){return personalDailyLimit;} public void setPersonalDailyLimit(int v){personalDailyLimit=v;}
        public int getGlobalDailyLimit(){return globalDailyLimit;} public void setGlobalDailyLimit(int v){globalDailyLimit=v;}
        public int getPersonalConcurrency(){return personalConcurrency;} public void setPersonalConcurrency(int v){personalConcurrency=v;}
        public int getGlobalConcurrency(){return globalConcurrency;} public void setGlobalConcurrency(int v){globalConcurrency=v;}
        public String getConsentVersion(){return consentVersion;} public void setConsentVersion(String v){consentVersion=v;}
        public String getCredentialEncryptionKey(){return credentialEncryptionKey;} public void setCredentialEncryptionKey(String v){credentialEncryptionKey=v;}
    }

    public static class Tts {
        private boolean mockEnabled;
        public boolean isMockEnabled(){return mockEnabled;}
        public void setMockEnabled(boolean value){mockEnabled=value;}
    }
}
