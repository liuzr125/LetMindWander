package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Service
public class WechatGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(WechatGateway.class);
    private final AppProperties properties;
    private final AppParameterService parameters;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private volatile String accessToken;
    private volatile Instant accessTokenExpiresAt = Instant.EPOCH;

    public WechatGateway(AppProperties properties, AppParameterService parameters, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.parameters = parameters;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Identity exchangeCode(String code) {
        if (properties.getWechat().isMockEnabled()) {
            String openId = "mock_" + hex(CryptoUtils.sha256(code)).substring(0, 32);
            return new Identity(parameters.optional("wechat.app_id", properties.getWechat().getAppId()), openId);
        }
        requireWechatConfig();
        String url = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId())
                .queryParam("secret", appSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();
        Map response = getJson(url, "微信登录服务暂时不可用");
        if (response == null || response.get("openid") == null) {
            LOGGER.warn("WeChat code exchange rejected: errcode={}, errmsg={}",
                    response == null ? "unknown" : response.get("errcode"),
                    response == null ? "empty response" : response.get("errmsg"));
            throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_LOGIN_FAILED", "微信登录失败，请重试");
        }
        return new Identity(appId(), String.valueOf(response.get("openid")));
    }

    public String resolveMobile(String credential) {
        if (properties.getWechat().isMockEnabled()) {
            String mobile = credential != null && credential.startsWith("mock:")
                    ? credential.substring("mock:".length()) : "13800008000";
            if (!mobile.matches("\\+?[0-9]{7,20}")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_REQUIRED", "微信手机号授权结果无效");
            }
            return mobile;
        }
        requireWechatConfig();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Collections.singletonMap("code", credential);
        String responseBody = restTemplate.postForObject(
                "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + getAccessToken(),
                new HttpEntity<Map<String, String>>(body, headers), String.class);
        Map response = parseJson(responseBody, "微信手机号服务暂时不可用");
        if (response == null || !Integer.valueOf(0).equals(response.get("errcode"))) {
            LOGGER.warn("WeChat phone API rejected authorization: errcode={}, errmsg={}",
                    response == null ? "unknown" : response.get("errcode"),
                    response == null ? "empty response" : response.get("errmsg"));
            if (response != null && Integer.valueOf(40029).equals(response.get("errcode"))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_CODE_INVALID", "手机号授权码无效，请确认当前小程序 AppID 后重新授权");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_REQUIRED", "未能读取微信手机号，请重新授权");
        }
        Map phoneInfo = (Map) response.get("phone_info");
        Object phone = phoneInfo == null ? null : phoneInfo.get("phoneNumber");
        if (phone == null) throw new ApiException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_REQUIRED", "未能读取微信手机号，请重新授权");
        return String.valueOf(phone);
    }

    private synchronized String getAccessToken() {
        if (StringUtils.hasText(accessToken) && accessTokenExpiresAt.isAfter(Instant.now().plusSeconds(60))) return accessToken;
        String url = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appId())
                .queryParam("secret", appSecret())
                .toUriString();
        Map response = getJson(url, "微信凭据服务暂时不可用");
        if (response == null || response.get("access_token") == null) {
            LOGGER.warn("WeChat access-token request rejected: errcode={}, errmsg={}",
                    response == null ? "unknown" : response.get("errcode"),
                    response == null ? "empty response" : response.get("errmsg"));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_SERVICE_UNAVAILABLE", "微信服务暂时不可用");
        }
        accessToken = String.valueOf(response.get("access_token"));
        Number expiresIn = (Number) response.get("expires_in");
        accessTokenExpiresAt = Instant.now().plusSeconds(expiresIn == null ? 7000 : expiresIn.longValue());
        return accessToken;
    }

    private void requireWechatConfig() {
        appId(); appSecret();
    }

    private String appId() { return parameters.required("wechat.app_id"); }
    private String appSecret() { return parameters.required("wechat.app_secret"); }

    /**
     * Some reverse proxies label WeChat's JSON body as text/plain. Reading it
     * as text first keeps the integration independent from that header.
     */
    private Map getJson(String url, String unavailableMessage) {
        try {
            return parseJson(restTemplate.getForObject(url, String.class), unavailableMessage);
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_SERVICE_UNAVAILABLE", unavailableMessage);
        }
    }

    private Map parseJson(String body, String unavailableMessage) {
        if (!StringUtils.hasText(body)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_SERVICE_UNAVAILABLE", unavailableMessage);
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_SERVICE_UNAVAILABLE", unavailableMessage);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) builder.append(String.format("%02x", value));
        return builder.toString();
    }

    public static class Identity {
        private final String appId;
        private final String openId;
        public Identity(String appId, String openId) { this.appId = appId; this.openId = openId; }
        public String getAppId() { return appId; }
        public String getOpenId() { return openId; }
    }
}
