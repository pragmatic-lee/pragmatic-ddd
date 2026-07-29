package io.pragmatic.ddd.mybatis.id;

import io.pragmatic.ddd.base.id.IdGeneratorDefinition;
import io.pragmatic.ddd.base.id.IdGeneratorRegistry;
import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IdType;
import io.pragmatic.ddd.mybatis.MysqlTestSupport;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MySQL 版集成测试：复用 {@link MysqlTestSupport} 连接真实 MySQL，验证
 * IdSegmentMapper + DbSegmentAllocator 的号段分配、游标推进、
 * 渠道隔离与并发行锁语义。无可达 MySQL 时整体跳过（构建仍成功）。
 */
class MysqlIdSegmentAllocatorTest {

    private static final String SCHEMA_SQL =
            "/io/pragmatic/ddd/mybatis/id/schema/id-segment-schema-mysql.sql";

    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void beforeAll() {
        Assumptions.assumeTrue(MysqlTestSupport.isAvailable(), "MySQL 不可用，跳过集成测试");
        try {
            sqlSessionFactory = MysqlTestSupport.sessionFactory(SCHEMA_SQL, IdSegmentMapper.class);
        } catch (Exception e) {
            throw new IllegalStateException("构建 SqlSessionFactory 失败", e);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // 每个用例前清空并重置种子，保证隔离。
        try (SqlSession session = sqlSessionFactory.openSession(true);
             Statement st = session.getConnection().createStatement()) {
            st.execute("DELETE FROM id_segment");
            st.execute("INSERT INTO id_segment (biz_key, current_max_id, step, remark) "
                    + "VALUES ('order', 0, 1000, 'order'), ('payment', 999999, 1000, 'payment')");
        }
    }

    private DbSegmentAllocator allocator() {
        return new DbSegmentAllocator(sqlSessionFactory);
    }

    @Test
    void allocateNext_returnsFirstSegmentAndAdvancesCursor() throws Exception {
        DbSegmentAllocator allocator = allocator();

        IdSegment first = allocator.allocateNext("order");
        Assertions.assertThat(first.current()).isEqualTo(1);
        Assertions.assertThat(first.max()).isEqualTo(1000);
        Assertions.assertThat(currentMaxOf("order")).isEqualTo(1000);

        IdSegment second = allocator.allocateNext("order");
        Assertions.assertThat(second.current()).isEqualTo(1001);
        Assertions.assertThat(second.max()).isEqualTo(2000);
        Assertions.assertThat(currentMaxOf("order")).isEqualTo(2000);
    }

    @Test
    void allocateNext_isolatesChannels() throws Exception {
        DbSegmentAllocator allocator = allocator();

        allocator.allocateNext("order");

        // payment 的游标不应被 order 的分配影响。
        Assertions.assertThat(currentMaxOf("payment")).isEqualTo(999999);
        IdSegment paymentSeg = allocator.allocateNext("payment");
        Assertions.assertThat(paymentSeg.current()).isEqualTo(1000000);
    }

    @Test
    void registry_producesSequentialAndUniqueIds() throws Exception {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        registry.register(new IdGeneratorDefinition("order", 1, 1000, IdType.LONG, null, "order"),
                allocator());

        Set<Long> ids = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 1500; i++) {
            ids.add(registry.nextId("order"));
        }
        Assertions.assertThat(ids).hasSize(1500);
        Assertions.assertThat(ids).contains(1L, 1000L, 1001L, 1500L);
    }

    @Test
    void concurrentAllocation_noOverlap() throws Exception {
        int threads = 10;
        int step = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<Long> allIds = ConcurrentHashMap.newKeySet();

        DbSegmentAllocator allocator = allocator();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    IdSegment seg = allocator.allocateNext("order");
                    for (long id = seg.current(); id <= seg.max(); id++) {
                        allIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        pool.shutdown();

        // 10 段 × 1000 = 10000 个 ID，跨线程零重叠、连续覆盖 [1, 10000]，验证行锁有效。
        Assertions.assertThat(allIds).hasSize(threads * step);
        long min = allIds.stream().mapToLong(Long::longValue).min().orElse(0);
        long max = allIds.stream().mapToLong(Long::longValue).max().orElse(0);
        Assertions.assertThat(min).isEqualTo(1);
        Assertions.assertThat(max).isEqualTo((long) threads * step);
    }

    private long currentMaxOf(String bizKey) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            IdSegmentMapper mapper = session.getMapper(IdSegmentMapper.class);
            return mapper.selectForUpdate(bizKey).getCurrentMaxId();
        }
    }
}
