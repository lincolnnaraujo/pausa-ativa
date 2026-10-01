package fixtures.arquitetura.r3.agenda.application;

import fixtures.arquitetura.r3.agenda.adapter.out.persistence.JornadaEntity;

/** Viola a R3: o caso de uso usa a entidade de persistência em vez de uma porta de saída. */
public class IniciarJornadaService {

    JornadaEntity entidade;
}
