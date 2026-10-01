package br.com.pausaativa.agenda.domain;

/** Resposta a um marco que não está pendente, com um status diferente do atual. */
public class RespostaDeMarcoRecusadaException extends RegraDeNegocioException {

    RespostaDeMarcoRecusadaException(StatusMarco status) {
        super(
                status == StatusMarco.AGENDADO
                        ? "O lembrete ainda não disparou."
                        : "O lembrete já foi encerrado como %s. A edição de registros do mesmo dia chega na v0.4.0."
                                .formatted(status));
    }
}
