package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.domain.PlanoDeMarcos;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração da Agenda ({@code pausa-ativa.agenda.*}).
 *
 * @param intervalo tempo trabalhado entre marcos de hidratação: 30 min em uso normal, menor no modo
 *     demonstração (spec H2, seção 9)
 */
@ConfigurationProperties("pausa-ativa.agenda")
public record ConfiguracaoDaAgenda(@DefaultValue("30m") Duration intervalo) {

    public PlanoDeMarcos planoDeHidratacao() {
        return PlanoDeMarcos.hidratacao(intervalo);
    }
}
