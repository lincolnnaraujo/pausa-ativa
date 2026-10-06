package br.com.pausaativa.historico.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Concluídos ÷ (concluídos + falhas) de uma categoria (spec H4, seção 3.1). Só existe com pelo menos um
 * concluído ou uma falha: sem eles, o histórico mostra "sem dados", e não 0%.
 */
public record TaxaDeSucesso(int concluidos, int falhas) {

    /** A meta do épico: pelo menos 80% dos marcos concluídos. */
    public static final int META_PERCENTUAL = 80;

    public TaxaDeSucesso {
        if (concluidos < 0 || falhas < 0) {
            throw new IllegalArgumentException("Contagens negativas: %d e %d".formatted(concluidos, falhas));
        }
        if (concluidos + falhas == 0) {
            throw new IllegalArgumentException("Sem concluídos nem falhas, não há taxa");
        }
    }

    /**
     * Percentual com uma casa, truncado: 85,714… vira 85,7, e 79,96 vira 79,9. A tela nunca mostra 80,0%
     * numa taxa abaixo da meta.
     */
    public BigDecimal percentual() {
        return BigDecimal.valueOf(concluidos * 100L).divide(BigDecimal.valueOf(respondidos()), 1, RoundingMode.DOWN);
    }

    /** Compara a fração exata, e não o percentual truncado. */
    public boolean metaAtingida() {
        return concluidos * 100L >= META_PERCENTUAL * respondidos();
    }

    private long respondidos() {
        return (long) concluidos + falhas;
    }
}
