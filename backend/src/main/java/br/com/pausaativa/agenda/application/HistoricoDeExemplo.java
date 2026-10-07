package br.com.pausaativa.agenda.application;

import static br.com.pausaativa.agenda.application.HistoricoDeExemplo.TipoDeDia.ABA_FECHADA;
import static br.com.pausaativa.agenda.application.HistoricoDeExemplo.TipoDeDia.COMUM;
import static br.com.pausaativa.agenda.application.HistoricoDeExemplo.TipoDeDia.FALTA;
import static br.com.pausaativa.agenda.application.HistoricoDeExemplo.TipoDeDia.FINALIZADO_CEDO;
import static br.com.pausaativa.agenda.application.HistoricoDeExemplo.TipoDeDia.SEM_FINALIZAR;

import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos.PedidoDeBloco;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.ExercicioProposto;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.MetaDeAgua;
import br.com.pausaativa.agenda.domain.PlanoDeMarcos;
import br.com.pausaativa.agenda.domain.StatusMarco;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Jornadas fictícias dos 45 dias anteriores a hoje, para a demonstração começar com histórico (spec H4,
 * seção 9). Cada dia é vivido pelo domínio da Agenda, minuto a minuto, como o agendador e o usuário fariam:
 * as situações saem das regras de verdade, e não de números escritos à mão.
 *
 * <p>Só dias úteis, com faltas, dias finalizados cedo, dias sem finalizar (encerrados automaticamente),
 * tardes com a aba fechada (lembretes não entregues), adiamentos e semanas acima e abaixo da meta.
 *
 * <p>É determinístico: a sorte de cada dia sai de uma semente fixa e da data, então uma data gera sempre o
 * mesmo dia, em qualquer subida. Só os identificadores mudam.
 */
final class HistoricoDeExemplo {

    static final int DIAS = 45;

    /** O que acontece num dia útil. */
    enum TipoDeDia {
        COMUM,
        /** Sem jornada. */
        FALTA,
        /** Finalizado entre 4 e 6 horas trabalhadas: o resto fica não concluído. */
        FINALIZADO_CEDO,
        /** Ninguém finaliza: o agendador encerra a jornada depois da meia-noite. */
        SEM_FINALIZAR,
        /** A aba fica fechada por uma ou duas horas à tarde: os lembretes desse trecho não são entregues. */
        ABA_FECHADA
    }

    /**
     * O tipo de cada dia segue um ciclo de 11 dias. Como 11 é primo com 7, em qualquer janela de 45 dias
     * cada posição do ciclo cai em pelo menos dois dias úteis: todo tipo aparece, qualquer que seja hoje.
     */
    private static final TipoDeDia[] CICLO = {
        FALTA, COMUM, ABA_FECHADA, COMUM, FINALIZADO_CEDO, COMUM, COMUM, SEM_FINALIZAR, COMUM, ABA_FECHADA, COMUM
    };

    /**
     * Chance de concluir um lembrete, por semana, em ciclo: duas semanas abaixo da meta de 80% a cada seis.
     * O exercício fica dez pontos abaixo da água.
     */
    private static final double[] DISPOSICAO_DA_SEMANA = {0.95, 0.86, 0.70, 0.93, 0.72, 0.97};

    private static final double EXERCICIO_A_MENOS = 0.10;
    private static final double CHANCE_DE_ADIAR = 0.07;
    private static final double CHANCE_DE_BLOCO_DE_10_MIN = 0.2;
    private static final long SEMENTE = 20_261_007L;

    /** Os planos de uso real, 30 min, mesmo na demonstração de 1 min: o histórico imita dias de verdade. */
    private static final List<PlanoDeMarcos> PLANOS = List.of(
            PlanoDeMarcos.hidratacao(PlanoDeMarcos.INTERVALO_PADRAO),
            PlanoDeMarcos.exercicio(PlanoDeMarcos.INTERVALO_PADRAO));

    private static final Duration MINUTO = Duration.ofMinutes(1);
    /** Nenhum dia de exemplo passa disto; se passar, é defeito do roteiro. */
    private static final Duration DURACAO_MAXIMA = Duration.ofHours(20);

    private final ZoneId fuso;
    private final Function<PedidoDeBloco, List<ExercicioProposto>> montador;

    /** @param montador monta os exercícios de um bloco, como o Treino faria com um perfil de exemplo */
    HistoricoDeExemplo(ZoneId fuso, Function<PedidoDeBloco, List<ExercicioProposto>> montador) {
        this.fuso = fuso;
        this.montador = montador;
    }

    /** As jornadas dos 45 dias anteriores ao dia de {@code agora}, em ordem de data. Hoje fica livre. */
    List<Jornada> ate(Instant agora) {
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        List<Jornada> jornadas = new ArrayList<>();
        for (LocalDate dia = hoje.minusDays(DIAS); dia.isBefore(hoje); dia = dia.plusDays(1)) {
            new DiaDeExemplo(dia).viver().ifPresent(jornadas::add);
        }
        return jornadas;
    }

    static TipoDeDia tipo(LocalDate dia) {
        if (dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return FALTA;
        }
        return CICLO[Math.floorMod(dia.toEpochDay(), CICLO.length)];
    }

    private static double disposicaoDaSemana(LocalDate dia) {
        LocalDate segunda = dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return DISPOSICAO_DA_SEMANA[Math.floorMod(Math.floorDiv(segunda.toEpochDay(), 7), DISPOSICAO_DA_SEMANA.length)];
    }

    /** Um dia vivido. O roteiro é sorteado no começo; a reação a cada lembrete, no disparo dele. */
    private final class DiaDeExemplo {

