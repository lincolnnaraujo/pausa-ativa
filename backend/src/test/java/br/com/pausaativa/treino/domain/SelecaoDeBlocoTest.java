package br.com.pausaativa.treino.domain;

import static br.com.pausaativa.treino.domain.Equipamento.APOIO_DE_FLEXAO;
import static br.com.pausaativa.treino.domain.Equipamento.HALTERES_2KG;
import static br.com.pausaativa.treino.domain.Nivel.INICIANTE;
import static br.com.pausaativa.treino.domain.Nivel.INTERMEDIARIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Seleção do bloco (spec H3, seção 3.3), com o catálogo revisado pelo usuário. */
class SelecaoDeBlocoTest {

    private static final SelecaoDeBloco SELECAO = new SelecaoDeBloco(CatalogoDeTeste.EXERCICIOS);
    private static final Duration CINCO_MIN = Duration.ofMinutes(5);
    private static final Duration DEZ_MIN = Duration.ofMinutes(10);

    private static PerfilFisico perfil(
            Nivel nivel, Set<Equipamento> equipamentos, boolean aceitaChao, Set<Articulacao> poupa) {
        return new PerfilFisico(poupa, nivel, equipamentos, aceitaChao);
    }

    /** Iniciante, sem equipamento, aceita o chão, nada a poupar: o exemplo da seção 3.3. */
    private static final PerfilFisico DO_EXEMPLO = perfil(INICIANTE, Set.of(), true, Set.of());

    private static BlocoDeExercicio montar(PerfilFisico perfil, Duration duracao, int marco, List<String> usados) {
        return SELECAO.montar(perfil, new PedidoDeBloco(duracao, marco, usados));
    }

    /** Faz o papel da Agenda: monta os 8 blocos do dia, informando a cada um o que já foi usado. */
    private static List<BlocoDeExercicio> diaInteiro(PerfilFisico perfil, Duration duracao) {
        List<String> usados = new ArrayList<>();
        List<BlocoDeExercicio> blocos = new ArrayList<>();
        for (int marco = 1; marco <= 8; marco++) {
            BlocoDeExercicio bloco = montar(perfil, duracao, marco, usados);
            blocos.add(bloco);
            usados.addAll(codigos(bloco));
        }
        return blocos;
    }

    private static List<String> codigos(BlocoDeExercicio bloco) {
        return bloco.itens().stream().map(item -> item.exercicio().codigo()).toList();
    }

    private static List<Exercicio> exercicios(List<BlocoDeExercicio> blocos) {
        return blocos.stream()
                .flatMap(b -> b.itens().stream())
                .map(ItemDoBloco::exercicio)
                .toList();
    }

    @Test
    void montaOExemploDaSpecComAEstimativaDe278Segundos() {
        BlocoDeExercicio bloco = montar(DO_EXEMPLO, CINCO_MIN, 1, List.of());

        assertThat(codigos(bloco))
                .containsExactly(
                        "sentar-e-levantar",
                        "flexao-na-parede",
                        "anjo-na-parede",
                        "prancha",
                        "ponte-de-gluteo",
                        "mobilidade-toracica");
        assertThat(bloco.itens())
                .extracting(item -> item.quantidade().texto())
                .containsExactly(
                        "10 repetições", "10 repetições", "8 repetições", "20 s", "12 repetições", "8 por lado");
        assertThat(bloco.estimativa()).isEqualTo(Duration.ofSeconds(278));
        assertThat(bloco.duracao()).isEqualTo(CINCO_MIN);
    }

    @Test
    void cenario1PoupaJoelhoESemHalteresNuncaRecebeAgachamentoAfundoNemHalteres() {
        PerfilFisico perfil = perfil(INTERMEDIARIO, Set.of(APOIO_DE_FLEXAO), true, Set.of(Articulacao.JOELHO));

        for (Duration duracao : List.of(CINCO_MIN, DEZ_MIN)) {
            List<Exercicio> doDia = exercicios(diaInteiro(perfil, duracao));

            assertThat(doDia)
                    .isNotEmpty()
                    .extracting(Exercicio::codigo)
                    .doesNotContain("agachamento-livre", "afundo-alternado", "bird-dog");
            assertThat(doDia).allSatisfy(exercicio -> {
                assertThat(exercicio.restricoes()).doesNotContain(Articulacao.JOELHO);
                assertThat(exercicio.equipamento()).isNotEqualTo(Optional.of(HALTERES_2KG));
            });
        }
    }

    @Test
    void cenario7RestricoesQueEsvaziamOCatalogoCompletamOBlocoComAReserva() {
        PerfilFisico poupaTudo = perfil(INICIANTE, Set.of(), false, EnumSet.allOf(Articulacao.class));

        BlocoDeExercicio bloco = montar(poupaTudo, DEZ_MIN, 1, List.of());

        // Primeiro todos os elegíveis, uma vez cada; depois só a reserva se repete.
        assertThat(codigos(bloco))
                .containsExactly(
                        "sentar-e-levantar",
                        "retracao-escapular",
                        "extensao-de-quadril",
                        "mobilidade-toracica",
                        "marcha-estacionaria",
                        "elevacao-de-panturrilha",
                        "alongamento-quadril",
                        "alongamento-punhos",
                        "mobilidade-toracica",
                        "alongamento-punhos");
        assertThat(bloco.itens()).allSatisfy(item -> {
            assertThat(item.exercicio().restricoes()).isEmpty();
            assertThat(item.exercicio().noChao()).isFalse();
        });
        assertThat(bloco.itens().subList(8, 10))
                .allSatisfy(item -> assertThat(item.exercicio().reserva()).isTrue());
        assertThat(bloco.estimativa()).isEqualTo(Duration.ofSeconds(597));
    }

