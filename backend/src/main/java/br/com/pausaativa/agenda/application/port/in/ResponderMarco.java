package br.com.pausaativa.agenda.application.port.in;

import java.util.UUID;

/** Repetir a mesma resposta devolve a situação atual, sem erro (Cenário 6). */
public interface ResponderMarco {

    SituacaoDaJornada concluir(UUID marcoId);

    SituacaoDaJornada falhar(UUID marcoId);
}
