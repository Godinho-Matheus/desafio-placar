# Desafio Placar

## Sobre o projeto

Sistema de gerenciamento de placares de partidas de futebol em tempo real. O
backend é Jakarta EE e a interface web é construída com Apache Wicket. O
PostgreSQL é a fonte de verdade dos jogos; o Redis funciona como cache do placar
atual (apenas `placarA` e `placarB`); o RabbitMQ propaga de forma assíncrona as
atualizações de placar. Tudo é empacotado como um único WAR, cujo alvo de deploy
é o Payara Server.

A API REST fica sob `/api/jogos` e permite criar jogos, listar (com filtro por
status), buscar por identificador, atualizar o placar e encerrar um jogo. A
regra de negócio principal é que um jogo `ENCERRADO` não pode ter o placar
alterado.

## Arquitetura

Monólito Jakarta EE em um único WAR no Payara. O PostgreSQL é a única fonte de
verdade; o Redis é apenas cache do placar atual; o RabbitMQ é usado para
propagação assíncrona. A interface Wicket usa polling e chama o `JogoService`
diretamente (nunca a API REST). Não há WebSocket. Não há Outbox, retry nem
dead-letter nesta versão.

```
REST / Wicket -> JogoService -> PostgreSQL -> commit
                                                 |
                                                 v
                                   CDI Event AFTER_SUCCESS
                                   /                       \
                          (invalida Redis)      (RabbitMQ -> Consumer -> Redis -> Wicket polling)
```

Fluxo: uma atualização de placar é persistida no PostgreSQL dentro de uma
transação. Após o commit, um evento CDI observado em `AFTER_SUCCESS` executa dois
passos independentes: invalida a chave do placar no Redis e publica um evento no
RabbitMQ. O consumidor RabbitMQ recebe o evento e grava o novo placar no Redis. A
`JogosPage` faz polling periódico chamando `JogoService.obterPlacarAtual(jogoId)`,
que lê o Redis e, quando não houver valor em cache (ou o Redis estiver
indisponível), busca o placar no PostgreSQL. Falhas de Redis ou RabbitMQ após o
commit não desfazem a alteração já persistida.

## Tecnologias

- Java 21
- Jakarta EE 11 (JAX-RS, JPA, CDI) — provido pelo Payara em runtime
- Payara Server 7.2026.2 (servidor de aplicação alvo)
- Apache Wicket 10.11.0 (interface web) + integração Wicket-CDI
- PostgreSQL — driver JDBC `org.postgresql:postgresql` 42.7.4
- Redis — cliente Jedis 5.2.0
- RabbitMQ — cliente `com.rabbitmq:amqp-client` 5.25.0
- MicroProfile OpenAPI 3.1.2 (documento OpenAPI, provido pelo Payara)
- Swagger UI 5.32.14 (WebJar, empacotado no WAR)
- Maven (build; empacotamento `war`)

## Pré-requisitos

- Java 21
- Maven
- Payara Server 7.2026.2
- PostgreSQL
- Redis
- RabbitMQ

## Configuração

A conexão com o PostgreSQL é resolvida por JNDI (DataSource gerenciado pelo
Payara). Já Redis e RabbitMQ são configurados por propriedades de sistema da JVM
ou variáveis de ambiente, com a precedência: propriedade de sistema (`-D...`) →
variável de ambiente → valor padrão. Nenhuma credencial real de produção fica no código-fonte; quando previsto, podem existir defaults adequados ao desenvolvimento local.

### PostgreSQL / Payara DataSource

A aplicação **não** recebe host, porta, usuário ou senha do PostgreSQL
diretamente. O caminho é:

```
persistence.xml -> JNDI jdbc/placar -> Payara JDBC Resource -> JDBC Connection Pool -> PostgreSQL
```

Pontos importantes:

- O banco de dados deve existir previamente.
- Usuário e senha pertencem à configuração do **Connection Pool** no Payara.
- O nome JNDI esperado é `jdbc/placar` (referenciado pelo `<jta-data-source>` do
  `persistence.xml`).
- A `Datasource Classname` é `org.postgresql.ds.PGSimpleDataSource`.
- As propriedades principais do DataSource são: `serverName`, `portNumber`,
  `databaseName`, `user`, `password`.
