package io.stockvaluation.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BotDetector {

    private static final Set<String> BOT_KEYWORDS = Set.of(
        "bot", "spider", "crawl", "slurp", "bingpreview",
        "facebookexternalhit", "yandex", "duckduckbot",
        "baiduspider", "sogou", "exabot", "facebot", "ia_archiver",
        "telegrambot", "whatsapp", "linkedinbot", "pinterest",
        "crawler", "python-requests", "java", "libwww-perl",
        "okhttp", "httpclient", "php", "curl"
    );

    // Known suspicious IP ranges (simplified)
    private static final List<String> SUSPICIOUS_IP_PREFIXES = List.of(
        "66.249.",    // Googlebot
        "157.55.",    // Microsoft
        "199.16.",    // Twitter
        "31.13.",     // Facebook
        "52.", "54.", "18." // AWS ranges (broad)
    );

    // Simple rate tracking: IP -> last access timestamps
    private static final Map<String, List<Long>> REQUEST_HISTORY = new ConcurrentHashMap<>();
    private static final long RATE_WINDOW_MS = 5000; // 5 seconds
    private static final int MAX_REQUESTS_PER_WINDOW = 10;

    public static boolean isBot(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        // 1. Check User-Agent
        if (userAgent == null || BOT_KEYWORDS.stream()
                .anyMatch(userAgent.toLowerCase()::contains)) {
            return true;
        }

        // 2. Check suspicious IP ranges
        for (String prefix : SUSPICIOUS_IP_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }

        // 3. Check if cookies are missing (bots often don't send them)
        if (request.getCookies() == null || request.getCookies().length == 0) {
            return true;
        }

        // 4. Simple rate-limit check per IP
        long now = Instant.now().toEpochMilli();
        REQUEST_HISTORY.putIfAbsent(ip, new ArrayList<>());
        List<Long> timestamps = REQUEST_HISTORY.get(ip);

        synchronized (timestamps) {
            timestamps.add(now);
            timestamps.removeIf(ts -> now - ts > RATE_WINDOW_MS);
            if (timestamps.size() > MAX_REQUESTS_PER_WINDOW) {
                return true;
            }
        }

        return false; // Passed all checks — likely human
    }
}
