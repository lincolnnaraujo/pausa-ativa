package br.com.pausaativa.agenda.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Intervalo em que o tempo trabalhado fica congelado (almoço). Sem fim enquanto em curso. */
public final class Pausa {

    private final Instant inicio;
    private Instant fim;

    private Pausa(Instant inicio, Instant fim) {
        this.inicio = inicio;
        this.fim = fim;
    }

    static Pausa iniciadaEm(Instant inicio) {
        return new Pausa(inicio, null);
    }

    boolean emCurso() {
        return fim == null;
    }

    void encerrar(Instant agora) {
        fim = agora;
    }

    /** Quanto desta pausa cai antes de {@code limite}. */
    Duration duracaoAte(Instant limite) {
        Instant fimEfetivo = fim == null || fim.isAfter(limite) ? limite : fim;
        return fimEfetivo.isAfter(inicio) ? Duration.between(inicio, fimEfetivo) : Duration.ZERO;
    }

    public Instant inicio() {
        return inicio;
    }

    public Optional<Instant> fim() {
        return Optional.ofNullable(fim);
    }
}
