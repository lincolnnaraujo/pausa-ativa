package br.com.pausaativa.treino.domain;

import static br.com.pausaativa.treino.domain.FormaDeQuantidade.POR_LADO;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.REPETICOES;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.SEGUNDOS;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.SEGUNDOS_POR_LADO;
import static br.com.pausaativa.treino.domain.Nivel.INICIANTE;
import static br.com.pausaativa.treino.domain.Nivel.INTERMEDIARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Quantidades, exercícios, perfil e pedido: as peças da seleção (spec H3, seções 3.1 a 3.3). */
class CatalogoEPerfilTest {

    private static Exercicio exercicio(
            Integer iniciante, Equipamento equipamento, boolean noChao, Set<Articulacao> restricoes, boolean reserva) {
        return new Exercicio(
                "teste",
                "Teste",
                GrupoMuscular.PERNAS,
                1,
                "Como fazer",
                REPETICOES,
                iniciante,
                12,
                equipamento,
                noChao,
                restricoes,
                reserva);
    }

    private static final PerfilFisico LIVRE = new PerfilFisico(Set.of(), INTERMEDIARIO, Set.of(), true);

    @Nested
    class Quantidades {

        @Test
        void estimaTresSegundosPorRepeticaoEDobraPorLado() {
            assertThat(new Quantidade(REPETICOES, 10).execucao()).isEqualTo(Duration.ofSeconds(30));
            assertThat(new Quantidade(POR_LADO, 6).execucao()).isEqualTo(Duration.ofSeconds(36));
            assertThat(new Quantidade(SEGUNDOS, 20).execucao()).isEqualTo(Duration.ofSeconds(20));
            assertThat(new Quantidade(SEGUNDOS_POR_LADO, 30).execucao()).isEqualTo(Duration.ofSeconds(60));
        }

        @Test
        void textoParaATela() {
            assertThat(new Quantidade(REPETICOES, 10).texto()).isEqualTo("10 repetições");
            assertThat(new Quantidade(REPETICOES, 1).texto()).isEqualTo("1 repetição");
            assertThat(new Quantidade(POR_LADO, 6).texto()).isEqualTo("6 por lado");
            assertThat(new Quantidade(SEGUNDOS, 20).texto()).isEqualTo("20 s");
            assertThat(new Quantidade(SEGUNDOS_POR_LADO, 30).texto()).isEqualTo("30 s por lado");
        }

        @Test
        void itemSomaQuinzeSegundosDeTroca() {
            ItemDoBloco item =
                    new ItemDoBloco(exercicio(10, null, false, Set.of(), false), new Quantidade(REPETICOES, 10));

            assertThat(item.estimativa()).isEqualTo(Duration.ofSeconds(45));
        }

