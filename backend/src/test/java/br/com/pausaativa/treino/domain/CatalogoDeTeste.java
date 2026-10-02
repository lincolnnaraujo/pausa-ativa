package br.com.pausaativa.treino.domain;

import static br.com.pausaativa.treino.domain.Articulacao.CERVICAL;
import static br.com.pausaativa.treino.domain.Articulacao.JOELHO;
import static br.com.pausaativa.treino.domain.Articulacao.LOMBAR;
import static br.com.pausaativa.treino.domain.Articulacao.OMBRO;
import static br.com.pausaativa.treino.domain.Articulacao.PUNHO;
import static br.com.pausaativa.treino.domain.Equipamento.APOIO_DE_FLEXAO;
import static br.com.pausaativa.treino.domain.Equipamento.CADEIRA;
import static br.com.pausaativa.treino.domain.Equipamento.HALTERES_2KG;
import static br.com.pausaativa.treino.domain.Equipamento.MESA;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.POR_LADO;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.REPETICOES;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.SEGUNDOS;
import static br.com.pausaativa.treino.domain.FormaDeQuantidade.SEGUNDOS_POR_LADO;
import static br.com.pausaativa.treino.domain.GrupoMuscular.BRACOS;
import static br.com.pausaativa.treino.domain.GrupoMuscular.CARDIO_LEVE;
import static br.com.pausaativa.treino.domain.GrupoMuscular.CORE;
import static br.com.pausaativa.treino.domain.GrupoMuscular.COSTAS;
import static br.com.pausaativa.treino.domain.GrupoMuscular.MOBILIDADE;
import static br.com.pausaativa.treino.domain.GrupoMuscular.OMBROS;
import static br.com.pausaativa.treino.domain.GrupoMuscular.PEITO;
import static br.com.pausaativa.treino.domain.GrupoMuscular.PERNAS;
import static br.com.pausaativa.treino.domain.GrupoMuscular.POSTERIOR;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Os 22 exercícios do catálogo do épico (seção "Treino"), revisado pelo usuário em 2026-10-02. */
final class CatalogoDeTeste {

    static final List<Exercicio> EXERCICIOS;

    static {
        Construtor c = new Construtor();
        c.add("sentar-e-levantar", "Sentar e levantar da cadeira", PERNAS, REPETICOES, 10, 15, CADEIRA, false);
        c.add("agachamento-livre", "Agachamento livre", PERNAS, REPETICOES, 10, 15, null, false, JOELHO);
        c.add("afundo-alternado", "Afundo alternado", PERNAS, POR_LADO, null, 8, null, false, JOELHO);
        c.add("elevacao-de-panturrilha", "Elevação de panturrilha", PERNAS, REPETICOES, 15, 20, null, false);
        c.add("ponte-de-gluteo", "Ponte de glúteo", POSTERIOR, REPETICOES, 12, 15, null, true);
        c.add(
                "extensao-de-quadril",
                "Extensão de quadril em pé, apoiado na mesa",
                POSTERIOR,
                POR_LADO,
                10,
                12,
                MESA,
                false);
        c.add("flexao-na-parede", "Flexão na parede", PEITO, REPETICOES, 10, 15, null, false, OMBRO, PUNHO);
        c.add("flexao-inclinada", "Flexão inclinada na mesa", PEITO, REPETICOES, 8, 12, MESA, false, OMBRO, PUNHO);
        c.add("flexao-com-apoio", "Flexão com apoio", PEITO, REPETICOES, null, 8, APOIO_DE_FLEXAO, true, OMBRO);
        c.add("prancha", "Prancha", CORE, SEGUNDOS, 20, 40, null, true, OMBRO, PUNHO, LOMBAR);
        c.add("bird-dog", "Bird-dog", CORE, POR_LADO, 6, 10, null, true, PUNHO, JOELHO);
        c.add("remada-curvada", "Remada curvada", COSTAS, REPETICOES, 12, 15, HALTERES_2KG, false, LOMBAR);
        c.add("anjo-na-parede", "Anjo na parede", COSTAS, REPETICOES, 8, 12, null, false, OMBRO);
        c.add("retracao-escapular", "Retração escapular em pé", COSTAS, REPETICOES, 12, 15, null, false);
        c.add(
                "desenvolvimento",
                "Desenvolvimento de ombros",
                OMBROS,
                REPETICOES,
                10,
                12,
                HALTERES_2KG,
                false,
                OMBRO,
                CERVICAL);
        c.add("elevacao-lateral", "Elevação lateral", OMBROS, REPETICOES, 10, 12, HALTERES_2KG, false, OMBRO, CERVICAL);
        c.add("rosca-direta", "Rosca direta", BRACOS, REPETICOES, 12, 15, HALTERES_2KG, false, PUNHO);
        c.reserva("marcha-estacionaria", "Marcha estacionária", CARDIO_LEVE, SEGUNDOS, 60, 90);
        c.reserva("mobilidade-toracica", "Mobilidade torácica", MOBILIDADE, POR_LADO, 8, 10);
        c.add("mobilidade-cervical", "Mobilidade cervical", MOBILIDADE, SEGUNDOS, 30, 45, null, false, CERVICAL);
        c.reserva("alongamento-quadril", "Alongamento de flexores do quadril", MOBILIDADE, SEGUNDOS_POR_LADO, 30, 30);
        c.reserva("alongamento-punhos", "Alongamento de punhos e antebraços", MOBILIDADE, SEGUNDOS, 30, 30);
        EXERCICIOS = List.copyOf(c.exercicios);
    }

    private CatalogoDeTeste() {}

    private static final class Construtor {

        private final List<Exercicio> exercicios = new ArrayList<>();

        void add(
                String codigo,
                String nome,
                GrupoMuscular grupo,
                FormaDeQuantidade forma,
                Integer iniciante,
                int intermediario,
                Equipamento equipamento,
                boolean noChao,
                Articulacao... restricoes) {
            exercicios.add(new Exercicio(
                    codigo,
                    nome,
                    grupo,
                    exercicios.size() + 1,
                    "Como fazer: " + nome,
                    forma,
                    iniciante,
                    intermediario,
                    equipamento,
                    noChao,
                    Set.of(restricoes),
                    false));
        }

        void reserva(
                String codigo,
                String nome,
                GrupoMuscular grupo,
                FormaDeQuantidade forma,
                int iniciante,
                int intermediario) {
            exercicios.add(new Exercicio(
                    codigo,
                    nome,
                    grupo,
                    exercicios.size() + 1,
                    "Como fazer: " + nome,
                    forma,
                    iniciante,
                    intermediario,
                    null,
                    false,
                    Set.of(),
                    true));
        }
    }
}
