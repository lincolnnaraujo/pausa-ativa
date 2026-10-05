package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.domain.PlanoDeMarcos;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração da Agenda ({@code pausa-ativa.agenda.*}).
 *
 * @param intervalo tempo trabalhado entre marcos de hidratação: 30 min em uso normal, menor no modo
 *     demonstração (spec H2, seção 9). O exercício usa o dobro (decisão D9 da spec H3).
 */
@ConfigurationProperties("pausa-ativa.agenda")
public record ConfiguracaoDaAgenda(@DefaultValue("30m") Duration intervalo) {

    /** Os planos de toda jornada: 16 marcos de água e 8 de exercício. */
    public List<PlanoDeMarcos> planos() {
        return List.of(PlanoDeMarcos.hidratacao(intervalo), PlanoDeMarcos.exercicio(intervalo));
    }
}
