package br.com.pausaativa.agenda.domain;

import static br.com.pausaativa.agenda.domain.StatusJornada.EM_ANDAMENTO;
import static br.com.pausaativa.agenda.domain.StatusJornada.ENCERRADA_AUTOMATICAMENTE;
import static br.com.pausaativa.agenda.domain.StatusJornada.FINALIZADA;
import static br.com.pausaativa.agenda.domain.StatusJornada.PAUSADA;
import static br.com.pausaativa.agenda.domain.StatusMarco.ADIADO;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Jornada de trabalho de um dia: raiz do agregado da Agenda, dona das pausas e dos marcos.
 *
 * <p>Os marcos são calculados sobre o <b>tempo trabalhado</b> (início até agora, menos as pausas),
 * nunca sobre o relógio de parede. Nenhum método lê o relógio: o instante atual chega por parâmetro.
 * Regras detalhadas na seção 3 das specs H2 (jornada e água) e H3 (exercício e adiamento).
 */
public final class Jornada {

    private final UUID id;
    private final LocalDate dataReferencia;
    private final MetaDeAgua meta;
    private final DuracaoDoBloco duracaoDoBloco;
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
            DuracaoDoBloco duracaoDoBloco,
            Instant iniciadaEm,
            List<Pausa> pausas,
            List<Marco> marcos,
            StatusJornada status,
            Instant finalizadaEm) {
        this.id = id;
        this.dataReferencia = dataReferencia;
        this.meta = meta;
        this.duracaoDoBloco = duracaoDoBloco;
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
            DuracaoDoBloco duracaoDoBloco,
            Instant iniciadaEm,
            StatusJornada status,
            Instant finalizadaEm,
            List<Pausa> pausas,
            List<Marco> marcos) {
        return new Jornada(
                id,
                dataReferencia,
                meta,
                duracaoDoBloco,
                iniciadaEm,
                new ArrayList<>(pausas),
                new ArrayList<>(marcos),
                status,
                finalizadaEm);
    }

    /**
     * Começa o dia e agenda todos os marcos dos planos (decisão D1 da spec H2). O plano de hidratação é
     * obrigatório, e a meta de água é dividida entre os marcos dele.
     */
    public static Jornada iniciar(
            UUID id,
            Instant agora,
            ZoneId fuso,
            MetaDeAgua meta,
            DuracaoDoBloco duracaoDoBloco,
            List<PlanoDeMarcos> planos) {
        if (planos.stream().map(PlanoDeMarcos::categoria).distinct().count() != planos.size()) {
            throw new IllegalArgumentException("Um plano por categoria: " + planos);
        }
        PlanoDeMarcos hidratacao = planos.stream()
                .filter(plano -> plano.categoria() == Categoria.HIDRATACAO)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("A jornada precisa do plano de hidratação"));
        BigDecimal volume = meta.volumePorMarco(hidratacao.quantidade());
        List<Marco> marcos = new ArrayList<>();
        for (PlanoDeMarcos plano : planos) {
            for (int sequencia = 1; sequencia <= plano.quantidade(); sequencia++) {
                marcos.add(Marco.agendado(plano, sequencia, plano == hidratacao ? volume : null));
            }
        }
        return new Jornada(
                id,
                LocalDate.ofInstant(agora, fuso),
                meta,
                duracaoDoBloco,
                agora,
                new ArrayList<>(),
                marcos,
                EM_ANDAMENTO,
                null);
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
     *
     * <p>O exercício disparado sai com o bloco preparado, ainda sem exercícios: a aplicação os pede ao
     * Treino e chama {@link #atribuirBloco} antes de gravar.
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
                if (marco.categoria() == Categoria.EXERCICIO) {
                    prepararBloco(marco);
                }
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
        marcos.stream()
                .filter(marco -> marco.status() == AGENDADO || marco.status() == PENDENTE)
                .forEach(marco -> {
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
     * ao usuário e viram {@code NAO_ENTREGUE} (Cenário 8). Um adiado segue o bloco seguinte.
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

    /**
     * Adia um bloco de exercício pendente (spec H3, seção 3.5). O seguinte terá 10 min e decidirá o
     * destino dos dois. Adiar de novo o mesmo marco não muda nada.
     *
     * @return se a jornada mudou
     */
    public boolean adiarMarco(UUID marcoId, Instant agora) {
        Marco marco = marco(marcoId);
        if (marco.categoria() != Categoria.EXERCICIO) {
            throw AdiamentoRecusadoException.hidratacao();
        }
        if (marco.status() == ADIADO) {
            return false;
        }
        if (marco.status() != PENDENTE) {
            throw AdiamentoRecusadoException.naoPendente(marco.status());
        }
        if (compensaAdiamento(marco)) {
            throw AdiamentoRecusadoException.jaCompensa();
        }
        if (seguinte(marco).isEmpty()) {
            throw AdiamentoRecusadoException.ultimo();
        }
        marco.adiar(agora);
        eventos.add(new MarcoAdiado(marco.id(), marco.categoria(), marco.sequencia()));
        return true;
    }

    /** Se o botão Adiar vale para o marco agora. Calculado aqui para a tela não repetir a regra. */
    public boolean podeAdiar(Marco marco) {
        return marco.categoria() == Categoria.EXERCICIO
                && marco.status() == PENDENTE
                && !compensaAdiamento(marco)
                && seguinte(marco).isPresent();
    }

    /**
     * Entrega ao marco de exercício disparado os exercícios que o Treino montou para o bloco dele.
     *
     * @throws IllegalStateException se o marco não tem bloco ou o bloco já tem exercícios
     * @throws IllegalArgumentException se a lista está vazia ou não cabe na duração do bloco
     */
    public void atribuirBloco(UUID marcoId, List<ExercicioProposto> exercicios) {
        marco(marcoId).atribuirExercicios(exercicios);
    }

    /**
     * Códigos dos exercícios já propostos hoje, do bloco mais antigo para o mais recente. O Treino usa a
     * lista para variar os blocos (spec H3, seção 3.3) sem consultar a Agenda.
     */
    public List<String> exerciciosPropostosNoDia() {
        return marcos.stream()
                .filter(marco -> marco.categoria() == Categoria.EXERCICIO)
                .sorted(Comparator.comparingInt(Marco::sequencia))
                .flatMap(marco -> marco.bloco().stream())
                .flatMap(bloco -> bloco.exercicios().stream())
                .map(ExercicioProposto::codigo)
                .toList();
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

    /** Soma dos marcos de hidratação concluídos (Cenário 2 da H2). */
    public BigDecimal aguaIngeridaMl() {
        return marcos.stream()
                .filter(marco -> marco.categoria() == Categoria.HIDRATACAO && marco.status() == CONCLUIDO)
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

    /** Registra o status final do marco e, se ele compensava um adiado, resolve o adiado junto. */
    private void registrarEncerramento(Marco marco) {
        eventos.add(new MarcoEncerrado(marco.id(), marco.categoria(), marco.sequencia(), marco.status()));
        anterior(marco).filter(adiado -> adiado.status() == ADIADO).ifPresent(adiado -> {
            adiado.resolverAdiamento(marco.status());
            eventos.add(new MarcoEncerrado(adiado.id(), adiado.categoria(), adiado.sequencia(), adiado.status()));
        });
    }

    /** Depois de um adiamento, o bloco tem 10 min, mesmo num dia de blocos de 10 min (decisão D3). */
    private void prepararBloco(Marco marco) {
        boolean compensa = compensaAdiamento(marco);
        marco.prepararBloco(compensa ? DuracaoDoBloco.DEZ_MINUTOS : duracaoDoBloco, compensa);
    }

    private boolean compensaAdiamento(Marco marco) {
        return anterior(marco).filter(anterior -> anterior.status() == ADIADO).isPresent();
    }

    private Optional<Marco> anterior(Marco marco) {
        return marco(marco.categoria(), marco.sequencia() - 1);
    }

    private Optional<Marco> seguinte(Marco marco) {
        return marco(marco.categoria(), marco.sequencia() + 1);
    }

    private Optional<Marco> marco(Categoria categoria, int sequencia) {
        return marcos.stream()
                .filter(marco -> marco.categoria() == categoria && marco.sequencia() == sequencia)
                .findFirst();
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

    /** A duração escolhida ao iniciar o dia. O bloco que compensa um adiamento tem 10 min (D3). */
    public DuracaoDoBloco duracaoDoBloco() {
        return duracaoDoBloco;
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