    @Test
    void naoRepeteExercicioNoBlocoEnquantoHouverElegivelDeFora() {
        for (BlocoDeExercicio bloco :
                diaInteiro(perfil(INTERMEDIARIO, EnumSet.allOf(Equipamento.class), true, Set.of()), DEZ_MIN)) {
            assertThat(codigos(bloco)).doesNotHaveDuplicates();
        }
    }

    @Nested
    class Alternancia {

        @Test
        void cadaMarcoComecaPorUmGrupoDiferenteDoRodizio() {
            List<BlocoDeExercicio> dia = diaInteiro(DO_EXEMPLO, CINCO_MIN);

            assertThat(dia.subList(0, 5))
                    .extracting(bloco -> bloco.itens().getFirst().exercicio().grupo())
                    .containsExactly(
                            GrupoMuscular.PERNAS,
                            GrupoMuscular.PEITO,
                            GrupoMuscular.COSTAS,
                            GrupoMuscular.CORE,
                            GrupoMuscular.POSTERIOR);
        }

        @Test
        void oSegundoBlocoPrefereOsExerciciosQueOPrimeiroNaoUsou() {
            List<String> primeiro = codigos(montar(DO_EXEMPLO, CINCO_MIN, 1, List.of()));

            BlocoDeExercicio segundo = montar(DO_EXEMPLO, CINCO_MIN, 2, primeiro);

            assertThat(codigos(segundo))
                    .containsExactly(
                            "flexao-inclinada",
                            "retracao-escapular",
                            "bird-dog",
                            "extensao-de-quadril",
                            "mobilidade-cervical",
                            "anjo-na-parede");
            assertThat(segundo.estimativa()).isEqualTo(CINCO_MIN);
        }

        @Test
        void aoLongoDoDiaTodoElegivelAparece() {
            PerfilFisico perfil = perfil(INTERMEDIARIO, EnumSet.allOf(Equipamento.class), true, Set.of());

            Set<String> doDia = exercicios(diaInteiro(perfil, DEZ_MIN)).stream()
                    .map(Exercicio::codigo)
                    .collect(Collectors.toSet());

            assertThat(doDia).hasSize(CatalogoDeTeste.EXERCICIOS.size());
        }
    }

    @Nested
    class PorNivel {

        @Test
        void iniciantePegaAQuantidadeDeInicianteENuncaRecebeOsExerciciosSoDoIntermediario() {
            List<BlocoDeExercicio> dia =
                    diaInteiro(perfil(INICIANTE, EnumSet.allOf(Equipamento.class), true, Set.of()), DEZ_MIN);

            assertThat(exercicios(dia))
                    .extracting(Exercicio::codigo)
                    .doesNotContain("afundo-alternado", "flexao-com-apoio");
            assertThat(dia.getFirst().itens().getFirst().quantidade().texto()).isEqualTo("10 repetições");
        }

        @Test
        void intermediarioPegaAQuantidadeMaiorERecebeAfundoEFlexaoComApoio() {
            List<BlocoDeExercicio> dia =
                    diaInteiro(perfil(INTERMEDIARIO, EnumSet.allOf(Equipamento.class), true, Set.of()), DEZ_MIN);

            assertThat(exercicios(dia)).extracting(Exercicio::codigo).contains("afundo-alternado", "flexao-com-apoio");
            assertThat(dia.getFirst().itens().getFirst().quantidade().texto()).isEqualTo("15 repetições");
        }
    }

    @Test
    void semAceitarOChaoNenhumExercicioDeChaoEntra() {
        List<BlocoDeExercicio> dia =
                diaInteiro(perfil(INTERMEDIARIO, EnumSet.allOf(Equipamento.class), false, Set.of()), DEZ_MIN);

        assertThat(exercicios(dia)).noneMatch(Exercicio::noChao);
    }

    @Test
    void qualquerPerfilRecebeBlocoNaoVazioDentroDaDuracao() {
        List<Set<Articulacao>> poupas = List.of(Set.of(), EnumSet.allOf(Articulacao.class), Set.of(Articulacao.OMBRO));
        List<Set<Equipamento>> equipamentos = List.of(Set.of(), EnumSet.allOf(Equipamento.class));

        for (Nivel nivel : Nivel.values()) {
            for (boolean chao : new boolean[] {true, false}) {
                for (Set<Articulacao> poupa : poupas) {
                    for (Set<Equipamento> equipamento : equipamentos) {
                        for (Duration duracao : List.of(CINCO_MIN, DEZ_MIN)) {
                            for (BlocoDeExercicio bloco :
                                    diaInteiro(perfil(nivel, equipamento, chao, poupa), duracao)) {
                                assertThat(bloco.itens()).isNotEmpty();
                                assertThat(bloco.estimativa()).isLessThanOrEqualTo(duracao);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void eDeterministicaMesmoComOCatalogoEmOutraOrdem() {
        List<Exercicio> embaralhado = new ArrayList<>(CatalogoDeTeste.EXERCICIOS);
        Collections.reverse(embaralhado);
        SelecaoDeBloco outra = new SelecaoDeBloco(embaralhado);
        PedidoDeBloco pedido = new PedidoDeBloco(DEZ_MIN, 3, List.of("prancha", "sentar-e-levantar"));

        BlocoDeExercicio primeira = SELECAO.montar(DO_EXEMPLO, pedido);

        assertThat(SELECAO.montar(DO_EXEMPLO, pedido)).isEqualTo(primeira);
        assertThat(codigos(outra.montar(DO_EXEMPLO, pedido))).isEqualTo(codigos(primeira));
    }
}
