package br.com.pausaativa.agenda.application.port.in;

/**
 * Acerta a jornada aberta depois de o backend ficar fora do ar e encerra a jornada esquecida
 * (spec H2, seção 3.5). Chamado na subida do backend.
 */
public interface ReconciliarJornadas {

    void reconciliar();
}
