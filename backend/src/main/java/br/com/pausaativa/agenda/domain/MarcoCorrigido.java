package br.com.pausaativa.agenda.domain;

import java.util.UUID;

/** O usuário corrigiu a resposta de um lembrete de hoje: concluído virou falha, ou o contrário (spec H4). */
public record MarcoCorrigido(UUID marcoId, Categoria categoria, int sequencia, StatusMarco de, StatusMarco para)
        implements EventoDaJornada {}
