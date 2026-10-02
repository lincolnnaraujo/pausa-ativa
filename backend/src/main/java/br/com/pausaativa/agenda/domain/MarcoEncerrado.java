package br.com.pausaativa.agenda.domain;

import java.util.UUID;

/** O marco chegou a um status final: respondido, prazo vencido, dia finalizado ou não entregue. */
public record MarcoEncerrado(UUID marcoId, Categoria categoria, int sequencia, StatusMarco status)
        implements EventoDaJornada {}
