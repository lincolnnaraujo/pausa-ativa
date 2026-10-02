package br.com.pausaativa.agenda.application.port.out;

import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.domain.EventoDaJornada;
import java.util.List;

/**
 * Publicado (evento do Spring) sempre que uma jornada é gravada com mudanças. Os adapters de SSE e de
 * métricas o recebem depois do commit, para nunca notificar algo que foi desfeito.
 *
 * @param situacao a jornada como ficou
 * @param eventos o que aconteceu com os marcos nesta alteração, na ordem
 */
public record JornadaAlterada(SituacaoDaJornada situacao, List<EventoDaJornada> eventos) {}
