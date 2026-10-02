package br.com.pausaativa.sistema.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(
        description = "Situação do backend. Não consulta o banco; o estado do banco fica em /actuator/health.",
        requiredProperties = {"aplicacao", "versao", "agora", "fuso"})
record StatusResposta(
        @Schema(example = "pausa-ativa") String aplicacao,
        @Schema(example = "0.2.0") String versao,

        @Schema(description = "Horário do servidor no fuso de negócio", example = "2026-10-01T09:00:00-03:00")
        OffsetDateTime agora,

        @Schema(example = "America/Sao_Paulo") String fuso) {}
