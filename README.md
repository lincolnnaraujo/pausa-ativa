# Pausa Ativa

Aplicação web que roda no seu computador e distribui hidratação e exercício curto ao longo da jornada de home office.

> **Versão atual: v0.1.0 (fundação).** Esta versão sobe a aplicação inteira e mostra se o servidor está no ar. Jornada, lembretes de água, exercícios e gráficos chegam nas próximas versões. Veja o [plano completo](docs/epico-pausa-ativa.md).

## O que você precisa

- **Docker Desktop** instalado e **aberto**. No Linux, Docker Engine com o plugin Compose v2.
- **Git**, para baixar o projeto.
- **Google Chrome**, o único navegador testado.

Não é preciso instalar Java, Node nem banco de dados: tudo roda dentro do Docker.

## Subir a aplicação

Os comandos funcionam no PowerShell, no Git Bash, no Linux e no macOS.

**1. Baixe o projeto**

```sh
git clone https://github.com/lincolnnaraujo/pausa-ativa.git
cd pausa-ativa
```

**2. Crie o arquivo de configuração**

```sh
cp .env.example .env
```

Abra o `.env` e troque `troque-esta-senha` por uma senha sua. Esse arquivo não vai para o git.

**3. Suba tudo**

```sh
docker compose up -d --wait
```

Na primeira vez leva alguns minutos, porque o Docker baixa e monta as imagens. O comando só termina quando tudo estiver pronto.

**Pronto.** Abra **http://127.0.0.1:38742** no Chrome.

## Conferir se está funcionando

A página deve mostrar **Servidor no ar**, com a versão e o horário do servidor.

Se quiser conferir pelo terminal:

```sh
docker compose ps
```

Os três serviços (`postgres`, `backend` e `frontend`) devem aparecer como `healthy`.

## Parar e voltar

| Quero… | Comando |
|---|---|
| Parar, mantendo os dados | `docker compose down` |
| Subir de novo | `docker compose up -d --wait` |
| Atualizar para uma versão nova | `git pull` e depois `docker compose up -d --build --wait` |
| **Apagar todos os dados** (não tem volta) | `docker compose down -v` |

A aplicação volta sozinha quando o Docker Desktop abre, por exemplo depois de reiniciar o computador.

## Se algo der errado

**`required variable POSTGRES_... is missing a value`**
Falta o arquivo `.env`. Faça o passo 2.

**`failed to connect to the docker API` ou `Cannot connect to the Docker daemon`**
O Docker Desktop está fechado. Abra-o, espere ele terminar de iniciar e repita o passo 3.

**`port is already allocated`, `address already in use` ou `forbidden by its access permissions` ao subir**
Outro programa usa a porta 38742. No `.env`, troque `FRONTEND_PORT` por outra porta **abaixo de 49152** (no Windows, as portas acima disso podem estar reservadas pelo sistema) e repita o passo 3. O endereço passa a ser `http://127.0.0.1:<nova porta>`.

**A página mostra "Servidor indisponível"**
O servidor ainda está iniciando ou parou. Veja o estado com `docker compose ps` e os erros com `docker compose logs backend`.

**Troquei a senha do `.env` (ou baixei o projeto de novo) e o `backend` não sobe**
O banco guarda a senha usada na **primeira** subida e ignora mudanças depois disso. Volte a senha antiga no `.env` ou, se puder perder os dados, apague o banco com `docker compose down -v` e suba de novo.

## Documentação

| Documento | Conteúdo |
|---|---|
| [Épico](docs/epico-pausa-ativa.md) | Objetivo, regras de negócio, histórias e critérios de aceitação |
| [Arquitetura](docs/arquitetura/README.md) | Diagramas C4 (contexto, containers e componentes) e regras de arquitetura |
| [Contrato da API](docs/api/openapi.json) | OpenAPI 3.1, gerado pelo código e conferido em teste |
| [Releases](docs/releases/) | O que cada versão entregou |
| [Specs](docs/specs/) | Especificação de cada história, escrita antes do código |

## Para desenvolvedores

Requisitos: **Java 25**, **Node 24.12+** e **Docker** (os testes do backend sobem um Postgres real).

**Backend** (`cd backend`)

| Comando | Para quê |
|---|---|
| `./mvnw verify` | Tudo que o CI roda: formatação, testes, regras de arquitetura, contrato OpenAPI e cobertura mínima de 80% |
| `./mvnw spotless:apply` | Corrige a formatação do Java |
| `./mvnw spring-boot:test-run` | Sobe só o backend em `http://localhost:8080`, com um Postgres temporário em container |
| `./mvnw verify -Dopenapi.atualizar=true` | Regenera `docs/api/openapi.json` depois de mudar a API (revise o diff) |

**Frontend** (`cd frontend`, depois `npm ci` uma vez)

| Comando | Para quê |
|---|---|
| `npm run dev` | Página em `http://localhost:5173`, falando com o backend local da porta 8080 |
| `npm run lint` | ESLint |
| `npm run typecheck` | Checagem de tipos (`vue-tsc`) |
| `npm test` | Testes com cobertura mínima de 80% |

O fluxo de trabalho é SDD (Spec-Driven Development): cada história ganha uma spec em `docs/specs/` antes do código e é entregue como uma release. Todo pull request passa pelo CI (backend, frontend e subida com `docker compose`).

### Estrutura

```
backend/    API em Spring Boot 4 (Java 25), arquitetura hexagonal
frontend/   Página em Vue 3 + TypeScript, servida por nginx
docs/       Épico, specs, arquitetura, contrato da API e releases
docker-compose.yml
.env.example
```
