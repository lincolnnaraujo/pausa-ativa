package br.com.pausaativa.agenda.application.port.out;

import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.ExercicioProposto;
import java.util.List;

/** O que a Agenda precisa do Treino: saber se há perfil e montar o bloco de um marco (spec H3, seção 4). */
public interface MontadorDeBlocos {

    /** Sem perfil, o dia não começa (Cenário 6 da H3). */
    boolean perfilPreenchido();

    /** Exercícios do bloco, na ordem em que devem ser feitos, com o perfil atual. */
    List<ExercicioProposto> montar(PedidoDeBloco pedido);

    /** Como {@link #montar}, com um perfil de exemplo: os dados de exemplo da demonstração (spec H4, seção 9). */
    List<ExercicioProposto> montarDeExemplo(PedidoDeBloco pedido);

    /**
     * @param numeroDoMarco sequência do marco de exercício, de 1 a 8
     * @param propostosNoDia códigos dos exercícios dos blocos anteriores do dia, do mais antigo para o
     *     mais recente
     */
    record PedidoDeBloco(DuracaoDoBloco duracao, int numeroDoMarco, List<String> propostosNoDia) {

        public PedidoDeBloco {
            propostosNoDia = List.copyOf(propostosNoDia);
        }
    }
}
