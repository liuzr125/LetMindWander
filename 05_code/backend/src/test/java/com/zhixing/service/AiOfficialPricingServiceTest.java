package com.zhixing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiOfficialPricingServiceTest {
    private static final String COMPLETE_PAGE =
            "<table>" +
            "<tr><td>模型</td><td>deepseek-flash(1)</td><td>deepseek-v4-pro</td></tr>" +
            "<tr><td>模型版本</td><td>DeepSeek-V4.1-Flash</td><td>DeepSeek-V4-Pro-0813</td></tr>" +
            "<tr><td>价格</td><td>百万tokens输入（缓存命中）</td><td>空闲时段</td><td>0.02元</td><td>0.15元</td></tr>" +
            "<tr><td>高峰时段</td><td>0.04元</td><td>0.30元</td></tr>" +
            "<tr><td>百万tokens输入（缓存未命中）</td><td>空闲时段</td><td>1元</td><td>4.5元</td></tr>" +
            "<tr><td>高峰时段</td><td>2元</td><td>9.0元</td></tr>" +
            "<tr><td>百万tokens输出</td><td>空闲时段</td><td>4元</td><td>13.5元</td></tr>" +
            "<tr><td>高峰时段</td><td>8元</td><td>27.0元</td></tr>" +
            "</table><p>高峰时段为北京时间周一至周五 9:00 - 12:00、14:00 - 18:00，其余为空闲时段。</p>";

    @Test
    void parsesBothModelsAndPeakOffPeakMatrix() {
        AiOfficialPricingService.ParsedPage page = AiOfficialPricingService.parseOfficialPage(COMPLETE_PAGE);
        AiOfficialPricingService.OfficialModelPrice flash = page.models.get("deepseek-flash");
        AiOfficialPricingService.OfficialModelPrice pro = page.models.get("deepseek-v4-pro");

        assertEquals(2, page.models.size());
        assertEquals("DeepSeek-V4.1-Flash", flash.modelVersion);
        assertEquals(new BigDecimal("0.02"), flash.offPeak.inputCacheHit);
        assertEquals(new BigDecimal("1"), flash.offPeak.inputCacheMiss);
        assertEquals(new BigDecimal("8"), flash.peak.output);
        assertEquals(new BigDecimal("0.30"), pro.peak.inputCacheHit);
        assertEquals(new BigDecimal("4.5"), pro.offPeak.inputCacheMiss);
        assertEquals(new BigDecimal("27.0"), pro.peak.output);
    }

    @Test
    void rejectsIncompleteOrChangedOfficialPage() {
        assertThrows(IllegalArgumentException.class,
                () -> AiOfficialPricingService.parseOfficialPage(COMPLETE_PAGE.replace("27.0元", "")));
        assertThrows(IllegalArgumentException.class,
                () -> AiOfficialPricingService.parseOfficialPage(COMPLETE_PAGE.replace("14:00 - 18:00", "")));
    }

    @Test
    void appliesOfficialBeijingPeakBoundaries() {
        assertEquals(false, AiService.isPeakPeriod(Instant.parse("2026-09-21T00:59:59Z")));
        assertEquals(true, AiService.isPeakPeriod(Instant.parse("2026-09-21T01:00:00Z")));
        assertEquals(false, AiService.isPeakPeriod(Instant.parse("2026-09-21T04:00:00Z")));
        assertEquals(true, AiService.isPeakPeriod(Instant.parse("2026-09-21T06:00:00Z")));
        assertEquals(false, AiService.isPeakPeriod(Instant.parse("2026-09-21T10:00:00Z")));
        assertEquals(false, AiService.isPeakPeriod(Instant.parse("2026-09-20T02:00:00Z")));
    }
}