        private final LocalDate dia;
        private final TipoDeDia tipo;
        private final SplittableRandom sorte;
        private final Map<Instant, List<Consumer<Instant>>> acoes = new HashMap<>();
        private Jornada jornada;
        private double disposicao;
        private Instant abaFechadaDe = Instant.MAX;
        private Instant abaFechadaAte = Instant.MIN;

        DiaDeExemplo(LocalDate dia) {
            this.dia = dia;
            this.tipo = tipo(dia);
            this.sorte = new SplittableRandom(SEMENTE + dia.toEpochDay());
        }

        Optional<Jornada> viver() {
            if (tipo == FALTA) {
                return Optional.empty();
            }
            Instant inicio = as(LocalTime.of(8, 0).plusMinutes(sorte.nextInt(61)));
            Instant almoco = as(LocalTime.of(12, 0).plusMinutes(sorte.nextInt(31)));
            Duration duracaoDoAlmoco = Duration.ofMinutes(45 + sorte.nextInt(31));
            DuracaoDoBloco duracaoDoBloco = sorte.nextDouble() < CHANCE_DE_BLOCO_DE_10_MIN
                    ? DuracaoDoBloco.DEZ_MINUTOS
                    : DuracaoDoBloco.CINCO_MINUTOS;
            disposicao = disposicaoDaSemana(dia) + (sorte.nextDouble() - 0.5) * 0.08;
            Duration trabalhoDoDia = tipo == FINALIZADO_CEDO
                    ? Duration.ofHours(4).plusMinutes(sorte.nextInt(121))
                    : Duration.ofHours(8).plusMinutes(5 + sorte.nextInt(16));
            if (tipo == ABA_FECHADA) {
                abaFechadaDe = inicio.plus(Duration.ofHours(5).plusMinutes(sorte.nextInt(61)));
                abaFechadaAte = abaFechadaDe.plus(Duration.ofMinutes(60 + sorte.nextInt(61)));
            }

            jornada = Jornada.iniciar(UUID.randomUUID(), inicio, fuso, MetaDeAgua.PADRAO, duracaoDoBloco, PLANOS);
            agendar(almoco, jornada::pausar);
            agendar(almoco.plus(duracaoDoAlmoco), jornada::retomar);
            for (Instant agora = inicio; jornada.status().aberta(); agora = agora.plus(MINUTO)) {
                if (agora.isAfter(inicio.plus(DURACAO_MAXIMA))) {
                    throw new IllegalStateException("O dia de exemplo " + dia + " não terminou");
                }
                for (Marco marco : jornada.avancar(agora).disparados()) {
                    montarBloco(marco);
                    reagir(marco, agora);
                }
                for (Consumer<Instant> acao : acoes.getOrDefault(agora, List.of())) {
                    acao.accept(agora);
                }
                encerrarSeForAHora(agora, trabalhoDoDia);
            }
            return Optional.of(jornada);
        }

        /** Sem finalizar, o agendador encerra no primeiro segundo do dia seguinte, com tudo respondido. */
        private void encerrarSeForAHora(Instant agora, Duration trabalhoDoDia) {
            if (tipo == SEM_FINALIZAR) {
                if (jornada.marcos().stream().allMatch(marco -> marco.status().encerrado())) {
                    jornada.encerrarAutomaticamente(
                            dia.plusDays(1).atStartOfDay(fuso).toInstant());
                }
            } else if (jornada.tempoTrabalhado(agora).compareTo(trabalhoDoDia) >= 0) {
                jornada.finalizar(agora);
            }
        }

        /** Como a {@link AvancoDaJornada}: o bloco recebe os exercícios no disparo, variando os do dia. */
        private void montarBloco(Marco marco) {
            marco.bloco()
                    .ifPresent(bloco -> jornada.atribuirBloco(
                            marco.id(),
                            montador.apply(new PedidoDeBloco(
                                    bloco.duracao(), marco.sequencia(), jornada.exerciciosPropostosNoDia()))));
        }

        /**
         * Com a aba aberta, o lembrete chega na hora. Depois, o usuário conclui, marca falha, adia o
         * exercício ou deixa o prazo vencer, que também dá falha. Com a aba fechada, nada chega, e o prazo
         * vencido dá não entregue.
         */
        private void reagir(Marco marco, Instant disparo) {
            double reacao = sorte.nextDouble();
            Instant resposta = disparo.plus(Duration.ofMinutes(1 + sorte.nextInt(10)));
            if (!disparo.isBefore(abaFechadaDe) && disparo.isBefore(abaFechadaAte)) {
                return;
            }
            jornada.confirmarRecebimento(marco.id(), disparo);
            double chance = marco.categoria() == Categoria.EXERCICIO ? disposicao - EXERCICIO_A_MENOS : disposicao;
            if (reacao < CHANCE_DE_ADIAR && jornada.podeAdiar(marco)) {
                agendar(resposta, agora -> responder(marco, agora, jornada::adiarMarco));
            } else if (reacao < chance) {
                agendar(resposta, agora -> responder(marco, agora, jornada::concluirMarco));
            } else if (reacao < (1 + chance) / 2) {
                agendar(resposta, agora -> responder(marco, agora, jornada::falharMarco));
            }
        }

        /** Só responde o que ainda espera resposta: o prazo pode ter vencido ou o dia, acabado. */
        private void responder(Marco marco, Instant agora, BiPredicate<UUID, Instant> resposta) {
            if (marco.status() == StatusMarco.PENDENTE) {
                resposta.test(marco.id(), agora);
            }
        }

        private void agendar(Instant quando, Consumer<Instant> acao) {
            acoes.computeIfAbsent(quando, chave -> new ArrayList<>()).add(acao);
        }

        private Instant as(LocalTime hora) {
            return dia.atTime(hora).atZone(fuso).toInstant();
        }
    }
}
