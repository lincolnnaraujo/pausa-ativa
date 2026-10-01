# syntax=docker/dockerfile:1

# ---- Build: compila com o Maven Wrapper (mesma versão do Maven local e do CI) ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# Testes, lint e cobertura rodam no CI; aqui só empacota.
# O cache do ~/.m2 sobrevive entre builds (BuildKit), então dependências não são baixadas de novo.
RUN --mount=type=cache,target=/root/.m2 \
    sh mvnw -B -ntp -q package -DskipTests -Djacoco.skip=true -Dspotless.check.skip=true \
    && cp target/pausa-ativa-backend-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extraido

# ---- Runtime: só a JRE, usuário sem privilégios ----
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

# Camadas da menos para a mais volátil: mudar o código não invalida as dependências.
COPY --from=build /build/extraido/dependencies/ ./
COPY --from=build /build/extraido/spring-boot-loader/ ./
COPY --from=build /build/extraido/snapshot-dependencies/ ./
COPY --from=build /build/extraido/application/ ./

USER app
EXPOSE 8080

# Liveness não depende do banco: o Postgres fora deixa o /actuator/health DOWN sem o container
# ser considerado doente.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

# A JVM usa até 75% do limite de memória do container (512 MB no compose) e roda em UTC;
# o fuso de negócio vem do Clock injetado.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", \
            "org.springframework.boot.loader.launch.JarLauncher"]
