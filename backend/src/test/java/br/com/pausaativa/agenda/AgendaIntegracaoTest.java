package br.com.pausaativa.agenda;

import static br.com.pausaativa.agenda.domain.StatusMarco.AGENDADO;
import static br.com.pausaativa.agenda.domain.StatusMarco.CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.FALHA;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_ENTREGUE;
import static br.com.pausaativa.agenda.domain.StatusMarco.PENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.pausaativa.RelogioDeTeste;
import br.com.pausaativa.TesteDeIntegracao;
import br.com.pausaativa.agenda.application.JornadaNaoEncontradaException;
import br.com.pausaativa.agenda.application.port.in.AvancarAgenda;
import br.com.pausaativa.agenda.application.port.in.ConfirmarRecebimento;
import br.com.pausaativa.agenda.application.port.in.ConsultarJornadaAtual;
import br.com.pausaativa.agenda.application.port.in.FinalizarJornada;
import br.com.pausaativa.agenda.application.port.in.IniciarJornada;
import br.com.pausaativa.agenda.application.port.in.PausarJornada;
import br.com.pausaativa.agenda.application.port.in.ResponderMarco;
import br.com.pausaativa.agenda.application.port.in.RetomarJornada;
import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.application.port.in.SituacaoDoMarco;
import br.com.pausaativa.agenda.application.port.out.JornadaAlterada;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.JornadaJaIniciadaException;
import br.com.pausaativa.agenda.domain.MarcoDisparado;
import br.com.pausaativa.agenda.domain.MarcoEncerrado;
import br.com.pausaativa.agenda.domain.MarcoNaoEncontradoException;
import br.com.pausaativa.agenda.domain.MetaDeAguaInvalidaException;
import br.com.pausaativa.agenda.domain.RespostaDeMarcoRecusadaException;
import br.com.pausaativa.agenda.domain.StatusJornada;
import br.com.pausaativa.agenda.domain.StatusMarco;
import br.com.pausaativa.treino.application.port.in.SalvarPerfil;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

