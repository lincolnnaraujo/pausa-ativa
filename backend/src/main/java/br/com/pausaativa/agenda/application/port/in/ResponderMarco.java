package br.com.pausaativa.agenda.application.port.in;

import java.util.UUID;

/** Repetir a mesma resposta devolve a situação atual, sem erro (Cenário 6 da H2). */
public interface ResponderMarco {

    SituacaoDaJornada concluir(UUID marcoId);

    SituacaoDaJornada falhar(UUID marcoId);

    /**
     * Adia o bloco de exercício pendente; o seguinte terá 10 min (spec H3, seção 3.5).
     *
     * @throws br.com.pausaativa.agenda.domain.AdiamentoRecusadoException água, último bloco, bloco que já
     *     compensa um adiamento ou marco que não está pendente
     */
    SituacaoDaJornada adiar(UUID marcoId);
}
