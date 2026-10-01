package fixtures.arquitetura.r2.agenda.domain;

import fixtures.arquitetura.r2.agenda.adapter.in.web.MarcoController;

/** Viola a R2: o domínio referencia um adapter. */
public class MarcoQueConheceOAdapter {

    MarcoController controller;
}
