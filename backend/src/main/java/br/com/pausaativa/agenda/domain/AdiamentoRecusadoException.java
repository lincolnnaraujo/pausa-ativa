package br.com.pausaativa.agenda.domain;

/** Adiar um marco que não pode ser adiado (spec H3, seção 3.5). A mensagem é exibida ao usuário. */
public class AdiamentoRecusadoException extends RegraDeNegocioException {

    private AdiamentoRecusadoException(String mensagem) {
        super(mensagem);
    }

    static AdiamentoRecusadoException hidratacao() {
        return new AdiamentoRecusadoException("Só o bloco de exercício pode ser adiado.");
    }

    static AdiamentoRecusadoException naoPendente(StatusMarco status) {
        return new AdiamentoRecusadoException(
                status == StatusMarco.AGENDADO
                        ? "O lembrete ainda não disparou."
                        : "O bloco já foi encerrado como %s.".formatted(status));
    }

    /** Cenário 5 da H3: o adiamento vale uma vez por cadeia. */
    static AdiamentoRecusadoException jaCompensa() {
        return new AdiamentoRecusadoException("Este bloco já compensa um adiamento: conclua ou marque falha.");
    }

    /** Decisão D5: o último bloco do dia não tem seguinte que o compense. */
    static AdiamentoRecusadoException ultimo() {
        return new AdiamentoRecusadoException(
                "O último bloco do dia não pode ser adiado: não há bloco seguinte para compensar.");
    }
}
