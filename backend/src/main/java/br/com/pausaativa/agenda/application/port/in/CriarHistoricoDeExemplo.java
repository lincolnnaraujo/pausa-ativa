package br.com.pausaativa.agenda.application.port.in;

/**
 * Cria jornadas de exemplo nos 45 dias anteriores a hoje, para a demonstração começar com histórico (spec
 * H4, seção 9). Só o modo demonstração chama, na subida.
 */
public interface CriarHistoricoDeExemplo {

    /** @return quantas jornadas criou: nenhuma se o banco já tem jornada de um dia anterior */
    int criar();
}
