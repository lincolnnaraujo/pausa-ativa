package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.application.port.out.ContagemDoDia;
import br.com.pausaativa.agenda.application.port.out.JornadaRepository;
import br.com.pausaativa.agenda.domain.Jornada;
import br.com.pausaativa.agenda.domain.JornadaJaIniciadaException;
import br.com.pausaativa.agenda.domain.StatusJornada;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JornadaRepositoryJpa implements JornadaRepository {

    private static final Set<StatusJornada> ABERTAS = EnumSet.of(StatusJornada.EM_ANDAMENTO, StatusJornada.PAUSADA);

    private final JornadaJpa jpa;

    JornadaRepositoryJpa(JornadaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Jornada> buscarComBloqueio(UUID jornadaId) {
        return jpa.buscarComBloqueio(jornadaId).map(JornadaEntity::paraDominio);
    }

    @Override
    public Optional<Jornada> buscarAbertaComBloqueio() {
        return jpa.buscarPorStatusComBloqueio(ABERTAS).map(JornadaEntity::paraDominio);
    }

    @Override
    public Optional<Jornada> buscarPorMarcoComBloqueio(UUID marcoId) {
        return jpa.buscarPorMarcoComBloqueio(marcoId).map(JornadaEntity::paraDominio);
    }

    @Override
    public Optional<Jornada> buscarAberta() {
        return jpa.findFirstByStatusIn(ABERTAS).map(JornadaEntity::paraDominio);
    }

    @Override
    public Optional<Jornada> buscarDoDia(LocalDate dia) {
        return jpa.findByDataReferencia(dia).map(JornadaEntity::paraDominio);
    }

    @Override
    public boolean existeNoDia(LocalDate dia) {
        return jpa.existsByDataReferencia(dia);
    }

    @Override
    public boolean existeAntesDe(LocalDate dia) {
        return jpa.existsByDataReferenciaBefore(dia);
    }

    @Override
    public List<ContagemDoDia> contarPorDia(LocalDate de, LocalDate ate) {
        return jpa.contarPorDia(de, ate);
    }

    /**
     * Atualiza a entidade já carregada na transação (ou cria uma nova) e envia ao banco na hora: as
     * restrições de uma jornada por dia e de uma jornada aberta valem mesmo entre requisições simultâneas.
     */
    @Override
    public void salvar(Jornada jornada) {
        JornadaEntity entidade = jpa.findById(jornada.id())
                .map(existente -> {
                    existente.atualizarCom(jornada);
                    return existente;
                })
                .orElseGet(() -> JornadaEntity.nova(jornada));
        try {
            jpa.saveAndFlush(entidade);
        } catch (DataIntegrityViolationException e) {
            if (violouUnicidadeDaJornada(e)) {
                throw new JornadaJaIniciadaException(jornada.dataReferencia());
            }
            throw e;
        }
    }

    private static boolean violouUnicidadeDaJornada(DataIntegrityViolationException e) {
        String mensagem = String.valueOf(e.getMostSpecificCause().getMessage());
        return mensagem.contains("uk_jornada_data_referencia") || mensagem.contains("uk_jornada_aberta");
    }
}
