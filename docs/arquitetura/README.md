# Arquitetura do Pausa Ativa

Documentação no modelo [C4](https://c4model.com/), do mais geral para o mais detalhado. Reflete a **v0.1.0**.

| Nível | Documento | Pergunta que responde |
|---|---|---|
| 1. Contexto | [c4-1-contexto.md](c4-1-contexto.md) | Quem usa o sistema e com o que ele conversa? |
| 2. Containers | [c4-2-containers.md](c4-2-containers.md) | Quais processos rodam e como se falam? |
| 3. Componentes | [c4-3-componentes.md](c4-3-componentes.md) | Como o backend está organizado por dentro? |

**Convenção dos diagramas:** linha cheia é o que já existe; linha tracejada é o que está planejado, com a história que o entrega entre parênteses.

## Decisões principais

| Tema | Decisão | Onde está |
|---|---|---|
| Estilo | Monólito modular, arquitetura hexagonal, um único deploy | [Nível 3](c4-3-componentes.md) |
| Módulos | Agenda, Treino e Histórico (negócio) + Sistema (técnico) | [Nível 3](c4-3-componentes.md) |
| Regras de arquitetura | R1 a R5, verificadas pelo ArchUnit em toda build | [Nível 3](c4-3-componentes.md#regras-de-arquitetura) |
| Tempo | Um único `Clock` injetado, fuso `America/Sao_Paulo`; JVM e banco em UTC | [Nível 3](c4-3-componentes.md) |
| Exposição | Só o frontend tem porta no host, em `127.0.0.1` | [Nível 2](c4-2-containers.md) |
| Contrato | OpenAPI gerado pelo código e versionado em [`docs/api/openapi.json`](../api/openapi.json) | [Nível 2](c4-2-containers.md) |

As decisões completas e suas justificativas estão no [épico](../epico-pausa-ativa.md#decisões-de-arquitetura) e na [spec da H1](../specs/h1-fundacao.md).
