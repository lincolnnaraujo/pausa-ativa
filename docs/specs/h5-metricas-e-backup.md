# Spec H5: Operar com métricas e backup (release v0.5.0)

> **Status:** em revisão pelo usuário. As decisões que o épico não tomava estão na seção 11, com a recomendação primeiro.
> **Origem:** História 5 de [`docs/epico-pausa-ativa.md`](../epico-pausa-ativa.md).
> **Depende de:** H2 (v0.2.0). As métricas que o painel mostra vêm da H2 à H4, entregue em 2026-10-07.
> **Data:** 2026-10-07.

## 1. Objetivo

Ao fim desta release, o usuário:

- abre o **Grafana** em `127.0.0.1` e vê, sem configurar nada, um painel com os lembretes por situação, o atraso dos disparos e a saúde da aplicação;
- faz um **backup** do banco com um comando só, em qualquer terminal;
- **restaura** um backup e encontra o histórico como estava;
- recebe um erro claro, e nenhum arquivo vazio, se tentar o backup com o banco parado.

## 2. Escopo

| Dentro | Fora |
|---|---|
| Prometheus no compose, coletando o `/actuator/prometheus` do backend pela rede interna | Alertas (Alertmanager, notificação de métrica fora do limite) |
| Grafana no compose, com a fonte de dados e o painel provisionados como arquivos | Logs no Grafana (Loki): os logs seguem em JSON no `docker compose logs` |
| Histogramas nas métricas de tempo, para o painel mostrar o p95 contra os limites do épico | Taxa de sucesso no Grafana (D5): a oficial é a do Histórico |
| Backup e restauração por serviços do compose, iguais no PowerShell, no Git Bash, no Linux e no macOS | Backup automático agendado (D7) |
| Os dois serviços novos também no modo demonstração | Link para o painel na tela da aplicação (D12) |
| Verificação dos três cenários no CI, no job do compose | Mudanças no frontend |

## 3. Comportamento

### 3.1 Prometheus

| Item | Definição |
|---|---|
| Coleta | `backend:8080/actuator/prometheus` a cada 15 s, pela rede interna. O nginx continua devolvendo 404 para esse caminho no host. |
| Porta no host | Nenhuma **(D2)**. O Grafana lê o Prometheus pela rede interna. |
| Retenção | 90 dias ou 1 GB, o que vier primeiro **(D4)**, no volume `prometheus-dados` |
| Recursos | Limite de memória de 256 MB, conferido na verificação (T5) |
| Saúde | Healthcheck em `/-/ready`. O backend não depende do Prometheus: com ele fora, a aplicação segue igual. Com o backend fora, a métrica `up` vira 0, e o painel mostra isso. |
| Reinício do backend | Os contadores voltam a zero a cada subida. O painel usa `increase()` e `rate()`, que tratam o recomeço. |

### 3.2 Grafana e o painel

| Item | Definição |
|---|---|
| Endereço | `http://127.0.0.1:38744` (`GRAFANA_PORT` no `.env`); na demonstração, `38745` **(D2)** |
| Acesso | Anônimo, como leitor, sem tela de login **(D3)**. O painel abre direto na página inicial. |
| Provisionamento | A fonte de dados (Prometheus, com `uid` fixo) e o painel são arquivos do repositório, carregados na subida. Mudar o painel é mudar o JSON. |
| Sem internet | Desligados o relatório de uso, a busca de atualizações, o feed de notícias e a instalação de plugins: o épico diz que nada depende de serviço externo. |
| Aparência | Tema escuro, interface em português e as cores da identidade Sereno & Balanceado nas séries **(D10)** |
| Recursos | Limite de memória de 256 MB, conferido na verificação (T5); volume `grafana-dados` para o banco interno do Grafana |

**Painel "Pausa Ativa".** O intervalo padrão é "hoje" (`now/d` até agora), e o seletor de tempo do Grafana troca para a semana ou o mês.

