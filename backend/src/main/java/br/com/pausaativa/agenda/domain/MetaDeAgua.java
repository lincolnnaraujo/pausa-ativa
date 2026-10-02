package br.com.pausaativa.agenda.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Meta de água da jornada, em mililitros.
 *
 * <p>O teto de 6.000 ml (750 ml/h em 8 h) fica abaixo da capacidade de 0,8 a 1,0 L/h dos rins
 * citada no épico (decisão D4 da spec H2).
 */
public record MetaDeAgua(int mililitros) {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 6_000;
    public static final MetaDeAgua PADRAO = new MetaDeAgua(3_000);

    public MetaDeAgua {
        if (mililitros < MINIMO || mililitros > MAXIMO) {
            throw new MetaDeAguaInvalidaException(mililitros);
        }
    }

    /** Volume de cada marco. Com 16 marcos a divisão é exata (187,5 ml para 3.000 ml). */
    BigDecimal volumePorMarco(int quantidadeDeMarcos) {
        return BigDecimal.valueOf(mililitros).divide(BigDecimal.valueOf(quantidadeDeMarcos), 4, RoundingMode.HALF_EVEN);
    }
}
