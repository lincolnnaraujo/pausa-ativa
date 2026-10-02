package br.com.pausaativa.agenda.application.port.in;

/**
 * Tick do agendador, chamado a cada segundo: dispara os marcos devidos, vence prazos e encerra a
 * jornada esquecida.
 */
public interface AvancarAgenda {

    void avancar();
}
