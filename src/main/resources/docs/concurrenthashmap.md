# ConcurrentHashMap 정리

## 1. 개념
`ConcurrentHashMap`은 **멀티스레드 환경에서 안전하게(Map 구조가 깨지지 않게)** 사용할 수 있는 `HashMap`의 동시성 버전이다.

일반 `HashMap`은 여러 스레드가 동시에 put/remove 할 때 내부 구조가 꼬이면서 **ConcurrentModificationException**이 발생하거나 **무한 루프**에 빠질 수 있다.

`ConcurrentHashMap`은 이런 문제를 해결하기 위해 **버킷 단위 락(lock) + CAS(Compare-And-Swap)** 기법을 사용한다.

---

## 2. 내부 동작 원리 (Java 8 기준)

| 특징 | 설명 |
|------|------|
| 구조 | 단일 해시 테이블 (세그먼트 제거됨) |
| 락 범위 | 버킷 단위로 최소화 (버킷별 synchronized 블록) |
| 원자적 연산 | `compute`, `merge`, `putIfAbsent` 등은 내부적으로 CAS 연산 사용 |
| 크기 조정 | 여러 스레드가 협력(cooperative resize) 방식으로 수행 |
| 카운터 | `LongAdder` 유사 구조로 분산된 셀(cell) 사용 (병목 줄임) |

---

## 3. 주요 메서드

| 메서드 | 설명 | 원자성 |
|--------|------|--------|
| `get(key)` | 키 조회 | O(1) 스레드 안전 |
| `put(key, val)` | 값 삽입 | 구조는 안전하지만 덮어쓰기 가능 |
| `putIfAbsent(key, val)` | 키가 없을 때만 삽입 | ✅ 원자적 |
| `compute(key, fn)` | 기존 값 기반으로 계산/갱신 | ✅ 원자적 |
| `merge(key, val, fn)` | 병합 연산 | ✅ 원자적 |

> **주의:** `get()`과 `put()`의 조합은 원자적이지 않다. (예: 존재 검사 후 삽입은 `putIfAbsent`로 해야 함)

---

## 4. 일반 HashMap과 비교

| 항목 | HashMap | ConcurrentHashMap |
|------|----------|-------------------|
| 멀티스레드 안전성 | ❌ Unsafe | ✅ Safe |
| null 허용 | key/value 가능 | ❌ 허용 안 함 |
| 반복 중 변경 | ConcurrentModificationException | Weakly Consistent (안전하게 순회 가능) |
| 락 방식 | 전체 맵 락 필요 | 버킷 단위 락 + CAS |

---

## 5. 내부 원리 요약

### put() 수행 흐름
```
1. 해시 계산 → 버킷 결정
2. 해당 버킷에 synchronized 진입 (부분 락)
3. 노드가 없으면 새로 삽입
4. 기존 키면 값 덮어쓰기
5. 필요 시 트리화 (체인 길이 8 이상)
```

### get() 수행 흐름
```
1. 락 없이 바로 테이블에서 읽음
2. 읽는 도중 변경이 있어도 구조가 깨지지 않도록 설계
```

---

## 6. 원자적 보장 메서드 예시

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// 키가 없을 때만 삽입
map.putIfAbsent("a", 1);

// 기존 값 기반으로 계산
map.compute("a", (k, v) -> v == null ? 1 : v + 1);

// 병합
map.merge("a", 1, Integer::sum);
```

---

## 7. 주의할 점

- get/put 조합은 경쟁 상태(race condition)를 유발할 수 있다.
- null 키 또는 null 값은 절대 허용되지 않는다.
- 반복(iteration) 중 데이터 변경은 가능하지만 즉시 반영되지 않을 수 있다 (Weakly Consistent).

---

## ✅ 핵심 요약
> `ConcurrentHashMap`은 버킷 단위 락과 CAS로 구조를 보호하면서도 높은 병렬성을 보장한다.
> 단순 조회·삽입은 thread-safe지만, **연산 조합은 원자적 메서드로 처리해야 한다.**

