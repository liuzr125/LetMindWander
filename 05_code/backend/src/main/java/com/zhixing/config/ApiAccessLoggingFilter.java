package com.zhixing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 所有 /api/** 请求的统一访问审计日志。
 * 不记录 Authorization、管理员令牌、短信码、邀请码、注册票据或上传文件内容，防止日志成为凭证泄露面。
 */
@Component
public class ApiAccessLoggingFilter extends OncePerRequestFilter {
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("API_ACCESS");
    private static final int MAX_BODY_CHARS = 2048;
    private static final List<String> SENSITIVE_NAMES = Arrays.asList(
            "authorization", "token", "code", "smscode", "mobile", "invitecode", "registrationticket", "password");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest = new ContentCachingRequestWrapper(request);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(cachedRequest, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            ACCESS_LOG.info("api_access method={} path={} status={} elapsedMs={} client={} auth={} params={} body={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs,
                    client(request), authState(request), parameters(request), requestBody(cachedRequest));
        }
    }

    private String client(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.trim().isEmpty() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        String agent = request.getHeader("User-Agent");
        return ip + (agent == null ? "" : " (" + truncate(agent, 160) + ")");
    }

    private String authState(HttpServletRequest request) {
        if (hasText(request.getHeader("Authorization"))) return "bearer-present";
        if (hasText(request.getHeader("X-Admin-Token"))) return "admin-token-present";
        return "anonymous";
    }

    private String parameters(HttpServletRequest request) {
        List<String> values = new ArrayList<String>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String[] raw = request.getParameterValues(name);
            boolean privateKnowledgeQuery = request.getRequestURI().startsWith("/api/knowledge") && "query".equalsIgnoreCase(name);
            String value = isSensitive(name) || privateKnowledgeQuery ? "***" : truncate(raw == null ? "" : Arrays.toString(raw), 300);
            values.add(name + "=" + value);
        }
        return values.toString();
    }

    private String requestBody(ContentCachingRequestWrapper request) {
        if (request.getRequestURI().startsWith("/api/knowledge")) return "[private knowledge body omitted]";
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE)) {
            return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/") ? "[multipart omitted]" : "";
        }
        byte[] bytes = request.getContentAsByteArray();
        if (bytes.length == 0) return "";
        Charset charset;
        try { charset = request.getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(request.getCharacterEncoding()); }
        catch (Exception ignored) { charset = StandardCharsets.UTF_8; }
        String body = truncate(new String(bytes, charset), MAX_BODY_CHARS);
        return redactJson(body);
    }

    private String redactJson(String body) {
        for (String name : SENSITIVE_NAMES) {
            body = body.replaceAll("(?i)(\\\"" + name + "\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2");
        }
        return body;
    }

    private boolean isSensitive(String name) { return SENSITIVE_NAMES.contains(name.toLowerCase(Locale.ROOT)); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max) + "…"; }
}
