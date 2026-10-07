package br.com.pausaativa.historico.application.port.out;

import br.com.pausaativa.historico.domain.RegistroDoDia;
import java.time.LocalDate;
import java.util.List;

/** As contagens dos dias com jornada, que vêm da Agenda (decisão D1 da spec H4). */
public interface RegistrosDiarios {

    /** Os dias com jornada de {@code de} a {@code ate}, inclusive. */
    List<RegistroDoDia> entre(LocalDate de, LocalDate ate);
}
