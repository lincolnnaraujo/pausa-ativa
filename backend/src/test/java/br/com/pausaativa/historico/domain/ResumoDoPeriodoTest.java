package br.com.pausaativa.historico.domain;

import static br.com.pausaativa.historico.domain.Categoria.EXERCICIO;
import static br.com.pausaativa.historico.domain.Categoria.HIDRATACAO;
import static br.com.pausaativa.historico.domain.EstadoDaJornada.EM_ANDAMENTO;
import static br.com.pausaativa.historico.domain.EstadoDaJornada.FINALIZADA;
import static br.com.pausaativa.historico.domain.TipoDePeriodo.DIA;
import static br.com.pausaativa.historico.domain.TipoDePeriodo.MES;
import static br.com.pausaativa.historico.domain.TipoDePeriodo.SEMANA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Resumo de uma visão do histórico: Cenários 1, 2 e 6 da H4 e as regras da seção 3.2 da spec. */
class ResumoDoPeriodoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 6); // terça-feira
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 9, 28);

    private static ContagemPorSituacao contagem(
            int concluidos, int falhas, int naoEntregues, int naoConcluidos, int emAberto) {
        return new ContagemPorSituacao(concluidos, falhas, naoEntregues, naoConcluidos, emAberto);
    }

    private static RegistroDoDia registro(
            LocalDate data, EstadoDaJornada jornada, ContagemPorSituacao agua, ContagemPorSituacao exercicio) {
        return new RegistroDoDia(data, jornada, Map.of(HIDRATACAO, agua, EXERCICIO, exercicio));
    }

    private static Periodo semanaPassada() {
        return Periodo.contendo(SEMANA, SEGUNDA, HOJE);
    }

    @Test
    void cenario1TaxaDoDiaComOsNaoEntreguesAParte() {
        LocalDate ontem = LocalDate.of(2026, 10, 5);
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                Periodo.contendo(DIA, ontem, HOJE),
                List.of(registro(ontem, FINALIZADA, contagem(12, 2, 2, 0, 0), contagem(5, 2, 1, 0, 0))),
                HOJE);

        ResumoDaCategoria agua = resumo.categoria(HIDRATACAO);
        assertThat(agua.total()).isEqualTo(contagem(12, 2, 2, 0, 0));
        assertThat(agua.taxa().orElseThrow().percentual()).hasToString("85.7");
        assertThat(agua.taxa().orElseThrow().metaAtingida()).isTrue();
        assertThat(agua.diasComDados()).isEqualTo(1);
        assertThat(agua.diasNaMeta()).isEqualTo(1);

        ResumoDaCategoria exercicio = resumo.categoria(EXERCICIO);
        assertThat(exercicio.taxa().orElseThrow().percentual()).hasToString("71.4");
        assertThat(exercicio.diasNaMeta()).isZero();

        assertThat(resumo.dias()).singleElement().satisfies(dia -> {
            assertThat(dia.jornada()).contains(FINALIZADA);
            assertThat(dia.futuro()).isFalse();
        });
    }

    @Test
    void categoriasVemNaOrdemDaAguaParaOExercicio() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(semanaPassada(), List.of(), HOJE);

        assertThat(resumo.categorias()).extracting(ResumoDaCategoria::categoria).containsExactly(HIDRATACAO, EXERCICIO);
    }

    @Test
    void cenario2SemanaSomaOsMarcosEnaoAMediaDasTaxas() {
        // Segunda: 15 de 16 (93,75%). Terça, finalizada cedo: 1 de 2 (50%). A média daria 71,8%.
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                semanaPassada(),
                List.of(
                        registro(SEGUNDA, FINALIZADA, contagem(15, 1, 0, 0, 0), contagem(7, 1, 0, 0, 0)),
                        registro(SEGUNDA.plusDays(1), FINALIZADA, contagem(1, 1, 0, 14, 0), contagem(0, 1, 0, 7, 0))),
                HOJE);

        ResumoDaCategoria agua = resumo.categoria(HIDRATACAO);
        assertThat(agua.total()).isEqualTo(contagem(16, 2, 0, 14, 0));
        assertThat(agua.taxa().orElseThrow().percentual()).hasToString("88.8");
        assertThat(agua.diasComDados()).isEqualTo(2);
        assertThat(agua.diasNaMeta()).isEqualTo(1);

        ResumoDaCategoria exercicio = resumo.categoria(EXERCICIO);
        assertThat(exercicio.total()).isEqualTo(contagem(7, 2, 0, 7, 0));
        assertThat(exercicio.taxa().orElseThrow().percentual()).hasToString("77.7");
        assertThat(exercicio.diasNaMeta()).isEqualTo(1);
    }

    @Test
    void mesComDoisDiasNaMetaPodeFicarAbaixoDaMeta() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                Periodo.contendo(MES, LocalDate.of(2026, 9, 15), HOJE),
                List.of(
                        registro(
                                LocalDate.of(2026, 9, 1),
                                FINALIZADA,
                                contagem(12, 2, 2, 0, 0),
                                contagem(8, 0, 0, 0, 0)),
                        registro(
                                LocalDate.of(2026, 9, 2),
                                FINALIZADA,
                                contagem(12, 3, 1, 0, 0),
                                contagem(8, 0, 0, 0, 0)),
                        registro(
                                LocalDate.of(2026, 9, 3),
                                FINALIZADA,
                                contagem(11, 4, 1, 0, 0),
                                contagem(8, 0, 0, 0, 0))),
                HOJE);

        ResumoDaCategoria agua = resumo.categoria(HIDRATACAO);
        assertThat(agua.diasComDados()).isEqualTo(3);
        assertThat(agua.diasNaMeta()).isEqualTo(2); // 85,7% e 80,0%; o terceiro, 73,3%
        assertThat(agua.taxa().orElseThrow().percentual()).hasToString("79.5"); // 35 de 44
        assertThat(agua.taxa().orElseThrow().metaAtingida()).isFalse();
        assertThat(resumo.dias()).hasSize(30);
    }

    @Test
    void diaSemJornadaEntraSemContagensENaoContaComoFalha() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                semanaPassada(),
                List.of(registro(SEGUNDA, FINALIZADA, contagem(14, 2, 0, 0, 0), contagem(6, 2, 0, 0, 0))),
                HOJE);

        assertThat(resumo.dias()).hasSize(7);
        assertThat(resumo.dias().subList(1, 7)).allSatisfy(dia -> {
            assertThat(dia.jornada()).isEmpty();
            assertThat(dia.futuro()).isFalse();
            assertThat(dia.contagens()).isEmpty();
            assertThat(dia.contagem(HIDRATACAO)).isEqualTo(ContagemPorSituacao.VAZIA);
        });
        assertThat(resumo.categoria(HIDRATACAO).total()).isEqualTo(contagem(14, 2, 0, 0, 0));
        assertThat(resumo.categoria(HIDRATACAO).diasComDados()).isEqualTo(1);
        assertThat(resumo.semJornada()).isFalse();
    }

    @Test
    void hojeEmAndamentoEntraComOsNumerosParciaisEOsDiasSeguintesSaoFuturos() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                Periodo.contendo(SEMANA, HOJE, HOJE),
                List.of(
                        registro(HOJE.minusDays(1), FINALIZADA, contagem(14, 2, 0, 0, 0), contagem(7, 1, 0, 0, 0)),
                        registro(HOJE, EM_ANDAMENTO, contagem(3, 1, 0, 0, 12), contagem(1, 0, 0, 0, 7))),
                HOJE);

        DiaDoPeriodo hoje = resumo.dias().get(1);
        assertThat(hoje.jornada()).contains(EM_ANDAMENTO);
        assertThat(hoje.contagem(HIDRATACAO).taxa().orElseThrow().percentual()).hasToString("75.0");
        assertThat(resumo.categoria(HIDRATACAO).total()).isEqualTo(contagem(17, 3, 0, 0, 12));
        assertThat(resumo.dias().subList(2, 7)).allSatisfy(dia -> {
            assertThat(dia.futuro()).isTrue();
            assertThat(dia.jornada()).isEmpty();
        });
        assertThat(resumo.dias().subList(0, 2)).noneMatch(DiaDoPeriodo::futuro);
    }

    @Test
    void cenario6SemanaSemJornadaFicaSemTaxaESemZeros() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(semanaPassada(), List.of(), HOJE);

        assertThat(resumo.semJornada()).isTrue();
        assertThat(resumo.categorias()).allSatisfy(categoria -> {
            assertThat(categoria.taxa()).isEmpty();
            assertThat(categoria.total()).isEqualTo(ContagemPorSituacao.VAZIA);
            assertThat(categoria.diasComDados()).isZero();
            assertThat(categoria.diasNaMeta()).isZero();
        });
        assertThat(resumo.dias()).hasSize(7).allMatch(dia -> dia.jornada().isEmpty());
    }

    @Test
    void jornadaDaV020SoTemAguaEOExercicioFicaSemDados() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                semanaPassada(),
                List.of(new RegistroDoDia(SEGUNDA, FINALIZADA, Map.of(HIDRATACAO, contagem(14, 2, 0, 0, 0)))),
                HOJE);

        assertThat(resumo.categoria(HIDRATACAO).taxa()).isPresent();
        assertThat(resumo.categoria(EXERCICIO).taxa()).isEmpty();
        assertThat(resumo.categoria(EXERCICIO).diasComDados()).isZero();
        assertThat(resumo.dias().getFirst().contagem(EXERCICIO)).isEqualTo(ContagemPorSituacao.VAZIA);
    }

    @Test
    void diaSoComNaoEntreguesTemJornadaMasNaoTemDados() {
        ResumoDoPeriodo resumo = ResumoDoPeriodo.de(
                semanaPassada(),
                List.of(registro(SEGUNDA, FINALIZADA, contagem(0, 0, 16, 0, 0), contagem(0, 0, 8, 0, 0))),
                HOJE);

        assertThat(resumo.semJornada()).isFalse();
        assertThat(resumo.categoria(HIDRATACAO).taxa()).isEmpty();
        assertThat(resumo.categoria(HIDRATACAO).diasComDados()).isZero();
        assertThat(resumo.dias().getFirst().jornada()).isEqualTo(Optional.of(FINALIZADA));
    }

    @Test
    void registroForaDoPeriodoDepoisDeHojeOuRepetidoERecusado() {
        RegistroDoDia daSemanaSeguinte =
                registro(SEGUNDA.plusDays(7), FINALIZADA, ContagemPorSituacao.VAZIA, ContagemPorSituacao.VAZIA);
        RegistroDoDia deAmanha =
                registro(HOJE.plusDays(1), FINALIZADA, ContagemPorSituacao.VAZIA, ContagemPorSituacao.VAZIA);
        RegistroDoDia deSegunda = registro(SEGUNDA, FINALIZADA, ContagemPorSituacao.VAZIA, ContagemPorSituacao.VAZIA);

        assertThatThrownBy(() -> ResumoDoPeriodo.de(semanaPassada(), List.of(daSemanaSeguinte), HOJE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResumoDoPeriodo.de(Periodo.contendo(SEMANA, HOJE, HOJE), List.of(deAmanha), HOJE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResumoDoPeriodo.de(semanaPassada(), List.of(deSegunda, deSegunda), HOJE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dois registros");
    }
}
