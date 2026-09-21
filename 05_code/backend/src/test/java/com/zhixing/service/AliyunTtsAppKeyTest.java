package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AliyunTtsAppKeyTest {
    private final AppParameterService parameters=mock(AppParameterService.class);
    private final AliyunTtsService tts=new AliyunTtsService(null,parameters,null,null,new AppProperties());

    @Test
    void encryptedAppKeyWithWrongMasterKeyIsNotReportedAsMissing(){
        when(parameters.required("ALIYUN_NLS_APP_KEY")).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CREDENTIAL_KEY_UNAVAILABLE","密钥解密配置不可用"));
        ApiException error=assertThrows(ApiException.class,tts::requiredAppKey);
        assertEquals("TTS_APP_KEY_UNREADABLE",error.getCode());
        assertTrue(error.getMessage().contains("加密密钥"));
    }

    @Test
    void missingAppKeyHasSeparateError(){
        when(parameters.required("ALIYUN_NLS_APP_KEY")).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"PARAMETER_NOT_CONFIGURED","服务参数尚未完成配置"));
        assertEquals("TTS_NOT_CONFIGURED",assertThrows(ApiException.class,tts::requiredAppKey).getCode());
    }

    @Test
    void readableAppKeyIsUsedWithoutModification(){
        when(parameters.required("ALIYUN_NLS_APP_KEY")).thenReturn("example-project-key");
        assertEquals("example-project-key",tts.requiredAppKey());
    }
}
