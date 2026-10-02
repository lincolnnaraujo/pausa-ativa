package br.com.pausaativa.agenda.domain;

import java.util.List;

/**
 * Resultado de avançar a jornada até um instante.
 *
 * @param disparados marcos que viraram {@code PENDENTE} agora e precisam ser notificados
 * @param houveMudanca se a jornada mudou e precisa ser gravada
 */
public record Avanco(List<Marco> disparados, boolean houveMudanca) {

    static final Avanco NENHUM = new Avanco(List.of(), false);
}
