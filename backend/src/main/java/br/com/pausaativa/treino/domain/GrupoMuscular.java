package br.com.pausaativa.treino.domain;

/** Grupos na ordem do rodízio da seleção (spec H3, seção 3.3). A ordem das constantes é a do rodízio. */
public enum GrupoMuscular {
    PERNAS("Pernas"),
    PEITO("Peito"),
    COSTAS("Costas"),
    CORE("Core"),
    POSTERIOR("Posterior"),
    OMBROS("Ombros"),
    BRACOS("Braços"),
    MOBILIDADE("Mobilidade"),
    CARDIO_LEVE("Cardio leve");

    private final String nome;

    GrupoMuscular(String nome) {
        this.nome = nome;
    }

    /** Nome para a tela. */
    public String nome() {
        return nome;
    }
}
