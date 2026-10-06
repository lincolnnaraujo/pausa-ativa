package br.com.pausaativa.agenda.application.port.in;

import br.com.pausaativa.agenda.domain.StatusMarco;
import java.util.UUID;

/** Correção no mesmo dia (spec H4, seção 3.3). Corrigir para a situação atual devolve a mesma situação. */
public interface CorrigirMarco {

    /**
     * @param correta {@code CONCLUIDO} ou {@code FALHA}
     * @throws br.com.pausaativa.agenda.domain.CorrecaoRecusadaException depois do dia da jornada, ou se o
     *     marco não é concluído nem falha
     */
    SituacaoDaJornada corrigir(UUID marcoId, StatusMarco correta);
}
