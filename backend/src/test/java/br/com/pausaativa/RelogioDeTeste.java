package br.com.pausaativa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio dos testes de integração: parado num instante, só anda quando o teste manda. */
public final class RelogioDeTeste extends Clock {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private volatile Instant agora;

    public RelogioDeTeste(Instant inicial) {
        this.agora = inicial;
    }

    public void ajustarPara(Instant instante) {
        agora = instante;
    }

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return SAO_PAULO;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return Clock.fixed(agora, zona);
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
