package br.com.pausaativa.treino.adapter.in.web;

import br.com.pausaativa.treino.domain.Articulacao;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "Perfil",
        description = "Perfil físico do usuário. Vale para os blocos montados depois da última alteração.",
        requiredProperties = {"articulacoesPoupadas", "nivel", "equipamentos", "aceitaChao"})
record PerfilResposta(
        List<Articulacao> articulacoesPoupadas,
        Nivel nivel,
        List<EquipamentoDoPerfil> equipamentos,
        boolean aceitaChao) {

    static PerfilResposta de(PerfilFisico perfil) {
        return new PerfilResposta(
                List.copyOf(perfil.articulacoesPoupadas()),
                perfil.nivel(),
                perfil.equipamentos().stream().map(EquipamentoDoPerfil::de).toList(),
                perfil.aceitaChao());
    }
}
