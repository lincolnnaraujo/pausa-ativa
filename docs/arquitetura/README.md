# Arquitetura do Pausa Ativa

Documentação no modelo [C4](https://c4model.com/), do mais geral para o mais detalhado. Reflete a **v0.4.0**.

| Nível | Documento | Pergunta que responde |
|---|---|---|
| 1. Contexto | [c4-1-contexto.md](c4-1-contexto.md) | Quem usa o sistema e com o que ele conversa? |
| 2. Containers | [c4-2-containers.md](c4-2-containers.md) | Quais processos rodam e como se falam? |
| 3. Componentes | [c4-3-componentes.md](c4-3-componentes.md) | Como o backend e a tela estão organizados por dentro? |

**Convenção dos diagramas:** linha cheia é o que já existe; linha tracejada é o que está planejado, com a história que o entrega entre parênteses.

## Decisões principais

| Tema | Decisão | Onde está |
|---|---|---|
| Estilo | Monólito modular, arquitetura hexagonal, um único deploy | [Nível 3](c4-3-componentes.md) |
| Módulos | Agenda, Treino e Histórico (negócio) + Sistema (técnico) | [Nível 3](c4-3-componentes.md) |
| Regras de arquitetura | R1 a R6, verificadas pelo ArchUnit em toda build | [Nível 3](c4-3-componentes.md#regras-de-arquitetura) |
| Tempo | Um único `Clock` injetado, fuso `America/Sao_Paulo`; JVM e banco em UTC. A R6 proíbe ler o relógio do sistema. | [Nível 3](c4-3-componentes.md) |
| Concorrência | Lock pessimista em toda alteração da jornada; restrições do banco barram duplicatas | [Nível 3](c4-3-componentes.md#agenda-por-dentro) |
| Blocos de exercício | A Agenda pede o bloco ao Treino no disparo, pela porta de entrada dele, e grava uma cópia no marco. A seleção é determinística e fica no domínio do Treino. | [Nível 3](c4-3-componentes.md#treino-por-dentro) |
| Histórico | O Histórico lê a Agenda pela porta de entrada dela, com a contagem por dia feita no banco. Calcula períodos e taxas, sem tabela própria. | [Nível 3](c4-3-componentes.md#histórico-por-dentro) |
| Gráficos | SVG em componentes próprios, sem biblioteca, com as cores dos tokens da identidade e uma tabela equivalente | [Nível 3](c4-3-componentes.md#frontend) |
| Tempo real | SSE do backend para a tela, enviado só depois do commit; a tela reconecta sozinha | [Nível 2](c4-2-containers.md#comunicação) |
| Exposição | Só o frontend tem porta no host, em `127.0.0.1` | [Nível 2](c4-2-containers.md) |
| Contrato | OpenAPI gerado pelo código e versionado em [`docs/api/openapi.json`](../api/openapi.json) | [Nível 2](c4-2-containers.md) |

As decisões completas e suas justificativas estão no [épico](../epico-pausa-ativa.md#decisões-de-arquitetura) e nas specs da [H1](../specs/h1-fundacao.md), da [H2](../specs/h2-jornada-hidratacao.md), da [H3](../specs/h3-blocos-de-exercicio.md) e da [H4](../specs/h4-historico-e-graficos.md).
