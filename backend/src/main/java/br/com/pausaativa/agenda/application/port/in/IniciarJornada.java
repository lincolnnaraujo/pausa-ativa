package br.com.pausaativa.agenda.application.port.in;

public interface IniciarJornada {

    /**
     * Começa o dia. Antes, encerra a jornada esquecida de um dia anterior, se houver.
     *
     * @throws br.com.pausaativa.agenda.domain.JornadaJaIniciadaException se o dia já tem jornada
     * @throws br.com.pausaativa.agenda.domain.MetaDeAguaInvalidaException fora de 1 a 6.000 ml
     */
    SituacaoDaJornada iniciar(int metaAguaMl);
}
