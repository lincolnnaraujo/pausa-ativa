# Spec H3: Montar e executar blocos de exercício (release v0.3.0)

> **Status:** rascunho para revisão do usuário (DoR). As decisões em aberto estão na seção 13.
> **Origem:** História 3 de [`docs/epico-pausa-ativa.md`](../epico-pausa-ativa.md). O catálogo foi revisado com o usuário em 2026-10-02.
> **Depende de:** H2 (v0.2.0), entregue em 2026-10-02.
> **Data:** 2026-10-02.

## 1. Objetivo

Ao fim desta release, o usuário:

- preenche uma vez o perfil físico: articulações a poupar, nível, equipamentos e se pode ir ao chão;
- escolhe, ao iniciar o dia, blocos de exercício de 5 min (padrão) ou 10 min;
- recebe a cada 60 min de tempo trabalhado um bloco de exercícios montado para o seu perfil;
- responde cada bloco com **Concluir**, **Adiar** ou **Falhar**, e o adiamento é resolvido pelo bloco seguinte;
- na hora cheia, recebe uma notificação só, com água e exercício, que responde de forma independente na página.

Os gráficos e a taxa de sucesso por categoria ficam para a H4.

## 2. Escopo

| Dentro | Fora (história que entrega) |
|---|---|
| Perfil físico: formulário, edição, obrigatório para iniciar o dia | Gráficos e taxa de sucesso por categoria (H4) |
| Catálogo de 22 exercícios via migração, com instrução curta | Edição do catálogo pela tela; progressão de carga (melhoria futura) |
| Seleção determinística do bloco por perfil, grupo e duração | Cronômetro guiado durante o bloco (melhoria futura) |
| 8 marcos de exercício por jornada, a cada 60 min trabalhados | Edição de respostas no mesmo dia (H4) |
| Concluir, Adiar e Falhar; status `ADIADO` | Prometheus e Grafana (H5) |
| Duração do bloco escolhida ao iniciar (5 ou 10 min) | |
| Notificação combinada na hora cheia | |
| Modo demonstração com exercício a cada 2 min | |
| Métricas e logs dos blocos | |

## 3. Regras de domínio refinadas

O épico define as regras. Esta seção as torna precisas o bastante para o código e os testes. Os pontos que o épico não decidia estão marcados com **(D*n*)** e listados na seção 13.

### 3.1 Perfil físico

| Campo | Valores | Padrão no formulário |
|---|---|---|
| Articulações a poupar | Nenhuma ou várias de: joelho, ombro, punho, lombar, cervical | Nenhuma |
| Nível | Iniciante, intermediário | Iniciante |
| Equipamentos | Nenhum ou vários de: apoio de flexão, halteres de 2 kg | Nenhum |
| Exercícios no chão | Sim ou não | Sim |

- Cadeira e mesa são consideradas disponíveis: estão em todo home office.
- Existe um perfil só, porque o usuário é único. Ele pode ser editado a qualquer momento. A mudança vale para os blocos montados depois dela; os blocos já disparados não mudam.
- Sem perfil, o dia não começa (Cenário 6). A tela leva ao formulário, e o backend também recusa o início **(D7)**.

### 3.2 Catálogo