| Linha | Painel | O que mostra | Métrica | Limite marcado |
|---|---|---|---|---|
| Lembretes | Água por situação | Lembretes encerrados por situação, em barras empilhadas por hora (ou por dia, em intervalos longos) | `pausaativa.marcos.encerrados{categoria="HIDRATACAO"}` | — |
| | Exercício por situação | O mesmo, para o exercício | `pausaativa.marcos.encerrados{categoria="EXERCICIO"}` | — |
| | Atraso do disparo | p95 e máximo do atraso entre o instante calculado e o disparo, por categoria | `pausaativa.marcos.atraso.disparo` | 5 s (pontualidade do épico) |
| | No período | Lembretes disparados, adiados e corrigidos, e blocos montados | `pausaativa.marcos.disparados`, `.adiados`, `.corrigidos`, `pausaativa.blocos.montados` | — |
| | Abas conectadas | Conexões SSE abertas: sem nenhuma, os lembretes viram não entregues | `pausaativa.sse.conexoes` | — |
| Aplicação | Backend no ar | `up` do alvo: no ar ou fora | `up{job="pausa-ativa"}` | — |
| | Tempo das operações | p95 por rota da API, com as operações de lembrete em destaque | `http.server.requests` | 200 ms (performance do épico) |
| | Consulta do histórico | p95 por período | `pausaativa.historico.consultas` | 500 ms (agregação mensal do épico) |
| | Erros da API | Respostas 5xx por minuto | `http.server.requests{outcome="SERVER_ERROR"}` | — |
| | Memória da JVM | Heap usado e máximo, e o resto da memória da JVM | `jvm.memory.used`, `jvm.memory.max` | 384 MB, os 75% do limite de 512 MB do container |
| | CPU e pausas do GC | Uso de CPU do processo e tempo em pausa do GC | `process.cpu.usage`, `jvm.gc.pause` | — |
| | Conexões do banco | Conexões do Hikari ativas, ociosas e esperando | `hikaricp.connections.*` | — |

Cores das situações, iguais às dos gráficos do Histórico: concluído na cor da categoria (azul céu na água, teal no exercício), falha em coral, não entregue em cinza claro e não concluído em cinza médio. Os valores vêm dos tokens de `frontend/src/assets/main.css`, copiados para o JSON do painel.

### 3.3 Backup

| Item | Definição |
|---|---|
| Comando | `docker compose run --rm backup` **(D6)** |
| O que faz | `pg_dump` em formato custom (comprimido), com a mesma imagem do banco (`postgres:18-alpine`): a versão do `pg_dump` sempre bate com a do servidor |
| Onde grava | `./backups/pausa-ativa-AAAA-MM-DD-HHMMSS.dump`, no fuso de São Paulo. A pasta muda com `BACKUP_DIR` no `.env` **(D8)**, por exemplo para uma pasta sincronizada com a nuvem. |
| Sem arquivo vazio | Grava primeiro em `.parcial` e só renomeia depois de o `pg_dump` terminar bem. Se falhar, apaga o parcial (Cenário 3). |
| Banco parado | O serviço não sobe o Postgres sozinho. Com o banco fora, termina com código 1 e a mensagem "O banco não respondeu. Suba a aplicação com `docker compose up -d --wait` e tente de novo." (Cenário 3) |
| Saída | O caminho do arquivo, o tamanho e quantas jornadas o backup tem |
| Frequência | Manual **(D7)**. O README sugere um backup por semana e uma cópia fora do computador. |

### 3.4 Restauração

| Item | Definição |
|---|---|
| Comando | `docker compose run --rm restauracao <arquivo>`, com o nome de um arquivo da pasta de backups |
| Antes | O backend precisa estar parado (`docker compose stop backend`). Com outra conexão aberta no banco, a restauração recusa e diz o comando **(D9)**. |
| Confirmação | Pergunta o que vai acontecer e pede para digitar `RESTAURAR`. Para scripts e para o CI, `--sim` pula a pergunta **(D9)**. |
| Rede de segurança | Se o banco tiver dados, grava antes um backup dele, `antes-da-restauracao-AAAA-MM-DD-HHMMSS.dump` **(D9)** |
| Como restaura | `pg_restore --clean --if-exists --no-owner --single-transaction`: ou volta tudo, ou nada muda |
| Depois | `docker compose up -d --wait`. O Flyway confere o esquema; um backup de uma versão anterior é migrado para a atual na subida. |
| Volume novo (Cenário 2) | `docker compose down -v`, `docker compose up -d --wait postgres`, a restauração e `docker compose up -d --wait`. Sem o backend no ar, ninguém escreve no banco vazio antes da restauração. |
| Saída | Quantas jornadas e qual o último dia do banco restaurado |

## 4. Arquitetura

```mermaid
flowchart TB
    U(["👤 Usuário<br/><i>Chrome</i>"])
    subgraph host["Computador do usuário (Docker Compose)"]
        FE["<b>frontend</b><br/>nginx"]
        BE["<b>backend</b><br/>Spring Boot"]
        DB[("<b>postgres</b>")]
        PR["<b>prometheus</b><br/><i>sem porta no host</i>"]
        GR["<b>grafana</b><br/><i>127.0.0.1:38744</i>"]
        BK["<b>backup / restauracao</b><br/><i>só com docker compose run</i>"]
    end
    PASTA[("./backups")]
    U -->|"127.0.0.1:38742"| FE
    U -->|"127.0.0.1:38744"| GR
    FE --> BE
    BE --> DB
    PR -->|"coleta /actuator/prometheus<br/>a cada 15 s"| BE
    GR -->|"PromQL"| PR
    BK -->|"pg_dump / pg_restore"| DB
    BK --> PASTA
```

