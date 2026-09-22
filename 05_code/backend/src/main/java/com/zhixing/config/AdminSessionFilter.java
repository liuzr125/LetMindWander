package com.zhixing.config;

import com.zhixing.common.ApiException;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Collections;
import java.util.Enumeration;

/** Centralizes session and menu authorization before legacy admin controllers run. */
@Component
@Order(20)
public class AdminSessionFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTRIBUTE="adminPrincipal";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final AdminAuthService auth; private final AppProperties properties;
    public AdminSessionFilter(AdminAuthService auth,AppProperties properties){this.auth=auth;this.properties=properties;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){String path=request.getRequestURI();return "OPTIONS".equalsIgnoreCase(request.getMethod())||!path.startsWith("/api/admin/")||"/api/admin/auth/login".equals(path);}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        try {
            String token=request.getHeader("X-Admin-Token"),path=request.getRequestURI();
            String menu=menuFor(path);
            AdminPrincipal principal=legacyTestPrincipal(token);
            if(principal==null)principal=auth.require(token,menu);
            else if(menu!=null&&!principal.hasMenu(menu))throw new ApiException(HttpStatus.FORBIDDEN,"ADMIN_MENU_FORBIDDEN","当前测试角色没有该菜单权限");
            request.setAttribute(PRINCIPAL_ATTRIBUTE,principal);
            chain.doFilter(new LegacyAdminHeaderRequest(request,properties.getAdminToken()),response);
        } catch (ApiException exception) {
            writeError(response,exception);
        }
    }
    private void writeError(HttpServletResponse response,ApiException exception)throws IOException{
        response.resetBuffer();response.setStatus(exception.getStatus().value());response.setContentType("application/json;charset=UTF-8");
        Map<String,Object> body=new LinkedHashMap<String,Object>();body.put("code",exception.getCode());body.put("message",exception.getMessage());
        JSON.writeValue(response.getWriter(),body);
    }
    private String menuFor(String path){
        if(path.startsWith("/api/admin/auth/"))return null;
        if(path.startsWith("/api/admin/roles")||path.startsWith("/api/admin/accounts"))return "roles";
        if(path.startsWith("/api/admin/overview"))return "overview";
        if(path.startsWith("/api/admin/content"))return "content";
        if(path.startsWith("/api/admin/vocabulary/"))return "imports";
        if(path.startsWith("/api/admin/vocabulary-books"))return "words";
        if(path.startsWith("/api/admin/users")||path.startsWith("/api/admin/invites")||path.startsWith("/api/admin/admission")||path.startsWith("/api/admin/invite-stats"))return "users";
        if(path.startsWith("/api/admin/collection"))return "jobs";
        if(path.startsWith("/api/admin/feedback"))return "feedback";
        if(path.startsWith("/api/admin/study-records"))return "study_records";
        if(path.startsWith("/api/admin/ai/usage-logs"))return "ai_usage";
        if(path.startsWith("/api/admin/ai-audit"))return "ai_audit";
        if(path.startsWith("/api/admin/ai"))return "ai";
        if(path.startsWith("/api/admin/parameters"))return "parameters";
        throw new ApiException(HttpStatus.FORBIDDEN,"ADMIN_MENU_FORBIDDEN","该管理接口未配置菜单权限");
    }
    private AdminPrincipal legacyTestPrincipal(String token){
        if(!properties.isAdminLegacyTokenEnabled()||token==null||properties.getAdminToken()==null)return null;
        byte[] actual=token.getBytes(StandardCharsets.UTF_8),expected=properties.getAdminToken().getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(actual,expected))return null;
        return new AdminPrincipal(properties.getAdminPrincipalId(),"legacy-test-admin","SUPER_ADMIN","测试管理员",
                new LinkedHashSet<String>(Arrays.asList("overview","group_content","content","words","imports","articles","group_users","users","feedback","study_records","jobs","group_ai","ai","ai_audit","ai_usage","group_system","parameters","roles")));
    }
    private static final class LegacyAdminHeaderRequest extends HttpServletRequestWrapper {
        private final String token; LegacyAdminHeaderRequest(HttpServletRequest request,String token){super(request);this.token=token;}
        @Override public String getHeader(String name){return "X-Admin-Token".equalsIgnoreCase(name)?token:super.getHeader(name);}
        @Override public Enumeration<String> getHeaders(String name){return "X-Admin-Token".equalsIgnoreCase(name)?Collections.enumeration(Collections.singleton(token)):super.getHeaders(name);}
    }
}
