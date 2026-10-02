package br.com.pausaativa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** As migrações aplicam do zero em um Postgres limpo (spec H1, seção 5.5). */
@TesteDeIntegracao
class FlywayMigracaoTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaTodasAsMigracoesEmBancoLimpo() {
        List<Map<String, Object>> historico =
                jdbc.queryForList("select version, script, success from flyway_schema_history order by installed_rank");

        assertThat(historico)
                .extracting(migracao -> migracao.get("script"))
                .containsExactly("V1__baseline.sql", "V2__jornada_pausa_marco.sql", "V3__treino_catalogo_perfil.sql");
        assertThat(historico).allSatisfy(migracao -> assertThat(migracao).containsEntry("success", true));
    }
}
