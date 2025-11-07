# Atomic 타입 정리

## 1. 개념
`Atomic` 계열 클래스(`AtomicInteger`, `AtomicLong`, `AtomicBoolean`, etc.)는 **멀티스레드 환경에서 안전하게 동작하는 변수 타입**이다. 내부적으로 **CAS(Compare-And-Swap)** 연산을 사용해 락(lock) 없이 원자적 연산을 보장한다.

> 즉, 여러 스레드가 동시에 접근해도 덮어쓰기나 손실이 발생하지 않는다.

---

## 2. 원자적 연산이란?

일련의 연산이 **중간 상태 없이 한 번에 완료되는 것**을 말한다.

```java
// 예시: 일반 long
long count = 0;
count++; // 실제로는 read → increment → write (3단계)
```

이 경우, 여러 스레드가 동시에 `count++`를 수행하면 덮어쓰기가 발생한다.

반면 AtomicLong은 다음과 같이 내부에서 CAS를 사용한다.

```java
AtomicLong count = new AtomicLong(0);
count.incrementAndGet(); // 내부적으로 compareAndSet(oldValue, newValue)
```

이때 다른 스레드가 값을 바꿔치기 하려 하면 CAS가 실패하고 재시도하기 때문에 값 손실이 없다.

---

## 3. 대표 메서드

| 메서드 | 설명 |
|---------|------|
| `get()` | 현재 값 조회 |
| `set(value)` | 값 설정 |
| `incrementAndGet()` | +1 후 반환 |
| `decrementAndGet()` | -1 후 반환 |
| `addAndGet(n)` | n 더하고 반환 |
| `compareAndSet(expect, update)` | 기대값과 같을 때만 업데이트 (CAS 핵심) |

---

## 4. CAS 동작 원리

1. 현재 메모리 값을 읽는다.
2. 기대값(`expected`)과 비교한다.
3. 같으면 새 값(`update`)으로 교체한다.
4. 다르면 실패하고 다시 시도한다.

즉, 락을 걸지 않아도 안전하게 동시성 제어가 가능하다.

---

## 5. 사용 예시

```java
AtomicLong counter = new AtomicLong();

Runnable task = () -> {
    for (int i = 0; i < 1000; i++) {
        counter.incrementAndGet();
    }
};

ExecutorService pool = Executors.newFixedThreadPool(4);
for (int i = 0; i < 4; i++) pool.submit(task);

pool.shutdown();
System.out.println(counter.get()); // 4000 (정확히 일치)
```

---

## 6. 주의할 점

- 지역 변수(요청 단위)는 스레드마다 분리되어 있으므로 Atomic 타입이 필요 없다.
- 전역, static, singleton 객체처럼 **공유 상태(shared state)** 에서만 Atomic 타입을 사용한다.
- 연산이 복잡해지면 `synchronized`나 `Lock` 기반 접근이 더 명확할 수 있다.

---

## ✅ 핵심 요약
> Atomic 타입은 락 없이도 안전하게 공유 자원을 제어할 수 있는 **경량 동시성 도구**다.
> 내부적으로 CAS를 사용해 여러 스레드의 경쟁 상황에서도 값 손실을 막는다.

