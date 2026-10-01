package br.com.pausaativa.agenda.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Contrato HTTP da Agenda (spec H2, seção 6) com a aplicação inteira e Postgres real. */
@TesteDeIntegracao
class AgendaApiTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JornadaRepository repositorio;

    @Autowired
    TransactionTemplate transacao;

    MockMvcTester api;

    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        relogioAs("09:00");
        api = MockMvcTester.from(contexto);
    }

    private void relogioAs(String hora) {
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.parse(hora))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
    }

    /** Faz o papel do agendador, que chega na T4. */
    private void agendador(UUID jornadaId) {
        transacao.executeWithoutResult(status -> {
            var jornada = repositorio.buscarComBloqueio(jornadaId).orElseThrow();
            jornada.avancar(relogio.instant());
            repositorio.salvar(jornada);
        });
    }

    private MvcTestResult post(String caminho) {
        return api.post().uri(caminho).exchange();
    }

    private MvcTestResult iniciar(String corpo) {
        return api.post()
                .uri("/api/v1/jornadas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .exchange();
    }

    private UUID iniciarDia() {
        MvcTestResult resultado = iniciar("{}");
        assertThat(resultado).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(campo(resultado, "$.id"));
    }

    private UUID primeiroMarco() {
        return UUID.fromString(campo(api.get().uri("/api/v1/jornadas/atual").exchange(), "$.marcos[0].id"));
    }

    private static String campo(MvcTestResult resultado, String caminho) {
        try {
            return JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), caminho);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void semJornadaHojeAtualResponde204() {
        assertThat(api.get().uri("/api/v1/jornadas/atual")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void iniciarResponde201ComAJornadaNoFormatoDoContrato() {
        MvcTestResult resultado = iniciar("{}");

        assertThat(resultado).hasStatus(HttpStatus.CREATED).hasContentType(MediaType.APPLICATION_JSON);
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.status").isEqualTo("EM_ANDAMENTO");
        json.extractingPath("$.dataReferencia").isEqualTo("2026-10-02");
        json.extractingPath("$.iniciadaEm").isEqualTo("2026-10-02T09:00:00-03:00");
        json.extractingPath("$.finalizadaEm").isNull();
        json.extractingPath("$.metaAguaMl").isEqualTo(3000);
        json.extractingPath("$.aguaIngeridaMl").isEqualTo(0);
        json.extractingPath("$.marcos.length()").isEqualTo(16);
        json.extractingPath("$.marcos[0].status").isEqualTo("AGENDADO");
        json.extractingPath("$.marcos[0].volumeMl").isEqualTo(187.5);
        json.extractingPath("$.marcos[0].volumeAproximadoMl").isEqualTo(190);
        json.extractingPath("$.marcos[0].segundosTrabalhadosPrevistos").isEqualTo(1800);
        json.extractingPath("$.marcos[0].disparadoEm").isNull();
        json.extractingPath("$.marcos[0].mensagem").isEqualTo("Beba ~190 ml. Levante-se para buscar a água.");
    }

    @Test
    void iniciarSemCorpoUsaAMetaPadraoEComMetaInformadaUsaElla() {
        assertThat(post("/api/v1/jornadas")).hasStatus(HttpStatus.CREATED);
        jdbc.execute("truncate jornada cascade");

        assertThat(iniciar("{\"metaAguaMl\": 2000}"))
                .bodyJson()
                .extractingPath("$.marcos[0].volumeMl")
                .isEqualTo(125);
    }

    @Test
    void metaInvalidaResponde400ComProblemDetailsEmPortugues() {
        MvcTestResult resultado = iniciar("{\"metaAguaMl\": 0}");

        assertThat(resultado).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.title").isEqualTo("Requisição inválida");
        json.extractingPath("$.detail").asString().contains("entre 1 e 6000 ml");
        json.extractingPath("$.status").isEqualTo(400);
    }

    @Test
    void segundoInicioNoMesmoDiaResponde409() {
        iniciarDia();

        MvcTestResult resultado = iniciar("{}");

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado).bodyJson().extractingPath("$.title").isEqualTo("Operação recusada");
        assertThat(resultado).bodyJson().extractingPath("$.detail").asString().startsWith("Já existe uma jornada");
    }

    @Test
    void pausaRetomadaEFinalizacaoPorHttp() {
        UUID id = iniciarDia();

        relogioAs("12:00");
        assertThat(post("/api/v1/jornadas/" + id + "/pausa"))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("PAUSADA");
        relogioAs("13:00");
        assertThat(post("/api/v1/jornadas/" + id + "/retomada"))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("EM_ANDAMENTO");
        relogioAs("15:00");
        var finalizada = assertThat(post("/api/v1/jornadas/" + id + "/finalizacao"))
                .hasStatusOk()
                .bodyJson();

        finalizada.extractingPath("$.status").isEqualTo("FINALIZADA");
        finalizada.extractingPath("$.finalizadaEm").isEqualTo("2026-10-02T15:00:00-03:00");
        finalizada.extractingPath("$.tempoTrabalhadoSegundos").isEqualTo(5 * 3600);
        assertThat(post("/api/v1/jornadas/" + id + "/finalizacao")).hasStatusOk();
    }

    @Test
    void retomarJornadaQueNaoEstaPausadaResponde409ComMotivo() {
        UUID id = iniciarDia();

        MvcTestResult resultado = post("/api/v1/jornadas/" + id + "/retomada");

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT);
        assertThat(resultado)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Não é possível retomar: a jornada está em andamento.");
    }

    @Test
    void concluirMarcoPorHttpEhIdempotenteEOutraRespostaResponde409() {
        UUID id = iniciarDia();
        relogioAs("09:30");
        agendador(id);
        UUID marco = primeiroMarco();

        assertThat(post("/api/v1/marcos/" + marco + "/recebimento")).hasStatus(HttpStatus.NO_CONTENT);
        relogioAs("09:31");
        var concluida = assertThat(post("/api/v1/marcos/" + marco + "/conclusao"))
                .hasStatusOk()
                .bodyJson();
        concluida.extractingPath("$.marcos[0].status").isEqualTo("CONCLUIDO");
        concluida.extractingPath("$.marcos[0].respondidoEm").isEqualTo("2026-10-02T09:31:00-03:00");
        concluida.extractingPath("$.aguaIngeridaMl").isEqualTo(187.5);

        assertThat(post("/api/v1/marcos/" + marco + "/conclusao"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.aguaIngeridaMl")
                .isEqualTo(187.5);
        assertThat(post("/api/v1/marcos/" + marco + "/falha"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("já foi encerrado como CONCLUIDO");
    }

    @Test
    void falharMarcoPendente() {
        UUID id = iniciarDia();
        relogioAs("09:30");
        agendador(id);

        assertThat(post("/api/v1/marcos/" + primeiroMarco() + "/falha"))
                .bodyJson()
                .extractingPath("$.marcos[0].status")
                .isEqualTo("FALHA");
    }

    @Test
    void jornadaOuMarcoInexistentesRespondem404() {
        assertThat(post("/api/v1/jornadas/" + UUID.randomUUID() + "/finalizacao"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Não encontrado");
        assertThat(post("/api/v1/marcos/" + UUID.randomUUID() + "/conclusao")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(post("/api/v1/marcos/" + UUID.randomUUID() + "/recebimento")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void idMalformadoResponde400EmProblemDetails() {
        assertThat(post("/api/v1/jornadas/nao-e-um-uuid/pausa"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
