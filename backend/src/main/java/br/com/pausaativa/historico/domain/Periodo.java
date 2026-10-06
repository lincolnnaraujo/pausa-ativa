package br.com.pausaativa.historico.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;

/** Intervalo de dias de uma visão do histórico, com início e fim inclusivos (spec H4, seção 3.2). */
public record Periodo(TipoDePeriodo tipo, LocalDate inicio, LocalDate fim) {

    public Periodo {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("O período termina (%s) antes de começar (%s)".formatted(fim, inicio));
        }
    }

    /**
     * O período do tipo pedido que contém a data: o próprio dia, a semana de segunda a domingo ou o mês.
     *
     * @throws DataFuturaException se a data é depois de hoje
     */
    public static Periodo contendo(TipoDePeriodo tipo, LocalDate data, LocalDate hoje) {
        Objects.requireNonNull(tipo, "tipo");
        if (data.isAfter(hoje)) {
            throw new DataFuturaException(data);
        }
        return switch (tipo) {
            case DIA -> new Periodo(tipo, data, data);
            case SEMANA -> {
                LocalDate segunda = data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Periodo(tipo, segunda, segunda.plusDays(6));
            }
            case MES -> new Periodo(tipo, data.withDayOfMonth(1), data.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }

    public boolean contem(LocalDate data) {
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    /** Todos os dias do período, em ordem. */
    public List<LocalDate> dias() {
        return inicio.datesUntil(fim.plusDays(1)).toList();
    }
}
