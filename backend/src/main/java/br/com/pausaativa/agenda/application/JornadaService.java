package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.ConsultarJornadaAtual;
import br.com.pausaativa.agenda.application.port.in.ConsultarJornadaDoDia;
import br.com.pausaativa.agenda.application.port.in.FinalizarJornada;
import br.com.pausaativa.agenda.application.port.in.IniciarJornada;
import br.com.pausaativa.agenda.application.port.in.PausarJornada;
import br.com.pausaativa.agenda.application.port.in.RetomarJornada;
import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import br.com.pausaativa.agenda.application.port.out.MontadorDeBlocos;
import br.com.pausaativa.agenda.domain.DuracaoDoBloco;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.JornadaJaIniciadaException;
import br.com.pausaativa.agenda.domain.MetaDeAgua;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida da jornada: iniciar, pausar, retomar, finalizar e consultar. */
@Service
class JornadaService
        implements IniciarJornada,
                PausarJornada,
                RetomarJornada,
                FinalizarJornada,
                ConsultarJornadaAtual,
                ConsultarJornadaDoDia {

    private final JornadaRepository repositorio;
    private final MontadorDeBlocos montador;
    private final AvancoDaJornada avanco;
    private final ConfiguracaoDaAgenda configuracao;
    private final PublicadorDeAlteracoes publicador;
    private final Clock clock;

    JornadaService(
            JornadaRepository repositorio,
            MontadorDeBlocos montador,
            AvancoDaJornada avanco,
            ConfiguracaoDaAgenda configuracao,
            PublicadorDeAlteracoes publicador,
            Clock clock) {
        this.repositorio = repositorio;
        this.montador = montador;
        this.avanco = avanco;
        this.configuracao = configuracao;
        this.publicador = publicador;
        this.clock = clock;
    }

    /**
     * Exige o perfil físico (D7 da H3). Encerra antes a jornada esquecida; uma jornada ainda em uso, ou a
     * de hoje já finalizada, impede (D3 da H2).
     */
    @Override
    @Transactional
    public SituacaoDaJornada iniciar(int metaAguaMl, int duracaoBlocoMin) {
        MetaDeAgua meta = new MetaDeAgua(metaAguaMl);
        DuracaoDoBloco duracaoDoBloco = new DuracaoDoBloco(duracaoBlocoMin);
        if (!montador.perfilPreenchido()) {
            throw new PerfilAusenteException();
        }
        Instant agora = clock.instant();
        LocalDate hoje = LocalDate.ofInstant(agora, clock.getZone());

        Optional<Jornada> aberta = repositorio.buscarAbertaComBloqueio();
        if (aberta.isPresent()) {
            Jornada anterior = aberta.get();
            anterior.reconciliar(agora);
            if (!anterior.esquecida(agora, clock.getZone())) {
                throw new JornadaJaIniciadaException(anterior.dataReferencia());
            }
            anterior.encerrarAutomaticamente(agora);
            repositorio.salvar(anterior);
            publicador.publicar(anterior, agora);
        }
        if (repositorio.existeNoDia(hoje)) {
            throw new JornadaJaIniciadaException(hoje);
        }

        Jornada jornada =
                Jornada.iniciar(UUID.randomUUID(), agora, clock.getZone(), meta, duracaoDoBloco, configuracao.planos());
        repositorio.salvar(jornada);
        return publicador.publicar(jornada, agora);
    }

    @Override
    @Transactional
    public SituacaoDaJornada pausar(UUID jornadaId) {
        return alterar(jornadaId, Jornada::pausar);
    }

    @Override
    @Transactional
    public SituacaoDaJornada retomar(UUID jornadaId) {
        return alterar(jornadaId, Jornada::retomar);
    }

    @Override
    @Transactional
    public SituacaoDaJornada finalizar(UUID jornadaId) {
        return alterar(jornadaId, Jornada::finalizar);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacaoDaJornada> consultar() {
        Instant agora = clock.instant();
        return repositorio
                .buscarAberta()
                .or(() -> repositorio.buscarDoDia(LocalDate.ofInstant(agora, clock.getZone())))
                .map(jornada -> SituacaoDaJornada.de(jornada, agora, clock.getZone()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SituacaoDaJornada> doDia(LocalDate dia) {
        Instant agora = clock.instant();
        return repositorio.buscarDoDia(dia).map(jornada -> SituacaoDaJornada.de(jornada, agora, clock.getZone()));
    }

    /**
     * Põe a jornada em dia antes da operação, como faria o próximo tick: um marco que venceu há menos
     * de um segundo dispara (ou vence) antes de a jornada pausar ou finalizar.
     */
    private SituacaoDaJornada alterar(UUID jornadaId, BiConsumer<Jornada, Instant> operacao) {
        Instant agora = clock.instant();
        Jornada jornada = repositorio
                .buscarComBloqueio(jornadaId)
                .orElseThrow(() -> new JornadaNaoEncontradaException(jornadaId));
        avanco.avancar(jornada, agora);
        operacao.accept(jornada, agora);
        repositorio.salvar(jornada);
        return publicador.publicar(jornada, agora);
    }
}
