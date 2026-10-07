package br.com.pausaativa;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.in.IniciarJornada;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * O {@code /actuator/prometheus} que o Prometheus lê (spec H5, seção 5): as métricas de tempo publicam
 * histogramas, com uma faixa em cada limite do épico e um teto, e toda série leva a tag da aplicação.
 */
@TesteDeIntegracao
class MetricasPrometheusTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);
    private static final Pattern FAIXA = Pattern.compile("le=\"([^\"]+)\"");

    /** Com os filtros da aplicação: o {@code http.server.requests} é medido por um deles. */
    @Autowired
    MockMvcTester api;

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SalvarPerfil salvarPerfil;

    @Autowired
    IniciarJornada iniciarJornada;

    @Autowired
    AvancarAgenda avancarAgenda;

    @BeforeEach
    void umaRequisicaoUmaConsultaEUmDisparo() throws Exception {
        jdbc.execute("truncate jornada cascade");
        salvarPerfil.salvar(new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true));
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.of(9, 0))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());

        assertThat(api.get().uri("/api/v1/sistema/status")).hasStatusOk();
        assertThat(api.get().uri("/api/v1/historico?periodo=MES&data=2026-10-02"))
                .hasStatusOk();
        iniciarJornada.iniciar(3_000, 5);
        relogio.avancar(Duration.ofMinutes(30).plusSeconds(1));
        avancarAgenda.avancar();
    }

    private String metricas() throws UnsupportedEncodingException {
        var resultado = api.get().uri("/actuator/prometheus").exchange();
        assertThat(resultado).hasStatus(HttpStatus.OK);
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** As faixas ({@code le}) de um histograma, sem o +Inf, em segundos. */
    private static List<Double> faixas(String texto, String serie) {
        return Arrays.stream(texto.split("\n"))
                .filter(linha -> linha.startsWith(serie + "_bucket{"))
                .map(FAIXA::matcher)
                .filter(Matcher::find)
                .map(encontrado -> encontrado.group(1))
                .filter(faixa -> !faixa.equals("+Inf"))
                .map(Double::valueOf)
                .distinct()
                .sorted()
                .toList();
    }

    @Test
    void asOperacoesDaApiTemFaixaEm200MsETetoDe5s() throws Exception {
        List<Double> faixas = faixas(metricas(), "http_server_requests_seconds");

        assertThat(faixas).contains(0.2).isNotEmpty();
        assertThat(faixas.getLast()).isLessThanOrEqualTo(5.0);
    }

    @Test
    void oAtrasoDoDisparoTemFaixasEm1sE5sETetoDe30s() throws Exception {
        List<Double> faixas = faixas(metricas(), "pausaativa_marcos_atraso_disparo_seconds");

        assertThat(faixas).contains(1.0, 5.0);
        assertThat(faixas.getLast()).isLessThanOrEqualTo(30.0);
    }

    @Test
    void aConsultaDoHistoricoTemFaixaEm500MsETetoDe5s() throws Exception {
        List<Double> faixas = faixas(metricas(), "pausaativa_historico_consultas_seconds");

        assertThat(faixas).contains(0.5);
        assertThat(faixas.getLast()).isLessThanOrEqualTo(5.0);
    }

    @Test
    void todaSerieDaAplicacaoLevaATag() throws Exception {
        List<String> series = Arrays.stream(metricas().split("\n"))
                .filter(linha -> linha.startsWith("pausaativa_") || linha.startsWith("jvm_memory_used_bytes"))
                .toList();

        assertThat(series).isNotEmpty().allSatisfy(linha -> assertThat(linha).contains("application=\"pausa-ativa\""));
        assertThat(series)
                .anySatisfy(
                        linha -> assertThat(linha)
                                .startsWith(
                                        "pausaativa_marcos_disparados_total{application=\"pausa-ativa\",categoria=\"HIDRATACAO\"}"));
    }
}
