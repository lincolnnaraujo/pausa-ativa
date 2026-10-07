# C4 Nível 1: Contexto

O Pausa Ativa é usado por **uma pessoa**, no próprio computador, sem login. Não depende de nenhum serviço na internet.

```mermaid
flowchart LR
    U(["👤 Usuário em home office<br/><i>Trabalha no computador e quer<br/>beber água e se mexer ao longo do dia</i>"])
    S["<b>Pausa Ativa</b><br/><i>Aplicação web local que agenda<br/>marcos de hidratação e exercício</i>"]
    N["Notificações do Chrome<br/><i>Sistema de notificação do navegador</i>"]
    G["Grafana<br/><i>Painel de métricas</i>"]

    U -->|"Preenche o perfil físico; inicia, pausa<br/>e finaliza a jornada; responde e corrige lembretes;<br/>vê o histórico e os gráficos"| S
    S -->|"Lembretes de água e<br/>blocos de exercício"| N
    N -->|"Mostra o lembrete"| U
    U -.->|"Consulta métricas (H5)"| G
    G -.->|"Lê métricas (H5)"| S

    classDef futuro stroke-dasharray: 5 5
    class G futuro
```

## Elementos

| Elemento | Tipo | Papel | Desde |
|---|---|---|---|
| Usuário | Pessoa | Única pessoa que usa o sistema, no próprio computador. Desde a v0.4.0, vê o histórico e corrige os lembretes do dia. | v0.1.0 |
| Pausa Ativa | Sistema | Este sistema | v0.1.0 |
| Notificações do Chrome | Sistema externo | Exibe os lembretes, mesmo com a aba fora de foco. Na hora cheia, água e exercício vêm numa notificação só (v0.3.0). Os botões de resposta ficam na página. | v0.2.0 |
| Grafana | Sistema externo | Painel de métricas técnicas e de negócio | H5 (planejado) |

## Restrições

- Tudo roda localmente, via Docker Compose. Nada fica exposto fora de `127.0.0.1`.
- Sem autenticação: o sistema assume um único usuário, com um único perfil físico.
- O Chrome é o único navegador garantido.
- Os lembretes chegam pela aba aberta: sem ela, não há notificação, e o lembrete vira `NAO_ENTREGUE`.
