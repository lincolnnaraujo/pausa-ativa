# Spec H1 — Fundação do projeto (release v0.1.0)

> **Status:** aprovada pelo usuário em 2026-10-01, com as decisões da seção 13. Em implementação; progresso na seção 11.
> **Origem:** História 1 de [`docs/epico-pausa-ativa.md`](../epico-pausa-ativa.md).
> **Data:** 2026-10-01.

## 1. Objetivo

Deixar o repositório pronto para receber as Histórias 2 a 5. Ao fim desta release:

- a stack sobe com `docker compose up -d`, seguindo só o README;
- a arquitetura hexagonal é protegida por teste, e a build quebra se o domínio importar Spring ou JPA;
- o CI verifica lint, testes e cobertura mínima de 80% em todo pull request.

Esta release não tem regra de negócio. A única funcionalidade visível é a página inicial mostrando se o backend está no ar.

## 2. Escopo

| Dentro | Fora (história que entrega) |
|---|---|
| Monorepo `backend/` + `frontend/` | Tabelas de domínio, agendador, SSE, notificação (H2) |
| Esqueleto hexagonal dos módulos Agenda, Treino e Histórico | Perfil físico e catálogo de exercícios (H3) |
| Endpoint `GET /api/v1/sistema/status` | Gráficos e agregações (H4) |
| Flyway com migração `V1` de baseline | Prometheus, Grafana, scripts de backup (H5) |
| Actuator com health, liveness e readiness | |
| `docker-compose.yml` com frontend, backend e Postgres | |
| GitHub Actions e Dependabot | |
| README de subida, C4 níveis 1 a 3, release notes | |

## 3. Estrutura do repositório

