#!/bin/bash
#
# Executado pela imagem oficial do Payara (diretorio ${SCRIPT_DIR}/init.d)
# ANTES do dominio iniciar, porem DEPOIS do init_1_generate_deploy_commands.sh
# (o entrypoint roda "init_*" e so entao "init.d/*").
#
# Objetivo: montar o arquivo de post-boot de forma IDEMPOTENTE. No primeiro
# start o dominio ainda nao tem nada configurado, entao a ordem gerada e:
#   PlacarPool (connection pool) -> jdbc/placar (resource) -> deploy do WAR.
# Em um "docker compose restart app" o MESMO container reinicia e o estado do
# dominio (domain.xml) ja contem esses recursos e a aplicacao; recria-los
# geraria erros "already exists"/"already registered".
#
# Estrategia (robusta): este script REGERA por completo o arquivo de post-boot
# a cada boot, a partir do estado real do dominio lido do domain.xml (o dominio
# esta parado neste ponto, entao a leitura e a fonte de verdade). Adiciona
# APENAS os comandos realmente necessarios:
#   - create-jdbc-connection-pool ... PlacarPool  -> so se o pool nao existir;
#   - create-jdbc-resource ... jdbc/placar        -> so se o resource nao existir;
#   - deploy <war>                                -> so se a app nao estiver
#                                                    registrada.
# Regerar o arquivo (em vez de tentar editar o que o init_1 deixou) evita
# depender de conteudo remanescente de boots anteriores, que persiste no
# filesystem do container entre restarts. Nao usamos "|| true" para mascarar
# erros: simplesmente nao emitimos comandos redundantes. Preferiu-se ler o
# domain.xml a usar "asadmin list-*" porque, neste momento do ciclo, o DAS
# ainda NAO esta rodando.
#
# O PostgreSQL NUNCA e configurado pela aplicacao: host/porta/banco/usuario/
# senha sao propriedades do Connection Pool no Payara. Dentro da rede do Docker
# Compose o PostgreSQL responde pelo nome de servico "postgres".
#
set -euo pipefail

DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-placar}"
DB_USER="${POSTGRES_USER:-placar}"
DB_PASSWORD="${POSTGRES_PASSWORD:-placar_dev}"

POOL_NAME="PlacarPool"
RESOURCE_JNDI="jdbc/placar"
APP_NAME="desafio-placar"

# Arquivo de post-boot REALMENTE executado pelo Payara (com o sufixo -final).
POSTBOOT_FINAL="${POSTBOOT_COMMANDS_FINAL:-${CONFIG_DIR}/post-boot-commands-final.asadmin}"

# WAR a implantar (mesmo alvo que o init_1 usa em DEPLOY_DIR).
WAR_PATH="${DEPLOY_DIR}/${APP_NAME}.war"

# domain.xml persistido do dominio (fonte de verdade do estado). O dominio esta
# parado neste ponto, entao a leitura e segura.
DOMAIN_XML="${PAYARA_DIR}/glassfish/domains/${DOMAIN_NAME}/config/domain.xml"

pool_existe=false
resource_existe=false
app_registrada=false
if [ -f "${DOMAIN_XML}" ]; then
    if grep -q "name=\"${POOL_NAME}\"" "${DOMAIN_XML}"; then
        pool_existe=true
    fi
    if grep -q "jndi-name=\"${RESOURCE_JNDI}\"" "${DOMAIN_XML}"; then
        resource_existe=true
    fi
    # Aplicacao implantada aparece como <application name="desafio-placar" ...>.
    if grep -q "<application[^>]*name=\"${APP_NAME}\"" "${DOMAIN_XML}"; then
        app_registrada=true
    fi
fi

# Regera o post-boot do zero, na ordem correta: pool -> resource -> deploy.
# A criacao do pool NAO faz ping aqui de proposito: a conexao e resolvida de
# forma preguicosa (lazy) no primeiro uso. A ordem de subida (healthcheck do
# postgres no compose) garante que o banco ja esteja disponivel.
: > "${POSTBOOT_FINAL}"
if [ "${pool_existe}" = "false" ]; then
    echo "create-jdbc-connection-pool --datasourceclassname org.postgresql.ds.PGSimpleDataSource --restype javax.sql.DataSource --property serverName=${DB_HOST}:portNumber=${DB_PORT}:databaseName=${DB_NAME}:user=${DB_USER}:password=${DB_PASSWORD} ${POOL_NAME}" >> "${POSTBOOT_FINAL}"
fi
if [ "${resource_existe}" = "false" ]; then
    echo "create-jdbc-resource --connectionpoolid ${POOL_NAME} ${RESOURCE_JNDI}" >> "${POSTBOOT_FINAL}"
fi
if [ "${app_registrada}" = "false" ]; then
    echo "deploy ${DEPLOY_PROPS:-} ${WAR_PATH}" >> "${POSTBOOT_FINAL}"
fi

echo "[init.d] estado do dominio: pool=${pool_existe} resource=${resource_existe} app=${app_registrada}"
echo "[init.d] post-boot regenerado de forma idempotente (${DB_HOST}:${DB_PORT}/${DB_NAME}); apenas comandos necessarios"
