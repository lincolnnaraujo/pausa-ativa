package br.com.pausaativa.treino.domain;

/** Como a quantidade de um exercício é contada (spec H3, seção 3.2). */
public enum FormaDeQuantidade {
    REPETICOES,
    /** Repetições de cada lado: "6 por lado" são 12 no total. */
    POR_LADO,
    SEGUNDOS,
    /** Segundos de cada lado. */
    SEGUNDOS_POR_LADO
}
