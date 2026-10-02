package br.com.pausaativa.agenda.application.port.out;

import br.com.pausaativa.agenda.domain.Jornada;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência da jornada. Os métodos {@code ComBloqueio} travam a jornada até o fim da transação:
 * o agendador e os cliques do usuário não alteram a mesma jornada ao mesmo tempo.
 */
public interface JornadaRepository {

    Optional<Jornada> buscarComBloqueio(UUID jornadaId);

    Optional<Jornada> buscarAbertaComBloqueio();

    Optional<Jornada> buscarPorMarcoComBloqueio(UUID marcoId);

    Optional<Jornada> buscarAberta();

    Optional<Jornada> buscarDoDia(LocalDate dia);

    boolean existeNoDia(LocalDate dia);

    /**
     * Grava e envia ao banco na hora, para as restrições valerem dentro da transação.
     *
     * @throws br.com.pausaativa.agenda.domain.JornadaJaIniciadaException se o banco recusar uma
     *     segunda jornada para o dia ou uma segunda jornada aberta
     */
    void salvar(Jornada jornada);
}
