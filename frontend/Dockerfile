# syntax=docker/dockerfile:1

# ---- Build: gera os arquivos estáticos com o Vite ----
FROM node:24-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Lint, typecheck e testes rodam no CI; aqui só empacota.
RUN npx vite build

# ---- Runtime: nginx sem root ----
FROM nginxinc/nginx-unprivileged:1.31-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
