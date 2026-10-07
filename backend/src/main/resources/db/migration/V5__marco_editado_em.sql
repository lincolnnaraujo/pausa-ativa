-- H4: correção no mesmo dia (spec H4, seções 3.3 e 5). O marco guarda o instante da última correção;
-- a marca fica mesmo se a correção for desfeita. Só concluído e falha se corrigem (decisão D4).
alter table marco add column editado_em timestamptz;
alter table marco add constraint ck_marco_editado check (editado_em is null or status in ('CONCLUIDO', 'FALHA'));
