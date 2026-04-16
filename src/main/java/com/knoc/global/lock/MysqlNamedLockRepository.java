package com.knoc.global.lock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MysqlNamedLockRepository {
    private final JdbcTemplate jdbcTemplate;

    public MysqlNamedLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * MySQL Named Lock 획득.
     *
     * - Named Lock은 "트랜잭션"이 아니라 "DB 커넥션"에 귀속된다.
     * 즉, 락을 잡은 커넥션이 종료되면 락도 함께 해제된다.
     * - (핵심) COMMIT/ROLLBACK은 트랜잭션 경계일 뿐이고, Named Lock은 그보다 먼저 "커넥션"에 묶여 있다.
     * - 같은 key로 동시에 2개 이상 락을 잡을 수 없으므로,
     * key를 멱등키/주문번호 단위로 구성하면 "중복 처리"를 막는 임계영역을 만들 수 있다.
     *
     * @return true(1)면 락 획득 성공, false(0/null)면 timeout/실패
     */
    public boolean getLock(String key, int timeoutSeconds) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class, key, timeoutSeconds);
        return result != null && result == 1;
    }

    /**
     * MySQL Named Lock 해제.
     *
     * - 호출 커넥션이 해당 key의 락을 보유 중이어야 정상 해제된다.
     * - (핵심) 락을 잡은 커넥션과 다른 커넥션에서 RELEASE_LOCK을 호출하면 락이 풀리지 않는다.
     * - 일반적으로 비즈니스 로직에서는 "정리 단계"에서 호출해 예외가 나도 락이 풀리게 한다.
     */
    public Integer releaseLock(String key) {
        return jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, key); // 해제 결과(1/0)를 반환
    }
}
