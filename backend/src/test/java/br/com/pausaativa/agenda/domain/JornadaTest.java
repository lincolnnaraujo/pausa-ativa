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

/** Regras da jornada (spec H2, seção 3), com o tempo controlado pelo teste. */
class JornadaTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DIA = LocalDate.of(2026, 10, 2);
    private static final PlanoDeMarcos PLANO = PlanoDeMarcos.hidratacao(PlanoDeMarcos.INTERVALO_PADRAO);

    /** Horário de parede em São Paulo, no dia do teste. */
    private static Instant as(String hora) {
        return LocalDateTime.of(DIA, LocalTime.parse(hora)).atZone(SAO_PAULO).toInstant();
    }

    private static Jornada iniciadaAs(String hora) {
        return Jornada.iniciar(UUID.randomUUID(), as(hora), SAO_PAULO, MetaDeAgua.PADRAO, PLANO);
    }

    private static Marco marco(Jornada jornada, int sequencia) {
        return jornada.marcos().get(sequencia - 1);
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
        void agendaDezesseisMarcosDeHidratacaoACadaTrintaMinutosTrabalhados() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(jornada.status()).isEqualTo(StatusJornada.EM_ANDAMENTO);
            assertThat(jornada.dataReferencia()).isEqualTo(DIA);
            assertThat(jornada.marcos()).hasSize(16).allSatisfy(marco -> {
                assertThat(marco.status()).isEqualTo(AGENDADO);
                assertThat(marco.categoria()).isEqualTo(Categoria.HIDRATACAO);
                assertThat(marco.volumeMl()).isEqualByComparingTo("187.5");
            });
            assertThat(marco(jornada, 1).tempoTrabalhadoPrevisto()).isEqualTo(Duration.ofMinutes(30));
            assertThat(marco(jornada, 16).tempoTrabalhadoPrevisto()).isEqualTo(Duration.ofHours(8));
        }

        @Test
        void jornadaCompletaDisparaOsDezesseisMarcosNosInstantesCertosENadaDepoisDasOitoHoras() {
            Jornada jornada = iniciadaAs("09:00");

            List<Marco> disparados = agendadorAte(jornada, as("09:00"), as("18:30"));

            assertThat(disparados).hasSize(16);
            assertThat(disparados)
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

            assertThat(marco(jornada, 1).mensagem()).isEqualTo("Beba ~190 ml. Levante-se para buscar a água.");
            assertThat(marco(jornada, 2).mensagem()).isEqualTo("Beba ~190 ml.");
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

            assertThat(avanco.disparados()).containsExactly(marco(jornada, 1));
            assertThat(avanco.houveMudanca()).isTrue();
            assertThat(marco(jornada, 1).status()).isEqualTo(PENDENTE);
            assertThat(marco(jornada, 1).previstoPara()).contains(as("09:30"));
        }

        @Test
        void tickAtrasadoRegistraOInstantePrevistoParaMedirOAtraso() {
            Jornada jornada = iniciadaAs("09:00");

            jornada.avancar(as("09:30:02"));

            assertThat(marco(jornada, 1).previstoPara()).contains(as("09:30"));
            assertThat(marco(jornada, 1).disparadoEm()).contains(as("09:30:02"));
        }

        @Test
        void cenario3PausaDeAlmocoDeslocaOProximoMarcoPeloTempoPausado() {
            Jornada jornada = iniciadaAs("08:40");
            agendadorAte(jornada, as("08:40"), as("12:00"));
            assertThat(marco(jornada, 6).disparadoEm()).contains(as("11:40"));

            jornada.pausar(as("12:00"));
            assertThat(agendadorAte(jornada, as("12:00:01"), as("12:59:59"))).isEmpty();
            jornada.retomar(as("13:00"));

            assertThat(agendadorAte(jornada, as("13:00"), as("13:09:59"))).isEmpty();
            assertThat(jornada.avancar(as("13:10")).disparados()).containsExactly(marco(jornada, 7));
            assertThat(marco(jornada, 7).previstoPara()).contains(as("13:10"));
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
        void jornadaQueAtravessaAMeiaNoiteContinuaDisparandoEPertenceAoDiaEmQueComecou() {
            Jornada jornada = iniciadaAs("22:00");
            Instant meiaNoiteEMeia = as("00:30").plus(Duration.ofDays(1));

            List<Marco> disparados = agendadorAte(jornada, as("22:00"), meiaNoiteEMeia);

            assertThat(disparados).hasSize(5);
            assertThat(marco(jornada, 5).disparadoEm()).contains(meiaNoiteEMeia);
            assertThat(jornada.dataReferencia()).isEqualTo(DIA);
        }
    }

    @Nested
    class Resposta {

        @Test
        void cenario2ConcluirSomaOVolumeDoMarcoAAguaDoDia() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));

            boolean mudou = jornada.concluirMarco(marco(jornada, 1).id(), as("09:31"));

            assertThat(mudou).isTrue();
            assertThat(marco(jornada, 1).status()).isEqualTo(CONCLUIDO);
            assertThat(marco(jornada, 1).respondidoEm()).contains(as("09:31"));
            assertThat(jornada.aguaIngeridaMl()).isEqualByComparingTo("187.5");
        }

        @Test
        void cenario6RepetirAMesmaRespostaNaoMudaNada() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            UUID id = marco(jornada, 1).id();
            jornada.concluirMarco(id, as("09:31"));

            boolean mudou = jornada.concluirMarco(id, as("09:32"));

            assertThat(mudou).isFalse();
            assertThat(marco(jornada, 1).respondidoEm()).contains(as("09:31"));
            assertThat(jornada.aguaIngeridaMl()).isEqualByComparingTo("187.5");
        }

        @Test
        void outraRespostaAMarcoEncerradoERecusadaComMensagemClara() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            UUID id = marco(jornada, 1).id();
            jornada.concluirMarco(id, as("09:31"));

            assertThatThrownBy(() -> jornada.falharMarco(id, as("09:32")))
                    .isInstanceOf(RespostaDeMarcoRecusadaException.class)
                    .hasMessageContaining("já foi encerrado como CONCLUIDO")
                    .hasMessageContaining("v0.4.0");
        }

        @Test
        void responderMarcoQueAindaNaoDisparouERecusado() {
            Jornada jornada = iniciadaAs("09:00");

            assertThatThrownBy(() -> jornada.concluirMarco(marco(jornada, 1).id(), as("09:10")))
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

            jornada.concluirMarco(marco(jornada, 1).id(), as("09:50"));

            assertThat(marco(jornada, 1).status()).isEqualTo(CONCLUIDO);
        }

        @Test
        void confirmarRecebimentoUmaVezSoEApenasDeMarcoPendente() {
            Jornada jornada = iniciadaAs("09:00");
            UUID id = marco(jornada, 1).id();
            assertThat(jornada.confirmarRecebimento(id, as("09:10"))).isFalse();

            jornada.avancar(as("09:30"));

            assertThat(jornada.confirmarRecebimento(id, as("09:30:01"))).isTrue();
            assertThat(jornada.confirmarRecebimento(id, as("09:30:05"))).isFalse();
            assertThat(marco(jornada, 1).recebidoEm()).contains(as("09:30:01"));
        }
    }

    @Nested
    class Prazo {

        @Test
        void cenario4SemRespostaComRecebimentoViraFalhaQuandoDisparaOSeguinte() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.confirmarRecebimento(marco(jornada, 1).id(), as("09:30:01"));

            jornada.avancar(as("10:00"));

            assertThat(marco(jornada, 1).status()).isEqualTo(FALHA);
            assertThat(marco(jornada, 2).status()).isEqualTo(PENDENTE);
        }

        @Test
        void cenario5SemRecebimentoConfirmadoViraNaoEntregue() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));

            jornada.avancar(as("10:00"));

            assertThat(marco(jornada, 1).status()).isEqualTo(NAO_ENTREGUE);
        }

        @Test
        void prazoCongelaDuranteAPausa() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.confirmarRecebimento(marco(jornada, 1).id(), as("09:30:01"));
            jornada.pausar(as("09:45"));
            jornada.retomar(as("10:45"));

            jornada.avancar(as("10:59:59"));
            assertThat(marco(jornada, 1).status()).isEqualTo(PENDENTE);
            jornada.avancar(as("11:00"));
            assertThat(marco(jornada, 1).status()).isEqualTo(FALHA);
        }

        @Test
        void ultimoMarcoTambemTemPrazoDeTrintaMinutos() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("17:00"));
            jornada.confirmarRecebimento(marco(jornada, 16).id(), as("17:00:01"));

            jornada.avancar(as("17:29:59"));
            assertThat(marco(jornada, 16).status()).isEqualTo(PENDENTE);
            jornada.avancar(as("17:30"));
            assertThat(marco(jornada, 16).status()).isEqualTo(FALHA);
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
            jornada.concluirMarco(marco(jornada, 1).id(), as("09:31"));
            agendadorAte(jornada, as("09:31:01"), as("14:00"));
            jornada.confirmarRecebimento(marco(jornada, 10).id(), as("14:00:01"));

            boolean mudou = jornada.finalizar(as("14:05"));

            assertThat(mudou).isTrue();
            assertThat(jornada.status()).isEqualTo(StatusJornada.FINALIZADA);
            assertThat(jornada.finalizadaEm()).contains(as("14:05"));
            assertThat(marco(jornada, 1).status()).isEqualTo(CONCLUIDO);
            assertThat(marco(jornada, 10).status()).isEqualTo(NAO_CONCLUIDO);
            assertThat(jornada.marcos().subList(10, 16))
                    .extracting(Marco::status)
                    .containsOnly(NAO_CONCLUIDO);
            assertThat(jornada.tempoTrabalhado(as("18:00"))).isEqualTo(Duration.ofMinutes(305));
        }

        @Test
        void prazoJaVencidoNaHoraDeFinalizarContaComoFalha() {
            Jornada jornada = iniciadaAs("09:00");
            jornada.avancar(as("09:30"));
            jornada.confirmarRecebimento(marco(jornada, 1).id(), as("09:30:01"));

            jornada.finalizar(as("10:00:00.5"));

            assertThat(marco(jornada, 1).status()).isEqualTo(FALHA);
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
        void cenario8JornadaEsquecidaEEncerradaAutomaticamente() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("10:00"));
            jornada.confirmarRecebimento(marco(jornada, 2).id(), as("10:00:01"));
            Instant amanha = as("08:00").plus(Duration.ofDays(1));

            assertThat(jornada.esquecida(DIA)).isFalse();
            assertThat(jornada.esquecida(DIA.plusDays(1))).isTrue();
            boolean mudou = jornada.encerrarAutomaticamente(amanha);

            assertThat(mudou).isTrue();
            assertThat(jornada.status()).isEqualTo(StatusJornada.ENCERRADA_AUTOMATICAMENTE);
            assertThat(marco(jornada, 1).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(marco(jornada, 2).status()).isEqualTo(FALHA);
            assertThat(jornada.marcos().subList(2, 16))
                    .extracting(Marco::status)
                    .containsOnly(NAO_ENTREGUE);
            assertThat(jornada.esquecida(DIA.plusDays(1))).isFalse();
        }

        @Test
        void marcosQueVenceramComOBackendForaDoArNaoDisparamAtrasados() {
            Jornada jornada = iniciadaAs("09:00");
            agendadorAte(jornada, as("09:00"), as("10:10"));
            jornada.confirmarRecebimento(marco(jornada, 2).id(), as("10:00:01"));
            // Backend fora do ar das 10:10 às 11:05.

            boolean mudou = jornada.reconciliarAposReinicio(as("11:05"));

            assertThat(mudou).isTrue();
            assertThat(marco(jornada, 2).status()).isEqualTo(FALHA);
            assertThat(marco(jornada, 3).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(marco(jornada, 4).status()).isEqualTo(NAO_ENTREGUE);
            assertThat(marco(jornada, 5).status()).isEqualTo(AGENDADO);
            assertThat(jornada.avancar(as("11:30")).disparados()).containsExactly(marco(jornada, 5));
        }

        @Test
        void reinicioSemNadaVencidoNaoMudaAJornada() {
            Jornada jornada = iniciadaAs("09:00");

            assertThat(jornada.reconciliarAposReinicio(as("09:10"))).isFalse();
            assertThat(jornada.encerrarAutomaticamente(as("09:10"))).isTrue();
            assertThat(jornada.reconciliarAposReinicio(as("09:20"))).isFalse();
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
    void metaDiferenteDistribuiOVolumeIgualmenteEntreOsMarcos() {
        Jornada jornada = Jornada.iniciar(UUID.randomUUID(), as("09:00"), SAO_PAULO, new MetaDeAgua(2_000), PLANO);

        assertThat(marco(jornada, 1).volumeMl()).isEqualByComparingTo(new BigDecimal("125"));
        assertThat(marco(jornada, 1).volumeArredondado()).isEqualTo(130);
    }
}
