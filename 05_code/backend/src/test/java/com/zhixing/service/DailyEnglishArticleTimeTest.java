package com.zhixing.service;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyEnglishArticleTimeTest {
    @Test
    void mysqlDatetimeIsInterpretedAsBeijingLocalTime() {
        LocalDateTime databaseTime = LocalDateTime.of(2026, 9, 21, 19, 35);

        assertEquals(Instant.parse("2026-09-21T11:35:00Z"),
                DailyEnglishArticleService.sqlDateTimeInstant(databaseTime));
    }

    @Test
    void sqlTimestampKeepsItsExistingInstant() {
        Instant expected = Instant.parse("2026-09-21T11:35:00Z");

        assertEquals(expected,
                DailyEnglishArticleService.sqlDateTimeInstant(Timestamp.from(expected)));
    }
}
