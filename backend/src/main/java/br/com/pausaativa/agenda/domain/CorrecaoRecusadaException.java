package br.com.pausaativa.agenda.domain;

/** Corrigir um marco que não pode ser corrigido (spec H4, seção 3.3). A mensagem é exibida ao usuário. */
public class CorrecaoRecusadaException extends RegraDeNegocioException {

    private CorrecaoRecusadaException(String mensagem) {
        super(mensagem);
    }

    /** Cenário 5 da H4: só até 23:59:59 do dia em que a jornada começou (decisão D5). */
    static CorrecaoRecusadaException foraDoDia() {
        return new CorrecaoRecusadaException("Só dá para corrigir os lembretes de hoje.");
    }

    /** Decisão D4: agendado e pendente ainda pedem resposta; não entregue e não concluído não foram respondidos. */
    static CorrecaoRecusadaException situacao() {
        return new CorrecaoRecusadaException("Só dá para corrigir um lembrete concluído ou com falha.");
    }
}
