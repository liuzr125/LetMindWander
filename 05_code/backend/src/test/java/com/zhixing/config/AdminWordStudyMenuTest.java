package com.zhixing.config;
import com.zhixing.common.ApiException;
import com.zhixing.service.AdminAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AdminWordStudyMenuTest {
 @Test void modeAndLogsRequireStudyRecordsMenu() throws Exception {
  AdminAuthService auth=mock(AdminAuthService.class);when(auth.require("session","study_records")).thenThrow(new ApiException(HttpStatus.FORBIDDEN,"ADMIN_MENU_FORBIDDEN","没有权限"));
  for(String path:new String[]{"/api/admin/study-records/word-study/mode","/api/admin/study-records/word-study/events"}){
   MockHttpServletRequest request=new MockHttpServletRequest("PUT",path);request.addHeader("X-Admin-Token","session");MockHttpServletResponse response=new MockHttpServletResponse();AtomicBoolean reached=new AtomicBoolean();
   new AdminSessionFilter(auth,new AppProperties()).doFilter(request,response,(req,res)->reached.set(true));assertEquals(403,response.getStatus());assertFalse(reached.get());
  }
 }
}
