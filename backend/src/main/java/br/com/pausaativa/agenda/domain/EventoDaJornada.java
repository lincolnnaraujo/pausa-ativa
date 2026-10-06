package br.com.pausaativa.agenda.domain;

import java.util.UUID;

/** Algo que aconteceu com um marco. A jornada acumula e a aplicação publica depois de gravar. */
public sealed interface EventoDaJornada permits MarcoDisparado, MarcoEncerrado, MarcoAdiado, MarcoCorrigido {

    UUID marcoId();

    Categoria categoria();
}
