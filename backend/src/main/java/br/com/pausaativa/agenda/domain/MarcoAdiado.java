package br.com.pausaativa.agenda.domain;

import java.util.UUID;

/** O usuário adiou o bloco de exercício: o seguinte vai compensá-lo e decidir o destino dos dois. */
public record MarcoAdiado(UUID marcoId, Categoria categoria, int sequencia) implements EventoDaJornada {}
