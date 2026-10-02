package br.com.pausaativa.agenda.domain;

import static br.com.pausaativa.agenda.domain.StatusJornada.EM_ANDAMENTO;
import static br.com.pausaativa.agenda.domain.StatusJornada.ENCERRADA_AUTOMATICAMENTE;
import static br.com.pausaativa.agenda.domain.StatusJornada.FINALIZADA;
import static br.com.pausaativa.agenda.domain.StatusJornada.PAUSADA;
import static br.com.pausaativa.agenda.domain.StatusMarco.AGENDADO;
import static br.com.pausaativa.agenda.domain.StatusMarco.CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.FALHA;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_CONCLUIDO;
import static br.com.pausaativa.agenda.domain.StatusMarco.NAO_ENTREGUE;
import static br.com.pausaativa.agenda.domain.StatusMarco.PENDENTE;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Jornada de trabalho de um dia: raiz do agregado da Agenda, dona das pausas e dos marcos.
 *
 * <p>Os marcos são calculados sobre o <b>tempo trabalhado</b> (início até agora, menos as pausas),
 * nunca sobre o relógio de parede. Nenhum método lê o relógio: o instante atual chega por parâmetro.
 * Regras detalhadas na seção 3 da spec H2.
 */
public final class Jornada {

    private final UUID id;
    private final LocalDate dataReferencia;
    private final MetaDeAgua meta;
    private final Instant iniciadaEm;
    private final List<Pausa> pausas;
    private final List<Marco> marcos;
    private final List<EventoDaJornada> eventos = new ArrayList<>();
    private StatusJornada status;
    private Instant finalizadaEm;

    private Jornada(
            UUID id,
            LocalDate dataReferencia,
            MetaDeAgua meta,
            Instant iniciadaEm,
            List<Pausa> pausas,
            List<Marco> marcos,
            StatusJornada status,
            Instant finalizadaEm) {
        this.id = id;
        this.dataReferencia = dataReferencia;
        this.meta = meta;
        this.iniciadaEm = iniciadaEm;
        this.pausas = pausas;
        this.marcos = marcos;
        this.status = status;
        this.finalizadaEm = finalizadaEm;
    }

    /** Remonta uma jornada gravada. Uso exclusivo da persistência; marcos em ordem de categoria e sequência. */
    public static Jornada reconstituir(
            UUID id,
            LocalDate dataReferencia,
            MetaDeAgua meta,
            Instant iniciadaEm,
            StatusJornada status,
            Instant finalizadaEm,
            List<Pausa> pausas,
            List<Marco> marcos) {
        return new Jornada(
                id,
                dataReferencia,
                meta,
                iniciadaEm,
                new ArrayList<>(pausas),
                new ArrayList<>(marcos),
                status,
                finalizadaEm);
    }

    /** Começa o dia e agenda todos os marcos do plano (decisão D1). */
    public static Jornada iniciar(UUID id, Instant agora, ZoneId fuso, MetaDeAgua meta, PlanoDeMarcos plano) {
        BigDecimal volume = meta.volumePorMarco(plano.quantidade());
        List<Marco> marcos = new ArrayList<>();
        for (int sequencia = 1; sequencia <= plano.quantidade(); sequencia++) {
            marcos.add(Marco.agendado(plano, sequencia, volume));
        }
        return new Jornada(
                id, LocalDate.ofInstant(agora, fuso), meta, agora, new ArrayList<>(), marcos, EM_ANDAMENTO, null);
    }

    /** Início até agora (ou até o fim da jornada), menos as pausas. */
    public Duration tempoTrabalhado(Instant agora) {
        Instant fim = finalizadaEm != null && finalizadaEm.isBefore(agora) ? finalizadaEm : agora;
        if (!fim.isAfter(iniciadaEm)) {
            return Duration.ZERO;
        }
        Duration total = Duration.between(iniciadaEm, fim);
        for (Pausa pausa : pausas) {
            total = total.minus(pausa.duracaoAte(fim));
        }
        return total;
    }

