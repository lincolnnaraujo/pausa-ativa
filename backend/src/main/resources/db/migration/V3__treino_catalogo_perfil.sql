-- H3: catálogo de exercícios e perfil físico (spec H3, seção 5).
-- O catálogo entra por migração e não muda pela tela. Foi revisado com o usuário em 2026-10-02.

create table exercicio (
    codigo                   varchar(40)  primary key,
    nome                     varchar(80)  not null,
    grupo                    varchar(20)  not null,
    -- Posição no catálogo: desempata a escolha dentro de um grupo.
    ordem                    integer      not null,
    instrucao                varchar(300) not null,
    forma                    varchar(20)  not null,
    -- Nulo: o exercício é só do intermediário.
    quantidade_iniciante     integer,
    quantidade_intermediario integer      not null,
    -- Nulo: não exige equipamento.
    equipamento              varchar(20),
    no_chao                  boolean      not null,
    reserva                  boolean      not null,

    constraint uk_exercicio_ordem unique (ordem),
    constraint ck_exercicio_grupo
        check (grupo in ('PERNAS', 'PEITO', 'COSTAS', 'CORE', 'POSTERIOR', 'OMBROS', 'BRACOS', 'MOBILIDADE', 'CARDIO_LEVE')),
    constraint ck_exercicio_forma check (forma in ('REPETICOES', 'POR_LADO', 'SEGUNDOS', 'SEGUNDOS_POR_LADO')),
    constraint ck_exercicio_equipamento
        check (equipamento is null or equipamento in ('CADEIRA', 'MESA', 'APOIO_DE_FLEXAO', 'HALTERES_2KG')),
    constraint ck_exercicio_quantidades
        check ((quantidade_iniciante is null or quantidade_iniciante > 0) and quantidade_intermediario > 0),
    -- A reserva serve para qualquer perfil. A falta de restrição é conferida pelo domínio ao carregar.
    constraint ck_exercicio_reserva
        check (not reserva or (equipamento is null and not no_chao and quantidade_iniciante is not null))
);

create table exercicio_restricao (
    exercicio_codigo varchar(40) not null references exercicio (codigo),
    articulacao      varchar(20) not null,

    primary key (exercicio_codigo, articulacao),
    constraint ck_exercicio_restricao check (articulacao in ('JOELHO', 'OMBRO', 'PUNHO', 'LOMBAR', 'CERVICAL'))
);

-- Um perfil só: o usuário é único.
create table perfil_fisico (
    id            integer     primary key,
    nivel         varchar(20) not null,
    aceita_chao   boolean     not null,
    atualizado_em timestamptz not null,

    constraint ck_perfil_unico check (id = 1),
    constraint ck_perfil_nivel check (nivel in ('INICIANTE', 'INTERMEDIARIO'))
);

create table perfil_articulacao (
    perfil_id   integer     not null references perfil_fisico (id) on delete cascade,
    articulacao varchar(20) not null,

    primary key (perfil_id, articulacao),
    constraint ck_perfil_articulacao check (articulacao in ('JOELHO', 'OMBRO', 'PUNHO', 'LOMBAR', 'CERVICAL'))
);

-- Cadeira e mesa contam como disponíveis e não entram no perfil.
create table perfil_equipamento (
    perfil_id   integer     not null references perfil_fisico (id) on delete cascade,
    equipamento varchar(20) not null,

    primary key (perfil_id, equipamento),
    constraint ck_perfil_equipamento check (equipamento in ('APOIO_DE_FLEXAO', 'HALTERES_2KG'))
);

insert into exercicio
    (codigo, nome, grupo, ordem, instrucao, forma, quantidade_iniciante, quantidade_intermediario, equipamento, no_chao, reserva)
