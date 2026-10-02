package br.com.pausaativa.agenda.application.port.in;

import java.util.UUID;

/** O frontend avisa que o marco chegou. Sem esse aviso, o prazo vencido vira NAO_ENTREGUE (Cenário 5). */
public interface ConfirmarRecebimento {

    void confirmar(UUID marcoId);
}
