package br.com.pausaativa.treino.domain;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * O que o usuário pode fazer (spec H3, seção 3.1). Um perfil só, porque o usuário é único.
 *
 * @param articulacoesPoupadas nenhuma, uma ou várias
 * @param equipamentos os que o usuário tem; cadeira e mesa contam como disponíveis sem estar aqui
 * @param aceitaChao se exercícios deitado ou de quatro podem entrar no bloco
 */
public record PerfilFisico(
        Set<Articulacao> articulacoesPoupadas, Nivel nivel, Set<Equipamento> equipamentos, boolean aceitaChao) {

    public PerfilFisico {
        articulacoesPoupadas = Conjuntos.copia(articulacoesPoupadas, Articulacao.class);
        Objects.requireNonNull(nivel, "nivel");
        equipamentos = Conjuntos.copia(equipamentos, Equipamento.class);
    }

    boolean dispoeDe(Equipamento equipamento) {
        return equipamento.sempreDisponivel() || equipamentos.contains(equipamento);
    }

    boolean poupaAlgumaDe(Set<Articulacao> articulacoes) {
        return !Collections.disjoint(articulacoesPoupadas, articulacoes);
    }
}
