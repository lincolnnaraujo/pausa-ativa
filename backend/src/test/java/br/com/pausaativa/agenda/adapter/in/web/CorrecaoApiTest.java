package br.com.pausaativa.agenda.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
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

/** Correção no mesmo dia e jornada de um dia pela API, com Postgres real (spec H4, seções 3.3 e 6). */
@TesteDeIntegracao
class CorrecaoApiTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AvancarAgenda avancarAgenda;

    @Autowired
    SalvarPerfil salvarPerfil;

    @Autowired
    MeterRegistry metricas;

    MockMvcTester api;

    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        salvarPerfil.salvar(new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true));
        relogioAs(HOJE, "09:00");
        api = MockMvcTester.from(contexto);
    }

    private void relogioAs(LocalDate dia, String hora) {
        relogio.ajustarPara(LocalDateTime.of(dia, LocalTime.parse(hora))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
    }

    private static String corpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult atual() {
        return api.get().uri("/api/v1/jornadas/atual").exchange();
    }

    private UUID marco(String categoria, int sequencia) {
        List<String> ids = JsonPath.read(
                corpo(atual()),
                "$.marcos[?(@.categoria == '" + categoria + "' && @.sequencia == " + sequencia + ")].id");
        return UUID.fromString(ids.getFirst());
    }

    private MvcTestResult corrigir(UUID marco, String corpo) {
        return api.post()
                .uri("/api/v1/marcos/" + marco + "/correcao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    /** Dia iniciado às 09:00, com a 1ª água marcada como falha às 09:31. */
    private UUID primeiraAguaComFalha() {
        assertThat(api.post()
                        .uri("/api/v1/jornadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(HttpStatus.CREATED);
        relogioAs(HOJE, "09:30");
        avancarAgenda.avancar();
        UUID agua = marco("HIDRATACAO", 1);
        relogioAs(HOJE, "09:31");
        assertThat(api.post().uri("/api/v1/marcos/" + agua + "/falha")).hasStatus(HttpStatus.OK);
        return agua;
    }

    private double corrigidos(String categoria, String para) {
        var contador = metricas.find("pausaativa.marcos.corrigidos")
                .tags("categoria", categoria, "para", para)
                .counter();
        return contador == null ? 0 : contador.count();
    }

    @Test
    void cenario4CorrigirFalhaParaConcluidoMarcaEditadoESomaAAgua() {
        UUID agua = primeiraAguaComFalha();
        double antes = corrigidos("HIDRATACAO", "CONCLUIDO");
        relogioAs(HOJE, "18:00");

        MvcTestResult resultado = corrigir(agua, "{\"status\": \"CONCLUIDO\"}");

        assertThat(resultado).hasStatus(HttpStatus.OK);
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.marcos[0].status").isEqualTo("CONCLUIDO");
        json.extractingPath("$.marcos[0].editadoEm").isEqualTo("2026-10-02T18:00:00-03:00");
        json.extractingPath("$.marcos[0].respondidoEm").isEqualTo("2026-10-02T09:31:00-03:00");
        json.extractingPath("$.marcos[0].podeCorrigir").isEqualTo(true);
        json.extractingPath("$.aguaIngeridaMl").isEqualTo(187.5);
        assertThat(atual()).bodyJson().extractingPath("$.marcos[0].editadoEm").isEqualTo("2026-10-02T18:00:00-03:00");
        assertThat(corrigidos("HIDRATACAO", "CONCLUIDO")).isEqualTo(antes + 1);
    }

    @Test
    void podeCorrigirSoNasRespostasDoDia() {
        primeiraAguaComFalha();

        var json = assertThat(atual()).bodyJson();
        json.extractingPath("$.marcos[0].podeCorrigir").isEqualTo(true);
        json.extractingPath("$.marcos[0].editadoEm").isNull();
        json.extractingPath("$.marcos[1].podeCorrigir").isEqualTo(false); // agendado
    }

    @Test
    void corrigirParaASituacaoAtualDevolveAMesmaSemMarcarEditado() {
        UUID agua = primeiraAguaComFalha();

        assertThat(corrigir(agua, "{\"status\": \"FALHA\"}"))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.marcos[0].editadoEm")
                .isNull();
    }

    @Test
    void cenario5DepoisDoDiaResponde409ENadaMuda() {
        UUID agua = primeiraAguaComFalha();
        relogioAs(HOJE, "18:00");
        UUID jornada = UUID.fromString(JsonPath.read(corpo(atual()), "$.id"));
        assertThat(api.post().uri("/api/v1/jornadas/" + jornada + "/finalizacao"))
                .hasStatus(HttpStatus.OK);
        relogioAs(HOJE.plusDays(1), "08:00");

        assertThat(corrigir(agua, "{\"status\": \"CONCLUIDO\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Só dá para corrigir os lembretes de hoje.");
        var json = assertThat(api.get().uri("/api/v1/jornadas?data=2026-10-02")).bodyJson();
        json.extractingPath("$.marcos[0].status").isEqualTo("FALHA");
        json.extractingPath("$.marcos[0].podeCorrigir").isEqualTo(false);
    }

    @Test
    void lembreteQueAindaPedeRespostaResponde409() {
        primeiraAguaComFalha();
        relogioAs(HOJE, "10:00");
        avancarAgenda.avancar();

        assertThat(corrigir(marco("HIDRATACAO", 2), "{\"status\": \"CONCLUIDO\"}"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Só dá para corrigir um lembrete concluído ou com falha.");
    }

    @Test
    void corpoSemStatusOuComOutraSituacaoResponde400() {
        UUID agua = primeiraAguaComFalha();

        assertThat(corrigir(agua, "{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Informe o status correto: CONCLUIDO ou FALHA.");
        assertThat(corrigir(agua, "{\"status\": \"NAO_ENTREGUE\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void marcoInexistenteResponde404() {
        assertThat(corrigir(UUID.randomUUID(), "{\"status\": \"FALHA\"}")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void d6CorrigirOBlocoQueCompensouCorrigeOAdiado() {
        primeiraAguaComFalha();
        relogioAs(HOJE, "10:00");
        avancarAgenda.avancar();
        assertThat(api.post().uri("/api/v1/marcos/" + marco("EXERCICIO", 1) + "/adiamento"))
                .hasStatus(HttpStatus.OK);
        relogioAs(HOJE, "11:00");
        avancarAgenda.avancar();
        UUID segundo = marco("EXERCICIO", 2);
        assertThat(api.post().uri("/api/v1/marcos/" + segundo + "/conclusao")).hasStatus(HttpStatus.OK);
        relogioAs(HOJE, "11:10");

        MvcTestResult resultado = corrigir(segundo, "{\"status\": \"FALHA\"}");

        String json = corpo(resultado);
        List<String> situacoes =
                JsonPath.read(json, "$.marcos[?(@.categoria == 'EXERCICIO' && @.sequencia <= 2)].status");
        List<String> editados =
                JsonPath.read(json, "$.marcos[?(@.categoria == 'EXERCICIO' && @.sequencia <= 2)].editadoEm");
        assertThat(situacoes).containsExactly("FALHA", "FALHA");
        assertThat(editados).containsOnly("2026-10-02T11:10:00-03:00");
    }

    @Test
    void jornadaDeUmDiaPassadoComOMesmoFormatoDaAtual() {
        primeiraAguaComFalha();
        relogioAs(HOJE, "18:00");
        UUID jornada = UUID.fromString(JsonPath.read(corpo(atual()), "$.id"));
        assertThat(api.post().uri("/api/v1/jornadas/" + jornada + "/finalizacao"))
                .hasStatus(HttpStatus.OK);
        relogioAs(HOJE.plusDays(3), "09:00");

        var json = assertThat(api.get().uri("/api/v1/jornadas?data=2026-10-02"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        json.extractingPath("$.id").isEqualTo(jornada.toString());
        json.extractingPath("$.status").isEqualTo("FINALIZADA");
        json.extractingPath("$.marcos.length()").isEqualTo(24);
        json.extractingPath("$.marcos[0].status").isEqualTo("FALHA");
    }

    @Test
    void diaSemJornadaResponde204EDataInvalida400() {
        assertThat(api.get().uri("/api/v1/jornadas?data=2026-09-30")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.get().uri("/api/v1/jornadas?data=30-09-2026"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void restricaoDoBancoSoAceitaEditadoEmConcluidoOuFalha() {
        primeiraAguaComFalha();

        assertThat(jdbc.update("update marco set editado_em = now() where status = 'FALHA'"))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update marco set editado_em = now() where status = 'AGENDADO'"))
                .hasMessageContaining("ck_marco_editado");
    }
}
