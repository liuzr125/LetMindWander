package com.zhixing;

import okhttp3.Request;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Prevents mixing the NLS SDK's Okio 1.x with Kotlin-based OkHttp 4.x. */
class AliyunTtsDependencyCompatibilityTest {
    @Test
    void okhttpAndOkioCanBuildTheTokenRequestTogether() {
        Request request = new Request.Builder()
                .url("http://nls-meta.cn-shanghai.aliyuncs.com/")
                .header("Accept", "application/json")
                .get()
                .build();
        assertEquals("nls-meta.cn-shanghai.aliyuncs.com", request.url().host());
    }
}
