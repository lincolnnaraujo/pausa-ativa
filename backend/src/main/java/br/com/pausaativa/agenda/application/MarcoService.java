package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.ConfirmarRecebimento;
import br.com.pausaativa.agenda.application.port.in.CorrigirMarco;
import br.com.pausaativa.agenda.application.port.in.ResponderMarco;
import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.MarcoNaoEncontradoException;
import br.com.pausaativa.agenda.domain.StatusMarco;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Respostas aos marcos e confirmação de recebimento. A jornada é posta em dia antes: concluir um marco
 * cujo prazo venceu há menos de um segundo, antes do tick, é recusado como deveria.
 */
@Service
class MarcoService implements ResponderMarco, ConfirmarRecebimento, CorrigirMarco {

    private final JornadaRepository repositorio;
    private final AvancoDaJornada avanco;
    private final PublicadorDeAlteracoes publicador;
    private final Clock clock;

    MarcoService(
            JornadaRepository repositorio, AvancoDaJornada avanco, PublicadorDeAlteracoes publicador, Clock clock) {
        this.repositorio = repositorio;
        this.avanco = avanco;
        this.publicador = publicador;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SituacaoDaJornada concluir(UUID marcoId) {
        return alterar(marcoId, (jornada, agora) -> jornada.concluirMarco(marcoId, agora));
    }

    @Override
    @Transactional
    public SituacaoDaJornada falhar(UUID marcoId) {
        return alterar(marcoId, (jornada, agora) -> jornada.falharMarco(marcoId, agora));
    }

    @Override
    @Transactional
    public SituacaoDaJornada adiar(UUID marcoId) {
        return alterar(marcoId, (jornada, agora) -> jornada.adiarMarco(marcoId, agora));
    }

    @Override
    @Transactional
    public SituacaoDaJornada corrigir(UUID marcoId, StatusMarco correta) {
        return alterar(marcoId, (jornada, agora) -> jornada.corrigirMarco(marcoId, correta, agora, clock.getZone()));
    }

    @Override
    @Transactional
    public void confirmar(UUID marcoId) {
        alterar(marcoId, (jornada, agora) -> jornada.confirmarRecebimento(marcoId, agora));
    }

    /** Só grava e anuncia quando algo mudou: repetir a mesma resposta não gera escrita nem evento. */
    private SituacaoDaJornada alterar(UUID marcoId, BiPredicate<Jornada, Instant> operacao) {
        Instant agora = clock.instant();
        Jornada jornada = repositorio
                .buscarPorMarcoComBloqueio(marcoId)
                .orElseThrow(() -> new MarcoNaoEncontradoException(marcoId));
        boolean emDia = avanco.avancar(jornada, agora).houveMudanca();
        boolean respondeu = operacao.test(jornada, agora);
        if (!emDia && !respondeu) {
            return SituacaoDaJornada.de(jornada, agora, clock.getZone());
        }
        repositorio.salvar(jornada);
        return publicador.publicar(jornada, agora);
    }
}
