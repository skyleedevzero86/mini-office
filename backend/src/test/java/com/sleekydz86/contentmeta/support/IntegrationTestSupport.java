package com.sleekydz86.contentmeta.support;

import com.sleekydz86.contentmeta.migration.application.MigrationService;
import com.sleekydz86.contentmeta.migration.application.ReadSourceSwitch;
import com.sleekydz86.contentmeta.migration.domain.MigrationMode;
import com.sleekydz86.contentmeta.migration.domain.ReadSource;
import com.sleekydz86.contentmeta.migration.domain.VerifyResult;
import com.sleekydz86.contentmeta.migration.infrastructure.MigrationRunRow;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 실제 MySQL 8 컨테이너에 Flyway(구시스템 V0.x + V1~V10)를 올려 테스트한다.
 * 테스트마다 신규 테이블·이관 기록을 비우고 구시스템 샘플을 다시 넣는다 (코드표·품질 규칙·배치 정의는 남긴다).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    private static final List<String> RESET_TABLES = List.of(
            "content_episode", "content_season", "content_credit", "content_person", "content_localization",
            "content_title_region", "content_title_country", "content_title_genre", "content_external_id", "content_title",
            "migration_issue", "migration_work_key", "migration_verify", "migration_run", "legacy_change_log",
            "dq_result", "batch_job_run", "work_log", "movies", "MergedTVShow", "cartoon_video");

    // 바이너리 로그가 켜진 MySQL에서 함수·트리거를 만들려면 log-bin-trust-function-creators가 필요하다
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.40")
            .withDatabaseName("content_meta")
            .withUsername("app")
            .withPassword("app")
            .withCommand("--log-bin-trust-function-creators=1", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    static {
        MYSQL.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MigrationService migrationService;

    @Autowired
    protected ReadSourceSwitch readSourceSwitch;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String separator = MYSQL.getJdbcUrl().contains("?") ? "&" : "?";
        registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + separator + "serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : RESET_TABLES) {
                    statement.execute("TRUNCATE TABLE " + table);
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/legacy/V0_2__legacy_sample_data.sql"));
            // 샘플을 다시 넣으며 트리거가 남긴 변경 기록은 지운다 (운영에서는 트리거보다 먼저 있던 데이터다)
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE legacy_change_log");
            }
            return null;
        });
        readSourceSwitch.switchTo(ReadSource.LEGACY);
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    protected long newRun(MigrationMode mode) {
        return newRun(mode, 500);
    }

    protected long newRun(MigrationMode mode, int batchSize) {
        return migrationService.createRun(mode, "테스트 " + mode.name(), batchSize).getRunId();
    }

    /** 백필을 끝까지 돌리고 작업 상태를 돌려준다 */
    protected MigrationRunRow backfillAll() {
        return backfillAll(500);
    }

    protected MigrationRunRow backfillAll(int batchSize) {
        long runId = newRun(MigrationMode.BACKFILL, batchSize);
        migrationService.backfill(runId, 1000, 0);
        return migrationService.run(runId);
    }

    protected VerifyResult verifyNow() {
        return migrationService.verify(newRun(MigrationMode.VERIFY));
    }

    /** 백필 → 대조 → 읽기 NEW 전환 */
    protected void openNewReadSource() {
        backfillAll();
        verifyNow();
        readSourceSwitch.switchTo(ReadSource.NEW);
    }
}
