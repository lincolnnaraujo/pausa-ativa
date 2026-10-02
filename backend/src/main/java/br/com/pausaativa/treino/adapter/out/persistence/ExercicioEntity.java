package br.com.pausaativa.treino.adapter.out.persistence;

import br.com.pausaativa.treino.domain.Articulacao;
import br.com.pausaativa.treino.domain.Equipamento;
import br.com.pausaativa.treino.domain.Exercicio;
import br.com.pausaativa.treino.domain.FormaDeQuantidade;
import br.com.pausaativa.treino.domain.GrupoMuscular;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Immutable;

/** Linha do catálogo. Só leitura: o catálogo muda apenas por migração. */
@Entity
@Immutable
@Table(name = "exercicio")
class ExercicioEntity {

    @Id
    private String codigo;

    private String nome;

    @Enumerated(EnumType.STRING)
    private GrupoMuscular grupo;

    private int ordem;
    private String instrucao;

    @Enumerated(EnumType.STRING)
    private FormaDeQuantidade forma;

    private Integer quantidadeIniciante;
    private int quantidadeIntermediario;

    @Enumerated(EnumType.STRING)
    private Equipamento equipamento;

    private boolean noChao;
    private boolean reserva;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exercicio_restricao", joinColumns = @JoinColumn(name = "exercicio_codigo"))
    @Column(name = "articulacao")
    @Enumerated(EnumType.STRING)
    private Set<Articulacao> restricoes = new HashSet<>();

    protected ExercicioEntity() {}

    /** O construtor do domínio confere a invariante da reserva também para o que vem do banco. */
    Exercicio paraDominio() {
        return new Exercicio(
                codigo,
                nome,
                grupo,
                ordem,
                instrucao,
                forma,
                quantidadeIniciante,
                quantidadeIntermediario,
                equipamento,
                noChao,
                restricoes,
                reserva);
    }
}
