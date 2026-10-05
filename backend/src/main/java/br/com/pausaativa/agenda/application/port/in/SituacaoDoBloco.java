package br.com.pausaativa.agenda.application.port.in;

import br.com.pausaativa.agenda.domain.BlocoDoMarco;
import br.com.pausaativa.agenda.domain.ExercicioProposto;
import java.util.List;

/** Retrato do bloco de um marco de exercício, como foi proposto no disparo. */
public record SituacaoDoBloco(
        int duracaoMin, long segundosEstimados, boolean compensaAdiamento, List<Exercicio> exercicios) {

    /** @param quantidade texto para a tela: "10 repetições", "6 por lado", "20 s" */
    public record Exercicio(String codigo, String nome, String grupo, String quantidade, String instrucao) {}

    static SituacaoDoBloco de(BlocoDoMarco bloco) {
        return new SituacaoDoBloco(
                bloco.duracao().minutos(),
                bloco.estimativa().toSeconds(),
                bloco.compensaAdiamento(),
                bloco.exercicios().stream().map(SituacaoDoBloco::exercicio).toList());
    }

    private static Exercicio exercicio(ExercicioProposto proposto) {
        return new Exercicio(
                proposto.codigo(), proposto.nome(), proposto.grupo(), proposto.quantidade(), proposto.instrucao());
    }
}
