package com.sunxien.agentsdk.common;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author sunxien
 * @date 2026/4/10
 * @since 1.0.0-SNAPSHOT
 */
@Slf4j
public class Helper {

    static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * @return String
     */
    public static String currentDateTime() {
        return LocalDateTime.now().format(dtf);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        log.info("Hello World! DateTime: {}", currentDateTime());
    }
}
