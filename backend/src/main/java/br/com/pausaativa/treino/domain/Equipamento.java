package br.com.pausaativa.treino.domain;

/** Equipamento que um exercício exige. Cadeira e mesa estão em todo home office (spec H3, seção 3.1). */
public enum Equipamento {
    CADEIRA(true),
    MESA(true),
    APOIO_DE_FLEXAO(false),
    HALTERES_2KG(false);

    private final boolean sempreDisponivel;

    Equipamento(boolean sempreDisponivel) {
        this.sempreDisponivel = sempreDisponivel;
    }

    public boolean sempreDisponivel() {
        return sempreDisponivel;
    }
}
