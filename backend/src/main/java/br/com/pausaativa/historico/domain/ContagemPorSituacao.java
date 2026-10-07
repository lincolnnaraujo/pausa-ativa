package br.com.pausaativa.historico.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Quantos lembretes de uma categoria terminaram em cada situação. Não entregue, não concluído e em aberto
 * ficam fora da taxa e aparecem à parte (Cenário 1 da H4).
 */
public record ContagemPorSituacao(int concluidos, int falhas, int naoEntregues, int naoConcluidos, int emAberto) {

    public static final ContagemPorSituacao VAZIA = new ContagemPorSituacao(0, 0, 0, 0, 0);

    public ContagemPorSituacao {
        if (concluidos < 0 || falhas < 0 || naoEntregues < 0 || naoConcluidos < 0 || emAberto < 0) {
            throw new IllegalArgumentException("Contagem negativa");
        }
    }

    /** Esta contagem com mais {@code quantidade} lembretes na situação. */
    public ContagemPorSituacao com(Situacao situacao, int quantidade) {
        Objects.requireNonNull(situacao, "situacao");
        return switch (situacao) {
            case CONCLUIDO ->
                new ContagemPorSituacao(concluidos + quantidade, falhas, naoEntregues, naoConcluidos, emAberto);
            case FALHA ->
                new ContagemPorSituacao(concluidos, falhas + quantidade, naoEntregues, naoConcluidos, emAberto);
            case NAO_ENTREGUE ->
                new ContagemPorSituacao(concluidos, falhas, naoEntregues + quantidade, naoConcluidos, emAberto);
            case NAO_CONCLUIDO ->
                new ContagemPorSituacao(concluidos, falhas, naoEntregues, naoConcluidos + quantidade, emAberto);
            case EM_ABERTO ->
                new ContagemPorSituacao(concluidos, falhas, naoEntregues, naoConcluidos, emAberto + quantidade);
        };
    }

    public ContagemPorSituacao mais(ContagemPorSituacao outra) {
        return new ContagemPorSituacao(
                concluidos + outra.concluidos,
                falhas + outra.falhas,
                naoEntregues + outra.naoEntregues,
                naoConcluidos + outra.naoConcluidos,
                emAberto + outra.emAberto);
    }

    /** Vazia sem nenhum concluído nem falha: "sem dados", e não 0% (Cenário 6 da H4). */
    public Optional<TaxaDeSucesso> taxa() {
        return concluidos + falhas == 0 ? Optional.empty() : Optional.of(new TaxaDeSucesso(concluidos, falhas));
    }
}