Arquivos novos:

| Caminho | Conteúdo |
|---|---|
| `observabilidade/prometheus/prometheus.yml` | O alvo `pausa-ativa` (`backend:8080`, `/actuator/prometheus`, 15 s) |
| `observabilidade/grafana/provisionamento/fontes/prometheus.yml` | A fonte de dados, com `uid: prometheus` |
| `observabilidade/grafana/provisionamento/paineis/paineis.yml` | Onde o Grafana procura os painéis |
| `observabilidade/grafana/paineis/pausa-ativa.json` | O painel da seção 3.2 |
| `scripts/backup.sh`, `scripts/restaurar.sh` | Os scripts que os serviços rodam, em `sh` (POSIX). Montados só para leitura e chamados com `sh`, sem depender do bit de execução no Windows. |

**No `docker-compose.yml`:** os serviços `prometheus` e `grafana`, sempre no ar **(D1)**, e `backup` e `restauracao` no perfil `ferramentas`, que o `up` não sobe; o `docker compose run` sobe o serviço pedido. O `grafana` espera o `prometheus` saudável. O `backend` não depende de nenhum dos dois.

**No `.env.example`:** `GRAFANA_PORT=38744` e `BACKUP_DIR=./backups`, ambos com padrão no compose. Um `.env` da v0.4.0 continua valendo sem mudança.

## 5. Métricas (backend)

O backend já publica as métricas (H2 a H4). Para o painel mostrar o p95 contra os limites do épico, as de tempo passam a publicar histogramas:

| Métrica | Histograma | Faixas que marcam os limites |
|---|---|---|
| `http.server.requests` | Sim, até 5 s | 200 ms |
| `pausaativa.marcos.atraso.disparo` | Sim, até 30 s | 1 s e 5 s |
| `pausaativa.historico.consultas` | Sim, até 5 s | 500 ms |

E todas ganham a tag `application="pausa-ativa"`. A configuração fica no `application.yml` (`management.metrics.distribution`). Nenhuma métrica de negócio nova.

O `/actuator/prometheus` continua fora do host: só o Prometheus, na rede interna, o lê.

## 6. Modo demonstração

O `docker-compose.demo.yml` sobe os cinco serviços no projeto `pausa-ativa-demo`, com o Grafana em `127.0.0.1:38745`. Com um lembrete por minuto, o painel mostra um dia inteiro em 16 min. O histórico de exemplo da H4 não passa pelas métricas, então o painel começa vazio e enche com o dia ao vivo.

O backup e a restauração também funcionam na demonstração, com `-f docker-compose.yml -f docker-compose.demo.yml` antes do `run`. É assim que o CI confere o Cenário 2: o histórico de exemplo dá 45 dias de dados para o backup.

## 7. Segurança e recursos

- O Grafana só escuta em `127.0.0.1`. Sem login, qualquer programa do próprio computador pode ler o painel, como já acontece com a aplicação.
- O Prometheus e o Actuator completo continuam sem porta no host.
- Os backups ficam fora do git (`backups/` no `.gitignore` desde a H1). Eles têm os dados da aplicação e nenhuma senha.
- A stack sobe de três para cinco containers. A soma dos limites passa de 512 MB (backend) para 1 GB. O uso real é medido na T5.

## 8. Critérios de aceitação: como cada um é verificado

| Cenário do épico | Teste automático | Verificação manual (T5) |
|---|---|---|
| 1. Dashboard pronto | Backend: o `/actuator/prometheus` publica os histogramas e as faixas da seção 5. CI: com a stack no ar, o alvo do Prometheus está `up`, o Grafana responde, o painel aparece na API do Grafana, e cada consulta do painel roda no Prometheus sem erro | Demonstração no Chrome: o painel abre sem login, com lembretes por situação, atraso do disparo e memória da JVM de um dia ao vivo; capturas |
| 2. Backup e restauração | CI, na demonstração: backup dos 45 dias de exemplo, `down -v`, restauração num volume novo e subida; a contagem e a impressão digital (`md5`) das jornadas e dos marcos, e o mês do Histórico pela API, iguais antes e depois | No PowerShell do Windows: backup, `down -v`, restauração e o Histórico conferido na tela |
| 3. Dump com banco parado | CI: com o Postgres parado, o backup termina com código diferente de zero, a mensagem da seção 3.3 e nenhum arquivo novo na pasta | O mesmo no PowerShell |

Também:

- O `shellcheck` confere os dois scripts no CI.
- A restauração recusa com o backend conectado, e pede a confirmação sem `--sim` (CI e manual).
- A aplicação continua no ar com o Prometheus e o Grafana parados (manual).

