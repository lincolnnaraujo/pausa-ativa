package br.com.pausaativa.agenda.adapter.in.agendador;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendamento (tick da agenda e heartbeat do SSE). Os testes o desligam com
 * {@code pausa-ativa.agenda.agendador.habilitado=false} e avançam a agenda à mão, com relógio controlado.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "pausa-ativa.agenda.agendador.habilitado", havingValue = "true", matchIfMissing = true)
class ConfiguracaoDoAgendador {}
