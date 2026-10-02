package br.com.pausaativa.agenda.domain;

public class TransicaoDeJornadaInvalidaException extends RegraDeNegocioException {

    TransicaoDeJornadaInvalidaException(String operacao, StatusJornada status) {
        super("Não é possível %s: a jornada está %s.".formatted(operacao, descricao(status)));
    }

    private static String descricao(StatusJornada status) {
        return switch (status) {
            case EM_ANDAMENTO -> "em andamento";
            case PAUSADA -> "pausada";
            case FINALIZADA -> "finalizada";
            case ENCERRADA_AUTOMATICAMENTE -> "encerrada automaticamente";
        };
    }
}