## 9. Plano de entrega em etapas

Branch `feat/h5-metricas-e-backup`. A execução para ao fim de cada etapa, e a próxima sessão retoma pela primeira etapa sem ✅.

| # | Etapa | Pronto quando | Status |
|---|---|---|---|
| T1 | Backend: histogramas e faixas das métricas de tempo, tag `application` | `verify` verde; teste de integração lendo o `/actuator/prometheus` | Pendente |
| T2 | Prometheus no compose e na demonstração: configuração, volume, retenção, limite de memória e healthcheck | `docker compose up -d --wait` com o alvo `up`; o CI confere | Pendente |
| T3 | Grafana: fonte de dados e painel provisionados, acesso anônimo, sem internet, em português e com a paleta | O painel abre sem login com dados; o CI confere o painel e as consultas | Pendente |
| T4 | Backup e restauração: scripts, serviços `backup` e `restauracao`, `BACKUP_DIR`, `shellcheck` | Cenários 2 e 3 no CI, com a demonstração | Pendente |
| T5 | Verificação ponta a ponta: Grafana no Chrome com um dia ao vivo, backup e restauração no PowerShell, banco parado, memória dos cinco containers | Cenários 1 a 3 conferidos, com capturas | Pendente |
| T6 | README, C4 (Prometheus e Grafana deixam de ser planejados), release notes, PR e CI | Aceite do usuário; merge e tag `v0.5.0` | Pendente |

## 10. Riscos

| Risco | Como a spec trata |
|---|---|
| Os contadores recomeçam a cada subida do backend | O painel usa `increase()` e `rate()`; o número oficial do dia continua no Histórico |
| Histogramas multiplicam as séries | Faixas limitadas (até 5 s ou 30 s) e só três métricas; com um usuário, são poucas centenas de séries |
| Script com CRLF quebra no container | O `.gitattributes` normaliza para LF, e os scripts são chamados com `sh` |
| Restaurar por cima de dados novos sem querer | Confirmação digitada e backup automático do banco atual antes de restaurar |
| Backup no mesmo disco da aplicação | O README recomenda uma cópia fora do computador, e o `BACKUP_DIR` aponta para outra pasta |

## 11. Decisões para o usuário confirmar

Lacunas que o épico não decidia. A recomendação vem primeiro.

| # | Decisão | Recomendação | Alternativa |
|---|---|---|---|
| D1 | Quando o Prometheus e o Grafana sobem | **Sempre, com a aplicação** (`docker compose up`), como o épico desenha a stack. Custo: até ~500 MB a mais de limite de memória | Num perfil opcional (`--profile observabilidade`), ligado só quando quiser ver o painel |
| D2 | Portas | **Grafana em `127.0.0.1:38744`** (demonstração: `38745`); **Prometheus sem porta**, lido só pelo Grafana | Expor também o Prometheus em `127.0.0.1`, para consultas avulsas |
| D3 | Acesso ao Grafana | **Anônimo, como leitor, sem login.** O painel é código: mudar é mudar o JSON | Login de administrador, com a senha no `.env`, para editar o painel pela tela |
| D4 | Quanto tempo de métricas guardar | **90 dias ou 1 GB.** O histórico de negócio fica no banco, sem limite | O padrão do Prometheus, 15 dias |
| D5 | Taxa de sucesso no Grafana | **Não.** As métricas contam o encerramento e não acompanham as correções; a taxa oficial é a do Histórico, que lê o banco | Calcular a taxa a partir dos contadores, sabendo que ela pode divergir do Histórico |
| D6 | Como rodar o backup e a restauração | **Serviços do compose** (`docker compose run --rm backup`): o mesmo comando em qualquer terminal, só com o Docker | Scripts no computador, um `.sh` e um `.ps1` para cada operação |
| D7 | Backup automático | **Manual**, por comando, com a sugestão de um por semana no README | Um serviço que faz backup todo dia e guarda os últimos 7 |
| D8 | Onde ficam os backups | **`./backups`, configurável com `BACKUP_DIR`** no `.env` | Sempre em `./backups` |
| D9 | Proteções da restauração | **Exige o backend parado, pede a confirmação digitada e guarda antes um backup do banco atual** | Restaurar direto, sem perguntar |
| D10 | Aparência do painel | **Tema escuro, interface em português e as cores da identidade** | O padrão do Grafana |
| D11 | Verificação no CI | **Estender o job do compose** com os três cenários, usando a demonstração para ter dados (alguns minutos a mais de CI) | Verificar só manualmente |
| D12 | Link para o painel na aplicação | **Não nesta release.** O endereço fica no README; a porta é configurável, e a tela não a conhece | Um link "Painel de métricas" no rodapé |