```
pausa-ativa/
├── .github/
│   ├── workflows/ci.yml
│   └── dependabot.yml
├── backend/
│   ├── .mvn/wrapper/            # Maven Wrapper (não há Maven instalado na máquina)
│   ├── mvnw, mvnw.cmd
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/br/com/pausaativa/
│       │   ├── PausaAtivaApplication.java
│       │   ├── agenda/      { domain, application/port/in, application/port/out, adapter/in/web, adapter/out/persistence }
│       │   ├── treino/      { mesma estrutura }
│       │   ├── historico/   { mesma estrutura }
│       │   ├── sistema/     adapter/in/web/StatusController.java
│       │   └── shared/      config/ (Clock, Jackson, OpenAPI)
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/V1__baseline.sql
│       └── test/java/
│           ├── br/com/pausaativa/…        # testes por módulo + ArquiteturaTest
│           └── fixtures/arquitetura/…     # classes que violam regras de propósito (fora do pacote base)
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig*.json, eslint.config.js
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/ { main.ts, App.vue, api/, views/HomeView.vue }
├── docs/
│   ├── epico-pausa-ativa.md
│   ├── specs/h1-fundacao.md
│   ├── arquitetura/ { README.md, c4-1-contexto.md, c4-2-containers.md, c4-3-componentes.md }
│   ├── api/openapi.json
│   └── releases/v0.1.0.md
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

## 4. Versões fixadas

Consultadas em 2026-10-01. A última estável de cada uma, salvo nota.

| Componente | Versão |
|---|---|
| Java | 25 (Temurin) |
| Spring Boot | 4.1.1 |
| Maven | 3.9.x via Maven Wrapper 3.3.4 |
| springdoc-openapi | 3.1.1 (`starter-webmvc-api`, sem a interface do Swagger) |
| Testcontainers | 2.0.5 |
| ArchUnit | 1.5.1 |
| JaCoCo | 0.8.15 |
| Spotless (palantir-java-format) | 3.10.3 (palantir 2.101.0) |
| PostgreSQL | `postgres:18-alpine` |
| Node | 24 LTS (`node:24-alpine` no build) |
| Vue | 3.5.x |
| Vite | 8.x |
| TypeScript | **6.0.x**. A 7.0 é o port nativo e o `vue-tsc` ainda depende da API JS do compilador. Revisar quando o `vue-tsc` declarar suporte. |
| vue-tsc | 3.3.x |
| Vitest + `@vitest/coverage-v8` | 5.0.x |
| ESLint + eslint-plugin-vue | 10.x |
| nginx | `nginx:1.29-alpine` |
| Imagens Java | build `eclipse-temurin:25-jdk-alpine`, runtime `eclipse-temurin:25-jre-alpine` |

## 5. Backend

### 5.1 Pacotes

Cada bounded context segue a mesma estrutura hexagonal:

| Pacote | Conteúdo | Pode depender de |
|---|---|---|
| `<modulo>.domain` | Agregados, entidades, value objects, regras | Só JDK e `shared.domain` |
| `<modulo>.application.port.in` | Casos de uso (interfaces) e comandos/consultas. É a API pública do módulo. | `domain` |
| `<modulo>.application.port.out` | Interfaces de repositório, notificador | `domain` |
| `<modulo>.application` | Implementação dos casos de uso | `domain`, `port.in`, `port.out`; anotações `@Service` e `@Transactional` permitidas |
| `<modulo>.adapter.in.web` | Controllers REST | `application.port.in` |
| `<modulo>.adapter.out.persistence` | Entidades JPA, Spring Data, mapeadores | `application.port.out`, `domain` |
| `shared` | `Clock`, configuração transversal | — |

Na v0.1.0 os três módulos têm só os pacotes, cada um com `package-info.java` documentando seu papel. As classes chegam a partir da H2.

### 5.2 Regras ArchUnit

Arquivo `ArquiteturaTest`, importando `br.com.pausaativa` sem classes de teste.

| # | Regra |
|---|---|
| R1 | Classes em `..domain..` não dependem de `org.springframework..`, `jakarta.persistence..`, `org.hibernate..` nem `com.fasterxml.jackson..` / `tools.jackson..` |
| R2 | `..domain..` não depende de `..application..` nem de `..adapter..` |
| R3 | `..application..` não depende de `..adapter..` nem de `jakarta.persistence..` |
| R4 | Um módulo só acessa outro módulo pelo `application.port.in` dele |
| R5 | Sem ciclos entre módulos (`agenda`, `treino`, `historico`, `sistema`) |

Enquanto os pacotes estão vazios, as regras usam `allowEmptyShould(true)`. Esse ajuste sai na H2, quando houver classes.

**Prova das regras (inclui o Cenário 2).** Com os pacotes vazios, as regras passariam sem verificar nada. Por isso cada uma tem uma fixture que a viola de propósito, em `fixtures.arquitetura.rN`. O `RegrasDeArquiteturaTest` confere que cada regra **falha** com a mensagem esperada:

| Regra | Fixture |
|---|---|
| R1 (Cenário 2) | `r1.agenda.domain.MarcoComSpring`, anotada com `@Component` |
| R2 | `r2.agenda.domain.MarcoQueConheceOAdapter`, com campo do tipo de um controller |
| R3 | `r3.agenda.application.IniciarJornadaService`, usando a entidade de persistência |
| R4 | `r4.historico.adapter.in.web.HistoricoController`, acessando `agenda.domain.Jornada` (violação) e `agenda.application.port.in.ConsultarJornadas` (permitido; o teste confere que não é apontado) |
| R5 | `r5.agenda…IniciarJornada` ⇄ `r5.treino…MontarBloco`, ciclo entre portas de entrada |

As regras ficam em `RegrasDeArquitetura`, parametrizadas pelo pacote base. O `ArquiteturaTest` as aplica a `br.com.pausaativa`, e o `RegrasDeArquiteturaTest` às fixtures. As fixtures ficam fora de `br.com.pausaativa` para o component scan do Spring nunca as carregar nos testes de integração.

Verificado também no código real em 2026-10-01: uma classe temporária em `agenda.domain` com `@Component` fez `./mvnw verify` sair com código 1 apontando a R1.

### 5.3 Configuração

| Tema | Definição |
|---|---|
| Relógio | Bean `Clock` = `Clock.system(ZoneId.of("America/Sao_Paulo"))` em `shared.config`. Nenhuma classe chama `Instant.now()`, `LocalDateTime.now()` etc. sem `Clock` (regra ArchUnit R6, ativada na H2). |
| Fuso | JVM em UTC (`-Duser.timezone=UTC`); `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` |
| Logs | `logging.structured.format.console=ecs` (JSON em stdout). Perfil `test` usa texto. |
| Actuator | Expostos `health` e `info`. `management.endpoint.health.probes.enabled=true`. Grupo `readiness` inclui `db`. `show-components: always`. O endpoint `prometheus` é habilitado mas só fica acessível na rede interna (uso na H5). |
| Datasource | Hikari com `connection-timeout: 3s` e `validation-timeout: 2s`, para o health ficar `DOWN` rápido com o banco fora. `socketTimeout: 10` (s) no driver: sem ele, um banco que não responde prende a thread para sempre, porque o health checa a conexão com `isValid(0)`, que o driver trata como "sem limite". |
| JPA | `ddl-auto: validate`; `open-in-view: false` |
| Versão | `spring-boot-maven-plugin` com o goal `build-info`; a versão vem de `BuildProperties` |

### 5.4 Contrato: `GET /api/v1/sistema/status`

Primeiro endpoint REST. Estabelece o padrão de versionamento (`/api/v1`) e o uso do `Clock` injetado.

```http
GET /api/v1/sistema/status
200 OK
Content-Type: application/json