- O esquema da tabela `jogo` é criado/estendido na inicialização por JPA
  (EclipseLink, `create-or-extend-tables`), quando o PostgreSQL está disponível.

**Driver JDBC no domínio do Payara.** O JAR do driver PostgreSQL precisa estar
disponível para o **domínio** do Payara para que o servidor consiga criar o
Connection Pool. Copie o driver PostgreSQL (versão 42.7.4, a mesma definida no
`pom.xml`) para o diretório `lib` do domínio, por exemplo:

```
<PAYARA_HOME>/glassfish/domains/domain1/lib/
```

Depois reinicie o domínio. O driver empacotado dentro do WAR **não** é suficiente
para o servidor criar o Connection Pool; o JAR precisa estar no `lib` do domínio.
(Não copie o JAR do driver para dentro do repositório.)

**Criar o Connection Pool e o Resource (Admin Console).** Os valores abaixo são
apenas **exemplos** de desenvolvimento local — substitua pelos do seu ambiente.

1. Acesse o Payara Admin Console em `http://localhost:4848`.
2. `Resources` → `JDBC` → `JDBC Connection Pools` → `New`:
   - Pool Name: `PlacarPool`
   - Resource Type: `javax.sql.DataSource`
   - Datasource Classname: `org.postgresql.ds.PGSimpleDataSource`
   - Propriedades (exemplos):
     - `serverName=localhost`
     - `portNumber=5432`
     - `databaseName=placar`
     - `user=postgres`
     - `password=<senha>`
   - Use `Ping` para testar a conexão.
3. `Resources` → `JDBC` → `JDBC Resources` → `New`:
   - JNDI Name: `jdbc/placar`
   - Pool Name: `PlacarPool`

### Redis

O Redis é usado apenas como cache do placar atual. Configuração externa:

| Chave            | Descrição        | Padrão      |
| ---------------- | ---------------- | ----------- |
| `REDIS_HOST`     | host do Redis    | `localhost` |
| `REDIS_PORT`     | porta do Redis   | `6379`      |
| `REDIS_PASSWORD` | senha (opcional) | (sem padrão) |

Precedência: `-DREDIS_HOST=...` → variável de ambiente `REDIS_HOST` → padrão.

Exemplos:

```bash
# variável de ambiente
export REDIS_HOST=localhost
export REDIS_PORT=6379

# ou como propriedade de sistema da JVM (opções do domínio Payara)
-DREDIS_HOST=localhost -DREDIS_PORT=6379
```

### RabbitMQ

O RabbitMQ propaga o evento de placar de forma assíncrona. Configuração externa:

| Chave                  | Descrição            | Padrão      |
| ---------------------- | -------------------- | ----------- |
| `RABBITMQ_HOST`        | host do RabbitMQ     | `localhost` |
| `RABBITMQ_PORT`        | porta do RabbitMQ    | `5672`      |
| `RABBITMQ_USERNAME`    | usuário              | `guest`     |
| `RABBITMQ_PASSWORD`    | senha                | `guest`     |
| `RABBITMQ_VIRTUAL_HOST`| virtual host         | `/`         |

Precedência: propriedade de sistema → variável de ambiente → padrão.

O par `guest`/`guest` é o default conhecido do RabbitMQ, adequado apenas para
desenvolvimento local. Em qualquer ambiente real, as credenciais devem ser
fornecidas externamente pelas chaves acima. A topologia (exchange, fila e routing
key) é mantida constante no código e não é configurável por variáveis de
ambiente.

## Build e testes

Executar os testes unitários (não exigem Payara, PostgreSQL, Redis nem RabbitMQ):

```bash
mvn clean test
```

Gerar o WAR implantável:

```bash
mvn clean package
```

O artefato é gerado em `target/desafio-placar.war`.

## Deploy no Payara

1. Inicie o domínio do Payara.
2. Garanta que PostgreSQL, Redis e RabbitMQ estejam em execução.
3. Garanta que o recurso JDBC `jdbc/placar` esteja configurado e que o `Ping` do
   Connection Pool funcione.
4. Faça o deploy de `target/desafio-placar.war` pelo Admin Console:
   `Applications` → `Deploy` → selecione o WAR.

## URLs da aplicação

