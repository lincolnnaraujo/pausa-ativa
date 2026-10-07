package br.com.pausaativa.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.port.in.CriarHistoricoDeExemplo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;

/**
 * Histórico de exemplo da demonstração (spec H4, seção 9) gravado no Postgres e lido pela API, com o
 * catálogo da migração e sem perfil gravado. Hoje é quarta, 07/10/2026.
 */
@TesteDeIntegracao
class HistoricoDeExemploIntegracaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 7);

    @Autowired
    CriarHistoricoDeExemplo criarHistorico;

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    Environment ambiente;

    MockMvcTester api;

    @BeforeEach
    void bancoVazioESemPerfil() {
        jdbc.execute("truncate jornada cascade");
        jdbc.execute("truncate perfil_fisico cascade");
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.of(10, 0))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
        api = MockMvcTester.from(contexto);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void gravaOsDiasDeExemploComBlocosDoCatalogoEAApiMostraOHistorico() {
        int criadas = criarHistorico.criar();

        assertThat(criadas).isBetween(20, 33);
        assertThat(contar("select count(*) from jornada")).isEqualTo(criadas);
        assertThat(jdbc.queryForObject("select min(data_referencia) from jornada", LocalDate.class))
                .isAfterOrEqualTo(HOJE.minusDays(45));
        assertThat(jdbc.queryForObject("select max(data_referencia) from jornada", LocalDate.class))
                .isBefore(HOJE);
        assertThat(contar("select count(*) from jornada where status in ('EM_ANDAMENTO', 'PAUSADA')"))
                .isZero();
        assertThat(contar("select count(*) from marco")).isEqualTo(criadas * 24);
        assertThat(contar("select count(*) from item_do_bloco i where not exists"
                        + " (select 1 from exercicio e where e.codigo = i.exercicio_codigo)"))
                .as("os blocos vêm do catálogo de verdade")
                .isZero();
        assertThat(contar("select count(*) from item_do_bloco")).isPositive();
        assertThat(contar("select count(*) from perfil_fisico"))
                .as("o perfil do usuário fica vazio")
                .isZero();

        var mes = assertThat(api.get().uri("/api/v1/historico?periodo=MES&data=2026-09-15"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        mes.extractingPath("$.categorias[0].taxa").isNotNull();
        mes.extractingPath("$.categorias[1].taxa").isNotNull();
        mes.extractingPath("$.categorias[0].diasComDados").asNumber().isNotEqualTo(0);

        LocalDate umDia = jdbc.queryForObject("select max(data_referencia) from jornada", LocalDate.class);
        var dia = assertThat(api.get().uri("/api/v1/jornadas?data={data}", umDia))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        dia.extractingPath("$.marcos.length()").isEqualTo(24);
        dia.extractingPath("$.marcos[*].podeCorrigir").asArray().containsOnly(false);
    }

    @Test
    void comJornadaDeUmDiaAnteriorNaoCriaNada() {
        int criadas = criarHistorico.criar();

        assertThat(criarHistorico.criar()).isZero();
        assertThat(contar("select count(*) from jornada")).isEqualTo(criadas);
    }

    @Test
    void foraDaDemonstracaoNadaECriadoNaSubida() {
        assertThat(ambiente.getProperty("pausa-ativa.demonstracao.historico")).isEqualTo("false");
        assertThat(contexto.containsBean("historicoDeExemploNaSubida")).isFalse();
    }
}
