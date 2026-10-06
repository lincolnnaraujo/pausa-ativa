package br.com.pausaativa.agenda.adapter.in.web;

import br.com.pausaativa.agenda.domain.StatusMarco;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo do {@code POST /api/v1/marcos/{id}/correcao} (spec H4, seção 6). */
@Schema(name = "Correcao", requiredProperties = "status")
record CorrecaoRequisicao(
        @Schema(description = "A situação correta do lembrete: concluído ou falha")
        SituacaoCorrigida status) {

    /** Só concluído e falha se corrigem (decisão D4 da spec H4). */
    enum SituacaoCorrigida {
        CONCLUIDO,
        FALHA;

        StatusMarco paraDominio() {
            return StatusMarco.valueOf(name());
        }
    }

    StatusMarco paraDominio() {
        if (status == null) {
            throw new CorrecaoInvalidaException("Informe o status correto: CONCLUIDO ou FALHA.");
        }
        return status.paraDominio();
    }
}
