package br.com.pausaativa.agenda.domain;

/**
 * Situação de um marco (spec H2, seção 3.3, e spec H3, seção 3.6).
 *
 * <p>Só {@code CONCLUIDO} e {@code FALHA} entram na taxa de sucesso; {@code NAO_ENTREGUE} e
 * {@code NAO_CONCLUIDO} ficam fora do denominador.
 */
public enum StatusMarco {
    /** Criado ao iniciar o dia, aguardando o tempo trabalhado chegar ao marco. */
    AGENDADO,
    /** Disparado, aguardando resposta. */
    PENDENTE,
    CONCLUIDO,
    /** O usuário marcou falha, ou o prazo venceu com o recebimento confirmado. */
    FALHA,
    /** O usuário não chegou a ver o marco: backend fora do ar ou frontend sem confirmar o recebimento. */
    NAO_ENTREGUE,
    /** O dia foi finalizado antes de o marco ser respondido. */
    NAO_CONCLUIDO,
    /** Exercício adiado: sem prazo próprio, segue o destino do marco de exercício seguinte. */
    ADIADO;

    /** Status final. O {@code ADIADO} não é: ele ainda espera o bloco seguinte. */
    public boolean encerrado() {
        return this != AGENDADO && this != PENDENTE && this != ADIADO;
    }
}