        @Test
        void quantidadeZeroOuNegativaERecusada() {
            assertThatThrownBy(() -> new Quantidade(REPETICOES, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> exercicio(0, null, false, Set.of(), false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Elegibilidade {

        @Test
        void restricaoEmComumComOPerfilExclui() {
            Exercicio poupaJoelho = exercicio(10, null, false, Set.of(Articulacao.JOELHO), false);

            assertThat(poupaJoelho.elegivelPara(LIVRE)).isTrue();
            assertThat(poupaJoelho.elegivelPara(new PerfilFisico(
                            Set.of(Articulacao.JOELHO, Articulacao.OMBRO), INTERMEDIARIO, Set.of(), true)))
                    .isFalse();
            assertThat(poupaJoelho.elegivelPara(
                            new PerfilFisico(Set.of(Articulacao.OMBRO), INTERMEDIARIO, Set.of(), true)))
                    .isTrue();
        }

        @Test
        void equipamentoPrecisaEstarNoPerfilMenosCadeiraEMesa() {
            PerfilFisico comHalteres =
                    new PerfilFisico(Set.of(), INTERMEDIARIO, Set.of(Equipamento.HALTERES_2KG), true);

            assertThat(exercicio(10, Equipamento.HALTERES_2KG, false, Set.of(), false)
                            .elegivelPara(LIVRE))
                    .isFalse();
            assertThat(exercicio(10, Equipamento.HALTERES_2KG, false, Set.of(), false)
                            .elegivelPara(comHalteres))
                    .isTrue();
            assertThat(exercicio(10, Equipamento.CADEIRA, false, Set.of(), false)
                            .elegivelPara(LIVRE))
                    .isTrue();
            assertThat(exercicio(10, Equipamento.MESA, false, Set.of(), false).elegivelPara(LIVRE))
                    .isTrue();
        }

        @Test
        void soDoIntermediarioNaoEntraParaIniciante() {
            Exercicio soIntermediario = exercicio(null, null, false, Set.of(), false);

            assertThat(soIntermediario.quantidadePara(INICIANTE)).isEmpty();
            assertThat(soIntermediario.quantidadePara(INTERMEDIARIO)).contains(new Quantidade(REPETICOES, 12));
            assertThat(soIntermediario.elegivelPara(new PerfilFisico(Set.of(), INICIANTE, Set.of(), true)))
                    .isFalse();
            assertThat(soIntermediario.elegivelPara(LIVRE)).isTrue();
        }

        @Test
        void exercicioNoChaoSoEntraSeOPerfilAceita() {
            Exercicio noChao = exercicio(10, null, true, Set.of(), false);

            assertThat(noChao.elegivelPara(LIVRE)).isTrue();
            assertThat(noChao.elegivelPara(new PerfilFisico(Set.of(), INTERMEDIARIO, Set.of(), false)))
                    .isFalse();
        }

        @Test
        void reservaQueNaoServeParaQualquerPerfilERecusada() {
            assertThat(exercicio(10, null, false, Set.of(), true).reserva()).isTrue();
            assertThatThrownBy(() -> exercicio(10, null, false, Set.of(Articulacao.CERVICAL), true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("qualquer perfil");
            assertThatThrownBy(() -> exercicio(10, Equipamento.MESA, false, Set.of(), true))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> exercicio(10, null, true, Set.of(), true))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> exercicio(null, null, false, Set.of(), true))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void catalogoTemQuatroReservasEVinteEDoisExercicios() {
            assertThat(CatalogoDeTeste.EXERCICIOS).hasSize(22);
            assertThat(CatalogoDeTeste.EXERCICIOS)
                    .filteredOn(Exercicio::reserva)
                    .extracting(Exercicio::codigo)
                    .containsExactly(
                            "marcha-estacionaria", "mobilidade-toracica", "alongamento-quadril", "alongamento-punhos");
        }
    }

    @Nested
    class Construcao {

        @Test
        void exercicioSemCampoObrigatorioERecusado() {
            assertThatThrownBy(() -> new Exercicio(
                            " ",
                            "Nome",
                            GrupoMuscular.PERNAS,
                            1,
                            "Como",
                            REPETICOES,
                            10,
                            12,
                            null,
                            false,
                            Set.of(),
                            false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("codigo");
        }

        @Test
        void perfilGuardaCopiasImutaveis() {
            Set<Articulacao> poupa = EnumSet.of(Articulacao.PUNHO);
            PerfilFisico perfil = new PerfilFisico(poupa, INICIANTE, Set.of(), false);

            poupa.add(Articulacao.OMBRO);

            assertThat(perfil.articulacoesPoupadas()).containsExactly(Articulacao.PUNHO);
            assertThatThrownBy(() -> perfil.articulacoesPoupadas().add(Articulacao.JOELHO))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void pedidoComDuracaoOuMarcoInvalidoERecusado() {
            assertThatThrownBy(() -> new PedidoDeBloco(Duration.ZERO, 1, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PedidoDeBloco(Duration.ofMinutes(5), 0, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void exercicioMostraOCodigoNoToString() {
            assertThat(exercicio(10, null, false, Set.of(), false)).hasToString("teste");
        }
    }
}
