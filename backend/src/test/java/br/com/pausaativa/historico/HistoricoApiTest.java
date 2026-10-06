package br.com.pausaativa.historico;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@code GET /api/v1/historico} com a Agenda e o Postgres de verdade (spec H4, seções 3 e 6). As jornadas
 * são gravadas direto no banco, com as situações exatas de cada cenário. Hoje é sexta, 02/10/2026.
 */
@TesteDeIntegracao
class HistoricoApiTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry metricas;

    MockMvcTester api;

    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.of(9, 0))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
        api = MockMvcTester.from(contexto);
    }

    /** Situações em ordem de sequência: {@code situacoes("CONCLUIDO", 12, "FALHA", 2)}. */
    private static List<String> situacoes(Object... quantidadePorSituacao) {
        List<String> lista = new ArrayList<>();
        for (int i = 0; i < quantidadePorSituacao.length; i += 2) {
            lista.addAll(
                    Collections.nCopies((Integer) quantidadePorSituacao[i + 1], (String) quantidadePorSituacao[i]));
        }
        return lista;
    }

    /** Grava a jornada do dia com os marcos nas situações dadas; sem exercício, como na v0.2.0. */
    private void jornadaNoDia(LocalDate dia, String status, List<String> agua, List<String> exercicio) {
        UUID jornada = UUID.randomUUID();
        OffsetDateTime inicio =
                dia.atTime(9, 0).atZone(RelogioDeTeste.SAO_PAULO).toOffsetDateTime();
        boolean aberta = status.equals("EM_ANDAMENTO") || status.equals("PAUSADA");
        jdbc.update(
                "insert into jornada (id, versao, data_referencia, status, meta_agua_ml, iniciada_em, finalizada_em,"
                        + " duracao_bloco_min) values (?, 0, ?, ?, 3000, ?, ?, 5)",
                jornada,
                dia,
                status,
                inicio,
                aberta ? null : inicio.plusHours(9));
        gravarMarcos(jornada, "HIDRATACAO", 1800, agua);
        gravarMarcos(jornada, "EXERCICIO", 3600, exercicio);
    }

    private void gravarMarcos(UUID jornada, String categoria, int intervalo, List<String> situacoes) {
        for (int i = 0; i < situacoes.size(); i++) {
            int sequencia = i + 1;
            jdbc.update(
                    "insert into marco (id, jornada_id, categoria, sequencia, status, segundos_trabalhados_previstos,"
                            + " segundos_trabalhados_limite, volume_ml) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(),
                    jornada,
                    categoria,
                    sequencia,
                    situacoes.get(i),
                    sequencia * intervalo,
                    (sequencia + 1) * intervalo,
                    categoria.equals("HIDRATACAO") ? 187.5 : null);
        }
    }

    private MvcTestResult historico(String periodo, String data) {
        return api.get()
                .uri("/api/v1/historico?periodo={periodo}&data={data}", periodo, data)
                .exchange();
    }

    @Test
    void cenario1TaxaDoDiaComOsNaoEntreguesAParte() {
        jornadaNoDia(
                HOJE.minusDays(1),
                "FINALIZADA",
                situacoes("CONCLUIDO", 12, "FALHA", 2, "NAO_ENTREGUE", 2),
                situacoes("CONCLUIDO", 5, "FALHA", 2, "NAO_ENTREGUE", 1));

        MvcTestResult resultado = historico("DIA", "2026-10-01");

        assertThat(resultado).hasStatus(HttpStatus.OK).hasContentType(MediaType.APPLICATION_JSON);
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.periodo").isEqualTo("DIA");
        json.extractingPath("$.inicio").isEqualTo("2026-10-01");
        json.extractingPath("$.fim").isEqualTo("2026-10-01");
        json.extractingPath("$.categorias[0].categoria").isEqualTo("HIDRATACAO");
        json.extractingPath("$.categorias[0].concluidos").isEqualTo(12);
        json.extractingPath("$.categorias[0].falhas").isEqualTo(2);
        json.extractingPath("$.categorias[0].naoEntregues").isEqualTo(2);
        json.extractingPath("$.categorias[0].taxa").isEqualTo(85.7);
        json.extractingPath("$.categorias[0].metaAtingida").isEqualTo(true);
        json.extractingPath("$.categorias[1].categoria").isEqualTo("EXERCICIO");
        json.extractingPath("$.categorias[1].taxa").isEqualTo(71.4);
        json.extractingPath("$.categorias[1].metaAtingida").isEqualTo(false);
        json.extractingPath("$.dias.length()").isEqualTo(1);
        json.extractingPath("$.dias[0].jornada").isEqualTo("FINALIZADA");
        json.extractingPath("$.dias[0].categorias[0].taxa").isEqualTo(85.7);
    }

    @Test
    void cenario2SemanaSomaOsMarcosComHojeEmAndamentoEDiasFuturos() {
        jornadaNoDia(
                LocalDate.of(2026, 9, 28),
                "FINALIZADA",
                situacoes("CONCLUIDO", 15, "FALHA", 1),
                situacoes("CONCLUIDO", 7, "FALHA", 1));
        jornadaNoDia(
                LocalDate.of(2026, 9, 29),
                "FINALIZADA",
                situacoes("CONCLUIDO", 1, "FALHA", 1, "NAO_CONCLUIDO", 14),
                situacoes("FALHA", 1, "NAO_CONCLUIDO", 7));
        jornadaNoDia(
                HOJE,
                "EM_ANDAMENTO",
                situacoes("CONCLUIDO", 3, "FALHA", 1, "PENDENTE", 1, "AGENDADO", 11),
                situacoes("CONCLUIDO", 1, "ADIADO", 1, "AGENDADO", 6));

        var json = assertThat(historico("SEMANA", "2026-09-30")).bodyJson();

        json.extractingPath("$.inicio").isEqualTo("2026-09-28");
        json.extractingPath("$.fim").isEqualTo("2026-10-04");
        json.extractingPath("$.categorias[0].concluidos").isEqualTo(19);
        json.extractingPath("$.categorias[0].falhas").isEqualTo(3);
        json.extractingPath("$.categorias[0].naoConcluidos").isEqualTo(14);
        json.extractingPath("$.categorias[0].emAberto").isEqualTo(12);
        json.extractingPath("$.categorias[0].taxa").isEqualTo(86.3); // 19 de 22, e não a média dos dias
        json.extractingPath("$.categorias[0].diasComDados").isEqualTo(3);
        json.extractingPath("$.categorias[0].diasNaMeta").isEqualTo(1);
        json.extractingPath("$.categorias[1].taxa").isEqualTo(80.0);
        json.extractingPath("$.categorias[1].metaAtingida").isEqualTo(true);
        json.extractingPath("$.categorias[1].emAberto").isEqualTo(7);
        json.extractingPath("$.dias.length()").isEqualTo(7);
        json.extractingPath("$.dias[2].data").isEqualTo("2026-09-30");
        json.extractingPath("$.dias[2].jornada").isNull();
        json.extractingPath("$.dias[2].futuro").isEqualTo(false);
        json.extractingPath("$.dias[2].categorias.length()").isEqualTo(0);
        json.extractingPath("$.dias[4].jornada").isEqualTo("EM_ANDAMENTO");
        json.extractingPath("$.dias[4].categorias[0].taxa").isEqualTo(75.0);
        json.extractingPath("$.dias[5].futuro").isEqualTo(true);
        json.extractingPath("$.dias[6].futuro").isEqualTo(true);
    }

    @Test
    void jornadaDaV020SemExercicioDeixaOExercicioSemDados() {
        jornadaNoDia(LocalDate.of(2026, 9, 10), "FINALIZADA", situacoes("CONCLUIDO", 14, "FALHA", 2), List.of());

        var json = assertThat(historico("MES", "2026-09-10")).bodyJson();

        json.extractingPath("$.dias.length()").isEqualTo(30);
        json.extractingPath("$.categorias[0].taxa").isEqualTo(87.5);
        json.extractingPath("$.categorias[1].taxa").isNull();
        json.extractingPath("$.categorias[1].metaAtingida").isNull();
        json.extractingPath("$.categorias[1].diasComDados").isEqualTo(0);
        json.extractingPath("$.dias[9].categorias[1].categoria").isEqualTo("EXERCICIO");
        json.extractingPath("$.dias[9].categorias[1].concluidos").isEqualTo(0);
        json.extractingPath("$.dias[9].categorias[1].taxa").isNull();
    }

    @Test
    void cenario6SemanaSemJornadaFicaSemTaxaESemZerosContados() {
        var json = assertThat(historico("SEMANA", "2026-08-05")).bodyJson();

        json.extractingPath("$.inicio").isEqualTo("2026-08-03");
        json.extractingPath("$.categorias[0].taxa").isNull();
        json.extractingPath("$.categorias[0].concluidos").isEqualTo(0);
        json.extractingPath("$.categorias[0].diasComDados").isEqualTo(0);
        json.extractingPath("$.dias[*].jornada").asArray().containsOnlyNulls();
    }

    @Test
    void dataFuturaPeriodoInvalidoEDataMalformadaRespondem400() {
        assertThat(historico("DIA", "2026-10-03"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O histórico vai até hoje: 2026-10-03 ainda não chegou.");
        assertThat(historico("ANO", "2026-10-01")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(historico("DIA", "01/10/2026")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.get().uri("/api/v1/historico?data=2026-10-01")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mesComUmAnoDeDadosRespondeEmMenosDe500Ms() {
        jdbc.update("""
                insert into jornada (id, versao, data_referencia, status, meta_agua_ml, iniciada_em, finalizada_em,
                    duracao_bloco_min)
                select gen_random_uuid(), 0, d::date, 'FINALIZADA', 3000, d + interval '12 hours',
                    d + interval '21 hours', 5
                from generate_series(date '2025-10-01', date '2026-09-30', interval '1 day') as d
                """);
        jdbc.update("""
                insert into marco (id, jornada_id, categoria, sequencia, status, segundos_trabalhados_previstos,
                    segundos_trabalhados_limite, volume_ml)
                select gen_random_uuid(), j.id, 'HIDRATACAO', s,
                    case when s % 5 = 0 then 'FALHA' else 'CONCLUIDO' end, s * 1800, (s + 1) * 1800, 187.5
                from jornada j cross join generate_series(1, 16) as s
                """);
        jdbc.update("""
                insert into marco (id, jornada_id, categoria, sequencia, status, segundos_trabalhados_previstos,
                    segundos_trabalhados_limite, volume_ml)
                select gen_random_uuid(), j.id, 'EXERCICIO', s,
                    case when s % 4 = 0 then 'NAO_ENTREGUE' else 'CONCLUIDO' end, s * 3600, (s + 1) * 3600, null
                from jornada j cross join generate_series(1, 8) as s
                """);
        assertThat(jdbc.queryForObject("select count(*) from marco", Integer.class))
                .isEqualTo(365 * 24);
        historico("MES", "2026-09-15"); // aquece

        List<Long> tempos = new ArrayList<>();
        MvcTestResult resultado = null;
        for (int i = 0; i < 10; i++) {
            long inicio = System.nanoTime();
            resultado = historico("MES", "2026-09-15");
            tempos.add((System.nanoTime() - inicio) / 1_000_000);
        }

        assertThat(tempos).allSatisfy(ms -> assertThat(ms).isLessThan(500));
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.categorias[0].concluidos").isEqualTo(390);
        json.extractingPath("$.categorias[0].falhas").isEqualTo(90);
        json.extractingPath("$.categorias[0].taxa").isEqualTo(81.2);
        json.extractingPath("$.categorias[0].diasNaMeta").isEqualTo(30);
        json.extractingPath("$.categorias[1].naoEntregues").isEqualTo(60);
        json.extractingPath("$.categorias[1].taxa").isEqualTo(100.0);
        Timer timer = metricas.find("pausaativa.historico.consultas")
                .tag("periodo", "MES")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isGreaterThanOrEqualTo(11);
    }
}