- Interface web (Wicket): `http://localhost:8080/desafio-placar/`
- API REST: `http://localhost:8080/desafio-placar/api/jogos`
- Documento OpenAPI: `http://localhost:8080/openapi` (raiz do servidor, fora do
  contexto `/desafio-placar`)
- Swagger UI: `http://localhost:8080/desafio-placar/swagger/`

## API REST

Todos os corpos usam `Content-Type: application/json`. Os campos correspondem
exatamente aos DTOs da aplicação.

### Criar jogo

```bash
curl -i -X POST http://localhost:8080/desafio-placar/api/jogos \
  -H "Content-Type: application/json" \
  -d '{"timeA":"Time A","timeB":"Time B","dataHoraPartida":"2026-09-27T20:00:00-03:00"}'
```

Um jogo novo começa com placar 0x0 e status `EM_ANDAMENTO` (HTTP 201). Campo
obrigatório ausente resulta em HTTP 400.

### Listar jogos

```bash
curl -i http://localhost:8080/desafio-placar/api/jogos
curl -i "http://localhost:8080/desafio-placar/api/jogos?status=EM_ANDAMENTO"
curl -i "http://localhost:8080/desafio-placar/api/jogos?status=ENCERRADO"
```

Sem o parâmetro `status`, lista todos os jogos (HTTP 200). `status` vazio ou
inválido resulta em HTTP 400 informando os valores aceitos.

### Buscar jogo

```bash
curl -i http://localhost:8080/desafio-placar/api/jogos/1
```

HTTP 200 com o jogo; identificador inexistente resulta em HTTP 404.

### Atualizar placar

```bash
curl -i -X PUT http://localhost:8080/desafio-placar/api/jogos/1/placar \
  -H "Content-Type: application/json" \
  -d '{"placarA":2,"placarB":1}'
```

HTTP 200 com o placar atualizado. Placar negativo resulta em HTTP 400;
identificador inexistente em HTTP 404; atualizar placar de jogo `ENCERRADO` em
HTTP 409.

### Encerrar jogo

```bash
curl -i -X PUT http://localhost:8080/desafio-placar/api/jogos/1/status \
  -H "Content-Type: application/json" \
  -d '{"status":"ENCERRADO"}'
```

O único valor aceito é `ENCERRADO`. Encerrar um jogo `EM_ANDAMENTO` retorna
HTTP 200; repetir `ENCERRADO` em um jogo já encerrado é idempotente e também
retorna HTTP 200; qualquer outro valor resulta em HTTP 400.

## Fluxo de atualização em tempo real

1. Uma atualização de placar é persistida no PostgreSQL dentro de uma transação.
2. Após o commit, um observer CDI `AFTER_SUCCESS` invalida a chave do placar no
   Redis e publica um evento no RabbitMQ (passos independentes; falhas apenas em
   log).
3. O consumidor RabbitMQ recebe o evento e grava `placarA`/`placarB` no Redis.
4. A `JogosPage` faz polling periódico (`AjaxSelfUpdatingTimerBehavior`) e
   atualiza os placares exibidos sem recarregamento manual, lendo o Redis e caindo
   para o PostgreSQL quando não houver cache.

## Testes automatizados

`mvn test` roda os testes unitários com JUnit 5 sem necessidade de Payara,
PostgreSQL, Redis ou RabbitMQ. A suíte tem 32 testes, cobrindo: regras de negócio
do `JogoService`, observer e consumer do fluxo assíncrono, contrato da API REST
(201/200/400/404/409, corpo do `PUT /status` e idempotência do encerramento) e a
interface Wicket (controle desabilitado para jogo `ENCERRADO` e um ciclo de
polling rerenderizando o placar).

## Decisões e limitações conhecidas

- O PostgreSQL é a fonte de verdade; o Redis é apenas cache do placar atual.
- A propagação assíncrona via RabbitMQ não usa Outbox, retry nem dead-letter.
- O consumidor RabbitMQ usa `auto-ack`, coerente com o modelo de consistência
  eventual (sem reprocessamento ou deduplicação).
- A interface web usa polling (`AjaxSelfUpdatingTimerBehavior`), não WebSocket.
- Como a invalidação do Redis e a publicação no RabbitMQ ocorrem após o commit,
  uma falha nesses passos não desfaz a alteração já persistida no PostgreSQL; a
  próxima atualização bem-sucedida reconcilia o cache.