    /**
     * Dispara os marcos cujo tempo chegou e vence os prazos. Chamado a cada segundo pelo agendador.
     * Pausada ou encerrada, a jornada não muda: o tempo trabalhado está parado.
     */
    public Avanco avancar(Instant agora) {
        if (status != EM_ANDAMENTO) {
            return Avanco.NENHUM;
        }
        Duration trabalhado = tempoTrabalhado(agora);
        boolean venceuPrazo = vencerPrazos(trabalhado);
        List<Marco> disparados = new ArrayList<>();
        for (Marco marco : marcos) {
            if (marco.devidoEm(trabalhado)) {
                Instant previstoPara = instanteEmQueOTempoTrabalhadoChegaA(marco.tempoTrabalhadoPrevisto());
                marco.disparar(previstoPara, agora);
                disparados.add(marco);
                eventos.add(new MarcoDisparado(marco.id(), marco.categoria(), marco.sequencia(), previstoPara, agora));
            }
        }
        return new Avanco(List.copyOf(disparados), venceuPrazo || !disparados.isEmpty());
    }

    public void pausar(Instant agora) {
        if (status != EM_ANDAMENTO) {
            throw new TransicaoDeJornadaInvalidaException("pausar", status);
        }
        pausas.add(Pausa.iniciadaEm(agora));
        status = PAUSADA;
    }

    public void retomar(Instant agora) {
        if (status != PAUSADA) {
            throw new TransicaoDeJornadaInvalidaException("retomar", status);
        }
        encerrarPausaEmCurso(agora);
        status = EM_ANDAMENTO;
    }

    /**
     * Finaliza o dia. Prazos já vencidos são resolvidos antes; o que ainda estava agendado ou pendente
     * vira {@code NAO_CONCLUIDO} (pergunta 1 do épico). Finalizar de novo não muda nada.
     *
     * @return se a jornada mudou
     */
    public boolean finalizar(Instant agora) {
        if (!status.aberta()) {
            return false;
        }
        encerrarPausaEmCurso(agora);
        vencerPrazos(tempoTrabalhado(agora));
        marcos.stream().filter(marco -> !marco.status().encerrado()).forEach(marco -> {
            marco.encerrarSemResposta(NAO_CONCLUIDO);
            registrarEncerramento(marco);
        });
        status = FINALIZADA;
        finalizadaEm = agora;
        return true;
    }

    /**
     * Jornada de um dia anterior que ficou aberta e já não está em uso (spec H2, seção 3.5): não tem
     * mais marco por disparar nem por responder, ou está pausada desde um dia anterior. Uma jornada que
     * atravessa a meia-noite com marcos pela frente continua valendo.
     */
    public boolean esquecida(Instant agora, ZoneId fuso) {
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        if (!status.aberta() || !dataReferencia.isBefore(hoje)) {
            return false;
        }
        boolean semMarcosEmAberto =
                marcos.stream().allMatch(marco -> marco.status().encerrado());
        boolean pausadaDesdeOutroDia = pausas.stream()
                .filter(Pausa::emCurso)
                .anyMatch(pausa -> LocalDate.ofInstant(pausa.inicio(), fuso).isBefore(hoje));
        return semMarcosEmAberto || pausadaDesdeOutroDia;
    }

    /**
     * Fecha a jornada esquecida. Pendentes seguem a regra do prazo vencido; agendados nunca chegaram
     * ao usuário e viram {@code NAO_ENTREGUE} (Cenário 8).
     */
    public boolean encerrarAutomaticamente(Instant agora) {
        if (!status.aberta()) {
            return false;
        }
        encerrarPausaEmCurso(agora);
        for (Marco marco : marcos) {
            if (marco.status() == PENDENTE) {
                marco.vencerPrazo();
                registrarEncerramento(marco);
            } else if (marco.status() == AGENDADO) {
                marco.encerrarSemResposta(NAO_ENTREGUE);
                registrarEncerramento(marco);
            }
        }
        status = ENCERRADA_AUTOMATICAMENTE;
        finalizadaEm = agora;
        return true;
    }

