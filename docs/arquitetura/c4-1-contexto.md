# C4 Nível 1: Contexto

O Pausa Ativa é usado por **uma pessoa**, no próprio computador, sem login. Não depende de nenhum serviço na internet.

```mermaid
flowchart LR
    U(["👤 Usuário em home office<br/><i>Trabalha no computador e quer<br/>beber água e se mexer ao longo do dia</i>"])
    S["<b>Pausa Ativa</b><br/><i>Aplicação web local que agenda<br/>marcos de hidratação e exercício</i>"]
    N["Notificações do Chrome<br/><i>Sistema de notificação do navegador</i>"]
    G["Grafana<br/><i>Painel de métricas</i>"]

    U -->|"Abre a página, consulta o status<br/>(v0.1.0)"| S
    U -.->|"Inicia a jornada, responde marcos,<br/>vê gráficos (H2 a H4)"| S
    S -.->|"Lembretes de água e exercício (H2, H3)"| N
    N -.->|"Mostra o lembrete"| U
    U -.->|"Consulta métricas (H5)"| G
    G -.->|"Lê métricas (H5)"| S

    classDef futuro stroke-dasharray: 5 5
    class N,G futuro
```

## Elementos

| Elemento | Tipo | Papel | Desde |
|---|---|---|---|
| Usuário | Pessoa | Única pessoa que usa o sistema, no próprio computador | v0.1.0 |
| Pausa Ativa | Sistema | Este sistema | v0.1.0 |
| Notificações do Chrome | Sistema externo | Exibe os lembretes, mesmo com a aba fora de foco | H2 (planejado) |
| Grafana | Sistema externo | Painel de métricas técnicas e de negócio | H5 (planejado) |

## Restrições

- Tudo roda localmente, via Docker Compose. Nada fica exposto fora de `127.0.0.1`.
- Sem autenticação: o sistema assume um único usuário.
- O Chrome é o único navegador garantido.
