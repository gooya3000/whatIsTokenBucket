# Caffeine Cache 정리

## 1. 개념
`Caffeine`은 Java에서 가장 빠른 로컬 캐시 라이브러리 중 하나로, 구글의 `Guava Cache`를 계승한 프로젝트다.

> 메모리 기반의 키-값 캐시를 제공하며, LRU(Least Recently Used), LFU(Least Frequently Used) 등을 결합한 **Window TinyLFU** 알고리즘으로 높은 적중률을 보장한다.

---

## 2. 주요 특징

| 기능 | 설명 |
|------|------|
| **만료 정책 (Eviction)** | `expireAfterWrite`, `expireAfterAccess`, `refreshAfterWrite` 지원 |
| **최대 크기 제한** | `maximumSize(n)` 또는 `maximumWeight` 설정 가능 |
| **비동기 로딩** | `CacheLoader` 또는 `AsyncCache`로 비동기 캐시 지원 |
| **통계 수집** | 히트율(hit rate), 미스율(miss rate) 모니터링 가능 |
| **스레드 안전** | 모든 연산이 동시 접근에 안전 (ConcurrentHashMap 기반) |

---

## 3. 기본 사용 예시

```java
Cache<String, String> cache = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES) // 마지막 접근 후 10분 지나면 만료
        .maximumSize(100_000)                    // 최대 10만개 키 보관
        .build();

cache.put("user:1", "Alice");
String name = cache.getIfPresent("user:1");
```

> `getIfPresent()`는 캐시에 존재하지 않으면 null 반환.

---

## 4. 동적 로딩 (LoadingCache)

```java
LoadingCache<String, User> userCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .build(key -> userRepository.findById(key)); // 미스 시 자동 로드

User u1 = userCache.get("123"); // 없으면 DB에서 자동 로드
```

---

## 5. AsyncCache (비동기 캐시)

```java
AsyncLoadingCache<String, User> asyncCache = Caffeine.newBuilder()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .maximumSize(10_000)
        .buildAsync(key -> userRepository.findByIdAsync(key));

CompletableFuture<User> userFuture = asyncCache.get("123");
```

---

## 6. 만료 전략 요약

| 전략 | 설명 | 사용 시점 |
|------|------|------------|
| `expireAfterWrite` | 캐시에 저장된 순간부터 TTL 적용 | 세션/토큰 등 고정 TTL 데이터 |
| `expireAfterAccess` | 마지막 조회 시점 기준 TTL 적용 | 자주 접근되는 데이터 유지 |
| `refreshAfterWrite` | 일정 주기마다 백그라운드에서 새로 로드 | 외부 API, 설정 값 등 주기적 갱신 |

---

## 7. 내부 구조 핵심

- **ConcurrentHashMap**을 기반으로 구현되어 스레드 안전함.
- **Window TinyLFU** 알고리즘으로 자주 쓰이는 항목 유지.
- **W-TinyLFU 구조**:
  1. **Window Cache (1%)**: 최근 추가된 항목을 임시 저장.
  2. **Main Cache (99%)**: 빈도 기반 유지.
  3. 교체 시, TinyLFU가 각 항목의 접근 빈도를 비교해 교체 여부 결정.

---

## 8. Bucket4j와 함께 사용 시

| 항목 | 역할 |
|------|------|
| `Bucket4j` | 토큰 버킷 로직 (속도 제어) |
| `Caffeine` | 버킷 객체의 메모리 캐시 (사용자/IP별 버킷 관리) |

예시:
```java
private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(100_000)
        .build();
```

> 오래 사용하지 않은 버킷은 자동으로 제거되어 메모리 누수를 방지한다.

---

## 9. 장점 요약

- **GC 부담 적음**: 약한 참조(WeakReference) 기반으로 객체 수명 자동 관리
- **지연 로딩 지원**: 필요 시점에만 데이터 생성
- **Spring, Micronaut, Quarkus 등과 쉽게 통합 가능**
- **Redis 등 외부 캐시보다 빠른 로컬 메모리 캐시**

---

## ✅ 핵심 요약
> `Caffeine`은 고성능 로컬 캐시로, TTL, 크기 제한, 자동 로딩, 비동기 처리까지 지원한다.  
> `Bucket4j`와 조합하면 IP/유저 단위 RateLimit을 **효율적이고 안전하게 메모리 관리**할 수 있다.

