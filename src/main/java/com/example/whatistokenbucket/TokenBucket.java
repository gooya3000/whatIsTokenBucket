package com.example.whatistokenbucket;

import java.util.concurrent.atomic.AtomicLong;


/**
 * 토큰 버킷 알고리즘
 * "시간이 흐르면 토큰이 일정 속도로 자동으로 채워지고 요청이 올 떄마다 토큰을 1개씩 꺼낸다"
 * filter, interceptor, redis 등에 활용하여 불필요한 리소스를 차단할 수 있다.
 * 실무 사용 케이스 ==============================================================
 * API Gateway / OpenAPI 레이트리밋: 고객사별/키별 QPS 제한 및 버스트 제어.
 * 로그인/인증 시도 제한: 아이디/아이피별 초당/분당 시도 제한으로 브루트포스 차단.
 * 메시지 발송(문자/알림/이메일): 발송 벤더 정책(QPS 한도)에 맞춰 퍼블리셔 측 속도 제어.
 * 업로드/다운로드 속도 제한: 네트워크 트래픽 셰이핑(특정 사용자/경로 대역폭 캡).
 * 크롤러/배치 잡의 외부 호출: 외부 API의 Rate Limit을 초과하지 않도록 호출 간격 제어.
 * 멀티 테넌트 서비스: 테넌트별 “공정 사용” 보장(노이즈 유저가 자원 독식 못 하게).
 * 내부 큐 소비 속도 제어: 컨슈머가 다운스트림(디비/서드파티)을 과부하하지 않도록 pull 속도 조절.
 * ===========================================================================
 * QPS: Queries Per Second 초당 허용 쿼리(요청) 수
 * CAS: Compare And Swap(또는 Set) 원자적 연산. "현재 값이 내가 예상한 값이면 새 값으로 바꾸고, 아니면 누가 이미 바꿨으니 아무것도 안한다."
 * 1_000_000_000.0 ns = 1 sec
 */
public final class TokenBucket {

    private final long capacity; // 최대 토큰 수(버킷의 한도)
    private final double refillPerSec; // 초당 리필 토큰 ex. refillPerSec=10 초당 10개의 요청만 허용. 1초에 10개 채워짐.
    private final AtomicLong tokens; // 현재 토큰 수
    private final AtomicLong lastRefillNanos; // 마지막 리필 시각(나노초)
    private final long nanosPerToken; // 토큰 1개의 초(나노초)

    public TokenBucket(long capacity, double refilPerSec) {

        if (capacity <= 0 || refilPerSec <= 0) {
            throw new IllegalArgumentException("capacity and refillPerSec must be > 0");
        }
        this.capacity = capacity; // 설정한 버킷 토큰 수
        this.refillPerSec = refilPerSec; // 설정한 리필 속도
        this.tokens = new AtomicLong(capacity); // 시작 시 토큰 가득 채움
        this.lastRefillNanos = new AtomicLong(System.nanoTime()); // 마지막 리필 시각(나노초)

        this.nanosPerToken = (long) Math.floor(1_000_000_000.0 / refillPerSec); // 1초 / 초당 리필 토큰 = 토큰 1개의 초(나노초)

    }

    /** 경과 시간마다 토큰을 채운다 **/
    private void refill() {

        final long now = System.nanoTime(); // 현재 시각
        final long last = lastRefillNanos.get(); // 마지막 리필 시각
        final long elapsed = now - last; // 경과 시각
        if (elapsed <= 0) return; // 경과한 시간이 없으면 종료(=리필하지 않음)

        // 시간이 얼마나 흘렀는지 계산하고 흘러간 시간만큼 토큰을 얼마나 채워야 하는지를 구하는 부분!
        final double seconds = elapsed / 1_000_000_000.0; // 경과 초 = 경과 나노초 1e9
        final long toAdd = (long) Math.floor(seconds * refillPerSec); // 경과 시간 * 리필 속도 = 리필 토큰 수(소수점 버림)
        if (toAdd <= 0) return; // 채울 토큰이 양수가 아니면 종료(=리필하지 않음)


        final long advance = nanosPerToken * toAdd; // 채워진 토큰만큼 경과해야 하는 시간 계산

        // 마지막 리필 시각 CAS로 원자 갱신
        if (lastRefillNanos.compareAndSet(last, last + advance)) {
            // 현재 토큰 수에 toAdd를 더하되 capacity를 넘지 않게 캡핑
            long prev, next;
            do {
                prev = tokens.get();
                next = Math.min(capacity, prev + toAdd);
            } while (!tokens.compareAndSet(prev, next));
        }

    }

    /** 토큰 1개 사용 시도(성공 시 true, 실패 시 false). */
    public boolean tryConsume() {
        refill(); // 사용 전에 최신화
        long prev, next;
        do {
            prev = tokens.get(); // 현재 토큰 확인
            if (prev <= 0) return false; // 없으면 실패
            next = prev - 1; // 1개 사용
        } while (!tokens.compareAndSet(prev, next)); // CAS로 원자 갱신
        return true; // 성공
    }

    /** 남은 토큰 수(조회 시에도 최신화). */
    public long getRemainingTokens() {
        refill();
        return tokens.get();
    }

    public long getCapacity() {
        return capacity;
    }


}
