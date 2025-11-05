package com.example.whatistokenbucket.ratelimit;

import com.example.whatistokenbucket.TokenBucket;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 토큰 버킷 알고리즘을 이용하여 QPS 를 제한하는 인터셉터
 * (단일 인스턴스 환경을 가정)
 * 제한 정책 예시: [메서드:URI:X-User-Id] 키 조합에 대해 [초당 10회] 제한
 * 제한 정책 초과시: 429 Too Many Request 반환 종료
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>(); // 요청 키[메서드:URI:X-User-Id] 별 토큰 버킷 맵


    private static final long CAPACITY = 10;          // 최대 토큰 수
    private static final double REFILL_PER_SEC = 10;  // 초당 리필 토큰 수
    private static final Duration RETRY_AFTER = Duration.ofSeconds(1);

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {

        final String method = req.getMethod();
        final String uri = req.getRequestURI();
        final String userId = getUserId(req);
        final String key = method + ":" + uri + ":" + userId;

        final TokenBucket bucket = buckets.computeIfAbsent( // Thread-Safe 한 ConcurrentHashMap 로 요청 키에 대한 토큰 버킷 원자적 처리
                key, k -> new TokenBucket(CAPACITY, REFILL_PER_SEC)
        );

        if (!bucket.tryConsume()) {

            // 표준 레이트리밋 관례 헤더(권장됨)
            res.setHeader("Retry-After", String.valueOf(RETRY_AFTER.getSeconds())); // 몇 초 뒤 재시도 안내
            res.setHeader("X-RateLimit-Limit", String.valueOf(CAPACITY)); // 버킷 용량
            res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, bucket.getRemainingTokens()))); // 남은 토큰
            res.setHeader("X-RateLimit-Window", "1s"); // 윈도우 힌트

            // 429 Too Many Requests
            res.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests (token bucket)");
            return false; // 종료
        }

        return true;
    }

    /**
     * X-User-Id 가 없을 때 대체 식별자 반환
     * @param req 요청 내용
     * @return X-User-Id 또는 대체 식별자
     */
    private String getUserId(HttpServletRequest req) {
        String xUserId = req.getHeader("X-User-Id");
        if (xUserId != null && !xUserId.isBlank()) {
            return xUserId.trim();
        }

        String ip = req.getRemoteAddr(); // 실무에선 프록시/로드밸런서 헤더 처리 필요(X-Forwarded-For 등)
        return Objects.toString(ip, "anonymous");
    }
}
