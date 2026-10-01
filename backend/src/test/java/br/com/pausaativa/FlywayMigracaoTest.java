package br.com.pausaativa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** As migrações aplicam do zero em um Postgres limpo (spec H1, seção 5.5). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayMigracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaBaselineEmBancoLimpo() {
        List<Map<String, Object>> historico = jdbc.queryForList(
                "select version, script, success from flyway_schema_history order by installed_rank");

        assertThat(historico)
                .singleElement()
                .satisfies(migracao -> {
                    assertThat(migracao).containsEntry("version", "1");
                    assertThat(migracao).containsEntry("script", "V1__baseline.sql");
                    assertThat(migracao).containsEntry("success", true);
                });
    }
}
