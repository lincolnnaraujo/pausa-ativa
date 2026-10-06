package br.com.pausaativa.agenda.domain;

import static br.com.pausaativa.agenda.domain.StatusMarco.ADIADO;
import static br.com.pausaativa.agenda.domain.StatusMarco.AGENDADO;
import static br.com.pausaativa.agenda.domain.StatusMarco.CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.FALHA;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_ENTREGUE;
import static br.com.pausaativa.agenda.domain.StatusMarco.PENDENTE;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lembrete da jornada. Dispara quando o tempo trabalhado chega a {@link #tempoTrabalhadoPrevisto()}
 * e precisa de resposta até {@link #tempoTrabalhadoLimite()}, ambos em tempo trabalhado.
 *
 * <p>A hidratação tem volume; o exercício tem bloco, montado no disparo. As transições são feitas pela
 * {@link Jornada}, raiz do agregado.
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
    private Instant editadoEm;
    private BlocoDoMarco bloco;

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
        this.volumeMl = volumeMl == null ? null : semZerosNemExpoente(volumeMl);
        this.status = AGENDADO;
    }

    /** 187.5000 vira 187.5, e 125.0000 vira 125 (o {@code stripTrailingZeros} sozinho daria 1.25E+2). */
    private static BigDecimal semZerosNemExpoente(BigDecimal valor) {
        BigDecimal semZeros = valor.stripTrailingZeros();
        return semZeros.scale() < 0 ? semZeros.setScale(0) : semZeros;
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
            Instant respondidoEm,
            Instant editadoEm,
            BlocoDoMarco bloco) {
        Marco marco = new Marco(id, categoria, sequencia, tempoTrabalhadoPrevisto, tempoTrabalhadoLimite, volumeMl);
        marco.status = status;
        marco.previstoPara = previstoPara;
        marco.disparadoEm = disparadoEm;
        marco.recebidoEm = recebidoEm;
        marco.respondidoEm = respondidoEm;
        marco.editadoEm = editadoEm;
        marco.bloco = bloco;
        return marco;
    }

    /**
     * O prazo é um intervalo depois do disparo: o instante em que dispararia o marco seguinte.
     *
     * @param volumeMl nulo no exercício
     */
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

    /** Adiar conta como resposta: o usuário viu o bloco. As regras de quem pode adiar ficam na jornada. */
    void adiar(Instant agora) {
        if (recebidoEm == null) {
            recebidoEm = agora;
        }
        status = ADIADO;
        respondidoEm = agora;
    }

    /** O adiado segue o destino do bloco seguinte (spec H3, seção 3.5). */
    void resolverAdiamento(StatusMarco destino) {
        status = destino;
    }

    /** Sem resposta no prazo: falha se o usuário recebeu o lembrete, não entregue se não recebeu. */
    void vencerPrazo() {
        status = recebidoEm != null ? FALHA : NAO_ENTREGUE;
    }

    void encerrarSemResposta(StatusMarco motivo) {
        status = motivo;
    }

    /** Só uma resposta se corrige: concluído ou falha (decisão D4 da spec H4). */
    boolean corrigivel() {
        return status == CONCLUIDO || status == FALHA;
    }

    /** Troca a resposta e guarda o instante da correção. O instante da resposta original fica. */
    void corrigir(StatusMarco correta, Instant agora) {
        status = correta;
        editadoEm = agora;
    }

    void prepararBloco(DuracaoDoBloco duracao, boolean compensaAdiamento) {
        bloco = BlocoDoMarco.semExercicios(duracao, compensaAdiamento);
    }

    void atribuirExercicios(List<ExercicioProposto> exercicios) {
        if (bloco == null) {
            throw new IllegalStateException("O marco %s %d não tem bloco: só um exercício disparado recebe exercícios"
                    .formatted(categoria, sequencia));
        }
        bloco = bloco.comExercicios(exercicios);
    }

    /**
     * Texto da notificação. Na água, os marcos ímpares (meia hora) também orientam a levantar (pergunta 3
     * do épico). No exercício, a duração e a quantidade de exercícios do bloco.
     */
    public String mensagem() {
        return switch (categoria) {
            case HIDRATACAO -> {
                String beba = "Beba ~%d ml.".formatted(volumeArredondado());
                yield sequencia % 2 == 1 ? beba + " Levante-se para buscar a água." : beba;
            }
            case EXERCICIO -> mensagemDoBloco();
        };
    }

    private String mensagemDoBloco() {
        if (bloco == null) {
            return "Bloco de exercício.";
        }
        String texto = "Bloco de %d min".formatted(bloco.duracao().minutos());
        int quantidade = bloco.exercicios().size();
        if (quantidade > 0) {
            texto += ": %d %s".formatted(quantidade, quantidade == 1 ? "exercício" : "exercícios");
        }
        return bloco.compensaAdiamento() ? texto + ". Inclui o bloco adiado." : texto + ".";
    }

    /** Volume para exibição, arredondado para a dezena: 187,5 ml vira 190. Só na hidratação. */
    public int volumeArredondado() {
        if (volumeMl == null) {
            throw new IllegalStateException("O marco de " + categoria + " não tem volume de água");
        }
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

    /** Nulo no exercício. */
    public BigDecimal volumeMl() {
        return volumeMl;
    }

    /** Vazio na hidratação e no exercício que ainda não disparou. */
    public Optional<BlocoDoMarco> bloco() {
        return Optional.ofNullable(bloco);
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

    /** Vazio até a primeira correção. Fica mesmo se a correção for desfeita. */
    public Optional<Instant> editadoEm() {
        return Optional.ofNullable(editadoEm);
    }
}
