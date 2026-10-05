-- H3: marcos de exercício, adiamento e o bloco de cada marco (spec H3, seção 5).

-- Duração do bloco escolhida ao iniciar o dia. As jornadas da v0.2.0 ficam com 5 min, o padrão.
alter table jornada add column duracao_bloco_min integer not null default 5;
alter table jornada alter column duracao_bloco_min drop default;
alter table jornada add constraint ck_jornada_duracao_bloco check (duracao_bloco_min in (5, 10));

alter table marco drop constraint ck_marco_categoria;
alter table marco add constraint ck_marco_categoria check (categoria in ('HIDRATACAO', 'EXERCICIO'));

alter table marco drop constraint ck_marco_status;
alter table marco add constraint ck_marco_status
    check (status in ('AGENDADO', 'PENDENTE', 'CONCLUIDO', 'FALHA', 'NAO_ENTREGUE', 'NAO_CONCLUIDO', 'ADIADO'));
-- Só o exercício pode ser adiado.
alter table marco add constraint ck_marco_adiado check (status <> 'ADIADO' or categoria = 'EXERCICIO');

-- Só a água tem volume.
alter table marco alter column volume_ml drop not null;
alter table marco add constraint ck_marco_volume check ((categoria = 'HIDRATACAO') = (volume_ml is not null));

-- Bloco do exercício, preenchido no disparo. Depois de um adiamento, tem 10 min (decisão D3).
alter table marco add column duracao_bloco_min integer;
alter table marco add column compensa_adiamento boolean;
alter table marco add constraint ck_marco_bloco check (
    (duracao_bloco_min is null) = (compensa_adiamento is null)
    and (duracao_bloco_min is null or (categoria = 'EXERCICIO' and duracao_bloco_min in (5, 10))));

-- Exercícios do bloco, copiados do catálogo no disparo (decisão D1): o histórico mostra o que foi proposto,
-- mesmo que o catálogo mude depois. Por isso não há chave estrangeira para exercicio.
create table item_do_bloco (
    marco_id           uuid         not null references marco (id) on delete cascade,
    ordem              integer      not null,
    exercicio_codigo   varchar(40)  not null,
    nome               varchar(80)  not null,
    grupo              varchar(20)  not null,
    quantidade_texto   varchar(40)  not null,
    segundos_estimados integer      not null,
    instrucao          varchar(300) not null,

    primary key (marco_id, ordem),
    constraint ck_item_do_bloco_ordem check (ordem >= 1),
    constraint ck_item_do_bloco_segundos check (segundos_estimados > 0)
);
