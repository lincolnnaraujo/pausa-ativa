package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.application.port.in.SituacaoDoBloco;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "Bloco",
        description = "Bloco de exercício do marco, como foi proposto no disparo.",
        requiredProperties = {"duracaoMin", "segundosEstimados", "compensaAdiamento", "itens"})
record BlocoResposta(
        @Schema(description = "Duração do bloco: 5 ou 10 min", example = "5")
        int duracaoMin,

        @Schema(description = "Soma das estimativas dos exercícios, com as trocas", example = "278")
        long segundosEstimados,

        @Schema(description = "Se o bloco compensa o anterior, que foi adiado; nesse caso tem 10 min")
        boolean compensaAdiamento,

        @Schema(description = "Exercícios na ordem em que devem ser feitos")
        List<ItemResposta> itens) {

    @Schema(
            name = "ItemDoBloco",
            requiredProperties = {"exercicio", "grupo", "quantidade", "instrucao"})
    record ItemResposta(
            @Schema(example = "Sentar e levantar da cadeira")
            String exercicio,

            @Schema(example = "Pernas") String grupo,

            @Schema(description = "\"10 repetições\", \"6 por lado\" ou \"20 s\"", example = "10 repetições")
            String quantidade,

            @Schema(example = "Sente e levante da cadeira sem usar as mãos, com os pés na largura do quadril.")
            String instrucao) {}

    static BlocoResposta de(SituacaoDoBloco bloco) {
        return new BlocoResposta(
                bloco.duracaoMin(),
                bloco.segundosEstimados(),
                bloco.compensaAdiamento(),
                bloco.exercicios().stream()
                        .map(exercicio -> new ItemResposta(
                                exercicio.nome(), exercicio.grupo(), exercicio.quantidade(), exercicio.instrucao()))
                        .toList());
    }
}
