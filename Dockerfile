# =============================================================================
# Estagio 1 - build (Maven + JDK 21)
# -----------------------------------------------------------------------------
# Compila o projeto e roda os testes como parte do build (mvn clean package),
# produzindo target/desafio-placar.war. Nao exige Java nem Maven na maquina
# hospedeira.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 1) Baixa as dependencias primeiro (melhor cache de camadas): se apenas o
#    codigo mudar, o download de dependencias e reaproveitado.
COPY pom.xml ./
RUN mvn -B -e dependency:go-offline

# 2) Copia o codigo-fonte e empacota (os testes fazem parte do build).
COPY src ./src
RUN mvn -B clean package

# 3) Extrai o driver JDBC do PostgreSQL (versao 42.7.4, a mesma do pom.xml) do
#    repositorio local do Maven para um caminho fixo, para copia-lo ao dominio
#    do Payara no estagio de runtime.
RUN cp /root/.m2/repository/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar \
       /build/postgresql.jar

# =============================================================================
# Estagio 2 - runtime (Payara Server 7.2026.2)
# -----------------------------------------------------------------------------
# Publica o WAR e prepara a configuracao automatica do dominio:
#  - driver JDBC do PostgreSQL no lib do dominio (necessario para o pool);
#  - script init.d que gera o post-boot com PlacarPool + jdbc/placar;
#  - WAR na pasta de deployments (implantado automaticamente na subida).
# =============================================================================
FROM payara/server-full:7.2026.2

# PAYARA_DIR e DOMAIN_NAME sao definidos pela imagem oficial (defaults:
# /opt/payara/appserver e "domain1", confirmado na imagem payara/server-full:7.2026.2).
# O driver JDBC precisa estar no lib do dominio para que o Payara consiga criar
# o Connection Pool.
COPY --from=build /build/postgresql.jar ${PAYARA_DIR}/glassfish/domains/${DOMAIN_NAME}/lib/postgresql.jar

# Script de inicializacao (roda ANTES do dominio subir) que gera o arquivo de
# post-boot commands a partir de variaveis de ambiente.
COPY docker/payara/init.d/01-gerar-datasource.sh ${SCRIPT_DIR}/init.d/01-gerar-datasource.sh
USER root
RUN chmod +x ${SCRIPT_DIR}/init.d/01-gerar-datasource.sh
USER payara

# WAR implantado automaticamente pela imagem oficial (varre $DEPLOY_DIR apos a
# subida do dominio). finalName do pom = desafio-placar -> contexto
# /desafio-placar.
COPY --from=build /build/target/desafio-placar.war ${DEPLOY_DIR}/desafio-placar.war

EXPOSE 8080 4848