/** Casos de uso da Agenda com Postgres real e relógio controlado (spec H2, T2). */
@TesteDeIntegracao
@RecordApplicationEvents
class AgendaIntegracaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 2);

    @Autowired
    RelogioDeTeste relogio;

    @Autowired
    IniciarJornada iniciarJornada;

    @Autowired
    PausarJornada pausarJornada;

    @Autowired
    RetomarJornada retomarJornada;

    @Autowired
    FinalizarJornada finalizarJornada;

    @Autowired
    ConsultarJornadaAtual consultarJornadaAtual;

    @Autowired
    ResponderMarco responderMarco;

    @Autowired
    ConfirmarRecebimento confirmarRecebimento;

    @Autowired
    AvancarAgenda avancarAgenda;

    @Autowired
    JornadaRepository repositorio;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ConfigurableApplicationContext contexto;

    @Autowired
    ApplicationEvents eventosPublicados;

    @Autowired
    MeterRegistry metricas;

    @Autowired
    SalvarPerfil salvarPerfil;

    /** Sem perfil, o dia não começa (Cenário 6 da H3). */
    @BeforeEach
    void comecarDoZero() {
        jdbc.execute("truncate jornada cascade");
        salvarPerfil.salvar(new PerfilFisico(Set.of(), Nivel.INICIANTE, Set.of(), true));
        relogio.ajustarPara(as(HOJE, "09:00"));
    }

    private static Instant as(LocalDate dia, String hora) {
        return LocalDateTime.of(dia, LocalTime.parse(hora))
                .atZone(RelogioDeTeste.SAO_PAULO)
                .toInstant();
    }

    private void relogioAs(String hora) {
        relogio.ajustarPara(as(HOJE, hora));
    }

    private static SituacaoDoMarco agua(SituacaoDaJornada situacao, int sequencia) {
        return marco(situacao, Categoria.HIDRATACAO, sequencia);
    }

    private static SituacaoDoMarco marco(SituacaoDaJornada situacao, Categoria categoria, int sequencia) {
        return situacao.marcos().stream()
                .filter(marco -> marco.categoria() == categoria && marco.sequencia() == sequencia)
                .findFirst()
                .orElseThrow();
    }

    private static List<StatusMarco> statusDaAgua(SituacaoDaJornada situacao) {
        return situacao.marcos().stream()
                .filter(marco -> marco.categoria() == Categoria.HIDRATACAO)
                .map(SituacaoDoMarco::status)
                .toList();
    }

    private SituacaoDaJornada atual() {
        return consultarJornadaAtual.consultar().orElseThrow();
    }

    private int contar(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    @Test
    void iniciarGravaOsVinteEQuatroMarcosEAConsultaDevolveOMesmoEstado() {
        SituacaoDaJornada iniciada = iniciarJornada.iniciar(3_000, 5);

        assertThat(iniciada.status()).isEqualTo(StatusJornada.EM_ANDAMENTO);
        assertThat(iniciada.dataReferencia()).isEqualTo(HOJE);
        assertThat(iniciada.iniciadaEm()).isEqualTo(OffsetDateTime.parse("2026-10-02T09:00:00-03:00"));
        assertThat(iniciada.marcos())
                .hasSize(24)
                .allSatisfy(marco -> assertThat(marco.status()).isEqualTo(AGENDADO));
        assertThat(iniciada.marcos())
                .filteredOn(marco -> marco.categoria() == Categoria.HIDRATACAO)
                .hasSize(16)
                .allSatisfy(marco -> assertThat(marco.volumeMl()).isEqualByComparingTo("187.5"));
        assertThat(contar("marco")).isEqualTo(24);

        relogio.avancar(Duration.ofHours(1));
        SituacaoDaJornada consultada = atual();

        assertThat(consultada.tempoTrabalhadoSegundos()).isEqualTo(3_600);
        assertThat(consultada.marcos()).isEqualTo(iniciada.marcos());
    }

    @Test
    void pausaRetomadaEFinalizacaoFicamGravadas() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();

        relogioAs("12:00");
        assertThat(pausarJornada.pausar(id).status()).isEqualTo(StatusJornada.PAUSADA);
        relogioAs("13:00");
        assertThat(retomarJornada.retomar(id).status()).isEqualTo(StatusJornada.EM_ANDAMENTO);
        relogioAs("15:00");
        finalizarJornada.finalizar(id);

        relogioAs("18:00");
        SituacaoDaJornada finalizada = atual();
        assertThat(finalizada.status()).isEqualTo(StatusJornada.FINALIZADA);
        assertThat(finalizada.finalizadaEm()).isEqualTo(OffsetDateTime.parse("2026-10-02T15:00:00-03:00"));
        assertThat(finalizada.tempoTrabalhadoSegundos())
                .isEqualTo(Duration.ofHours(5).toSeconds());
        // Sem tick neste teste, cada comando põe a jornada em dia: os marcos que já tinham vencido disparam e
        // expiram sem recebimento; os que ainda viriam (a partir do 10º, das 15:00) ficam não concluídos.
        assertThat(statusDaAgua(finalizada).subList(0, 9)).containsOnly(NAO_ENTREGUE);
        assertThat(statusDaAgua(finalizada).subList(9, 16)).containsOnly(NAO_CONCLUIDO);
        assertThat(jdbc.queryForObject("select count(*) from pausa where fim is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void finalizarDeNovoDevolveAMesmaSituacao() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        relogioAs("10:00");
        SituacaoDaJornada primeira = finalizarJornada.finalizar(id);

        relogioAs("11:00");
        SituacaoDaJornada segunda = finalizarJornada.finalizar(id);

        assertThat(segunda.finalizadaEm()).isEqualTo(primeira.finalizadaEm());
        assertThat(segunda.tempoTrabalhadoSegundos()).isEqualTo(primeira.tempoTrabalhadoSegundos());
    }

    @Test
    void marcoDisparadoRecebidoEConcluidoFicaGravado() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        relogioAs("09:30");
        avancarAgenda.avancar();
        UUID marco1 = agua(atual(), 1).id();
        assertThat(agua(atual(), 1).status()).isEqualTo(PENDENTE);

        confirmarRecebimento.confirmar(marco1);
        relogioAs("09:31");
        SituacaoDaJornada respondida = responderMarco.concluir(marco1);

        assertThat(agua(respondida, 1).status()).isEqualTo(CONCLUIDO);
        assertThat(respondida.aguaIngeridaMl()).isEqualByComparingTo("187.5");
        SituacaoDoMarco gravado = agua(atual(), 1);
        assertThat(gravado.status()).isEqualTo(CONCLUIDO);
        assertThat(gravado.disparadoEm()).isEqualTo(OffsetDateTime.parse("2026-10-02T09:30:00-03:00"));
        assertThat(gravado.recebidoEm()).isEqualTo(OffsetDateTime.parse("2026-10-02T09:30:00-03:00"));
        assertThat(gravado.respondidoEm()).isEqualTo(OffsetDateTime.parse("2026-10-02T09:31:00-03:00"));
    }

    @Test
    void cenario6RespostaRepetidaNaoMudaNadaEOutraRespostaERecusada() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        relogioAs("09:30");
        avancarAgenda.avancar();
        UUID marco1 = agua(atual(), 1).id();
        relogioAs("09:31");
        responderMarco.concluir(marco1);

        relogioAs("09:32");
        SituacaoDaJornada repetida = responderMarco.concluir(marco1);

        assertThat(agua(repetida, 1).respondidoEm()).isEqualTo(OffsetDateTime.parse("2026-10-02T09:31:00-03:00"));
        assertThatThrownBy(() -> responderMarco.falhar(marco1)).isInstanceOf(RespostaDeMarcoRecusadaException.class);
        assertThat(agua(atual(), 1).status()).isEqualTo(CONCLUIDO);
    }

    @Test
    void umaJornadaPorDiaMesmoDepoisDeFinalizar() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();

        assertThatThrownBy(() -> iniciarJornada.iniciar(3_000, 5)).isInstanceOf(JornadaJaIniciadaException.class);
        finalizarJornada.finalizar(id);
        assertThatThrownBy(() -> iniciarJornada.iniciar(3_000, 5))
                .isInstanceOf(JornadaJaIniciadaException.class)
                .hasMessageContaining("2026-10-02");
        assertThat(contar("jornada")).isEqualTo(1);
    }

    @Test
    void iniciarODiaEncerraAJornadaEsquecidaDeOntem() {
        relogio.ajustarPara(as(HOJE.minusDays(1), "09:00"));
        UUID deOntem = iniciarJornada.iniciar(3_000, 5).id();

        relogioAs("08:00");
        SituacaoDaJornada deHoje = iniciarJornada.iniciar(2_000, 5);

        assertThat(deHoje.dataReferencia()).isEqualTo(HOJE);
        assertThat(deHoje.metaAguaMl()).isEqualTo(2_000);
        SituacaoDaJornada ontem = transacao.execute(status -> repositorio
                .buscarComBloqueio(deOntem)
                .map(jornada -> SituacaoDaJornada.de(jornada, relogio.instant(), RelogioDeTeste.SAO_PAULO))
                .orElseThrow());
        assertThat(ontem.status()).isEqualTo(StatusJornada.ENCERRADA_AUTOMATICAMENTE);
        assertThat(ontem.marcos()).extracting(SituacaoDoMarco::status).containsOnly(NAO_ENTREGUE);
    }

    @Test
    void jornadaQueAtravessaAMeiaNoiteImpedeIniciarOutra() {
        relogioAs("22:00");
        iniciarJornada.iniciar(3_000, 5);

        relogio.ajustarPara(as(HOJE.plusDays(1), "00:30"));

        assertThatThrownBy(() -> iniciarJornada.iniciar(3_000, 5)).isInstanceOf(JornadaJaIniciadaException.class);
    }

    @Test
    void cenario8JornadaEsquecidaEEncerradaNaSubidaDoBackend() {
        relogio.ajustarPara(as(HOJE.minusDays(1), "09:00"));
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        relogio.ajustarPara(as(HOJE.minusDays(1), "09:30"));
        avancarAgenda.avancar();
        confirmarRecebimento.confirmar(agua(atual(), 1).id());

        relogioAs("08:00");
        contexto.publishEvent(
                new ApplicationReadyEvent(new SpringApplication(), new String[0], contexto, Duration.ZERO));

        assertThat(consultarJornadaAtual.consultar()).isEmpty();
        assertThat(jdbc.queryForObject("select status from jornada where id = ?", String.class, id))
                .isEqualTo("ENCERRADA_AUTOMATICAMENTE");
        assertThat(jdbc.queryForObject(
                        "select status from marco where jornada_id = ? and categoria = 'HIDRATACAO' and sequencia = 1",
                        String.class,
                        id))
                .isEqualTo(FALHA.name());
        // As outras 15 de água e as 8 de exercício.
        assertThat(jdbc.queryForObject(
                        "select count(*) from marco where jornada_id = ? and status = 'NAO_ENTREGUE'",
                        Integer.class,
                        id))
                .isEqualTo(23);
    }

    @Test
    void reinicioNoMesmoDiaMarcaComoNaoEntregueOQueVenceuComOBackendFora() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        relogioAs("09:30");
        avancarAgenda.avancar();
        confirmarRecebimento.confirmar(agua(atual(), 1).id());

        relogioAs("10:45");
        contexto.publishEvent(
                new ApplicationReadyEvent(new SpringApplication(), new String[0], contexto, Duration.ZERO));

        SituacaoDaJornada situacao = atual();
        assertThat(situacao.status()).isEqualTo(StatusJornada.EM_ANDAMENTO);
        assertThat(statusDaAgua(situacao).subList(0, 4)).containsExactly(FALHA, NAO_ENTREGUE, NAO_ENTREGUE, AGENDADO);
        assertThat(marco(situacao, Categoria.EXERCICIO, 1).status()).isEqualTo(NAO_ENTREGUE);
        assertThat(marco(situacao, Categoria.EXERCICIO, 1).bloco()).isNull();
    }

    @Test
    void doisIniciosSimultaneosGravamUmaJornadaSo() throws Exception {
        List<Object> resultados = emParalelo(2, () -> iniciarJornada.iniciar(3_000, 5));

        assertThat(resultados).filteredOn(SituacaoDaJornada.class::isInstance).hasSize(1);
        assertThat(resultados)
                .filteredOn(JornadaJaIniciadaException.class::isInstance)
                .hasSize(1);
        assertThat(contar("jornada")).isEqualTo(1);
        assertThat(contar("marco")).isEqualTo(24);
    }

    @Test
    void duasConclusoesSimultaneasDoMesmoMarcoSaoSerializadasPeloBloqueio() throws Exception {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        relogioAs("09:30");
        avancarAgenda.avancar();
        UUID marco1 = agua(atual(), 1).id();

        List<Object> resultados = emParalelo(2, () -> responderMarco.concluir(marco1));

        assertThat(resultados).allSatisfy(resultado -> assertThat(resultado).isInstanceOf(SituacaoDaJornada.class));
        assertThat(resultados)
                .extracting(resultado -> agua((SituacaoDaJornada) resultado, 1).status())
                .containsOnly(CONCLUIDO);
        assertThat(atual().aguaIngeridaMl()).isEqualByComparingTo("187.5");
    }

    @Test
    void bancoRecusaMarcoDuplicadoESegundaJornadaAberta() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();

        assertThatThrownBy(() -> jdbc.update("""
                        insert into marco (id, jornada_id, categoria, sequencia, status,
                            segundos_trabalhados_previstos, segundos_trabalhados_limite, volume_ml)
                        values (?, ?, 'HIDRATACAO', 1, 'AGENDADO', 1800, 3600, 187.5)
                        """, UUID.randomUUID(), id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_marco_sequencia");
        assertThatThrownBy(() -> jdbc.update("""
                        insert into jornada (id, versao, data_referencia, status, meta_agua_ml,
                            duracao_bloco_min, iniciada_em)
                        values (?, 0, date '2026-10-03', 'EM_ANDAMENTO', 3000, 5, now())
                        """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_jornada_aberta");
    }

    @Test
    void metaInvalidaNaoGravaNada() {
        assertThatThrownBy(() -> iniciarJornada.iniciar(0, 5)).isInstanceOf(MetaDeAguaInvalidaException.class);
        assertThatThrownBy(() -> iniciarJornada.iniciar(6_001, 5)).isInstanceOf(MetaDeAguaInvalidaException.class);
        assertThat(contar("jornada")).isZero();
    }

    @Test
    void jornadaOuMarcoInexistentesSaoRecusados() {
        assertThatThrownBy(() -> finalizarJornada.finalizar(UUID.randomUUID()))
                .isInstanceOf(JornadaNaoEncontradaException.class);
        assertThatThrownBy(() -> responderMarco.concluir(UUID.randomUUID()))
                .isInstanceOf(MarcoNaoEncontradoException.class);
        assertThat(consultarJornadaAtual.consultar()).isEmpty();
    }

    @Test
    void tickEncerraSozinhoAJornadaEsquecidaQuandoODiaVira() {
        relogio.ajustarPara(as(HOJE.minusDays(1), "09:00"));
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        // Até as 18:00, quando vence o 8º exercício (D6 da H3) e não sobra marco em aberto.
        for (int meiaHora = 1; meiaHora <= 18; meiaHora++) {
            relogio.avancar(Duration.ofMinutes(30));
            avancarAgenda.avancar();
        }
        assertThat(atual().status()).isEqualTo(StatusJornada.EM_ANDAMENTO);

        relogio.ajustarPara(as(HOJE, "00:00:01"));
        avancarAgenda.avancar();

        assertThat(consultarJornadaAtual.consultar()).isEmpty();
        assertThat(jdbc.queryForObject("select status from jornada where id = ?", String.class, id))
                .isEqualTo("ENCERRADA_AUTOMATICAMENTE");
    }

    @Test
    void comandoPoeAJornadaEmDiaAntesDeAgir() {
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("09:30");
        avancarAgenda.avancar();
        UUID marco1 = agua(atual(), 1).id();

        // Prazo venceu às 10:00, mas o tick ainda não rodou: concluir agora seria aceitar uma resposta atrasada.
        relogioAs("10:00:00.4");

        assertThatThrownBy(() -> responderMarco.concluir(marco1))
                .isInstanceOf(RespostaDeMarcoRecusadaException.class)
                .hasMessageContaining("NAO_ENTREGUE");
    }

    @Test
    void tickPublicaOsEventosDosMarcosESemMudancaNaoPublicaNada() {
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("09:30");
        eventosPublicados.clear();

        avancarAgenda.avancar();
        relogioAs("09:30:01");
        avancarAgenda.avancar();

        assertThat(eventosPublicados.stream(JornadaAlterada.class))
                .singleElement()
                .satisfies(alteracao -> {
                    assertThat(alteracao.situacao().marcos().getFirst().status())
                            .isEqualTo(PENDENTE);
                    assertThat(alteracao.eventos())
                            .singleElement()
                            .isInstanceOfSatisfying(
                                    MarcoDisparado.class,
                                    disparado ->
                                            assertThat(disparado.sequencia()).isEqualTo(1));
                });
    }

    @Test
    void respostaRepetidaNaoPublicaNovaAlteracao() {
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("09:30");
        avancarAgenda.avancar();
        UUID marco1 = agua(atual(), 1).id();
        responderMarco.concluir(marco1);
        eventosPublicados.clear();

        responderMarco.concluir(marco1);

        assertThat(eventosPublicados.stream(JornadaAlterada.class)).isEmpty();
    }

    @Test
    void metricasContamDisparosEncerramentosEAtraso() {
        double disparadosAntes = contador("pausaativa.marcos.disparados");
        double falhasAntes = contador("pausaativa.marcos.encerrados", "status", "FALHA");
        long atrasosAntes = atrasosRegistrados();
        iniciarJornada.iniciar(3_000, 5);
        relogioAs("09:30:02");
        avancarAgenda.avancar();
        confirmarRecebimento.confirmar(agua(atual(), 1).id());

        relogioAs("10:00");
        avancarAgenda.avancar();

        // Água 1 às 09:30; água 2 e exercício 1 às 10:00.
        assertThat(contador("pausaativa.marcos.disparados")).isEqualTo(disparadosAntes + 3);
        assertThat(contador("pausaativa.marcos.encerrados", "status", "FALHA")).isEqualTo(falhasAntes + 1);
        assertThat(atrasosRegistrados()).isEqualTo(atrasosAntes + 3);
    }

    private long atrasosRegistrados() {
        return metricas.find("pausaativa.marcos.atraso.disparo").timers().stream()
                .mapToLong(timer -> timer.count())
                .sum();
    }

    private double contador(String nome, String... tags) {
        var busca = metricas.find(nome).tags(tags).counters();
        return busca.stream().mapToDouble(contador -> contador.count()).sum();
    }

    @Test
    void encerramentosDoFinalizarSaoPublicadosComoEventos() {
        UUID id = iniciarJornada.iniciar(3_000, 5).id();
        eventosPublicados.clear();

        finalizarJornada.finalizar(id);

        assertThat(eventosPublicados.stream(JornadaAlterada.class))
                .singleElement()
                .satisfies(alteracao -> assertThat(alteracao.eventos())
                        .hasSize(24)
                        .allSatisfy(evento -> assertThat(evento).isInstanceOf(MarcoEncerrado.class)));
    }

    /** Dispara a mesma tarefa em várias threads ao mesmo tempo; exceções viram resultado. */
    private static List<Object> emParalelo(int quantidade, Callable<?> tarefa) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(quantidade);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                futuros.add(threads.submit(() -> {
                    largada.await();
                    try {
                        return tarefa.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            largada.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            threads.shutdownNow();
        }
    }
}
