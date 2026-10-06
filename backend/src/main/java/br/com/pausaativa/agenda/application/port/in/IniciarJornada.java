package br.com.pausaativa.agenda.application.port.in;

public interface IniciarJornada {

    /**
     * Começa o dia. Antes, encerra a jornada esquecida de um dia anterior, se houver.
     *
     * @param duracaoBlocoMin duração dos blocos de exercício do dia: 5 ou 10 min
     * @throws br.com.pausaativa.agenda.domain.MetaDeAguaInvalidaException fora de 1 a 6.000 ml
     * @throws br.com.pausaativa.agenda.domain.DuracaoDoBlocoInvalidaException fora de 5 ou 10 min
     * @throws br.com.pausaativa.agenda.application.PerfilAusenteException sem perfil físico preenchido
     * @throws br.com.pausaativa.agenda.domain.JornadaJaIniciadaException se o dia já tem jornada
     */
    SituacaoDaJornada iniciar(int metaAguaMl, int duracaoBlocoMin);
}
