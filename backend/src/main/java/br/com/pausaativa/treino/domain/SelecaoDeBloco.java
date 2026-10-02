package br.com.pausaativa.treino.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Monta o bloco de exercício de um marco (spec H3, seção 3.3). É determinística: o mesmo catálogo, o
 * mesmo perfil e o mesmo pedido dão sempre o mesmo bloco.
 *
 * <ol>
 *   <li>Elegíveis: os exercícios do catálogo que o perfil pode fazer.
 *   <li>O rodízio de grupos começa num grupo diferente a cada marco.
 *   <li>Cada volta pelos grupos põe no máximo um exercício por grupo: o usado há mais tempo no dia que
 *       ainda caiba na duração.
 *   <li>Nenhum exercício se repete enquanto houver elegível de fora. Depois, só a reserva se repete.
 *   <li>A montagem termina quando uma volta inteira não acrescenta nada.
 * </ol>
 */
public final class SelecaoDeBloco {

    private static final GrupoMuscular[] GRUPOS = GrupoMuscular.values();

    private final List<Exercicio> catalogo;

    public SelecaoDeBloco(Collection<Exercicio> catalogo) {
        this.catalogo = catalogo.stream()
                .sorted(Comparator.comparingInt(Exercicio::ordem))
                .toList();
    }

    public BlocoDeExercicio montar(PerfilFisico perfil, PedidoDeBloco pedido) {
        List<Exercicio> elegiveis = catalogo.stream()
                .filter(exercicio -> exercicio.elegivelPara(perfil))
                .toList();
        Montagem montagem = new Montagem(perfil.nivel(), pedido.duracao(), pedido.usadosNoDia());

        boolean acrescentou;
        do {
            boolean soReserva = montagem.contemTodos(elegiveis);
            acrescentou = false;
            for (GrupoMuscular grupo : rodizio(pedido.numeroDoMarco())) {
                List<Exercicio> candidatos = elegiveis.stream()
                        .filter(exercicio -> exercicio.grupo() == grupo)
                        .filter(exercicio -> soReserva ? exercicio.reserva() : !montagem.contem(exercicio))
                        .sorted(montagem.doUsadoHaMaisTempo())
                        .toList();
                acrescentou |= montagem.porOPrimeiroQueCouber(candidatos);
            }
        } while (acrescentou);

        return new BlocoDeExercicio(pedido.duracao(), montagem.itens());
    }

    /** Os grupos a partir do grupo da posição (n − 1), em ciclo. */
    private static List<GrupoMuscular> rodizio(int numeroDoMarco) {
        int inicio = (numeroDoMarco - 1) % GRUPOS.length;
        List<GrupoMuscular> grupos = new ArrayList<>(GRUPOS.length);
        for (int i = 0; i < GRUPOS.length; i++) {
            grupos.add(GRUPOS[(inicio + i) % GRUPOS.length]);
        }
        return grupos;
    }

    /** Estado da montagem de um bloco: os itens, o tempo que sobra e a ordem de uso no dia. */
    private static final class Montagem {

        private final Nivel nivel;
        private final List<ItemDoBloco> itens = new ArrayList<>();
        private final Set<String> noBloco = new HashSet<>();
        /** Usos do dia, do mais antigo para o mais recente, incluindo os deste bloco. */
        private final List<String> usos;

        private Duration restante;

        Montagem(Nivel nivel, Duration duracao, List<String> usadosNoDia) {
            this.nivel = nivel;
            this.restante = duracao;
            this.usos = new ArrayList<>(usadosNoDia);
        }

        boolean contem(Exercicio exercicio) {
            return noBloco.contains(exercicio.codigo());
        }

        boolean contemTodos(List<Exercicio> exercicios) {
            return exercicios.stream().allMatch(this::contem);
        }

        /** Nunca usados primeiro (posição −1), depois do uso mais antigo ao mais recente; empate pela ordem do catálogo. */
        Comparator<Exercicio> doUsadoHaMaisTempo() {
            return Comparator.<Exercicio>comparingInt(exercicio -> usos.lastIndexOf(exercicio.codigo()))
                    .thenComparingInt(Exercicio::ordem);
        }

        boolean porOPrimeiroQueCouber(List<Exercicio> candidatos) {
            for (Exercicio exercicio : candidatos) {
                Optional<ItemDoBloco> item = exercicio.quantidadePara(nivel).map(q -> new ItemDoBloco(exercicio, q));
                if (item.isPresent() && item.get().estimativa().compareTo(restante) <= 0) {
                    itens.add(item.get());
                    noBloco.add(exercicio.codigo());
                    usos.add(exercicio.codigo());
                    restante = restante.minus(item.get().estimativa());
                    return true;
                }
            }
            return false;
        }

        List<ItemDoBloco> itens() {
            return itens;
        }
    }
}
