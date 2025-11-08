# Bucket4j 정리

## 1. 개요
`Bucket4j`는 자바에서 레이트 리미팅(rate limiting)을 구현하기 위한 **토큰 버킷(Token Bucket)** 알고리즘 기반 라이브러리다.

> 일정 속도로 토큰을 채우고, 요청마다 토큰을 하나씩 소비하는 방식으로 QPS(Queries Per Second) 또는 초당 요청량을 제한한다.

---

## 2. 핵심 개념

| 용어 | 설명 |
|------|------|
| `capacity` | 버킷이 담을 수 있는 최대 토큰 개수 (버스트 허용치) |
| `refillPerSec` | 초당 리필되는 토큰 개수 (평균 요청 허용 속도) |
| `Refill.greedy(n, d)` | 일정 주기(d)마다 n개의 토큰을 한꺼번에 채움 (고정 윈도우형) |
| `Refill.intervally(n, d)` | d 시간마다 토큰을 1개씩 고르게 채움 (나노초 기반 연속 리필) |
| `Bandwidth` | 버킷의 정책 (rate limit 설정) |
| `Bucket` | 실제 토큰 상태를 관리하는 객체 |
| `ConsumptionProbe` | 소비 결과 및 남은 토큰/대기 시간 정보 제공 |

---

## 3. 동작 원리 요약

1. 버킷은 초기 상태에서 `capacity` 만큼 토큰을 가지고 시작한다.
2. 요청이 들어올 때마다 1개의 토큰을 소모한다.
3. 토큰이 부족하면 `429 Too Many Requests` 응답을 반환한다.
4. 시간에 따라 `refillPerSec` 속도로 토큰이 다시 채워진다.

---

## 4. 예시 코드 (Spring Interceptor)

```java
private static final long CAPACITY = 10;
private static final double REFILL_PER_SEC = 10;

private static final long NANOS_PER_TOKEN = (long) (1_000_000_000.0 / REFILL_PER_SEC);

private final Bandwidth limit = Bandwidth.classic(
        CAPACITY,
        Refill.intervally(1, Duration.ofNanos(NANOS_PER_TOKEN))
);

private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(100_000)
        .build();

@Override
public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
    String key = req.getRemoteAddr(); // IP 기반 식별
    Bucket bucket = buckets.get(key, k -> Bucket.builder().addLimit(limit).build());

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

    if (!probe.isConsumed()) {
        long waitSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(waitSeconds));
        res.getWriter().write("Too Many Requests");
        return false;
    }

    res.setHeader("X-RateLimit-Limit", String.valueOf(CAPACITY));
    res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
    return true;
}
```

---

## 5. 버스트와 평균 속도 관계

- `capacity`는 **버스트 허용량**, `refillPerSec`는 **평균 허용 속도**를 의미한다.
- 예: `capacity=200`, `refillPerSec=100`이면 → 한 번에 최대 200건까지 허용하되, 2초 후에야 완전 복구됨.

```
시간(sec): 0      1      2
토큰수   : 200 -> 100 -> 200 (refillPerSec=100)
```

> 즉, 순간 폭주는 허용하지만, 일정 시간 이후엔 평균 속도(100/s)에 수렴한다.

---

## 6. Bucket4j의 장점

- 스레드 안전(Thread-safe)한 설계
- 분산 환경에서도 Redis, Hazelcast 등 외부 저장소 연동 가능
- `intervally` 방식으로 나노초 단위 정밀 제어 가능
- `ConsumptionProbe`를 통해 남은 토큰과 대기 시간 조회 가능

---

## 7. 실무 활용 예시

| 상황 | 사용 이유 |
|------|-------------|
| API 서버 | 사용자별 QPS 제한 (예: /api/payment 요청 10req/s) |
| 로그인 시도 제한 | Brute-force 공격 방어 |
| 외부 연동 API | 제3자 API 호출 속도 제한 (API key 단위) |
| Redis/Hazelcast | 다중 인스턴스 간 버킷 공유로 분산 레이트 리미팅 |

---

## ✅ 핵심 요약
> Bucket4j는 **정확하고 고성능의 토큰 버킷 구현체**로,
> `intervally` 방식을 사용하면 네가 만든 순수 자바 버전과 동일한 "나노초 단위 리필" 모델을 완벽히 재현할 수 있다.

