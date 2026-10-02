package br.com.pausaativa.treino.adapter.out.persistence;

import br.com.pausaativa.treino.domain.Articulacao;
import br.com.pausaativa.treino.domain.Equipamento;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** O perfil único: a linha tem sempre o id 1 ({@code ck_perfil_unico}). */
@Entity
@Table(name = "perfil_fisico")
class PerfilFisicoEntity {

    static final int ID_UNICO = 1;

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    private Nivel nivel;

    private boolean aceitaChao;
    private Instant atualizadoEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_articulacao", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "articulacao")
    @Enumerated(EnumType.STRING)
    private Set<Articulacao> articulacoesPoupadas = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "perfil_equipamento", joinColumns = @JoinColumn(name = "perfil_id"))
    @Column(name = "equipamento")
    @Enumerated(EnumType.STRING)
    private Set<Equipamento> equipamentos = new HashSet<>();

    protected PerfilFisicoEntity() {}

    static PerfilFisicoEntity novo() {
        PerfilFisicoEntity entidade = new PerfilFisicoEntity();
        entidade.id = ID_UNICO;
        return entidade;
    }

    /** Substitui os conjuntos inteiros; o Hibernate grava só a diferença. */
    void atualizarCom(PerfilFisico perfil, Instant agora) {
        nivel = perfil.nivel();
        aceitaChao = perfil.aceitaChao();
        atualizadoEm = agora;
        articulacoesPoupadas.retainAll(perfil.articulacoesPoupadas());
        articulacoesPoupadas.addAll(perfil.articulacoesPoupadas());
        equipamentos.retainAll(perfil.equipamentos());
        equipamentos.addAll(perfil.equipamentos());
    }

    PerfilFisico paraDominio() {
        return new PerfilFisico(articulacoesPoupadas, nivel, equipamentos, aceitaChao);
    }
}
