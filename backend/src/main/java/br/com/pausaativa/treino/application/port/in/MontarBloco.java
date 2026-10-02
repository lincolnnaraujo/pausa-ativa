package br.com.pausaativa.treino.application.port.in;

import java.time.Duration;
import java.util.List;

/**
 * Monta o bloco de exercício de um marco com o perfil atual (spec H3, seção 3.3). É a porta que a Agenda
 * usa. Os tipos são deste pacote, e não do domínio do Treino, porque a Agenda só enxerga o
 * {@code application.port.in} de outro módulo (regra R4).
 */
public interface MontarBloco {

    /** @throws IllegalStateException sem perfil preenchido: a Agenda não inicia o dia nesse caso */
    BlocoMontado montar(Pedido pedido);

    /**
     * @param numeroDoMarco 1 a 8
     * @param usadosNoDia códigos dos exercícios dos blocos anteriores do dia, do mais antigo para o
     *     mais recente
     */
    record Pedido(Duration duracao, int numeroDoMarco, List<String> usadosNoDia) {

        public Pedido {
            usadosNoDia = List.copyOf(usadosNoDia);
        }
    }

    record BlocoMontado(Duration duracao, Duration estimativa, List<Item> itens) {

        public BlocoMontado {
            itens = List.copyOf(itens);
        }
    }

    /**
     * Cópia do exercício no instante da montagem: o histórico mostra o que foi proposto de fato (D1).
     *
     * @param quantidade texto para a tela: "10 repetições", "6 por lado", "20 s"
     */
    record Item(
            String codigo, String exercicio, String grupo, String quantidade, Duration estimativa, String instrucao) {}
}
