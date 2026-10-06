package br.com.pausaativa.treino.adapter.in.web;

import br.com.pausaativa.treino.domain.Articulacao;
import br.com.pausaativa.treino.domain.Nivel;
import br.com.pausaativa.treino.domain.PerfilFisico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Schema(
        name = "SalvarPerfil",
        requiredProperties = {"nivel", "aceitaChao"})
record PerfilRequisicao(
        @Schema(description = "Articulações a poupar. Vazio ou ausente: nenhuma.")
        List<Articulacao> articulacoesPoupadas,

        Nivel nivel,

        @Schema(description = "Equipamentos disponíveis. Cadeira e mesa já contam como disponíveis.")
        List<EquipamentoDoPerfil> equipamentos,

        @Schema(description = "Se exercícios deitado ou de quatro podem entrar no bloco")
        Boolean aceitaChao) {

    PerfilFisico paraDominio() {
        if (nivel == null) {
            throw new PerfilInvalidoException("Informe o nível: INICIANTE ou INTERMEDIARIO.");
        }
        if (aceitaChao == null) {
            throw new PerfilInvalidoException("Informe se exercícios no chão podem entrar no bloco (aceitaChao).");
        }
        return new PerfilFisico(
                Set.copyOf(semNulos(articulacoesPoupadas)),
                nivel,
                semNulos(equipamentos).stream()
                        .map(EquipamentoDoPerfil::paraDominio)
                        .collect(Collectors.toSet()),
                aceitaChao);
    }

    private static <T> List<T> semNulos(List<T> lista) {
        return Optional.ofNullable(lista).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