    /**
     * Põe a jornada em dia sem disparar nada atrasado: prazos vencidos são resolvidos, e marcos cujo
     * instante já passou viram {@code NAO_ENTREGUE}. Usado na subida do backend (que pode ter ficado
     * fora do ar) e antes de decidir se uma jornada aberta ficou esquecida.
     */
    public boolean reconciliar(Instant agora) {
        if (!status.aberta()) {
            return false;
        }
        Duration trabalhado = tempoTrabalhado(agora);
        boolean mudou = vencerPrazos(trabalhado);
        for (Marco marco : marcos) {
            if (marco.devidoEm(trabalhado)) {
                marco.encerrarSemResposta(NAO_ENTREGUE);
                registrarEncerramento(marco);
                mudou = true;
            }
        }
        return mudou;
    }

    public boolean concluirMarco(UUID marcoId, Instant agora) {
        return responder(marcoId, CONCLUIDO, agora);
    }

    public boolean falharMarco(UUID marcoId, Instant agora) {
        return responder(marcoId, FALHA, agora);
    }

    private boolean responder(UUID marcoId, StatusMarco resposta, Instant agora) {
        Marco marco = marco(marcoId);
        boolean mudou = marco.responder(resposta, agora);
        if (mudou) {
            registrarEncerramento(marco);
        }
        return mudou;
    }

    /** Devolve os eventos acumulados desde a última extração e esvazia a lista. */
    public List<EventoDaJornada> extrairEventos() {
        List<EventoDaJornada> extraidos = List.copyOf(eventos);
        eventos.clear();
        return extraidos;
    }

    public boolean confirmarRecebimento(UUID marcoId, Instant agora) {
        return marco(marcoId).confirmarRecebimento(agora);
    }

    /** Soma dos marcos concluídos (Cenário 2). */
    public BigDecimal aguaIngeridaMl() {
        return marcos.stream()
                .filter(marco -> marco.status() == CONCLUIDO)
                .map(Marco::volumeMl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean vencerPrazos(Duration trabalhado) {
        boolean venceu = false;
        for (Marco marco : marcos) {
            if (marco.prazoVencidoEm(trabalhado)) {
                marco.vencerPrazo();
                registrarEncerramento(marco);
                venceu = true;
            }
        }
        return venceu;
    }

    private void registrarEncerramento(Marco marco) {
        eventos.add(new MarcoEncerrado(marco.id(), marco.categoria(), marco.sequencia(), marco.status()));
    }

    private void encerrarPausaEmCurso(Instant agora) {
        pausas.stream().filter(Pausa::emCurso).forEach(pausa -> pausa.encerrar(agora));
    }

    /** Instante de parede em que o tempo trabalhado atingiu {@code alvo}, descontando as pausas. */
    private Instant instanteEmQueOTempoTrabalhadoChegaA(Duration alvo) {
        Duration acumulado = Duration.ZERO;
        Instant cursor = iniciadaEm;
        for (Pausa pausa : pausas) {
            Duration trechoTrabalhado = Duration.between(cursor, pausa.inicio());
            if (acumulado.plus(trechoTrabalhado).compareTo(alvo) >= 0) {
                return cursor.plus(alvo.minus(acumulado));
            }
            acumulado = acumulado.plus(trechoTrabalhado);
            cursor = pausa.fim()
                    .orElseThrow(() -> new IllegalStateException("O tempo trabalhado não avança durante a pausa"));
        }
        return cursor.plus(alvo.minus(acumulado));
    }

    private Marco marco(UUID marcoId) {
        return marcos.stream()
                .filter(marco -> marco.id().equals(marcoId))
                .findFirst()
                .orElseThrow(() -> new MarcoNaoEncontradoException(marcoId));
    }

    public UUID id() {
        return id;
    }

    public LocalDate dataReferencia() {
        return dataReferencia;
    }

    public MetaDeAgua meta() {
        return meta;
    }

    public Instant iniciadaEm() {
        return iniciadaEm;
    }

    public StatusJornada status() {
        return status;
    }

    public Optional<Instant> finalizadaEm() {
        return Optional.ofNullable(finalizadaEm);
    }

    /** Início da pausa em curso; vazio fora da pausa. */
    public Optional<Instant> pausadaDesde() {
        return pausas.stream().filter(Pausa::emCurso).map(Pausa::inicio).findFirst();
    }

    public List<Pausa> pausas() {
        return Collections.unmodifiableList(pausas);
    }

    public List<Marco> marcos() {
        return Collections.unmodifiableList(marcos);
    }
}
