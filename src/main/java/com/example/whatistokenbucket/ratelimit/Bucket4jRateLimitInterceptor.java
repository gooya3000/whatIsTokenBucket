package com.example.whatistokenbucket.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Bucket4j + Caffeine 으로 리팩토링한 인터셉터
 * Bucket4j는 남은 토큰/다음 리필까지 대기시간 계산을 내장 → 헤더 작성과 정책 변경이 쉬움.
 * Caffeine 캐시로 "오래 안 쓰인 키"를 자동 만료(expireAfterAccess) + 최대 사이즈 제한(maximumSize).
 */
public class Bucket4jRateLimitInterceptor implements HandlerInterceptor {

    private static final long CAPACITY = 10;          // 최대 토큰 수
    private static final double REFILL_PER_SEC = 10;  // 초당 리필 토큰 수

    private static final long NANOS_PER_TOKEN =
            Math.max(1L, (long) Math.floor(1_000_000_000.0 / REFILL_PER_SEC)); // 토큰 1개의 초(나노초)

    private final Bandwidth limit = Bandwidth.classic(
            CAPACITY,
            Refill.intervally(1, Duration.ofNanos(NANOS_PER_TOKEN)) // .intervally 일정 시간마다 채움, .greedy 한 번에 채움
    );

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)  // 10분간 미접근 시 제거
            .maximumSize(100_000) // 키 상한
            .build();

    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        final String key = buildKey(req);
        final Bucket bucket = buckets.get(key, k -> Bucket.builder().addLimit(limit).build());

        // 1개 토큰 소모 시도 + 잔여 토큰/대기시간 확인
        final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            // 다음 토큰까지 남은 시간 → Retry-After 로 안내(초 단위; 최소 1초 표기)
            long waitSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());

            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setHeader("Retry-After", String.valueOf(waitSeconds));
            res.setHeader("X-RateLimit-Limit", String.valueOf(CAPACITY));
            res.setHeader("X-RateLimit-Remaining", "0");
            res.setHeader("X-RateLimit-Window", NANOS_PER_TOKEN + "ns/token");
            res.getWriter().write("Too Many Requests (bucket4j intervally + caffeine via Interceptor)");
            return false;
        }

        // 모니터링용 헤더
        res.setHeader("X-RateLimit-Limit", String.valueOf(CAPACITY));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        res.setHeader("X-RateLimit-Window", NANOS_PER_TOKEN + "ns/token");

        return true;
    }

    private String buildKey(HttpServletRequest req) {
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String user = getUserId(req);
        return method + ":" + uri + ":" + user;
    }

    private String getUserId(HttpServletRequest req) {
        String xUserId = req.getHeader("X-User-Id");
        if (xUserId != null && !xUserId.isBlank()) {
            return xUserId.trim();
        }

        String ip = req.getRemoteAddr();
        return Objects.toString(ip, "anonymous");
    }

}