O catálogo está no [épico](../epico-pausa-ativa.md), seção "Treino": 22 exercícios com grupo, quantidade por nível, equipamento, "no chão" e restrições. Ele entra no banco por migração e não muda pela tela. Cada exercício ganha uma instrução curta de como fazer **(D8)**; a proposta está no [apêndice A](#apêndice-a-instruções-dos-exercícios).

A quantidade é uma de três formas:

| Forma | Exemplo | Texto na tela |
|---|---|---|
| Repetições | Sentar e levantar, 10 | "10 repetições" |
| Repetições por lado | Bird-dog, 6 por lado | "6 por lado" |
| Segundos (por lado ou não) | Prancha, 20 s | "20 s" |

### 3.3 Seleção do bloco

A seleção é determinística: o mesmo perfil, a mesma duração e o mesmo histórico do dia dão sempre o mesmo bloco.

**1. Elegíveis.** Um exercício entra se cumpre todas as condições:

- não tem restrição em comum com as articulações a poupar;
- não exige equipamento, exige cadeira ou mesa, ou exige um equipamento do perfil;
- tem quantidade para o nível do perfil (o afundo e a flexão com apoio são só do intermediário);
- não é "no chão", ou o perfil aceita exercícios no chão.

Os exercícios de reserva (marcha estacionária, mobilidade torácica e os dois alongamentos) cumprem as condições para qualquer perfil. Por isso, nenhum perfil fica sem bloco, e o Cenário 7 resulta da própria regra.

**2. Estimativa de duração (D2).** Cada repetição vale 3 s, e cada segundo vale 1 s. "Por lado" dobra o tempo do exercício, e cada troca de exercício soma 15 s.

| Exemplo (iniciante) | Conta | Estimativa |
|---|---|---|
| Sentar e levantar, 10 | 10 × 3 s + 15 s | 45 s |
| Bird-dog, 6 por lado | 6 × 3 s × 2 + 15 s | 51 s |
| Prancha, 20 s | 20 s + 15 s | 35 s |

**3. Ordem.** Os grupos seguem uma ordem fixa: Pernas, Peito, Costas, Core, Posterior, Ombros, Braços, Mobilidade, Cardio leve.

- O bloco do marco *n* começa pelo grupo da posição (*n* − 1), contada em ciclo. Assim, cada hora começa por um grupo diferente: alternância entre marcos.
- A montagem percorre os grupos em rodízio, pondo no máximo um exercício por grupo a cada volta. Em cada grupo, os candidatos vão do usado há mais tempo no dia para o mais recente. Os nunca usados vêm primeiro, na ordem do catálogo. Assim, os exercícios variam de um bloco para o outro.

**4. Preenchimento.** Em cada grupo, entra o primeiro candidato cuja estimativa ainda cabe na duração do bloco; se nenhum cabe, a montagem passa ao próximo grupo. Um exercício que já está no bloco só se repete quando todos os elegíveis já entraram (segunda volta). A montagem termina quando uma volta inteira pelos grupos não acrescenta nada.

| Exemplo | Bloco montado (estimativa) |
|---|---|
| Iniciante, sem equipamento, aceita o chão, nada a poupar, marco 1, 5 min | Sentar e levantar 10 · Flexão na parede 10 · Anjo na parede 8 · Prancha 20 s · Ponte de glúteo 12 · Mobilidade torácica 8 por lado (≈ 4 min 38 s). Ombros e braços ficam de fora, por falta de halteres. A marcha (75 s) já não cabe nos 22 s restantes. |

O bloco fica gravado no marco no momento do disparo **(D1)**, com nome, quantidade e instrução de cada exercício. Mudanças posteriores no catálogo ou no perfil não alteram blocos já disparados, e o histórico da H4 mostra o que foi proposto de fato.

### 3.4 Marcos de exercício

- Ao iniciar o dia, a jornada cria os **8 marcos de exercício** `AGENDADO`, junto com os 16 de hidratação.
- O marco de exercício *n* (1 a 8) dispara aos *n* × 60 min de tempo trabalhado: na mesma hora cheia que o marco de água 2*n*.
- O prazo é de 60 min de tempo trabalhado, o instante do exercício seguinte. O 8º vence às 9 h trabalhadas **(D6)**, como o 16º de água vence às 8 h 30.
- O bloco tem a duração escolhida ao iniciar o dia (5 ou 10 min). Depois de um adiamento, o bloco seguinte tem 10 min **(D3)**.
- Pausa, finalização, jornada esquecida e backend fora seguem as regras da H2 (seções 3.3 a 3.5), como na água.

### 3.5 Adiar

| Regra | Definição |
|---|---|
| Quem pode | Só um marco de exercício `PENDENTE`. A hidratação não tem Adiar. |
| Limite | Uma vez por cadeia: o marco seguinte a um `ADIADO` não pode ser adiado (Cenário 5; 409 com a mensagem "Este bloco já compensa um adiamento: conclua ou marque falha"). |
| Último marco | O 8º não pode ser adiado: não há bloco seguinte **(D5)**. |
| Efeito | O marco vira `ADIADO`, sem prazo próprio. O bloco seguinte tem 10 min e avisa que compensa o adiado. |
| Resolução | O `ADIADO` segue o destino do marco seguinte. Concluído → os dois `CONCLUIDO` (Cenário 4). Falha → os dois `FALHA`. Não entregue ou não concluído → o adiado fica igual **(D4)**. |
| Idempotência | Adiar de novo o mesmo marco devolve o estado atual. Responder um `ADIADO` diretamente é recusado (409): ele é resolvido pelo seguinte. |

### 3.6 Ciclo de vida do marco de exercício

```mermaid
stateDiagram-v2
    [*] --> AGENDADO: iniciar o dia
    AGENDADO --> PENDENTE: tempo trabalhado chega ao marco (bloco montado)
    AGENDADO --> NAO_CONCLUIDO: finalizar o dia
    AGENDADO --> NAO_ENTREGUE: backend fora no instante,<br/>ou jornada encerrada automaticamente
    PENDENTE --> CONCLUIDO: usuário conclui
    PENDENTE --> FALHA: usuário marca falha,<br/>ou prazo vence com recebimento
    PENDENTE --> NAO_ENTREGUE: prazo vence sem recebimento
    PENDENTE --> NAO_CONCLUIDO: finalizar o dia
    PENDENTE --> ADIADO: usuário adia
    ADIADO --> CONCLUIDO: o seguinte é concluído
    ADIADO --> FALHA: o seguinte falha
    ADIADO --> NAO_ENTREGUE: o seguinte não é entregue
    ADIADO --> NAO_CONCLUIDO: o seguinte não é concluído
```

### 3.7 Notificação combinada

- Na hora cheia, o marco de água e o de exercício disparam no mesmo tick e chegam juntos à tela. A tela junta os dois numa notificação só: "Hora da água e do exercício 💧🏃", com "Beba ~190 ml. Bloco de 5 min: 6 exercícios." (Cenário 2).
- Na página, os dois aparecem em cartões separados, cada um com os seus botões. A resposta de um não muda o outro.
- Nas meias horas, a notificação continua sendo só de água, como na H2.

## 4. Arquitetura da H3

O **Treino** deixa de ser um módulo vazio: ele tem o perfil, o catálogo e a seleção. A **Agenda** ganha a segunda categoria de marco e pede os blocos ao Treino pela porta de entrada dele (regra R4).

```mermaid
flowchart LR
    subgraph agenda["agenda"]
        APPA["application<br/>AgendadorService, JornadaService"]
        PORTA["port.out<br/>MontadorDeBlocos"]
        ADP["adapter.out.treino<br/>MontadorDeBlocosDoTreino"]
    end
    subgraph treino["treino"]
        IN["port.in<br/>MontarBloco, ConsultarPerfil"]
        APPT["application<br/>MontagemDeBlocoService"]
        DOMT["domain<br/>PerfilFisico, Exercicio,<br/>SelecaoDeBloco"]
    end
    APPA --> PORTA
    ADP -.->|implementa| PORTA
    ADP --> IN
    APPT -.->|implementa| IN
    APPT --> DOMT
```

| Camada | Elementos |
|---|---|
| `treino.domain` | `PerfilFisico`, `Exercicio`, `Quantidade` (repetições, por lado, segundos), `SelecaoDeBloco` (seção 3.3, Java puro), `BlocoDeExercicio`, `ItemDoBloco` |
| `treino.application.port.in` | `ConsultarPerfil`, `SalvarPerfil`, `MontarBloco` (recebe perfil implícito, duração, número do marco e exercícios usados no dia) |
| `treino.application.port.out` | `PerfilRepository`, `CatalogoRepository` |
| `treino.adapter.in.web` | `PerfilController` |
| `treino.adapter.out.persistence` | Entidades JPA do perfil e do catálogo |
| `agenda.domain` | `Categoria.EXERCICIO`, `StatusMarco.ADIADO`, `PlanoDeMarcos.exercicio`, duração do bloco na jornada, regras de adiar e de resolução do adiado |
| `agenda.application.port.out` | `MontadorDeBlocos`: `perfilPreenchido()` e `montar(pedido)` |
| `agenda.adapter.out.treino` | `MontadorDeBlocosDoTreino`: implementa a porta da Agenda chamando o `MontarBloco` e o `ConsultarPerfil` do Treino. É a única peça da Agenda que conhece o Treino. |

**O domínio continua puro.** `Jornada.avancar` dispara os marcos como na H2. Para cada marco de exercício disparado, a aplicação pede o bloco ao `MontadorDeBlocos` e o entrega à jornada (`atribuirBloco`), na mesma transação e antes de gravar. Assim, nenhum marco de exercício `PENDENTE` fica sem bloco, e o domínio não faz I/O. O evento `JornadaAlterada` sai depois do commit, como na H2, já com o bloco.

**Dependência entre módulos.** A Agenda depende do Treino, e o Treino não conhece a Agenda: sem ciclos (R5). O pedido de bloco leva os códigos dos exercícios já usados no dia, que a Agenda conhece pelos blocos gravados. Assim, o Treino não precisa consultar a Agenda.

## 5. Modelo de dados

**`V3`: Treino**

| Tabela | Colunas principais | Restrições |
|---|---|---|
| `exercicio` | `codigo` (pk), `nome`, `grupo`, `ordem`, `instrucao`, `forma` (`REPETICOES`, `POR_LADO`, `SEGUNDOS`, `SEGUNDOS_POR_LADO`), `quantidade_iniciante` (nulo = só intermediário), `quantidade_intermediario`, `equipamento` (nulo, `CADEIRA`, `MESA`, `APOIO_DE_FLEXAO`, `HALTERES_2KG`), `no_chao`, `restricoes` (array) | Os 22 exercícios do catálogo, inseridos pela migração |
| `perfil_fisico` | `id` (sempre 1), `restricoes` (array), `nivel`, `equipamentos` (array), `aceita_chao`, `atualizado_em` | `check (id = 1)`: um perfil só |

**`V4`: Agenda**

| Mudança | Detalhe |
|---|---|
| `jornada.duracao_bloco_min` | `5` ou `10`; jornadas da v0.2.0 recebem `5` |
| `marco.categoria` | Aceita `EXERCICIO` |
| `marco.status` | Aceita `ADIADO` |
| `marco.volume_ml` | Nulo no exercício: `check ((categoria = 'HIDRATACAO') = (volume_ml is not null))` |
| `marco.duracao_bloco_min`, `marco.compensa_adiamento` | Preenchidos no disparo do exercício |
| Tabela `item_do_bloco` | `marco_id`, `ordem`, `exercicio_codigo`, `nome`, `grupo`, `quantidade_texto`, `segundos_estimados`, `instrucao`. Cópia do catálogo no instante do disparo (D1) |

Uma jornada iniciada na v0.2.0 e ainda aberta na subida da v0.3.0 continua só com água: os marcos de exercício nascem ao iniciar o dia.

## 6. Contrato REST

Prefixo `/api/v1`, erros em Problem Details, como na H2.

| Método e caminho | Corpo | Sucesso | Erros |
|---|---|---|---|
| `GET /perfil` | — | 200 com o perfil; 204 se não preenchido | — |
| `PUT /perfil` | `{ restricoes, nivel, equipamentos, aceitaChao }` | 200 (idempotente) | 400 valor inválido |
| `POST /jornadas` | `{ "metaAguaMl": 3000, "duracaoBlocoMin": 5 }` (ambos opcionais) | 201 | 400 duração diferente de 5 ou 10; **409 sem perfil** (D7) |
| `POST /marcos/{id}/adiamento` | — | 200 (idempotente) | 404; 409 hidratação, último marco, segundo adiamento ou marco já encerrado |
| Demais | Como na H2 | | Concluir ou Falhar um `ADIADO`: 409 |

Mudanças na representação:

- **Jornada:** ganha `duracaoBlocoMin`.
- **Marco:** `volumeMl` e `volumeAproximadoMl` passam a ser nulos no exercício. Ganha `bloco` (nulo até o disparo e na hidratação) e `podeAdiar`. O `podeAdiar` é calculado pelo backend, para a tela não repetir a regra.

```json
{
  "categoria": "EXERCICIO",
  "sequencia": 2,
  "status": "PENDENTE",
  "podeAdiar": true,
  "mensagem": "Bloco de 5 min: 6 exercícios.",
  "bloco": {
    "duracaoMin": 5,
    "segundosEstimados": 278,
    "compensaAdiamento": false,
    "itens": [
      {
        "exercicio": "Sentar e levantar da cadeira",
        "grupo": "Pernas",
        "quantidade": "10 repetições",
        "instrucao": "Sente e levante da cadeira sem usar as mãos, com os pés na largura do quadril."
      }
    ]
  }
}
```

## 7. Eventos (SSE)

Nenhum evento novo. O `marco-disparado` passa a vir também para o exercício, já com o bloco. Na hora cheia chegam dois `marco-disparado` (água e exercício) e depois um `jornada-atualizada`, todos da mesma alteração. A tela usa essa ordem para juntar a notificação (seção 3.7).

## 8. Frontend

| Situação | O que a tela mostra |
|---|---|
| Sem perfil | Formulário do perfil (seção 3.1) antes do "Iniciar dia", com a explicação de para que serve |
| Sem jornada hoje | Meta de água, **duração do bloco** (5 ou 10 min, lembrando a última escolha) e Iniciar dia; link **Editar perfil** |
| Exercício pendente | Cartão "Hora do exercício 🏃": duração, lista de exercícios com quantidade e instrução, e os botões **Concluir**, **Adiar** (só com `podeAdiar`) e **Falhar**. Com `compensaAdiamento`, o cartão avisa que o bloco inclui o adiado. |
| Água e exercício juntos | Os dois cartões, um sobre o outro |
| Exercício adiado | Na lista, "Adiado: resolvido pelo próximo bloco" |
| Lista do dia | Água e exercício na mesma lista, em ordem de horário, com a categoria indicada |
| Resumo | Contagem por categoria e situação |
| Editar perfil | O mesmo formulário, preenchido; vale para os próximos blocos |

A notificação combinada junta os `marco-disparado` que chegam antes do `jornada-atualizada` seguinte (seção 7). A `tag` é formada pelos ids dos marcos, então várias abas continuam mostrando uma notificação só.

## 9. Modo demonstração

O exercício dispara a cada **dobro do intervalo da água (D9)**: 60 min no uso normal e 2 min no `docker-compose.demo.yml`. Com isso, a hora cheia continua coincidindo com um marco de água par, e o dia inteiro de demonstração continua durando 16 min. Uma propriedade só (`pausa-ativa.agenda.intervalo`) controla as duas categorias.

## 10. Observabilidade

| Métrica | Tipo | Tags |
|---|---|---|
| `pausaativa.marcos.*` (da H2) | — | Já têm `categoria`; o exercício entra sem mudança |
| `pausaativa.marcos.adiados` | contador | — |
| `pausaativa.blocos.montados` | contador | `duracao`, `compensa_adiamento` |
| `pausaativa.blocos.itens` | distribuição | — |

Logs JSON com `jornadaId`, `marcoId` e os códigos dos exercícios do bloco montado.

## 11. Critérios de aceitação: como cada um é verificado

| Cenário do épico | Teste automático | Verificação no Chrome (modo demonstração) |
|---|---|---|
| 1. Seleção respeita restrição | Domínio do Treino: perfil que poupa joelho e sem halteres não recebe agachamento, afundo nem halteres, em todos os 8 marcos | Perfil assim; conferir os blocos do dia |
| 2. Notificação combinada | Domínio: água 2*n* e exercício *n* no mesmo tick. Frontend: uma notificação para os dois eventos | Na hora cheia, uma notificação só e dois cartões |
| 3. Adiar | Domínio e API: `ADIADO`; o seguinte tem 10 min e `compensaAdiamento` | Adiar e conferir o próximo bloco |
| 4. Adiado e concluído | Domínio: concluir o seguinte deixa os dois `CONCLUIDO` | Concluir o bloco seguinte |
| 5. Segundo adiamento | Domínio e API: 409 com a mensagem; `podeAdiar` falso | O botão Adiar não aparece |
| 6. Perfil ausente | API: 409 ao iniciar sem perfil. Frontend: formulário antes do Iniciar dia | Banco limpo: a tela pede o perfil |
| 7. Restrições eliminam o catálogo | Domínio: perfil que poupa tudo, sem equipamento e sem chão recebe só a reserva, em segunda volta se preciso | Perfil assim; conferir o bloco |

Também da seção "Cenários de Teste" do épico, nesta história:

- Jornada completa de 8 h: 16 marcos de água e 8 de exercício nos instantes corretos.
- Bloco respeita perfil, equipamento e duração (estimativa dentro do bloco).
- Adiamento dobra o próximo bloco e resolve os dois marcos juntos.
- A seleção é determinística: mesma entrada, mesmo bloco.

## 12. Plano de entrega em etapas

Branch `feat/h3-blocos-de-exercicio`. A execução para ao fim de cada etapa, e a próxima sessão retoma pela primeira etapa sem ✅.

| # | Etapa | Pronto quando | Status |
|---|---|---|---|
| T1 | Treino, domínio: perfil, catálogo, quantidades e seleção (seção 3.3) | Cenários 1 e 7 e determinismo cobertos em Java puro | Pendente |
| T2 | Treino: migração `V3` com o catálogo, persistência, `GET`/`PUT /perfil`, porta `MontarBloco` | Testes de integração e MockMvc; contrato atualizado | Pendente |
| T3 | Agenda, domínio: marcos de exercício, `ADIADO`, adiar e resolução, duração do bloco | Cenários 3 a 5 e jornada de 8 h cobertos em Java puro | Pendente |
| T4 | Agenda: migração `V4`, ligação com o Treino, API (adiamento, bloco, duração, perfil obrigatório), SSE, métricas | Integração com Postgres; contrato e tipos TS atualizados | Pendente |
| T5 | Frontend: perfil (formulário, edição, obrigatório) e duração do bloco ao iniciar | Lint, tipos e testes verdes, cobertura ≥ 80% | Pendente |
| T6 | Frontend: cartão do exercício, Adiar, notificação combinada, lista e resumo por categoria | Idem | Pendente |
| T7 | Verificação ponta a ponta no Chrome, em modo demonstração | Cenários 1 a 7 conferidos | Pendente |
| T8 | README, C4 (incluindo o nginx 1.31 que entrou depois da v0.2.0), release notes, PR e CI | Aceite do usuário; merge e tag `v0.3.0` | Pendente |

## 13. Decisões para o usuário confirmar

| # | Decisão | Recomendação | Alternativa |
|---|---|---|---|
| D1 | Quando montar o bloco | **No disparo**, gravado no marco. Usa o perfil do momento, conhece o adiamento anterior e alterna com base nos blocos que já saíram. | Montar os 8 ao iniciar o dia (mostra o plano antes, mas precisa remontar a cada adiamento ou mudança de perfil) |
| D2 | Estimativa de duração | **3 s por repetição e 15 s por troca**; segundos valem o que dizem; "por lado" dobra | Outros valores, por exemplo 4 s por repetição, para blocos mais curtos |
| D3 | Bloco depois de um adiamento, num dia de blocos de 10 min | **Continua 10 min.** O épico diz "o próximo bloco passa a ter 10 min", e o objetivo pede pausas de no máximo 10 min. Num dia de 10 min, adiar só transfere a resposta. | Dobrar sempre: 10 min viram 20 |
| D4 | O adiado quando o seguinte não é entregue ou o dia é finalizado antes | **Segue o seguinte** (`NAO_ENTREGUE` ou `NAO_CONCLUIDO`, fora da taxa), coerente com "finalizar cedo não é falha" | Virar `FALHA`, porque o exercício adiado não foi feito |
| D5 | Adiar o 8º marco | **Não permitido**: não há bloco seguinte para compensar | Permitir, e o adiado do 8º vira `NAO_CONCLUIDO` ao finalizar |
| D6 | Prazo do 8º marco | **9 h trabalhadas**, 60 min como os demais (mesma lógica do D2 da H2) | Ficar pendente até finalizar o dia |
| D7 | Perfil obrigatório | **Na tela e no backend** (409 ao iniciar sem perfil): a regra não depende de quem chama a API | Só na tela |
| D8 | Instrução de cada exercício | **Uma linha de como fazer**, escrita pelo Claude e revisada por você no apêndice A | Só o nome do exercício |
| D9 | Exercício no modo demonstração | **Dobro do intervalo da água** (2 min), com uma propriedade só | Propriedade própria para o exercício |

## Apêndice A: instruções dos exercícios

Proposta para revisão (D8). O texto aparece no cartão do bloco.

| Exercício | Como fazer |
|---|---|
| Sentar e levantar da cadeira | Sente e levante da cadeira sem usar as mãos, com os pés na largura do quadril. |
| Agachamento livre | Pés na largura dos ombros; desça como se fosse sentar, com o peito aberto e os joelhos na direção dos pés. |
| Afundo alternado | Dê um passo à frente e desça até os dois joelhos dobrarem perto de 90°; volte e troque de perna. |
| Elevação de panturrilha | Apoiado na mesa ou na parede, suba na ponta dos pés e desça devagar. |
| Ponte de glúteo | Deitado de costas, joelhos dobrados, eleve o quadril contraindo os glúteos e desça devagar. |
| Extensão de quadril em pé, apoiado na mesa | Mãos na mesa, leve uma perna estendida para trás sem arquear a lombar e volte devagar. |
| Flexão na parede | Mãos na parede na altura dos ombros; leve o peito à parede dobrando os cotovelos e empurre de volta. |
| Flexão inclinada na mesa | Mãos na borda de uma mesa firme, corpo reto; desça o peito até a mesa e empurre de volta. |
| Flexão com apoio | No chão, mãos nos apoios e corpo reto da cabeça aos pés; desça o peito e empurre de volta. Apoie os joelhos se precisar. |
| Prancha | Antebraços no chão, corpo reto da cabeça aos pés, abdômen contraído; respire normalmente. |
| Bird-dog | De quatro, estenda um braço à frente e a perna oposta para trás, sem girar o quadril; volte e troque. |
| Remada curvada | Tronco inclinado à frente com a coluna reta; puxe os halteres até a cintura e desça devagar. |
| Anjo na parede | Costas e cabeça na parede, braços em W encostados; deslize os braços para cima e para baixo sem descolar. |
| Retração escapular em pé (segurar 2 s) | Braços ao lado do corpo; junte as escápulas para trás e para baixo, segure 2 s e solte. |
| Desenvolvimento de ombros | Halteres na altura dos ombros; empurre para cima até quase estender os braços e desça devagar. |
| Elevação lateral | Halteres ao lado do corpo; eleve os braços pelas laterais até a altura dos ombros e desça devagar. |
| Rosca direta | Cotovelos parados ao lado do corpo; leve os halteres aos ombros dobrando os braços e desça devagar. |
| Marcha estacionária | Marche no lugar, elevando os joelhos e balançando os braços, num ritmo confortável. |
| Mobilidade torácica (rotação do tronco) | Braços cruzados no peito, gire o tronco devagar para um lado e para o outro, sem mexer o quadril. |
| Mobilidade cervical | Incline a cabeça devagar para cada lado e depois para a frente, sem forçar nem girar rápido. |
| Alongamento de flexores do quadril em pé | Um pé à frente e outro atrás; contraia o glúteo da perna de trás e leve o quadril à frente. |
| Alongamento de punhos e antebraços | Braço estendido, palma para cima; puxe os dedos para baixo com a outra mão, suavemente. Depois repita com a palma para baixo. |

> Este app não substitui orientação médica. As quantidades e as instruções são pontos de partida.
