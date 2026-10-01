package br.com.pausaativa.agenda.application;

import br.com.pausaativa.agenda.application.port.in.ConfirmarRecebimento;
import br.com.pausaativa.agenda.application.port.in.ResponderMarco;
import br.com.pausaativa.agenda.application.port.in.SituacaoDaJornada;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.MarcoNaoEncontradoException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Respostas aos marcos e confirmação de recebimento. Só grava quando algo mudou. */
@Service
class MarcoService implements ResponderMarco, ConfirmarRecebimento {

    private final JornadaRepository repositorio;
    private final Clock clock;

    MarcoService(JornadaRepository repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SituacaoDaJornada concluir(UUID marcoId) {
        Instant agora = clock.instant();
        Jornada jornada = jornadaDoMarco(marcoId);
        if (jornada.concluirMarco(marcoId, agora)) {
            repositorio.salvar(jornada);
        }
        return SituacaoDaJornada.de(jornada, agora, clock.getZone());
    }

    @Override
    @Transactional
    public SituacaoDaJornada falhar(UUID marcoId) {
        Instant agora = clock.instant();
        Jornada jornada = jornadaDoMarco(marcoId);
        if (jornada.falharMarco(marcoId, agora)) {
            repositorio.salvar(jornada);
        }
        return SituacaoDaJornada.de(jornada, agora, clock.getZone());
    }

    @Override
    @Transactional
    public void confirmar(UUID marcoId) {
        Jornada jornada = jornadaDoMarco(marcoId);
        if (jornada.confirmarRecebimento(marcoId, clock.instant())) {
            repositorio.salvar(jornada);
        }
    }

    private Jornada jornadaDoMarco(UUID marcoId) {
        return repositorio
                .buscarPorMarcoComBloqueio(marcoId)
                .orElseThrow(() -> new MarcoNaoEncontradoException(marcoId));
    }
}
