package br.com.pausaativa.treino.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * O que a Agenda informa para montar o bloco de um marco.
 *
 * @param duracao 5 ou 10 min (o Treino não impõe o valor; quem escolhe é a jornada)
 * @param numeroDoMarco 1 a 8; decide por qual grupo o rodízio começa
 * @param usadosNoDia códigos dos exercícios dos blocos anteriores do dia, do mais antigo para o mais
 *     recente. A Agenda conhece esses blocos, e assim o Treino não precisa consultá-la.
 */
public record PedidoDeBloco(Duration duracao, int numeroDoMarco, List<String> usadosNoDia) {

    public PedidoDeBloco {
        Objects.requireNonNull(duracao, "duracao");
        if (duracao.isNegative() || duracao.isZero()) {
            throw new IllegalArgumentException("A duração do bloco precisa ser positiva: " + duracao);
        }
        if (numeroDoMarco < 1) {
            throw new IllegalArgumentException("O número do marco começa em 1: " + numeroDoMarco);
        }
        usadosNoDia = List.copyOf(usadosNoDia);
    }
}
