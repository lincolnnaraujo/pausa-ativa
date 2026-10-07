package br.com.pausaativa.agenda.application.port.out;

import br.com.pausaativa.agenda.domain.Categoria;
import br.com.pausaativa.agenda.domain.StatusJornada;
import br.com.pausaativa.agenda.domain.StatusMarco;
import java.time.LocalDate;

/**
 * Uma linha da contagem agregada no banco: quantos marcos de uma categoria estão numa situação, na
 * jornada de um dia (spec H4, seção 4).
 */
public record ContagemDoDia(
        LocalDate data, StatusJornada jornada, Categoria categoria, StatusMarco status, long quantidade) {}
