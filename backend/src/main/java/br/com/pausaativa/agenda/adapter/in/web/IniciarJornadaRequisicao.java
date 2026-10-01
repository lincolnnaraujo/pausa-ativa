package br.com.pausaativa.agenda.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "IniciarJornada")
record IniciarJornadaRequisicao(
        @Schema(
                description = "Meta de água do dia, em ml. Sem ela, 3.000 ml.",
                minimum = "1",
                maximum = "6000",
                defaultValue = "3000",
                example = "3000")
        Integer metaAguaMl) {}
