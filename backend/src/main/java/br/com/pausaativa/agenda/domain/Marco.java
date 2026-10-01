package br.com.pausaativa.agenda.domain;

import static br.com.pausaativa.agenda.domain.StatusMarco.AGENDADO;
import static br.com.pausaativa.agenda.domain.StatusMarco.FALHA;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_ENTREGUE;
import static br.com.pausaativa.agenda.domain.StatusMarco.PENDENTE;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lembrete da jornada. Dispara quando o tempo trabalhado chega a {@link #tempoTrabalhadoPrevisto()}
 * e precisa de resposta até {@link #tempoTrabalhadoLimite()}, ambos em tempo trabalhado.
 *
 * <p>As transições são feitas pela {@link Jornada}, raiz do agregado.
 */
public final class Marco {

    private final UUID id;
    private final Categoria categoria;
    private final int sequencia;
    private final Duration tempoTrabalhadoPrevisto;
    private final Duration tempoTrabalhadoLimite;
    private final BigDecimal volumeMl;
    private StatusMarco status;
    private Instant previstoPara;
    private Instant disparadoEm;
    private Instant recebidoEm;
    private Instant respondidoEm;

    private Marco(
            UUID id,
            Categoria categoria,
            int sequencia,
            Duration tempoTrabalhadoPrevisto,
            Duration tempoTrabalhadoLimite,
            BigDecimal volumeMl) {
        this.id = id;
        this.categoria = categoria;
        this.sequencia = sequencia;
        this.tempoTrabalhadoPrevisto = tempoTrabalhadoPrevisto;
        this.tempoTrabalhadoLimite = tempoTrabalhadoLimite;
        this.volumeMl = volumeMl;
        this.status = AGENDADO;
    }

    /** Remonta um marco gravado. Uso exclusivo da persistência. */
    public static Marco reconstituir(
            UUID id,
            Categoria categoria,
            int sequencia,
            Duration tempoTrabalhadoPrevisto,
            Duration tempoTrabalhadoLimite,
            BigDecimal volumeMl,
            StatusMarco status,
            Instant previstoPara,
            Instant disparadoEm,
            Instant recebidoEm,
            Instant respondidoEm) {
        Marco marco = new Marco(id, categoria, sequencia, tempoTrabalhadoPrevisto, tempoTrabalhadoLimite, volumeMl);
        marco.status = status;
        marco.previstoPara = previstoPara;
        marco.disparadoEm = disparadoEm;
        marco.recebidoEm = recebidoEm;
        marco.respondidoEm = respondidoEm;
        return marco;
    }

    /** O prazo é um intervalo depois do disparo: o instante em que dispararia o marco seguinte. */
    static Marco agendado(PlanoDeMarcos plano, int sequencia, BigDecimal volumeMl) {
        Duration previsto = plano.intervalo().multipliedBy(sequencia);
        return new Marco(
                UUID.randomUUID(), plano.categoria(), sequencia, previsto, previsto.plus(plano.intervalo()), volumeMl);
    }

    boolean devidoEm(Duration tempoTrabalhado) {
        return status == AGENDADO && tempoTrabalhadoPrevisto.compareTo(tempoTrabalhado) <= 0;
    }

    boolean prazoVencidoEm(Duration tempoTrabalhado) {
        return status == PENDENTE && tempoTrabalhadoLimite.compareTo(tempoTrabalhado) <= 0;
    }

    void disparar(Instant previstoPara, Instant agora) {
        this.status = PENDENTE;
        this.previstoPara = previstoPara;
        this.disparadoEm = agora;
    }

    boolean confirmarRecebimento(Instant agora) {
        if (status != PENDENTE || recebidoEm != null) {
            return false;
        }
        recebidoEm = agora;
        return true;
    }

    /** Repetir a mesma resposta não muda nada (idempotente); outra resposta a um marco encerrado é recusada. */
    boolean responder(StatusMarco resposta, Instant agora) {
        if (status == resposta) {
            return false;
        }
        if (status != PENDENTE) {
            throw new RespostaDeMarcoRecusadaException(status);
        }
        if (recebidoEm == null) {
            recebidoEm = agora;
        }
        status = resposta;
        respondidoEm = agora;
        return true;
    }

    /** Sem resposta no prazo: falha se o usuário recebeu o lembrete, não entregue se não recebeu. */
    void vencerPrazo() {
        status = recebidoEm != null ? FALHA : NAO_ENTREGUE;
    }

    void encerrarSemResposta(StatusMarco motivo) {
        status = motivo;
    }

    /** Texto da notificação. Os marcos ímpares (meia hora) também orientam a levantar (pergunta 3 do épico). */
    public String mensagem() {
        String beba = "Beba ~%d ml.".formatted(volumeArredondado());
        return sequencia % 2 == 1 ? beba + " Levante-se para buscar a água." : beba;
    }

    /** Volume para exibição, arredondado para a dezena: 187,5 ml vira 190. */
    public int volumeArredondado() {
        return volumeMl.divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP).intValueExact() * 10;
    }

    public UUID id() {
        return id;
    }

    public Categoria categoria() {
        return categoria;
    }

    public int sequencia() {
        return sequencia;
    }

    public Duration tempoTrabalhadoPrevisto() {
        return tempoTrabalhadoPrevisto;
    }

    public Duration tempoTrabalhadoLimite() {
        return tempoTrabalhadoLimite;
    }

    public BigDecimal volumeMl() {
        return volumeMl;
    }

    public StatusMarco status() {
        return status;
    }

    public Optional<Instant> previstoPara() {
        return Optional.ofNullable(previstoPara);
    }

    public Optional<Instant> disparadoEm() {
        return Optional.ofNullable(disparadoEm);
    }

    public Optional<Instant> recebidoEm() {
        return Optional.ofNullable(recebidoEm);
    }

    public Optional<Instant> respondidoEm() {
        return Optional.ofNullable(respondidoEm);
    }
}
