package fixtures.arquitetura.r5.agenda.application.port.in;

import fixtures.arquitetura.r5.treino.application.port.in.MontarBloco;

/** Viola a R5 junto com {@link MontarBloco}: Agenda e Treino dependem um do outro. */
public interface IniciarJornada {

    void iniciar(MontarBloco montarBloco);
}
