package br.com.pausaativa.agenda.application;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.pausaativa.agenda.application.HistoricoDeExemplo.TipoDeDia;
import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos.PedidoDeBloco;
import br.com.pausaativa.agenda.domain.BlocoDoMarco;
import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.ExercicioProposto;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.Marco;
import br.com.pausaativa.agenda.domain.StatusJornada;
import br.com.pausaativa.agenda.domain.StatusMarco;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Dados de exemplo da demonstração (spec H4, seção 9). Hoje é quarta, 07/10/2026. */
class HistoricoDeExemploTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 7);

    private final List<PedidoDeBloco> pedidos = new ArrayList<>();

    /** Um exercício de 1 min por bloco, com o número do marco no código, como o Treino faria. */
    private final Function<PedidoDeBloco, List<ExercicioProposto>> montador = pedido -> {
        pedidos.add(pedido);
        return List.of(new ExercicioProposto(
                "exercicio-" + pedido.numeroDoMarco(),
                "Exercício",
                "Pernas",
                "10 repetições",
                Duration.ofMinutes(1),
                "Faça assim."));
    };

    private List<Jornada> jornadasAte(LocalDate hoje) {
        Instant agora = hoje.atTime(LocalTime.of(10, 0)).atZone(SAO_PAULO).toInstant();
        return new HistoricoDeExemplo(SAO_PAULO, montador).ate(agora);
    }

    private static long contar(Jornada jornada, Categoria categoria, StatusMarco status) {
        return jornada.marcos().stream()
                .filter(marco -> marco.categoria() == categoria && marco.status() == status)
                .count();
    }

    private static boolean naMeta(Collection<Jornada> jornadas, Categoria categoria) {
        long concluidos = jornadas.stream()
                .mapToLong(jornada -> contar(jornada, categoria, StatusMarco.CONCLUIDO))
                .sum();
        long falhas = jornadas.stream()
                .mapToLong(jornada -> contar(jornada, categoria, StatusMarco.FALHA))
                .sum();
        return concluidos * 100 >= 80 * (concluidos + falhas);
    }

    private static Stream<LocalDate> diasUteisAntesDe(LocalDate hoje) {
        return hoje.minusDays(HistoricoDeExemplo.DIAS)
                .datesUntil(hoje)
                .filter(dia -> dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY);
    }

    @Test
    void soDiasUteisDosQuarentaECincoDiasAnterioresEncerradosEHojeLivre() {
        List<Jornada> jornadas = jornadasAte(HOJE);

        assertThat(jornadas)
                .extracting(Jornada::dataReferencia)
                .isSorted()
                .doesNotHaveDuplicates()
                .allSatisfy(dia -> {
                    assertThat(dia).isBetween(HOJE.minusDays(45), HOJE.minusDays(1));
                    assertThat(dia.getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                });
        assertThat(jornadas).allSatisfy(jornada -> {
            assertThat(jornada.status()).isIn(StatusJornada.FINALIZADA, StatusJornada.ENCERRADA_AUTOMATICAMENTE);
            assertThat(jornada.marcos()).hasSize(24).allSatisfy(marco -> {
                assertThat(marco.status().encerrado()).isTrue();
                assertThat(marco.editadoEm()).isEmpty();
            });
            assertThat(jornada.iniciadaEm().atZone(SAO_PAULO).toLocalTime())
                    .isBetween(LocalTime.of(8, 0), LocalTime.of(9, 0));
            assertThat(jornada.pausas()).hasSizeLessThanOrEqualTo(1);
        });
    }

    @Test
    void temFaltasDiasFinalizadosCedoSemFinalizarEComLembretesNaoEntregues() {
        List<Jornada> jornadas = jornadasAte(HOJE);

        assertThat(jornadas.size()).isLessThan((int) diasUteisAntesDe(HOJE).count());
        assertThat(jornadas)
                .as("finalizado cedo: a tarde fica não concluída")
                .anySatisfy(jornada -> assertThat(contar(jornada, Categoria.HIDRATACAO, StatusMarco.NAO_CONCLUIDO))
                        .isGreaterThanOrEqualTo(4));
        assertThat(jornadas)
                .as("sem finalizar: o agendador encerra à meia-noite")
                .anySatisfy(jornada -> {
                    assertThat(jornada.status()).isEqualTo(StatusJornada.ENCERRADA_AUTOMATICAMENTE);
                    assertThat(jornada.finalizadaEm())
                            .contains(jornada.dataReferencia()
                                    .plusDays(1)
                                    .atStartOfDay(SAO_PAULO)
                                    .toInstant());
                    assertThat(jornada.marcos()).noneMatch(marco -> marco.status() == StatusMarco.NAO_CONCLUIDO);
                });
        assertThat(jornadas).as("aba fechada: os lembretes da tarde não chegam").anySatisfy(jornada -> {
            assertThat(contar(jornada, Categoria.HIDRATACAO, StatusMarco.NAO_ENTREGUE))
                    .isGreaterThanOrEqualTo(2);
            assertThat(jornada.marcos())
                    .filteredOn(marco -> marco.status() == StatusMarco.NAO_ENTREGUE)
                    .allSatisfy(marco -> assertThat(marco.recebidoEm()).isEmpty());
        });
    }

    @Test
    void emQualquerJanelaCadaTipoDeDiaCaiEmPeloMenosDoisDiasUteis() {
        for (LocalDate hoje = HOJE; hoje.isBefore(HOJE.plusDays(14)); hoje = hoje.plusDays(1)) {
            Map<TipoDeDia, Long> porTipo =
                    diasUteisAntesDe(hoje).collect(groupingBy(HistoricoDeExemplo::tipo, counting()));

            assertThat(porTipo.keySet()).as("hoje = %s", hoje).isEqualTo(EnumSet.allOf(TipoDeDia.class));
            assertThat(porTipo.values())
                    .as("hoje = %s", hoje)
                    .allSatisfy(dias -> assertThat(dias).isGreaterThanOrEqualTo(2));
        }
        assertThat(HistoricoDeExemplo.tipo(LocalDate.of(2026, 10, 3))).isEqualTo(TipoDeDia.FALTA); // sábado
    }

    @Test
    void emQualquerJanelaHaSemanasAcimaEAbaixoDaMetaNasDuasCategorias() {
        for (LocalDate hoje = HOJE; hoje.isBefore(HOJE.plusDays(14)); hoje = hoje.plusDays(1)) {
            Map<LocalDate, List<Jornada>> porSemana = jornadasAte(hoje).stream()
                    .collect(groupingBy(jornada ->
                            jornada.dataReferencia().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))));

            for (Categoria categoria : Categoria.values()) {
                assertThat(porSemana.values())
                        .as("%s, hoje = %s", categoria, hoje)
                        .anyMatch(semana -> naMeta(semana, categoria))
                        .anyMatch(semana -> !naMeta(semana, categoria));
            }
        }
    }

    @Test
    void blocosComExerciciosQueVariamNoDiaAdiamentosEBlocosDeDezMinutos() {
        List<Jornada> jornadas = jornadasAte(HOJE);

        List<BlocoDoMarco> blocos = jornadas.stream()
                .flatMap(jornada -> jornada.marcos().stream())
                .flatMap(marco -> marco.bloco().stream())
                .toList();
        assertThat(blocos)
                .hasSize(pedidos.size())
                .allSatisfy(bloco -> assertThat(bloco.exercicios()).isNotEmpty());
        assertThat(blocos).as("um par adiado").anyMatch(BlocoDoMarco::compensaAdiamento);
        assertThat(jornadas)
                .extracting(Jornada::duracaoDoBloco)
                .contains(DuracaoDoBloco.CINCO_MINUTOS, DuracaoDoBloco.DEZ_MINUTOS);
        assertThat(pedidos)
                .as("cada bloco conhece os exercícios já propostos no dia")
                .filteredOn(pedido -> pedido.numeroDoMarco() == 2)
                .allSatisfy(pedido -> assertThat(pedido.propostosNoDia()).containsExactly("exercicio-1"));
    }

    @Test
    void umaDataGeraSempreOMesmoDiaEmQualquerSubida() {
        List<Jornada> agora = jornadasAte(HOJE);
        List<Jornada> umaSemanaDepois = jornadasAte(HOJE.plusDays(7));

        List<String> emComum = agora.stream()
                .filter(jornada ->
                        !jornada.dataReferencia().isBefore(HOJE.plusDays(7).minusDays(45)))
                .map(HistoricoDeExemploTest::resumo)
                .toList();
        assertThat(emComum).hasSizeGreaterThan(20);
        assertThat(umaSemanaDepois.stream().map(HistoricoDeExemploTest::resumo).toList())
                .containsSubsequence(emComum);
    }

    /** Tudo o que o dia mostra, menos os identificadores. */
    private static String resumo(Jornada jornada) {
        return String.join(
                " ",
                jornada.dataReferencia().toString(),
                jornada.status().toString(),
                jornada.iniciadaEm().toString(),
                jornada.finalizadaEm().map(Instant::toString).orElse("-"),
                jornada.duracaoDoBloco().toString(),
                jornada.marcos().stream()
                        .map(HistoricoDeExemploTest::resumo)
                        .toList()
                        .toString());
    }

    private static String resumo(Marco marco) {
        return marco.categoria() + "-" + marco.sequencia() + ":" + marco.status() + "@"
                + marco.respondidoEm().map(Instant::toString).orElse("-");
    }
}
