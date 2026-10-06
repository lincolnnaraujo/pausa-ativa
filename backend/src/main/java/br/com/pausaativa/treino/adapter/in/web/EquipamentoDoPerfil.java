package br.com.pausaativa.treino.adapter.in.web;

import br.com.pausaativa.treino.domain.Equipamento;
import io.swagger.v3.oas.annotations.media.Schema;

/** Os equipamentos que o formulário oferece. Cadeira e mesa já contam como disponíveis. */
@Schema(name = "EquipamentoDoPerfil")
enum EquipamentoDoPerfil {
    APOIO_DE_FLEXAO(Equipamento.APOIO_DE_FLEXAO),
    HALTERES_2KG(Equipamento.HALTERES_2KG);

    private final Equipamento equipamento;

    EquipamentoDoPerfil(Equipamento equipamento) {
        this.equipamento = equipamento;
    }

    Equipamento paraDominio() {
        return equipamento;
    }

    static EquipamentoDoPerfil de(Equipamento equipamento) {
        return valueOf(equipamento.name());
    }
}
