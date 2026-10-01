-- H2: jornada de trabalho, pausas e marcos (spec H2, seção 5).
-- Instantes em timestamptz (gravados em UTC); o dia da jornada é a data em America/Sao_Paulo.

create table jornada (
    id              uuid        primary key,
    versao          bigint      not null,
    data_referencia date        not null,
    status          varchar(30) not null,
    meta_agua_ml    integer     not null,
    iniciada_em     timestamptz not null,
    finalizada_em   timestamptz,

    -- Uma jornada por dia (decisão D3).
    constraint uk_jornada_data_referencia unique (data_referencia),
    -- Teto de segurança de 6.000 ml (decisão D4).
    constraint ck_jornada_meta_agua check (meta_agua_ml between 1 and 6000),
    constraint ck_jornada_status
        check (status in ('EM_ANDAMENTO', 'PAUSADA', 'FINALIZADA', 'ENCERRADA_AUTOMATICAMENTE')),
    constraint ck_jornada_finalizada
        check ((status in ('EM_ANDAMENTO', 'PAUSADA')) = (finalizada_em is null))
);

-- No máximo uma jornada aberta, mesmo com dois "Iniciar dia" simultâneos.
create unique index uk_jornada_aberta on jornada ((true)) where status in ('EM_ANDAMENTO', 'PAUSADA');

create table pausa (
    jornada_id uuid        not null references jornada (id) on delete cascade,
    inicio     timestamptz not null,
    fim        timestamptz,

    primary key (jornada_id, inicio),
    constraint ck_pausa_intervalo check (fim is null or fim >= inicio)
);

create table marco (
    id                             uuid          primary key,
    jornada_id                     uuid          not null references jornada (id) on delete cascade,
    categoria                      varchar(20)   not null,
    sequencia                      integer       not null,
    status                         varchar(20)   not null,
    segundos_trabalhados_previstos integer       not null,
    -- Prazo de resposta, em tempo trabalhado. Cada marco guarda o seu: na H3 o exercício tem outro intervalo.
    segundos_trabalhados_limite    integer       not null,
    -- Exato: meta / 16 tem no máximo 4 casas decimais.
    volume_ml                      numeric(8, 4) not null,
    previsto_para                  timestamptz,
    disparado_em                   timestamptz,
    recebido_em                    timestamptz,
    respondido_em                  timestamptz,

    constraint uk_marco_sequencia unique (jornada_id, categoria, sequencia),
    constraint ck_marco_categoria check (categoria in ('HIDRATACAO')),
    constraint ck_marco_status
        check (status in ('AGENDADO', 'PENDENTE', 'CONCLUIDO', 'FALHA', 'NAO_ENTREGUE', 'NAO_CONCLUIDO')),
    constraint ck_marco_prazo check (segundos_trabalhados_limite > segundos_trabalhados_previstos)
);