{
  "aplicacao": "pausa-ativa",
  "versao": "0.1.0",
  "agora": "2026-10-01T09:00:00-03:00",
  "fuso": "America/Sao_Paulo"
}
```

- Não consulta o banco. Responde 200 mesmo com o Postgres fora; o estado do banco fica no `/actuator/health`.
- Contrato documentado via springdoc (code-first). Um teste gera o OpenAPI e compara com `docs/api/openapi.json`. Se divergir, o teste falha com instrução para regenerar (`./mvnw verify -Dopenapi.atualizar=true`). Assim o contrato versionado nunca fica desatualizado.
- O contrato declara `application/json` (via `produces`) e todos os campos como obrigatórios (`requiredProperties`). Sem isso, o springdoc publicava `*/*` e campos opcionais, e os tipos do frontend sairiam errados.
- `springdoc.paths-to-match: /api/**` deixa o Actuator fora do contrato. `writer-with-order-by-keys` mantém o arquivo estável entre execuções.

### 5.5 Flyway

- `V1__baseline.sql`: só um comentário explicando que as tabelas de domínio começam na `V2` (H2).
- Teste de integração confirma que a `V1` é aplicada em Postgres limpo (`flyway_schema_history` com 1 registro, sucesso).

### 5.6 Qualidade

- **Spotless** (palantir-java-format) em `check`, ligado à fase `verify`.
- **JaCoCo** com regra `LINE COVEREDRATIO >= 0.80` na fase `verify`. Exclui `PausaAtivaApplication` e `package-info`.
- Testes de integração com Testcontainers 2 (`testcontainers-postgresql`) e `@ServiceConnection`. Sem H2.

## 6. Frontend

### 6.1 Página inicial

`HomeView` mostra o título **Pausa Ativa** e um cartão de status do backend com três estados:

| Estado | Quando | Exibe |
|---|---|---|
| Carregando | Requisição em andamento | "Verificando o servidor…" |
| No ar | `GET /api/v1/sistema/status` respondeu 200 | Versão e horário do servidor (`HH:mm`, fuso de São Paulo) |
| Indisponível | Erro de rede, timeout de 5 s ou status ≠ 200 | Mensagem fixa e botão **Tentar novamente** |

Sem router e sem gerenciador de estado nesta release. Entram quando houver mais de uma tela.

### 6.2 Cliente HTTP

`src/api/sistema.ts` usa `fetch` com `AbortController` (timeout de 5 s) e tipos TypeScript do contrato. Não usa biblioteca de HTTP.

### 6.3 nginx

| Rota | Destino |
|---|---|
| `/` | Arquivos estáticos do build Vite, com fallback para `index.html` |
| `/api/` | `http://backend:8080/api/` |
| `/actuator/health` | `http://backend:8080/actuator/health` (só esse path do Actuator é exposto ao host) |

`proxy_buffering off` e `proxy_read_timeout` alto em `/api/` já ficam configurados para o SSE da H2.

### 6.4 Qualidade

- `npm run lint`: ESLint com `eslint-plugin-vue` e regras TypeScript.
- `npm run typecheck`: `vue-tsc --noEmit`.
- `npm test`: Vitest + Vue Test Utils + jsdom, cobertura v8 com limite de 80% de linhas.

## 7. Docker Compose

| Serviço | Imagem | Portas no host | Healthcheck | Depende de |
|---|---|---|---|---|
| `postgres` | `postgres:18-alpine` | nenhuma | `pg_isready` | — |
| `backend` | build `./backend` | nenhuma | `wget` em `/actuator/health/liveness` | `postgres` saudável |
| `frontend` | build `./frontend` | `127.0.0.1:${FRONTEND_PORT:-80}:80` | `wget` em `/` | `backend` saudável |

- Todos com `restart: unless-stopped`.
- `backend` com `mem_limit: 512m`. As opções da JVM (`-XX:MaxRAMPercentage=75 -Duser.timezone=UTC`) ficam no `ENTRYPOINT` do Dockerfile, e não em `JAVA_TOOL_OPTIONS`: essa variável faz a JVM imprimir "Picked up JAVA_TOOL_OPTIONS", uma linha fora do JSON, a cada start. Verificado na T5: heap máximo de 384 MB e cerca de 220 MiB em uso.
- Volume nomeado `pgdata` montado em `/var/lib/postgresql`. A partir do Postgres 18 a imagem guarda os dados em `/var/lib/postgresql/18/docker`, e montar em `/var/lib/postgresql/data` gera erro na subida.
- O healthcheck do container do backend usa **liveness**, que não depende do banco. Assim o Postgres fora deixa o `/actuator/health` em `DOWN` sem o Docker considerar o container doente.
- Imagens rodam com usuário não-root.

`.env.example` (copiado para `.env`, que fica fora do git):

```dotenv
POSTGRES_DB=pausaativa
POSTGRES_USER=pausaativa
POSTGRES_PASSWORD=troque-esta-senha
FRONTEND_PORT=80
```

O compose usa `${POSTGRES_PASSWORD:?defina POSTGRES_PASSWORD no .env}` para falhar com mensagem clara quando o `.env` não existir.

## 8. CI e manutenção

`.github/workflows/ci.yml`, disparado em `pull_request` e em `push` na `main`:

| Job | Passos |
|---|---|
| `backend` | `setup-java` 25 Temurin com cache Maven → `./mvnw -B verify` (Spotless, testes, Testcontainers, ArchUnit, JaCoCo 80%) → relatório JaCoCo como artefato |
| `frontend` | `setup-node` 24 com cache npm → `npm ci` → `lint` → `typecheck` → `test --coverage` |
| `compose` | Depende dos dois anteriores. `cp .env.example .env` → `docker compose up -d --build --wait` → `curl` em `/` e `/actuator/health` → `docker compose down -v` |

`.github/dependabot.yml`: atualizações semanais para `maven` (`/backend`), `npm` (`/frontend`), `docker` (os dois Dockerfiles) e `github-actions`.

Fluxo: branch `feat/h1-fundacao` → PR → merge → tag `v0.1.0`. O merge só acontece com o CI verde.

O repositório é privado numa conta GitHub Free, que não oferece proteção de branch nem rulesets para repositórios privados (verificado em 2026-10-01: a API devolve 403). O GitHub não bloqueia o merge com CI vermelho; a regra vale por disciplina. Para ter o bloqueio automático, seria preciso tornar o repositório público ou assinar o GitHub Pro.

## 9. Documentação entregue

| Arquivo | Conteúdo |
|---|---|
| `README.md` | Pré-requisitos, subir em 3 passos, verificar se está no ar, parar e apagar dados, porta 80 ocupada, comandos de desenvolvimento |
| `docs/arquitetura/c4-1-contexto.md` | Nível 1, a partir do épico |
| `docs/arquitetura/c4-2-containers.md` | Nível 2, refletindo o compose real (sem Prometheus e Grafana até a H5) |
| `docs/arquitetura/c4-3-componentes.md` | Nível 3 do backend: módulos, ports e adapters, e as regras ArchUnit |
| `docs/api/openapi.json` | Contrato gerado e verificado em teste |
| `docs/releases/v0.1.0.md` | O que foi entregue, como verificar, limitações conhecidas |

## 10. Critérios de aceitação: como cada um é verificado

| Cenário do épico | Verificação automática | Verificação manual (aceite) |
|---|---|---|
| 1. Subida do zero | Job `compose` do CI | Clonar, seguir o README, abrir `http://127.0.0.1` e ver "No ar" |
| 2. Regra de arquitetura protegida | `ArquiteturaTest`: R1 a R5 verdes no código real; teste da fixture prova que R1 quebra com `@Component` no domínio | Adicionar um import de Spring numa classe de domínio, rodar `./mvnw verify` e ver a falha |
| 3. Banco indisponível | Teste de integração: Postgres via Testcontainers; `docker pause` no container → `/actuator/health` = `DOWN` (503); `docker unpause` → volta a `UP` em até 10 s | `docker compose stop postgres` → `curl 127.0.0.1/actuator/health` retorna `DOWN`; `docker compose start postgres` → `UP` |
| 4. CI | O próprio workflow, com cobertura abaixo de 80% quebrando o job | Abrir o PR da H1 e ver os três jobs verdes |

Testes adicionais desta release:

- `StatusController` com MockMvc e `Clock` fixo: resposta exata do contrato.
- `HomeView`: os três estados, com `fetch` simulado, incluindo timeout.
- Flyway aplica a `V1` do zero.
- Contrato OpenAPI igual ao arquivo versionado.

## 11. Plano de entrega em etapas

Cada etapa termina verificável e vira pelo menos um commit. A T1 vai direto na `main`; da T2 em diante, na branch `feat/h1-fundacao`.

A execução para ao fim de cada etapa, e o usuário decide se continua. A próxima sessão retoma pela primeira etapa sem ✅.

| # | Etapa | Pronto quando | Status |
|---|---|---|---|
| T1 | `git init`, `.gitignore`, `.gitattributes`, `.env.example`, repositório no GitHub | Primeiro push com o épico e esta spec | ✅ 2026-10-01 |
| T2 | Backend: Maven Wrapper, `pom.xml`, app Spring Boot, `application.yml`, Flyway `V1` | `./mvnw verify` verde com teste de contexto via Testcontainers | ✅ 2026-10-01 |
| T3 | Backend: pacotes hexagonais, `Clock`, ArchUnit R1–R5 com teste da fixture | Cenário 2 provado em teste | ✅ 2026-10-01 |
| T4 | Backend: `/api/v1/sistema/status`, springdoc, snapshot OpenAPI, Spotless, JaCoCo 80% | `verify` verde com cobertura ≥ 80% | ✅ 2026-10-01 |
| T5 | Backend: teste do Cenário 3 (pause/unpause) e Dockerfile | Imagem builda e o container fica `healthy` | ✅ 2026-10-01 |
| T6 | Frontend: Vite + Vue + TS, `HomeView`, cliente HTTP, ESLint, Vitest 80%, Dockerfile + nginx | `lint`, `typecheck` e `test` verdes; imagem builda | Pendente |
| T7 | `docker-compose.yml` completo | Cenários 1 e 3 verificados manualmente na máquina local | Pendente |
| T8 | CI e Dependabot | Os três jobs verdes no PR | Pendente |
| T9 | README, C4, `openapi.json`, release notes | Documentação revisada; aceite do usuário; merge e tag `v0.1.0` | Pendente |

## 12. Riscos

| Risco | Mitigação |
|---|---|
| Porta 80 ocupada no Windows (IIS, outro app) | `FRONTEND_PORT` no `.env`; README explica como trocar |
| Docker Desktop parado na máquina (estava parado em 2026-10-01) | README lista iniciar o Docker Desktop como pré-requisito; testes de integração exigem Docker rodando |
| ~~Incompatibilidade entre springdoc 3.1 e Spring Boot 4.1~~ | Resolvido na T4: funciona (o start.spring.io já o lista para o Boot 4.1) |
| Teste de pause/unpause instável no CI | Timeouts curtos do Hikari e espera com `Awaitility` em vez de `sleep` |
| TypeScript 7 incompatível com `vue-tsc` | TS fixado em 6.0.x (seção 4) |
| Sem proteção de branch (repositório privado no GitHub Free) | Merge só com CI verde, por disciplina (seção 8) |

## 13. Decisões para o usuário confirmar

1. **Pacote base Java:** `br.com.pausaativa`.
2. **Repositório no GitHub:** `lincolnnaraujo/pausa-ativa`, **privado**.
3. **Porta padrão do frontend:** 80 (`http://127.0.0.1`), configurável.
4. **OpenAPI code-first** com snapshot versionado, em vez de escrever o contrato antes e gerar código.
5. **Formatação Java** com palantir-java-format via Spotless.

As perguntas pendentes 1 e 2 do épico afetam só a H2 e não bloqueiam esta história.
