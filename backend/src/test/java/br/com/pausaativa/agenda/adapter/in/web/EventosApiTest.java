package br.com.pausaativa.agenda.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.in.IniciarJornada;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Stream de eventos (spec H2, seção 7): o que cada aba conectada recebe. */
@TesteDeIntegracao
class EventosApiTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);

    @Autowired
    WebApplicationContext contexto;

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IniciarJornada iniciarJornada;

    @Autowired
    AvancarAgenda avancarAgenda;

    @Autowired
    CanalDeEventos canal;

    @Autowired
    MeterRegistry metricas;

    @Autowired
    SalvarPerfil salvarPerfil;

    MockMvc mvc;

    private final List<MvcResult> abas = new ArrayList<>();

    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        salvarPerfil.salvar(new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true));
        relogioAs("09:00");
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    /** O contexto é compartilhado: abas abertas num teste não podem receber eventos do seguinte. */
    @AfterEach
    void fecharAbas() {
        abas.forEach(this::fechar);
    }

    private void relogioAs(String hora) {
        relogio.ajustarPara(LocalDateTime.of(HOJE, LocalTime.parse(hora))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant());
    }

    private MvcResult abrirAba() throws Exception {
        MvcResult aba = mvc.perform(get("/api/v1/eventos").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        abas.add(aba);
        return aba;
    }

    private void fechar(MvcResult aba) {
        if (aba.getRequest().isAsyncStarted()) {
            aba.getRequest().getAsyncContext().complete();
        }
    }

    private static String recebido(MvcResult aba) throws Exception {
        return aba.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void conexaoAbreComoStreamDeEventosEJaRecebeUmComentario() throws Exception {
        MvcResult aba = abrirAba();

        assertThat(aba.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(recebido(aba)).contains(":conectado");
    }

    @Test
    void cenario1MarcoDisparadoChegaPeloStreamComAMensagem() throws Exception {
        MvcResult aba = abrirAba();
        iniciarJornada.iniciar(3_000, 5);

        relogioAs("09:30");
        avancarAgenda.avancar();

        String stream = recebido(aba);
        String doTick = stream.substring(stream.lastIndexOf("event:" + CanalDeEventos.MARCO_DISPARADO));
        assertThat(doTick)
                .startsWith("event:marco-disparado\ndata:{")
                .contains("\"sequencia\":1")
                .contains("\"status\":\"PENDENTE\"")
                .contains("Beba ~190 ml. Levante-se para buscar a água.");
        assertThat(doTick.indexOf("event:" + CanalDeEventos.JORNADA_ATUALIZADA))
                .as("jornada-atualizada vem depois do marco-disparado")
                .isPositive();
    }

    @Test
    void cenario2NaHoraCheiaChegamAguaEExercicioComOBlocoEDepoisAJornada() throws Exception {
        MvcResult aba = abrirAba();
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("09:30");
        avancarAgenda.avancar();
        int antesDaHoraCheia = recebido(aba).length();

        relogioAs("10:00");
        avancarAgenda.avancar();

        String doTick = recebido(aba).substring(antesDaHoraCheia);
        int agua = doTick.indexOf("\"categoria\":\"HIDRATACAO\"");
        int exercicio = doTick.indexOf("\"categoria\":\"EXERCICIO\"");
        int jornada = doTick.indexOf("event:" + CanalDeEventos.JORNADA_ATUALIZADA);
        assertThat(doTick.split("event:" + CanalDeEventos.MARCO_DISPARADO, -1)).hasSize(3);
        assertThat(agua).as("água antes do exercício").isPositive().isLessThan(exercicio);
        assertThat(exercicio).as("exercício antes da jornada").isLessThan(jornada);
        assertThat(doTick.substring(exercicio, jornada))
                .contains("\"mensagem\":\"Bloco de 5 min: 6 exercícios.\"")
                .contains("\"exercicio\":\"Sentar e levantar da cadeira\"")
                .contains("\"podeAdiar\":true");
    }

    @Test
    void todasAsAbasAbertasRecebemOMesmoMarco() throws Exception {
        MvcResult primeira = abrirAba();
        MvcResult segunda = abrirAba();
        iniciarJornada.iniciar(3_000, 5);

        relogioAs("09:30");
        avancarAgenda.avancar();

        assertThat(recebido(primeira)).contains("event:marco-disparado");
        assertThat(recebido(segunda)).contains("event:marco-disparado");
    }

    @Test
    void tickSemMudancaNaoEnviaNada() throws Exception {
        MvcResult aba = abrirAba();
        iniciarJornada.iniciar(3_000, 5);
        String antes = recebido(aba);

        relogioAs("09:10");
        avancarAgenda.avancar();

        assertThat(recebido(aba)).isEqualTo(antes);
    }

    @Test
    void abaFechadaSaiDoCanalEDaMetrica() throws Exception {
        int antes = canal.quantidadeDeConexoes();
        MvcResult aba = abrirAba();
        assertThat(canal.quantidadeDeConexoes()).isEqualTo(antes + 1);
        assertThat(metricas.get("pausaativa.sse.conexoes").gauge().value()).isEqualTo(antes + 1);

        fechar(aba);

        assertThat(canal.quantidadeDeConexoes()).isEqualTo(antes);
    }

    @Test
    void heartbeatMantemAConexaoViva() throws Exception {
        MvcResult aba = abrirAba();

        canal.manterConexoesVivas();

        assertThat(recebido(aba)).contains(":ping");
    }

    @Test
    void abaQueReconectaNaoRecebeOMarcoDeNovoEORecuperaPelaConsulta() throws Exception {
        MvcResult antesDeCair = abrirAba();
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("09:30");
        avancarAgenda.avancar();
        fechar(antesDeCair);

        MvcResult depoisDeReconectar = abrirAba();

        assertThat(recebido(depoisDeReconectar)).doesNotContain("event:marco-disparado");
        String atual = mvc.perform(get("/api/v1/jornadas/atual"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(atual).contains("\"status\":\"PENDENTE\"");
    }
}
