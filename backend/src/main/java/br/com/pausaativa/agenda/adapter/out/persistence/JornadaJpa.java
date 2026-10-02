package br.com.pausaativa.agenda.adapter.out.persistence;

import br.com.pausaativa.agenda.domain.StatusJornada;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** As consultas {@code ComBloqueio} emitem {@code SELECT ... FOR UPDATE} na linha da jornada. */
interface JornadaJpa extends JpaRepository<JornadaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JornadaEntity j where j.id = :id")
    Optional<JornadaEntity> buscarComBloqueio(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JornadaEntity j where j.status in :status")
    Optional<JornadaEntity> buscarPorStatusComBloqueio(Collection<StatusJornada> status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JornadaEntity j where j.id = (select m.jornada.id from MarcoEntity m where m.id = :marcoId)")
    Optional<JornadaEntity> buscarPorMarcoComBloqueio(UUID marcoId);

    Optional<JornadaEntity> findFirstByStatusIn(Collection<StatusJornada> status);

    Optional<JornadaEntity> findByDataReferencia(LocalDate dataReferencia);

    boolean existsByDataReferencia(LocalDate dataReferencia);
}
