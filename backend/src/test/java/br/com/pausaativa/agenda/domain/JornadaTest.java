package br.com.pausaativa.agenda.domain;

import static br.com.pausaativa.agenda.domain.StatusMarco.AGENDADO;
import static br.com.pausaativa.agenda.domain.StatusMarco.CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.FALHA;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_ENTREGUE;
import static br.com.pausaativa.agenda.domain.StatusMarco.PENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regras da jornada (spec H2, seção 3; spec H3, seções 3.4 a 3.6), com o tempo controlado pelo teste.
 * A jornada é a da v0.3.0: 16 marcos de água e 8 de exercício.
 */
class JornadaTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 2);
    private static final PlanoDeMarcos HIDRATACAO = PlanoDeMarcos.hidratacao(PlanoDeMarcos.INTERVALO_PADRAO);
    private static final PlanoDeMarcos EXERCICIO = PlanoDeMarcos.exercicio(PlanoDeMarcos.INTERVALO_PADRAO);

    /** Horário de parede em São Paulo, no dia do teste. */
    private static Instant as(String hora) {
        return LocalDateTime.of(DIA, LocalTime.parse(hora)).atZone(SAO_PAULO).toInstant();
    }

    private static Jornada iniciadaAs(String hora) {
        return iniciadaAs(hora, DuracaoDoBloco.PADRAO);
    }

    private static Jornada iniciadaAs(String hora, DuracaoDoBloco duracao) {
        return Jornada.iniciar(
                UUID.randomUUID(), as(hora), SAO_PAULO, MetaDeAgua.PADRAO, duracao, List.of(HIDRATACAO, EXERCICIO));
    }

    private static Marco agua(Jornada jornada, int sequencia) {
        return marco(jornada, Categoria.HIDRATACAO, sequencia);
    }

    private static Marco exercicio(Jornada jornada, int sequencia) {
        return marco(jornada, Categoria.EXERCICIO, sequencia);
    }

    private static Marco marco(Jornada jornada, Categoria categoria, int sequencia) {
        return jornada.marcos().stream()
                .filter(marco -> marco.categoria() == categoria && marco.sequencia() == sequencia)
                .findFirst()
                .orElseThrow();
    }

    private static List<Marco> daCategoria(Jornada jornada, Categoria categoria) {
        return jornada.marcos().stream()
                .filter(marco -> marco.categoria() == categoria)
                .toList();
    }

    /** Exercício como o Treino devolveria, com a estimativa em segundos. */
    private static ExercicioProposto proposto(String codigo, int segundos) {
        return new ExercicioProposto(
                codigo, "Exercício " + codigo, "Pernas", "10 repetições", Duration.ofSeconds(segundos), "Faça assim.");
    }

    /** Faz o papel do agendador: avança de segundo em segundo e devolve os marcos disparados. */
    private static List<Marco> agendadorAte(Jornada jornada, Instant de, Instant ate) {
        List<Marco> disparados = new ArrayList<>();
        for (Instant agora = de; !agora.isAfter(ate); agora = agora.plusSeconds(1)) {
            disparados.addAll(jornada.avancar(agora).disparados());
        }
        return disparados;
    }

    @Nested
    class Inicio {

        @Test
        void agendaDezesseisMarcosDeAguaACadaTrintaMinutosEOitoDeExercicioACadaSessenta() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(jornada.status()).isEqualTo(StatusJornada.EM_ANDAMENTO);
            assertThat(jornada.dataReferencia()).isEqualTo(DIA);
            assertThat(jornada.duracaoDoBloco()).isEqualTo(DuracaoDoBloco.CINCO_MINUTOS);
            assertThat(jornada.marcos())
                    .hasSize(24)
                    .allSatisfy(marco -> assertThat(marco.status()).isEqualTo(AGENDADO));
            assertThat(daCategoria(jornada, Categoria.HIDRATACAO))
                    .hasSize(16)
                    .allSatisfy(marco -> assertThat(marco.volumeMl()).isEqualByComparingTo("187.5"));
            assertThat(daCategoria(jornada, Categoria.EXERCICIO)).hasSize(8).allSatisfy(marco -> {
                assertThat(marco.volumeMl()).isNull();
                assertThat(marco.bloco()).isEmpty();
            });
            assertThat(agua(jornada, 1).tempoTrabalhadoPrevisto()).isEqualTo(Duration.ofMinutes(30));
            assertThat(agua(jornada, 16).tempoTrabalhadoPrevisto()).isEqualTo(Duration.ofHours(8));
            assertThat(exercicio(jornada, 1).tempoTrabalhadoPrevisto()).isEqualTo(Duration.ofHours(1));
            assertThat(exercicio(jornada, 8).tempoTrabalhadoPrevisto()).isEqualTo(Duration.ofHours(8));
            assertThat(exercicio(jornada, 8).tempoTrabalhadoLimite()).isEqualTo(Duration.ofHours(9));
        }

        @Test
        void jornadaCompletaDisparaOsVinteEQuatroMarcosNosInstantesCertosENadaDepoisDasOitoHoras() {
            Jornada jornada = iniciadaAs("09:00");

            List<Marco> disparados = agendadorAte(jornada, as("09:00"), as("18:30"));

            assertThat(disparados).hasSize(24);
            assertThat(disparados)
                    .filteredOn(marco -> marco.categoria() == Categoria.EXERCICIO)
                    .extracting(marco -> marco.disparadoEm().orElseThrow())
                    .containsExactly(
                            as("10:00"),
                            as("11:00"),
                            as("12:00"),
                            as("13:00"),
                            as("14:00"),
                            as("15:00"),
                            as("16:00"),
                            as("17:00"));
            assertThat(disparados)
                    .filteredOn(marco -> marco.categoria() == Categoria.HIDRATACAO)
                    .extracting(marco -> marco.disparadoEm().orElseThrow())
                    .containsExactly(
                            as("09:30"),
                            as("10:00"),
                            as("10:30"),
                            as("11:00"),
                            as("11:30"),
                            as("12:00"),
                            as("12:30"),
                            as("13:00"),
                            as("13:30"),
                            as("14:00"),
                            as("14:30"),
                            as("15:00"),
                            as("15:30"),
                            as("16:00"),
                            as("16:30"),
                            as("17:00"));
        }

        @Test
        void mensagemPedeOVolumeArredondadoEOsMarcosDaMeiaHoraPedemParaLevantar() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(agua(jornada, 1).mensagem()).isEqualTo("Beba ~190 ml. Levante-se para buscar a água.");
            assertThat(agua(jornada, 2).mensagem()).isEqualTo("Beba ~190 ml.");
        }

        @Test
        void metaForaDoIntervaloDeUmASeisMilMlERecusada() {
            assertThat(new MetaDeAgua(1).mililitros()).isEqualTo(1);
            assertThat(new MetaDeAgua(6_000).mililitros()).isEqualTo(6_000);
            for (int invalida : new int[] {0, -1, 6_001}) {
                assertThatThrownBy(() -> new MetaDeAgua(invalida))
                        .isInstanceOf(MetaDeAguaInvalidaException.class)
                        .hasMessageContaining("entre 1 e 6000 ml");
            }
        }
    }

    @Nested
    class Disparo {

        @Test
        void cenario1MarcoDisparaAosTrintaMinutosEFicaPendente() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(jornada.avancar(as("09:29:59")).disparados()).isEmpty();
            Avanco avanco = jornada.avancar(as("09:30"));

            assertThat(avanco.disparados()).containsExactly(agua(jornada, 1));
            assertThat(avanco.houveMudanca()).isTrue();
            assertThat(agua(jornada, 1).status()).isEqualTo(PENDENTE);
            assertThat(agua(jornada, 1).previstoPara()).contains(as("09:30"));
        }

        @Test
        void tickAtrasadoRegistraOInstantePrevistoParaMedirOAtraso() {
            Jornada jornada = iniciadaAs("09:00");

            jornada.avancar(as("09:30:02"));

            assertThat(agua(jornada, 1).previstoPara()).contains(as("09:30"));
            assertThat(agua(jornada, 1).disparadoEm()).contains(as("09:30:02"));
        }

        @Test
        void cenario3PausaDeAlmocoDeslocaOProximoMarcoPeloTempoPausado() {
            Jornada jornada = iniciadaAs("08:40");
            agendadorAte(jornada, as("08:40"), as("12:00"));
            assertThat(agua(jornada, 6).disparadoEm()).contains(as("11:40"));

            jornada.pausar(as("12:00"));
            assertThat(agendadorAte(jornada, as("12:00:01"), as("12:59:59"))).isEmpty();
            jornada.retomar(as("13:00"));

            assertThat(agendadorAte(jornada, as("13:00"), as("13:09:59"))).isEmpty();
            assertThat(jornada.avancar(as("13:10")).disparados()).containsExactly(agua(jornada, 7));
            assertThat(agua(jornada, 7).previstoPara()).contains(as("13:10"));
        }

        @Test
        void tempoTrabalhadoCongelaDuranteAPausaEDescontaVariasPausas() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.pausar(as("10:00"));
            jornada.retomar(as("10:15"));
            jornada.pausar(as("12:00"));

            assertThat(jornada.tempoTrabalhado(as("12:30"))).isEqualTo(Duration.ofMinutes(165));
            jornada.retomar(as("13:00"));
            assertThat(jornada.tempoTrabalhado(as("14:00"))).isEqualTo(Duration.ofMinutes(225));
        }

        @Test
        void pausadaDesdeInformaOInicioDaPausaEmCursoESoDela() {
            Jornada jornada = iniciadaAs("09:00");
            assertThat(jornada.pausadaDesde()).isEmpty();

            jornada.pausar(as("10:00"));
            jornada.retomar(as("10:15"));
            assertThat(jornada.pausadaDesde()).isEmpty();

            jornada.pausar(as("12:00"));
            assertThat(jornada.pausadaDesde()).contains(as("12:00"));
        }

        @Test
        void jornadaQueAtravessaAMeiaNoiteContinuaDisparandoEPertenceAoDiaEmQueComecou() {
            Jornada jornada = iniciadaAs("22:00");
            Instant meiaNoiteEMeia = as("00:30").plus(Duration.ofDays(1));

            List<Marco> disparados = agendadorAte(jornada, as("22:00"), meiaNoiteEMeia);

            assertThat(disparados).hasSize(7); // 5 de água e 2 de exercício
            assertThat(agua(jornada, 5).disparadoEm()).contains(meiaNoiteEMeia);
            assertThat(jornada.dataReferencia()).isEqualTo(DIA);
        }
    }

    @Nested
    class Resposta {

        @Test
        void cenario2ConcluirSomaOVolumeDoMarcoAAguaDoDia() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));

            boolean mudou = jornada.concluirMarco(agua(jornada, 1).id(), as("09:31"));

            assertThat(mudou).isTrue();
            assertThat(agua(jornada, 1).status()).isEqualTo(CONCLUIDO);
            assertThat(agua(jornada, 1).respondidoEm()).contains(as("09:31"));
            assertThat(jornada.aguaIngeridaMl()).isEqualByComparingTo("187.5");
        }

        @Test
        void cenario6RepetirAMesmaRespostaNaoMudaNada() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            UUID id = agua(jornada, 1).id();
            jornada.concluirMarco(id, as("09:31"));

            boolean mudou = jornada.concluirMarco(id, as("09:32"));

            assertThat(mudou).isFalse();
            assertThat(agua(jornada, 1).respondidoEm()).contains(as("09:31"));
            assertThat(jornada.aguaIngeridaMl()).isEqualByComparingTo("187.5");
        }

        @Test
        void outraRespostaAMarcoEncerradoERecusadaComMensagemClara() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            UUID id = agua(jornada, 1).id();
            jornada.concluirMarco(id, as("09:31"));

            assertThatThrownBy(() -> jornada.falharMarco(id, as("09:32")))
                    .isInstanceOf(RespostaDeMarcoRecusadaException.class)
                    .hasMessageContaining("já foi encerrado como CONCLUIDO")
                    .hasMessageContaining("v0.4.0");
        }

        @Test
        void responderMarcoQueAindaNaoDisparouERecusado() {
            Jornada jornada = iniciadaAs("09:00");

            assertThatThrownBy(() -> jornada.concluirMarco(agua(jornada, 1).id(), as("09:10")))
                    .isInstanceOf(RespostaDeMarcoRecusadaException.class)
                    .hasMessage("O lembrete ainda não disparou.");
        }

        @Test
        void marcoDeOutraJornadaNaoEEncontrado() {
            Jornada jornada = iniciadaAs("09:00");

            assertThatThrownBy(() -> jornada.concluirMarco(UUID.randomUUID(), as("09:31")))
                    .isInstanceOf(MarcoNaoEncontradoException.class);
        }

        @Test
        void podeResponderDuranteAPausa() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.pausar(as("09:40"));

            jornada.concluirMarco(agua(jornada, 1).id(), as("09:50"));

            assertThat(agua(jornada, 1).status()).isEqualTo(CONCLUIDO);
        }

        @Test
        void confirmarRecebimentoUmaVezSoEApenasDeMarcoPendente() {
            Jornada jornada = iniciadaAs("09:00");
            UUID id = agua(jornada, 1).id();
            assertThat(jornada.confirmarRecebimento(id, as("09:10"))).isFalse();

            jornada.avancar(as("09:30"));

            assertThat(jornada.confirmarRecebimento(id, as("09:30:01"))).isTrue();
            assertThat(jornada.confirmarRecebimento(id, as("09:30:05"))).isFalse();
            assertThat(agua(jornada, 1).recebidoEm()).contains(as("09:30:01"));
        }
    }

    @Nested
    class Prazo {

        @Test
        void cenario4SemRespostaComRecebimentoViraFalhaQuandoDisparaOSeguinte() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.confirmarRecebimento(agua(jornada, 1).id(), as("09:30:01"));

            jornada.avancar(as("10:00"));

            assertThat(agua(jornada, 1).status()).isEqualTo(FALHA);
            assertThat(agua(jornada, 2).status()).isEqualTo(PENDENTE);
        }

        @Test
        void cenario5SemRecebimentoConfirmadoViraNaoEntregue() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));

            jornada.avancar(as("10:00"));

            assertThat(agua(jornada, 1).status()).isEqualTo(NAO_ENTREGUE);
        }

        @Test
        void prazoCongelaDuranteAPausa() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.confirmarRecebimento(agua(jornada, 1).id(), as("09:30:01"));
            jornada.pausar(as("09:45"));
            jornada.retomar(as("10:45"));

            jornada.avancar(as("10:59:59"));
            assertThat(agua(jornada, 1).status()).isEqualTo(PENDENTE);
            jornada.avancar(as("11:00"));
            assertThat(agua(jornada, 1).status()).isEqualTo(FALHA);
        }

        @Test
        void ultimoMarcoTambemTemPrazoDeTrintaMinutos() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("17:00"));
            jornada.confirmarRecebimento(agua(jornada, 16).id(), as("17:00:01"));

            jornada.avancar(as("17:29:59"));
            assertThat(agua(jornada, 16).status()).isEqualTo(PENDENTE);
            jornada.avancar(as("17:30"));
            assertThat(agua(jornada, 16).status()).isEqualTo(FALHA);
        }
    }

    @Nested
    class Transicoes {

        @Test
        void pausarJornadaPausadaERecusado() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.pausar(as("12:00"));

            assertThatThrownBy(() -> jornada.pausar(as("12:01")))
                    .isInstanceOf(TransicaoDeJornadaInvalidaException.class)
                    .hasMessage("Não é possível pausar: a jornada está pausada.");
        }

        @Test
        void retomarJornadaQueNaoEstaPausadaERecusado() {
            Jornada jornada = iniciadaAs("09:00");

            assertThatThrownBy(() -> jornada.retomar(as("12:00")))
                    .isInstanceOf(TransicaoDeJornadaInvalidaException.class)
                    .hasMessage("Não é possível retomar: a jornada está em andamento.");
        }

        @Test
        void jornadaFinalizadaNaoPausaNemDisparaMarcos() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.finalizar(as("09:10"));

            assertThat(jornada.avancar(as("09:30"))).isEqualTo(Avanco.NENHUM);
            assertThatThrownBy(() -> jornada.pausar(as("09:40")))
                    .isInstanceOf(TransicaoDeJornadaInvalidaException.class)
                    .hasMessageContaining("finalizada");
        }
    }

    @Nested
    class Finalizacao {

        @Test
        void cenario7FinalizarAntesDasOitoHorasDeixaAgendadosEPendentesComoNaoConcluido() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("09:31"));
            jornada.concluirMarco(agua(jornada, 1).id(), as("09:31"));
            agendadorAte(jornada, as("09:31:01"), as("14:00"));
            jornada.confirmarRecebimento(agua(jornada, 10).id(), as("14:00:01"));

            boolean mudou = jornada.finalizar(as("14:05"));

            assertThat(mudou).isTrue();
            assertThat(jornada.status()).isEqualTo(StatusJornada.FINALIZADA);
            assertThat(jornada.finalizadaEm()).contains(as("14:05"));
            assertThat(agua(jornada, 1).status()).isEqualTo(CONCLUIDO);
            assertThat(agua(jornada, 10).status()).isEqualTo(NAO_CONCLUIDO);
            assertThat(jornada.marcos().subList(10, 16))
                    .extracting(Marco::status)
                    .containsOnly(NAO_CONCLUIDO);
            assertThat(jornada.tempoTrabalhado(as("18:00"))).isEqualTo(Duration.ofMinutes(305));
        }

        @Test
        void prazoJaVencidoNaHoraDeFinalizarContaComoFalha() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.confirmarRecebimento(agua(jornada, 1).id(), as("09:30:01"));

            jornada.finalizar(as("10:00:00.5"));

            assertThat(agua(jornada, 1).status()).isEqualTo(FALHA);
        }

        @Test
        void finalizarDuranteAPausaEncerraAPausa() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.pausar(as("12:00"));

            jornada.finalizar(as("13:00"));

            assertThat(jornada.pausas())
                    .singleElement()
                    .satisfies(pausa -> assertThat(pausa.fim()).contains(as("13:00")));
            assertThat(jornada.tempoTrabalhado(as("15:00"))).isEqualTo(Duration.ofHours(3));
        }

        @Test
        void finalizarDeNovoNaoMudaNada() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.finalizar(as("10:00"));

            assertThat(jornada.finalizar(as("11:00"))).isFalse();
            assertThat(jornada.finalizadaEm()).contains(as("10:00"));
        }
    }

    @Nested
    class Reconciliacao {

        @Test
        void cenario8JornadaDeOntemComOBackendForaDoArEEncerradaNaSubida() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("10:00"));
            jornada.confirmarRecebimento(agua(jornada, 2).id(), as("10:00:01"));
            // Backend fora do ar das 10:00:01 até amanhã às 08:00. Na subida: reconcilia e encerra.
            Instant amanha = as("08:00").plus(Duration.ofDays(1));

            jornada.reconciliar(amanha);
            assertThat(jornada.esquecida(amanha, SAO_PAULO)).isTrue();
            boolean mudou = jornada.encerrarAutomaticamente(amanha);

            assertThat(mudou).isTrue();
            assertThat(jornada.status()).isEqualTo(StatusJornada.ENCERRADA_AUTOMATICAMENTE);
            assertThat(agua(jornada, 1).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(agua(jornada, 2).status()).isEqualTo(FALHA);
            assertThat(jornada.marcos().subList(2, 16))
                    .extracting(Marco::status)
                    .containsOnly(NAO_ENTREGUE);
            assertThat(jornada.esquecida(amanha, SAO_PAULO)).isFalse();
        }

        @Test
        void jornadaSemMarcosPelaFrenteViraEsquecidaQuandoODiaVira() {
            Jornada jornada = iniciadaAs("09:00");
            // O 8º exercício vence às 9 h trabalhadas (D6 da H3): 18:00.
            agendadorAte(jornada, as("09:00"), as("18:00"));

            assertThat(jornada.esquecida(as("23:59:59"), SAO_PAULO)).isFalse();
            assertThat(jornada.esquecida(as("00:00").plus(Duration.ofDays(1)), SAO_PAULO))
                    .isTrue();
        }

        @Test
        void jornadaPausadaDesdeOntemEEsquecidaMesmoComMarcosPelaFrente() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.pausar(as("12:00"));
            Instant amanha = as("08:00").plus(Duration.ofDays(1));

            assertThat(jornada.esquecida(amanha, SAO_PAULO)).isTrue();
            jornada.encerrarAutomaticamente(amanha);

            assertThat(agua(jornada, 16).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(jornada.pausas())
                    .singleElement()
                    .satisfies(pausa -> assertThat(pausa.fim()).contains(amanha));
        }

        @Test
        void jornadaQueAtravessaAMeiaNoiteComMarcosPelaFrenteNaoEEsquecida() {
            Jornada jornada = iniciadaAs("22:00");
            Instant meiaNoiteEMeia = as("00:30").plus(Duration.ofDays(1));
            agendadorAte(jornada, as("22:00"), meiaNoiteEMeia);

            assertThat(jornada.esquecida(meiaNoiteEMeia, SAO_PAULO)).isFalse();
        }

        @Test
        void marcosQueVenceramComOBackendForaDoArNaoDisparamAtrasados() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("10:10"));
            jornada.confirmarRecebimento(agua(jornada, 2).id(), as("10:00:01"));
            // Backend fora do ar das 10:10 às 11:05.

            boolean mudou = jornada.reconciliar(as("11:05"));

            assertThat(mudou).isTrue();
            assertThat(agua(jornada, 2).status()).isEqualTo(FALHA);
            assertThat(agua(jornada, 3).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(agua(jornada, 4).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(agua(jornada, 5).status()).isEqualTo(AGENDADO);
            assertThat(jornada.avancar(as("11:30")).disparados()).containsExactly(agua(jornada, 5));
        }

        @Test
        void reinicioSemNadaVencidoNaoMudaAJornada() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(jornada.reconciliar(as("09:10"))).isFalse();
            assertThat(jornada.encerrarAutomaticamente(as("09:10"))).isTrue();
            assertThat(jornada.reconciliar(as("09:20"))).isFalse();
        }
    }

    @Nested
    class Eventos {

        @Test
        void disparoRegistraOInstantePrevistoEOAtraso() {
            Jornada jornada = iniciadaAs("09:00");

            jornada.avancar(as("09:30:02"));

            assertThat(jornada.extrairEventos())
                    .singleElement()
                    .isInstanceOfSatisfying(MarcoDisparado.class, evento -> {
                        assertThat(evento.marcoId()).isEqualTo(agua(jornada, 1).id());
                        assertThat(evento.sequencia()).isEqualTo(1);
                        assertThat(evento.atraso()).isEqualTo(Duration.ofSeconds(2));
                    });
        }

        @Test
        void encerramentosRegistramOStatusFinalDeCadaMarco() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.concluirMarco(agua(jornada, 1).id(), as("09:31"));
            jornada.avancar(as("10:00"));
            jornada.avancar(as("10:30"));
            jornada.extrairEventos();

            jornada.finalizar(as("10:40"));

            assertThat(jornada.extrairEventos())
                    .hasSize(22) // 14 de água e os 8 de exercício
                    .allSatisfy(evento -> assertThat(evento)
                            .isInstanceOfSatisfying(
                                    MarcoEncerrado.class,
                                    encerrado -> assertThat(encerrado.status()).isEqualTo(NAO_CONCLUIDO)));
        }

        @Test
        void respostaEPrazoVencidoGeramEncerramentoERepeticaoNaoGeraNada() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.concluirMarco(agua(jornada, 1).id(), as("09:31"));
            jornada.concluirMarco(agua(jornada, 1).id(), as("09:32"));
            jornada.avancar(as("10:00"));
            jornada.avancar(as("10:30"));

            assertThat(jornada.extrairEventos())
                    .filteredOn(evento -> evento.categoria() == Categoria.HIDRATACAO)
                    .extracting(evento -> evento.getClass().getSimpleName() + ":"
                            + switch (evento) {
                                case MarcoDisparado disparado -> disparado.sequencia();
                                case MarcoEncerrado encerrado -> encerrado.sequencia() + ":" + encerrado.status();
                            })
                    .containsExactly(
                            "MarcoDisparado:1",
                            "MarcoEncerrado:1:CONCLUIDO",
                            "MarcoDisparado:2",
                            "MarcoEncerrado:2:NAO_ENTREGUE",
                            "MarcoDisparado:3");
            assertThat(jornada.extrairEventos()).isEmpty();
        }

        @Test
        void reconciliacaoEEncerramentoAutomaticoRegistramOsNaoEntregues() {
            Jornada jornada = iniciadaAs("09:00");

            jornada.reconciliar(as("10:05"));
            assertThat(jornada.extrairEventos()).hasSize(3); // água 1 e 2, exercício 1
            jornada.encerrarAutomaticamente(as("08:00").plus(Duration.ofDays(1)));
            assertThat(jornada.extrairEventos()).hasSize(21); // água 3 a 16, exercício 2 a 8
        }
    }

    @Test
    void intervaloZeroOuNegativoEQuantidadeNaoPositivaSaoRecusados() {
        assertThatThrownBy(() -> PlanoDeMarcos.hidratacao(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalo");
        assertThatThrownBy(() -> PlanoDeMarcos.hidratacao(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlanoDeMarcos(Categoria.HIDRATACAO, Duration.ofMinutes(30), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantidade");
    }

    @Test
    void metaDiferenteDistribuiOVolumeIgualmenteEntreOsMarcosDeAgua() {
        Jornada jornada = Jornada.iniciar(
                UUID.randomUUID(),
                as("09:00"),
                SAO_PAULO,
                new MetaDeAgua(2_000),
                DuracaoDoBloco.PADRAO,
                List.of(HIDRATACAO, EXERCICIO));

        assertThat(agua(jornada, 1).volumeMl()).isEqualTo(new BigDecimal("125"));
        assertThat(agua(jornada, 1).volumeMl().toString()).isEqualTo("125");
        assertThat(agua(jornada, 1).volumeArredondado()).isEqualTo(130);
    }

    @Test
    void planoDeExercicioUsaODobroDoIntervaloDaAgua() {
        assertThat(EXERCICIO.intervalo()).isEqualTo(Duration.ofHours(1));
        assertThat(EXERCICIO.quantidade()).isEqualTo(8);
        assertThat(PlanoDeMarcos.exercicio(Duration.ofMinutes(1)).intervalo()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void jornadaPrecisaDoPlanoDeAguaEDeUmPlanoPorCategoria() {
        assertThatThrownBy(() -> Jornada.iniciar(
                        UUID.randomUUID(),
                        as("09:00"),
                        SAO_PAULO,
                        MetaDeAgua.PADRAO,
                        DuracaoDoBloco.PADRAO,
                        List.of(EXERCICIO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hidratação");
        assertThatThrownBy(() -> Jornada.iniciar(
                        UUID.randomUUID(),
                        as("09:00"),
                        SAO_PAULO,
                        MetaDeAgua.PADRAO,
                        DuracaoDoBloco.PADRAO,
                        List.of(HIDRATACAO, HIDRATACAO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Um plano por categoria");
    }

    @Test
    void jornadaSoComAguaContinuaValendo() {
        Jornada jornada = Jornada.iniciar(
                UUID.randomUUID(),
                as("09:00"),
                SAO_PAULO,
                MetaDeAgua.PADRAO,
                DuracaoDoBloco.PADRAO,
                List.of(HIDRATACAO));

        assertThat(jornada.marcos()).hasSize(16);
        assertThat(agendadorAte(jornada, as("09:00"), as("17:00"))).hasSize(16);
        assertThat(jornada.exerciciosPropostosNoDia()).isEmpty();
    }

    @Test
    void duracaoDoBlocoSoAceitaCincoOuDezMinutos() {
        assertThat(new DuracaoDoBloco(5).duracao()).isEqualTo(Duration.ofMinutes(5));
        assertThat(new DuracaoDoBloco(10).duracao()).isEqualTo(Duration.ofMinutes(10));
        for (int invalida : new int[] {0, -5, 7, 20}) {
            assertThatThrownBy(() -> new DuracaoDoBloco(invalida))
                    .isInstanceOf(DuracaoDoBlocoInvalidaException.class)
                    .hasMessage("O bloco de exercício tem 5 ou 10 min; foi informado %d min.".formatted(invalida));
        }
    }

    @Nested
    class Exercicio {

        @Test
        void cenario2AguaParEExercicioDisparamNoMesmoTick() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(jornada.avancar(as("09:30")).disparados()).containsExactly(agua(jornada, 1));
            Avanco horaCheia = jornada.avancar(as("10:00"));

            assertThat(horaCheia.disparados()).containsExactly(agua(jornada, 2), exercicio(jornada, 1));
            assertThat(jornada.extrairEventos())
                    .filteredOn(MarcoDisparado.class::isInstance)
                    .extracting(EventoDaJornada::categoria)
                    .containsExactly(Categoria.HIDRATACAO, Categoria.HIDRATACAO, Categoria.EXERCICIO);
        }

        @Test
        void exercicioDisparaComOBlocoDaDuracaoDoDiaAindaSemExercicios() {
            Jornada jornada = iniciadaAs("09:00", DuracaoDoBloco.DEZ_MINUTOS);

            jornada.avancar(as("10:00"));

            assertThat(exercicio(jornada, 1).status()).isEqualTo(PENDENTE);
            assertThat(exercicio(jornada, 1).bloco()).hasValueSatisfying(bloco -> {
                assertThat(bloco.duracao()).isEqualTo(DuracaoDoBloco.DEZ_MINUTOS);
                assertThat(bloco.compensaAdiamento()).isFalse();
                assertThat(bloco.exercicios()).isEmpty();
            });
            assertThat(exercicio(jornada, 1).mensagem()).isEqualTo("Bloco de 10 min.");
            assertThat(exercicio(jornada, 2).mensagem()).isEqualTo("Bloco de exercício.");
        }

        @Test
        void blocoAtribuidoGuardaOsExerciciosEAMensagemContaQuantosSao() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));

            jornada.atribuirBloco(
                    exercicio(jornada, 1).id(), List.of(proposto("sentar-levantar", 45), proposto("prancha", 35)));

            BlocoDoMarco bloco = exercicio(jornada, 1).bloco().orElseThrow();
            assertThat(bloco.exercicios())
                    .extracting(ExercicioProposto::codigo)
                    .containsExactly("sentar-levantar", "prancha");
            assertThat(bloco.estimativa()).isEqualTo(Duration.ofSeconds(80));
            assertThat(exercicio(jornada, 1).mensagem()).isEqualTo("Bloco de 5 min: 2 exercícios.");
        }

        @Test
        void exerciciosPropostosNoDiaVaoDoBlocoMaisAntigoAoMaisRecente() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));
            jornada.atribuirBloco(exercicio(jornada, 1).id(), List.of(proposto("a", 45), proposto("b", 45)));
            jornada.avancar(as("11:00"));
            jornada.atribuirBloco(exercicio(jornada, 2).id(), List.of(proposto("c", 45)));

            assertThat(jornada.exerciciosPropostosNoDia()).containsExactly("a", "b", "c");
            assertThat(exercicio(jornada, 2).mensagem()).isEqualTo("Bloco de 5 min: 1 exercício.");
        }

        @Test
        void atribuirBlocoAMarcoSemBlocoOuJaPreenchidoERecusado() {
            Jornada jornada = iniciadaAs("09:00");
            List<ExercicioProposto> exercicios = List.of(proposto("a", 45));
            UUID naoDisparado = exercicio(jornada, 1).id();
            assertThatThrownBy(() -> jornada.atribuirBloco(naoDisparado, exercicios))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("não tem bloco");

            jornada.avancar(as("10:00"));
            UUID daAgua = agua(jornada, 2).id();
            assertThatThrownBy(() -> jornada.atribuirBloco(daAgua, exercicios))
                    .isInstanceOf(IllegalStateException.class);

            UUID id = exercicio(jornada, 1).id();
            jornada.atribuirBloco(id, exercicios);
            assertThatThrownBy(() -> jornada.atribuirBloco(id, exercicios))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("já tem exercícios");
        }

        @Test
        void blocoVazioOuMaiorQueADuracaoERecusado() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));
            UUID id = exercicio(jornada, 1).id();

            assertThatThrownBy(() -> jornada.atribuirBloco(id, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pelo menos um");
            assertThatThrownBy(() -> jornada.atribuirBloco(id, List.of(proposto("a", 200), proposto("b", 101))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("não cabem no bloco de 5 min");
            jornada.atribuirBloco(id, List.of(proposto("a", 200), proposto("b", 100)));
            assertThat(exercicio(jornada, 1).bloco().orElseThrow().estimativa()).isEqualTo(Duration.ofMinutes(5));
        }

        @Test
        void concluirExercicioNaoSomaAgua() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));

            jornada.concluirMarco(exercicio(jornada, 1).id(), as("10:05"));

            assertThat(exercicio(jornada, 1).status()).isEqualTo(CONCLUIDO);
            assertThat(jornada.aguaIngeridaMl()).isEqualByComparingTo("0");
            assertThatThrownBy(() -> exercicio(jornada, 1).volumeArredondado())
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void exercicioSemRespostaVenceQuandoDisparaOSeguinte() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));
            jornada.confirmarRecebimento(exercicio(jornada, 1).id(), as("10:00:01"));

            jornada.avancar(as("10:59:59"));
            assertThat(exercicio(jornada, 1).status()).isEqualTo(PENDENTE);
            jornada.avancar(as("11:00"));

            assertThat(exercicio(jornada, 1).status()).isEqualTo(FALHA);
            assertThat(exercicio(jornada, 2).status()).isEqualTo(PENDENTE);
        }

        @Test
        void d6OitavoExercicioVenceAsNoveHorasTrabalhadas() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("17:00"));
            jornada.confirmarRecebimento(exercicio(jornada, 8).id(), as("17:00:01"));

            jornada.avancar(as("17:59:59"));
            assertThat(exercicio(jornada, 8).status()).isEqualTo(PENDENTE);
            jornada.avancar(as("18:00"));
            assertThat(exercicio(jornada, 8).status()).isEqualTo(FALHA);
        }
    }

    @Nested
    class Adiamento {

        /** Jornada de 09:00 com o 1º exercício disparado às 10:00 e adiado às 10:01. */
        private Jornada comPrimeiroExercicioAdiado(DuracaoDoBloco duracao) {
            Jornada jornada = iniciadaAs("09:00", duracao);
            jornada.avancar(as("10:00"));
            jornada.adiarMarco(exercicio(jornada, 1).id(), as("10:01"));
            return jornada;
        }

        @Test
        void cenario3AdiarDeixaOMarcoAdiadoEOProximoBlocoTemDezMinutos() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));
            assertThat(jornada.podeAdiar(exercicio(jornada, 1))).isTrue();

            boolean mudou = jornada.adiarMarco(exercicio(jornada, 1).id(), as("10:01"));

            assertThat(mudou).isTrue();
            assertThat(exercicio(jornada, 1).status()).isEqualTo(StatusMarco.ADIADO);
            assertThat(exercicio(jornada, 1).recebidoEm()).contains(as("10:01"));
            assertThat(exercicio(jornada, 1).respondidoEm()).contains(as("10:01"));
            assertThat(jornada.podeAdiar(exercicio(jornada, 1))).isFalse();

            jornada.avancar(as("11:00"));
            assertThat(exercicio(jornada, 1).status()).isEqualTo(StatusMarco.ADIADO);
            assertThat(exercicio(jornada, 2).bloco()).hasValueSatisfying(bloco -> {
                assertThat(bloco.duracao()).isEqualTo(DuracaoDoBloco.DEZ_MINUTOS);
                assertThat(bloco.compensaAdiamento()).isTrue();
            });
            jornada.atribuirBloco(exercicio(jornada, 2).id(), List.of(proposto("a", 300), proposto("b", 290)));
            assertThat(exercicio(jornada, 2).mensagem())
                    .isEqualTo("Bloco de 10 min: 2 exercícios. Inclui o bloco adiado.");
        }

        @Test
        void d3NumDiaDeDezMinutosOBlocoQueCompensaContinuaComDez() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.DEZ_MINUTOS);

            jornada.avancar(as("11:00"));

            assertThat(exercicio(jornada, 2).bloco().orElseThrow().duracao()).isEqualTo(DuracaoDoBloco.DEZ_MINUTOS);
        }

        @Test
        void cenario4AdiadoEConcluidoContamComoConcluidos() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            jornada.avancar(as("11:00"));
            jornada.extrairEventos();

            jornada.concluirMarco(exercicio(jornada, 2).id(), as("11:08"));

            assertThat(exercicio(jornada, 1).status()).isEqualTo(CONCLUIDO);
            assertThat(exercicio(jornada, 2).status()).isEqualTo(CONCLUIDO);
            assertThat(jornada.extrairEventos())
                    .extracting(
                            evento -> ((MarcoEncerrado) evento).sequencia() + ":" + ((MarcoEncerrado) evento).status())
                    .containsExactly("2:CONCLUIDO", "1:CONCLUIDO");
        }

        @Test
        void adiadoEFalhaContamComoFalha() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            jornada.avancar(as("11:00"));

            jornada.falharMarco(exercicio(jornada, 2).id(), as("11:08"));

            assertThat(exercicio(jornada, 1).status()).isEqualTo(FALHA);
            assertThat(exercicio(jornada, 2).status()).isEqualTo(FALHA);
        }

        @Test
        void d4AdiadoSegueOSeguinteQuandoOPrazoVence() {
            Jornada recebido = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            recebido.avancar(as("11:00"));
            recebido.confirmarRecebimento(exercicio(recebido, 2).id(), as("11:00:01"));
            Jornada naoRecebido = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            naoRecebido.avancar(as("11:00"));

            recebido.avancar(as("12:00"));
            naoRecebido.avancar(as("12:00"));

            assertThat(exercicio(recebido, 1).status()).isEqualTo(FALHA);
            assertThat(exercicio(naoRecebido, 1).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(exercicio(naoRecebido, 2).status()).isEqualTo(NAO_ENTREGUE);
        }

        @Test
        void d4AdiadoSegueOSeguinteQuandoODiaEFinalizado() {
            Jornada seguinteAgendado = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            Jornada seguintePendente = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            seguintePendente.avancar(as("11:00"));

            seguinteAgendado.finalizar(as("10:30"));
            seguintePendente.finalizar(as("11:10"));

            assertThat(exercicio(seguinteAgendado, 1).status()).isEqualTo(NAO_CONCLUIDO);
            assertThat(exercicio(seguintePendente, 1).status()).isEqualTo(NAO_CONCLUIDO);
            assertThat(exercicio(seguintePendente, 2).status()).isEqualTo(NAO_CONCLUIDO);
        }

        @Test
        void d4AdiadoSegueOSeguinteNaReconciliacaoENoEncerramentoAutomatico() {
            Jornada foraDoAr = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            Jornada esquecida = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            esquecida.pausar(as("10:30"));

            foraDoAr.reconciliar(as("11:05"));
            esquecida.encerrarAutomaticamente(as("08:00").plus(Duration.ofDays(1)));

            assertThat(exercicio(foraDoAr, 1).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(exercicio(foraDoAr, 2).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(exercicio(esquecida, 1).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(esquecida.marcos()).extracting(Marco::status).doesNotContain(StatusMarco.ADIADO);
        }

        @Test
        void cenario5BlocoQueCompensaNaoPodeSerAdiado() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            jornada.avancar(as("11:00"));
            UUID seguinte = exercicio(jornada, 2).id();

            assertThat(jornada.podeAdiar(exercicio(jornada, 2))).isFalse();
            assertThatThrownBy(() -> jornada.adiarMarco(seguinte, as("11:01")))
                    .isInstanceOf(AdiamentoRecusadoException.class)
                    .hasMessage("Este bloco já compensa um adiamento: conclua ou marque falha.");
            assertThat(exercicio(jornada, 2).status()).isEqualTo(PENDENTE);
        }

        @Test
        void d5OUltimoBlocoDoDiaNaoPodeSerAdiado() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("17:00"));
            UUID ultimo = exercicio(jornada, 8).id();

            assertThat(jornada.podeAdiar(exercicio(jornada, 8))).isFalse();
            assertThatThrownBy(() -> jornada.adiarMarco(ultimo, as("17:01")))
                    .isInstanceOf(AdiamentoRecusadoException.class)
                    .hasMessageContaining("último bloco do dia");
        }

        @Test
        void aguaNaoPodeSerAdiada() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            UUID id = agua(jornada, 1).id();

            assertThat(jornada.podeAdiar(agua(jornada, 1))).isFalse();
            assertThatThrownBy(() -> jornada.adiarMarco(id, as("09:31")))
                    .isInstanceOf(AdiamentoRecusadoException.class)
                    .hasMessage("Só o bloco de exercício pode ser adiado.");
        }

        @Test
        void adiarDeNovoNaoMudaNada() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);

            boolean mudou = jornada.adiarMarco(exercicio(jornada, 1).id(), as("10:02"));

            assertThat(mudou).isFalse();
            assertThat(exercicio(jornada, 1).respondidoEm()).contains(as("10:01"));
        }

        @Test
        void soOExercicioPendentePodeSerAdiado() {
            Jornada jornada = iniciadaAs("09:00");
            UUID agendado = exercicio(jornada, 1).id();
            assertThat(jornada.podeAdiar(exercicio(jornada, 1))).isFalse();
            assertThatThrownBy(() -> jornada.adiarMarco(agendado, as("09:10")))
                    .isInstanceOf(AdiamentoRecusadoException.class)
                    .hasMessage("O lembrete ainda não disparou.");

            jornada.avancar(as("10:00"));
            jornada.concluirMarco(agendado, as("10:05"));

            assertThatThrownBy(() -> jornada.adiarMarco(agendado, as("10:06")))
                    .isInstanceOf(AdiamentoRecusadoException.class)
                    .hasMessage("O bloco já foi encerrado como CONCLUIDO.");
        }

        @Test
        void responderOAdiadoDiretamenteERecusado() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);
            UUID adiado = exercicio(jornada, 1).id();

            assertThatThrownBy(() -> jornada.concluirMarco(adiado, as("10:05")))
                    .isInstanceOf(RespostaDeMarcoRecusadaException.class)
                    .hasMessage("Este bloco foi adiado: ele é resolvido pelo bloco seguinte.");
        }

        @Test
        void podeAdiarDuranteAPausa() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("10:00"));
            jornada.pausar(as("10:02"));

            jornada.adiarMarco(exercicio(jornada, 1).id(), as("10:03"));

            assertThat(exercicio(jornada, 1).status()).isEqualTo(StatusMarco.ADIADO);
        }

        @Test
        void adiadoNaoTemPrazoProprio() {
            Jornada jornada = comPrimeiroExercicioAdiado(DuracaoDoBloco.PADRAO);

            assertThat(StatusMarco.ADIADO.encerrado()).isFalse();
            jornada.avancar(as("10:59:59"));
            assertThat(exercicio(jornada, 1).status()).isEqualTo(StatusMarco.ADIADO);
        }
    }
}