values
    ('sentar-e-levantar', 'Sentar e levantar da cadeira', 'PERNAS', 1,
     'Sente e levante da cadeira sem usar as mãos, com os pés na largura do quadril.',
     'REPETICOES', 10, 15, 'CADEIRA', false, false),
    ('agachamento-livre', 'Agachamento livre', 'PERNAS', 2,
     'Pés na largura dos ombros; desça como se fosse sentar, com o peito aberto e os joelhos na direção dos pés.',
     'REPETICOES', 10, 15, null, false, false),
    ('afundo-alternado', 'Afundo alternado', 'PERNAS', 3,
     'Dê um passo à frente e desça até os dois joelhos dobrarem perto de 90°; volte e troque de perna.',
     'POR_LADO', null, 8, null, false, false),
    ('elevacao-de-panturrilha', 'Elevação de panturrilha', 'PERNAS', 4,
     'Apoiado na mesa ou na parede, suba na ponta dos pés e desça devagar.',
     'REPETICOES', 15, 20, null, false, false),
    ('ponte-de-gluteo', 'Ponte de glúteo', 'POSTERIOR', 5,
     'Deitado de costas, joelhos dobrados, eleve o quadril contraindo os glúteos e desça devagar.',
     'REPETICOES', 12, 15, null, true, false),
    ('extensao-de-quadril', 'Extensão de quadril em pé, apoiado na mesa', 'POSTERIOR', 6,
     'Mãos na mesa, leve uma perna estendida para trás sem arquear a lombar e volte devagar.',
     'POR_LADO', 10, 12, 'MESA', false, false),
    ('flexao-na-parede', 'Flexão na parede', 'PEITO', 7,
     'Mãos na parede na altura dos ombros; leve o peito à parede dobrando os cotovelos e empurre de volta.',
     'REPETICOES', 10, 15, null, false, false),
    ('flexao-inclinada', 'Flexão inclinada na mesa', 'PEITO', 8,
     'Mãos na borda de uma mesa firme, corpo reto; desça o peito até a mesa e empurre de volta.',
     'REPETICOES', 8, 12, 'MESA', false, false),
    ('flexao-com-apoio', 'Flexão com apoio', 'PEITO', 9,
     'No chão, mãos nos apoios e corpo reto da cabeça aos pés; desça o peito e empurre de volta. Apoie os joelhos se precisar.',
     'REPETICOES', null, 8, 'APOIO_DE_FLEXAO', true, false),
    ('prancha', 'Prancha', 'CORE', 10,
     'Antebraços no chão, corpo reto da cabeça aos pés, abdômen contraído; respire normalmente.',
     'SEGUNDOS', 20, 40, null, true, false),
    ('bird-dog', 'Bird-dog', 'CORE', 11,
     'De quatro, estenda um braço à frente e a perna oposta para trás, sem girar o quadril; volte e troque.',
     'POR_LADO', 6, 10, null, true, false),
    ('remada-curvada', 'Remada curvada', 'COSTAS', 12,
     'Tronco inclinado à frente com a coluna reta; puxe os halteres até a cintura e desça devagar.',
     'REPETICOES', 12, 15, 'HALTERES_2KG', false, false),
    ('anjo-na-parede', 'Anjo na parede', 'COSTAS', 13,
     'Costas e cabeça na parede, braços em W encostados; deslize os braços para cima e para baixo sem descolar.',
     'REPETICOES', 8, 12, null, false, false),
    ('retracao-escapular', 'Retração escapular em pé', 'COSTAS', 14,
     'Braços ao lado do corpo; junte as escápulas para trás e para baixo, segure 2 s e solte.',
     'REPETICOES', 12, 15, null, false, false),
    ('desenvolvimento', 'Desenvolvimento de ombros', 'OMBROS', 15,
     'Halteres na altura dos ombros; empurre para cima até quase estender os braços e desça devagar.',
     'REPETICOES', 10, 12, 'HALTERES_2KG', false, false),
    ('elevacao-lateral', 'Elevação lateral', 'OMBROS', 16,
     'Halteres ao lado do corpo; eleve os braços pelas laterais até a altura dos ombros e desça devagar.',
     'REPETICOES', 10, 12, 'HALTERES_2KG', false, false),
    ('rosca-direta', 'Rosca direta', 'BRACOS', 17,
     'Cotovelos parados ao lado do corpo; leve os halteres aos ombros dobrando os braços e desça devagar.',
     'REPETICOES', 12, 15, 'HALTERES_2KG', false, false),
    ('marcha-estacionaria', 'Marcha estacionária', 'CARDIO_LEVE', 18,
     'Marche no lugar, elevando os joelhos e balançando os braços, num ritmo confortável.',
     'SEGUNDOS', 60, 90, null, false, true),
    ('mobilidade-toracica', 'Mobilidade torácica', 'MOBILIDADE', 19,
     'Braços cruzados no peito, gire o tronco devagar para um lado e para o outro, sem mexer o quadril.',
     'POR_LADO', 8, 10, null, false, true),
    ('mobilidade-cervical', 'Mobilidade cervical', 'MOBILIDADE', 20,
     'Incline a cabeça devagar para cada lado e depois para a frente, sem forçar nem girar rápido.',
     'SEGUNDOS', 30, 45, null, false, false),
    ('alongamento-quadril', 'Alongamento de flexores do quadril', 'MOBILIDADE', 21,
     'Um pé à frente e outro atrás; contraia o glúteo da perna de trás e leve o quadril à frente.',
     'SEGUNDOS_POR_LADO', 30, 30, null, false, true),
    ('alongamento-punhos', 'Alongamento de punhos e antebraços', 'MOBILIDADE', 22,
     'Braço estendido, palma para cima; puxe os dedos para baixo com a outra mão, suavemente. Depois repita com a palma para baixo.',
     'SEGUNDOS', 30, 30, null, false, true);

insert into exercicio_restricao (exercicio_codigo, articulacao)
values
    ('agachamento-livre', 'JOELHO'),
    ('afundo-alternado', 'JOELHO'),
    ('flexao-na-parede', 'OMBRO'),
    ('flexao-na-parede', 'PUNHO'),
    ('flexao-inclinada', 'OMBRO'),
    ('flexao-inclinada', 'PUNHO'),
    ('flexao-com-apoio', 'OMBRO'),
    ('prancha', 'OMBRO'),
    ('prancha', 'PUNHO'),
    ('prancha', 'LOMBAR'),
    ('bird-dog', 'PUNHO'),
    ('bird-dog', 'JOELHO'),
    ('remada-curvada', 'LOMBAR'),
    ('anjo-na-parede', 'OMBRO'),
    ('desenvolvimento', 'OMBRO'),
    ('desenvolvimento', 'CERVICAL'),
    ('elevacao-lateral', 'OMBRO'),
    ('elevacao-lateral', 'CERVICAL'),
    ('rosca-direta', 'PUNHO'),
    ('mobilidade-cervical', 'CERVICAL');
