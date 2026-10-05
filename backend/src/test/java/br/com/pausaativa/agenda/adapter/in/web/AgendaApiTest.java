package br.com.pausaativa.agenda.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import com.jayway.jsonpath.JsonPath;
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
    AvancarAgenda avancarAgenda;

    @Autowired
    SalvarPerfil salvarPerfil;

    MockMvcTester api;

    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        salvarPerfil.salvar(new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true));
        relogioAs("09:00");
        api = MockMvcTester.from(contexto);
    }

    /** Id do marco de exercício {@code sequencia} na jornada atual. */
    private UUID exercicio(int sequencia) {
        List<String> ids = JsonPath.read(
                corpo(api.get().uri("/api/v1/jornadas/atual").exchange()),
                "$.marcos[?(@.categoria == 'EXERCICIO' && @.sequencia == " + sequencia + ")].id");
        return UUID.fromString(ids.getFirst());
    }

    private static String corpo(MvcTestResult resultado) {
        try {
            return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void relogioAs(String hora) {
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.parse(hora))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
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
        json.extractingPath("$.duracaoBlocoMin").isEqualTo(5);
        json.extractingPath("$.aguaIngeridaMl").isEqualTo(0);
        json.extractingPath("$.marcos.length()").isEqualTo(24);
        json.extractingPath("$.marcos[0].categoria").isEqualTo("HIDRATACAO");
        json.extractingPath("$.marcos[0].status").isEqualTo("AGENDADO");
        json.extractingPath("$.marcos[0].volumeMl").isEqualTo(187.5);
        json.extractingPath("$.marcos[0].volumeAproximadoMl").isEqualTo(190);
        json.extractingPath("$.marcos[0].segundosTrabalhadosPrevistos").isEqualTo(1800);
        json.extractingPath("$.marcos[0].disparadoEm").isNull();
        json.extractingPath("$.marcos[0].mensagem").isEqualTo("Beba ~190 ml. Levante-se para buscar a água.");
        json.extractingPath("$.marcos[0].podeAdiar").isEqualTo(false);
        json.extractingPath("$.marcos[0].bloco").isNull();
        // Em ordem de horário: na hora cheia, a água vem antes do exercício.
        json.extractingPath("$.marcos[1].categoria").isEqualTo("HIDRATACAO");
        json.extractingPath("$.marcos[2].categoria").isEqualTo("EXERCICIO");
        json.extractingPath("$.marcos[2].segundosTrabalhadosPrevistos").isEqualTo(3600);
        json.extractingPath("$.marcos[2].volumeMl").isNull();
        json.extractingPath("$.marcos[2].volumeAproximadoMl").isNull();
        json.extractingPath("$.marcos[2].bloco").isNull();
        json.extractingPath("$.marcos[2].mensagem").isEqualTo("Bloco de exercício.");
    }

    @Test
    void cenario6SemPerfilIniciarResponde409EPedeOPerfil() {
        jdbc.execute("truncate perfil_fisico cascade");

        MvcTestResult resultado = iniciar("{}");

        assertThat(resultado).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resultado)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Preencha o perfil físico antes de iniciar o dia: os blocos de exercício são montados"
                        + " com ele.");
        assertThat(api.get().uri("/api/v1/jornadas/atual")).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void duracaoDoBlocoDeDezMinutosEAceitaEOutraResponde400() {
        assertThat(iniciar("{\"duracaoBlocoMin\": 7}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("O bloco de exercício tem 5 ou 10 min; foi informado 7 min.");

        assertThat(iniciar("{\"duracaoBlocoMin\": 10}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.duracaoBlocoMin")
                .isEqualTo(10);
    }

    @Test
    void exercicioDisparadoTrazOBlocoMontadoParaOPerfil() {
        iniciarDia();
        relogioAs("10:00");
        avancarAgenda.avancar();

        var json = assertThat(api.get().uri("/api/v1/jornadas/atual")).bodyJson();

        json.extractingPath("$.marcos[2].status").isEqualTo("PENDENTE");
        json.extractingPath("$.marcos[2].podeAdiar").isEqualTo(true);
        json.extractingPath("$.marcos[2].mensagem").isEqualTo("Bloco de 5 min: 6 exercícios.");
        json.extractingPath("$.marcos[2].bloco.duracaoMin").isEqualTo(5);
        json.extractingPath("$.marcos[2].bloco.segundosEstimados").isEqualTo(278);
        json.extractingPath("$.marcos[2].bloco.compensaAdiamento").isEqualTo(false);
        json.extractingPath("$.marcos[2].bloco.itens.length()").isEqualTo(6);
        json.extractingPath("$.marcos[2].bloco.itens[0].exercicio").isEqualTo("Sentar e levantar da cadeira");
        json.extractingPath("$.marcos[2].bloco.itens[0].grupo").isEqualTo("Pernas");
        json.extractingPath("$.marcos[2].bloco.itens[0].quantidade").isEqualTo("10 repetições");
        json.extractingPath("$.marcos[2].bloco.itens[0].instrucao")
                .isEqualTo("Sente e levante da cadeira sem usar as mãos, com os pés na largura do quadril.");
    }

    @Test
    void cenario3AdiarPorHttpEOSeguinteTemDezMinutos() {
        iniciarDia();
        relogioAs("10:00");
        avancarAgenda.avancar();
        UUID primeiro = exercicio(1);

        var adiada = assertThat(post("/api/v1/marcos/" + primeiro + "/adiamento"))
                .hasStatusOk()
                .bodyJson();
        adiada.extractingPath("$.marcos[2].status").isEqualTo("ADIADO");
        adiada.extractingPath("$.marcos[2].podeAdiar").isEqualTo(false);
        assertThat(post("/api/v1/marcos/" + primeiro + "/adiamento")).hasStatusOk();

        relogioAs("11:00");
        avancarAgenda.avancar();
        var json = assertThat(api.get().uri("/api/v1/jornadas/atual")).bodyJson();
        json.extractingPath("$.marcos[5].sequencia").isEqualTo(2);
        json.extractingPath("$.marcos[5].bloco.duracaoMin").isEqualTo(10);
        json.extractingPath("$.marcos[5].bloco.compensaAdiamento").isEqualTo(true);
        json.extractingPath("$.marcos[5].podeAdiar").isEqualTo(false);
    }

    @Test
    void cenario5SegundoAdiamentoEAdiarAguaRespondem409ComMotivo() {
        iniciarDia();
        relogioAs("10:00");
        avancarAgenda.avancar();
        post("/api/v1/marcos/" + exercicio(1) + "/adiamento");
        relogioAs("11:00");
        avancarAgenda.avancar();

        assertThat(post("/api/v1/marcos/" + exercicio(2) + "/adiamento"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Este bloco já compensa um adiamento: conclua ou marque falha.");
        assertThat(post("/api/v1/marcos/" + primeiroMarco() + "/adiamento"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Só o bloco de exercício pode ser adiado.");
        assertThat(post("/api/v1/marcos/" + exercicio(1) + "/conclusao"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Este bloco foi adiado: ele é resolvido pelo bloco seguinte.");
        assertThat(post("/api/v1/marcos/" + UUID.randomUUID() + "/adiamento")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void cenario4ConcluirOSeguinteConcluiOAdiado() {
        iniciarDia();
        relogioAs("10:00");
        avancarAgenda.avancar();
        post("/api/v1/marcos/" + exercicio(1) + "/adiamento");
        relogioAs("11:00");
        avancarAgenda.avancar();

        var json = assertThat(post("/api/v1/marcos/" + exercicio(2) + "/conclusao"))
                .hasStatusOk()
                .bodyJson();

        json.extractingPath("$.marcos[2].status").isEqualTo("CONCLUIDO");
        json.extractingPath("$.marcos[5].status").isEqualTo("CONCLUIDO");
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
        var pausada = assertThat(post("/api/v1/jornadas/" + id + "/pausa")).bodyJson();
        pausada.extractingPath("$.status").isEqualTo("PAUSADA");
        pausada.extractingPath("$.pausadaDesde").isEqualTo("2026-10-02T12:00:00-03:00");
        relogioAs("13:00");
        var retomada = assertThat(post("/api/v1/jornadas/" + id + "/retomada")).bodyJson();
        retomada.extractingPath("$.status").isEqualTo("EM_ANDAMENTO");
        retomada.extractingPath("$.pausadaDesde").isNull();
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
        avancarAgenda.avancar();
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
        avancarAgenda.avancar();

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
