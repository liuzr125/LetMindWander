package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.mapper.AdminAiUsageMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 管理端「AI 用量日志」：按 天 × 用户 展示每日额度使用与调用明细。
 * 数据来源为 ai_daily_quota（每人每天一行的额度快照）与 ai_attempt（逐次调用记录），
 * 不额外落表，避免与真实调用链路产生二次写入不一致。
 */
@Service
public class AdminAiUsageService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_RANGE_DAYS = 92;

    private final AdminAiUsageMapper usage;
    private final AppParameterService parameters;
    private final com.zhixing.config.AppProperties properties;
    public AdminAiUsageService(AdminAiUsageMapper usage,AppParameterService parameters,com.zhixing.config.AppProperties properties) { this.usage = usage; this.parameters = parameters; this.properties = properties; }

    public Map<String,Object> logs(String rawFrom,String rawTo,String rawKeyword,Integer rawPage,Integer rawPageSize) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate to = parseDate(rawTo,today,"结束日期");
        LocalDate from = parseDate(rawFrom,to,"开始日期");
        if (from.isAfter(to)) throw fail("INVALID_DATE_RANGE","开始日期不能晚于结束日期");
        if (to.toEpochDay()-from.toEpochDay()>=MAX_RANGE_DAYS) throw fail("INVALID_DATE_RANGE","单次查询区间不能超过 "+MAX_RANGE_DAYS+" 天");
        String keyword = rawKeyword==null?"":rawKeyword.trim();
        if (keyword.length()>80) throw fail("INVALID_KEYWORD","搜索词最多 80 个字符");
        int page = rawPage==null?1:rawPage, pageSize = rawPageSize==null?20:rawPageSize;
        if (page<1||pageSize<1||pageSize>100) throw fail("INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 条");

        String dateFrom = from.format(DATE), dateTo = to.format(DATE);
        int total = usage.countUsageLogs(dateFrom,dateTo,keyword);
        int totalPages = total==0?0:(total+pageSize-1)/pageSize;
        List<Map<String,Object>> items = new ArrayList<Map<String,Object>>();
        for (Map<String,Object> row : usage.selectUsageLogs(dateFrom,dateTo,keyword,(page-1)*pageSize,pageSize)) items.add(item(row));

        Map<String,Object> quota = usage.summarizeQuota(dateFrom,dateTo,keyword);
        Map<String,Object> attempts = usage.summarizeAttempts(dateFrom,dateTo,keyword);
        Map<String,Object> summary = new LinkedHashMap<String,Object>();
        summary.put("userDays",number(quota,"activeUsers"));
        summary.put("usedTotal",number(quota,"usedTotal"));
        summary.put("limitTotal",number(quota,"limitTotal"));
        summary.put("succeededTotal",number(attempts,"succeededCount"));
        summary.put("failedTotal",number(attempts,"failedCount"));
        summary.put("inputTokens",number(attempts,"inputTokens"));
        summary.put("outputTokens",number(attempts,"outputTokens"));
        summary.put("costTotal",value(attempts,"costTotal"));
        // 当日行里的「上限」是额度快照；这里额外给出参数当前值，便于核对参数是否已生效
        summary.put("personalLimit",parameters.intValue(AppParameterService.AI_PERSONAL_DAILY_LIMIT,properties.getAi().getPersonalDailyLimit(),0,100000));
        summary.put("globalLimit",parameters.intValue(AppParameterService.AI_GLOBAL_DAILY_LIMIT,properties.getAi().getGlobalDailyLimit(),0,100000));

        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("dateFrom",dateFrom); result.put("dateTo",dateTo); result.put("keyword",keyword);
        result.put("total",total); result.put("page",page); result.put("pageSize",pageSize); result.put("totalPages",totalPages);
        result.put("summary",summary); result.put("items",items);
        return result;
    }

    private Map<String,Object> item(Map<String,Object> row) {
        Map<String,Object> out = new LinkedHashMap<String,Object>();
        out.put("quotaDate",value(row,"quotaDate"));
        out.put("userId",value(row,"userId"));
        out.put("nickname",value(row,"nickname"));
        out.put("shortId",value(row,"shortId"));
        out.put("mobile",maskMobile(value(row,"mobile")));
        out.put("usedCount",number(row,"usedCount"));
        out.put("limitCount",number(row,"limitCount"));
        out.put("remaining",Math.max(0,number(row,"limitCount")-number(row,"usedCount")));
        out.put("succeededCount",number(row,"succeededCount"));
        out.put("failedCount",number(row,"failedCount"));
        out.put("runningCount",number(row,"runningCount"));
        out.put("inputTokens",number(row,"inputTokens"));
        out.put("outputTokens",number(row,"outputTokens"));
        out.put("costCny",value(row,"costCny"));
        return out;
    }

    private String maskMobile(Object raw) {
        String mobile = raw==null?null:String.valueOf(raw).trim();
        if (mobile==null||mobile.isEmpty()) return null;
        if (mobile.length()<7) return "***";
        return mobile.substring(0,3)+"****"+mobile.substring(mobile.length()-4);
    }
    private LocalDate parseDate(String raw,LocalDate fallback,String label) {
        if (raw==null||raw.trim().isEmpty()) return fallback;
        try { return LocalDate.parse(raw.trim(),DATE); }
        catch (Exception e) { throw fail("INVALID_DATE","请按 YYYY-MM-DD 填写"+label); }
    }
    private int number(Map<String,Object> row,String key) {
        Object raw = row==null?null:row.get(key);
        if (raw==null) { for (Map.Entry<String,Object> entry : row.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) raw=entry.getValue(); }
        return raw instanceof Number?((Number)raw).intValue():0;
    }
    private Object value(Map<String,Object> row,String key) {
        if (row==null) return null;
        if (row.containsKey(key)) return row.get(key);
        for (Map.Entry<String,Object> entry : row.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }
    private ApiException fail(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
}
