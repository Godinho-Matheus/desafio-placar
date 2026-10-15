# Modelo C4 — Desafio Placar

Modelo da arquitetura implementada, verificado no código-fonte e na configuração do repositório em 15/10/2026. Abrange contexto (C1), contêineres (C2), componentes (C3), código (C4), fluxos dinâmicos e implantação.

Os [diagramas de sequência UML](sequencia/README.md) complementam as vistas dinâmicas com os fluxos de criação, consulta, atualização de placar, encerramento e polling, em PlantUML editável e SVG.

Os diagramas usam Mermaid, com tipos, tecnologias, responsabilidades e relações explícitos. Podem ser visualizados em um renderizador Markdown com suporte a Mermaid. Nos diagramas estruturais, setas contínuas representam chamadas ou comunicação; setas pontilhadas representam eventos ou dependências de configuração. No nível de código, as relações representam dependências entre classes.

## 1. Escopo e fronteiras

O Desafio Placar permite cadastrar partidas, listar e filtrar jogos, consultar detalhes, substituir placares e encerrar partidas. A interface acompanha os placares por polling Ajax a cada cinco segundos.

O sistema contém uma aplicação Java empacotada em `desafio-placar.war`, um banco PostgreSQL, um cache Redis e um broker RabbitMQ. Interface Wicket, API REST, regras de negócio, publicador e consumidor executam na mesma aplicação e JVM no Payara.

Em C4, um **contêiner** é uma aplicação ou armazenamento executável. Essa definição independe de Docker. Aqui existem quatro contêineres lógicos, que também correspondem aos quatro serviços do Compose. Páginas Wicket e consumidor RabbitMQ são componentes internos do WAR.

Os papéis de operador e pessoa que acompanha jogos descrevem formas de uso; não representam perfis de acesso implementados. O cliente da API representa um consumidor possível da interface pública: o repositório não contém um sistema externo de integração identificado.

## 2. C1 — Contexto do sistema

```mermaid
flowchart LR
    operador["Operador de partidas<br/>Pessoa<br/>Cadastra jogos, altera placares e encerra partidas"]
    publico["Pessoa que acompanha jogos<br/>Pessoa<br/>Consulta partidas e acompanha placares"]
    cliente["Cliente da API<br/>Software externo genérico<br/>Consome o contrato REST exposto"]
    sistema["Desafio Placar<br/>Sistema de software<br/>Gerencia partidas e propaga atualizações de placar"]

    operador -->|"Gerencia partidas pela interface web · HTTP"| sistema
    publico -->|"Consulta jogos e placares pela interface web · HTTP"| sistema
    cliente -->|"Cria, consulta e altera jogos · HTTP/JSON"| sistema

    classDef pessoa fill:#08427b,color:#fff,stroke:#052e56
    classDef sistema fill:#1168bd,color:#fff,stroke:#0b4884
    classDef externo fill:#666,color:#fff,stroke:#444
    class operador,publico pessoa
    class sistema sistema
    class cliente externo
```

PostgreSQL, Redis e RabbitMQ pertencem à fronteira do sistema e aparecem no detalhamento C2. Nenhuma dependência de serviço de terceiros hospedado externamente é exigida pela configuração atual.

## 3. C2 — Contêineres

```mermaid
flowchart TB
    usuario["Usuário<br/>Pessoa usando navegador"]
    cliente["Cliente da API<br/>Software externo genérico"]

    subgraph sistema["Sistema: Desafio Placar"]
        app["Aplicação Placar<br/>Java 21 · Jakarta EE 11 · Payara 7.2026.2<br/>WAR com Wicket, REST, serviço e mensageria"]
        db[("Banco de jogos<br/>PostgreSQL 16<br/>Estado completo das partidas; fonte de verdade")]
        cache[("Cache de placares<br/>Redis 7<br/>Somente placarA e placarB por jogo")]
        broker["Broker de eventos<br/>RabbitMQ 3 · exchange direct<br/>Propaga EventoPlacar de forma assíncrona"]
    end

    usuario -->|"Páginas, formulários e polling Ajax de 5 s · HTTP"| app
    cliente -->|"/desafio-placar/api/jogos · HTTP/JSON"| app
    app -->|"Lê e persiste jogos · JPA/JTA → DataSource JNDI → JDBC :5432"| db
    app -->|"Lê, invalida e grava placares · Jedis/RESP :6379"| cache
    app -->|"Publica EventoPlacar JSON · AMQP :5672"| broker
    broker -->|"Entrega eventos ao consumidor no mesmo WAR · AMQP :5672"| app

    classDef aplicacao fill:#1168bd,color:#fff,stroke:#0b4884
    classDef dados fill:#438dd5,color:#fff,stroke:#2167a5
    class app aplicacao
    class db,cache,broker dados
```

| Contêiner | Responsabilidade e dados | Tecnologia definida no projeto |
| --- | --- | --- |
| Aplicação Placar | Interface, contrato HTTP, regras, transações, publicação e consumo de eventos | Java 21; Jakarta EE 11; Payara 7.2026.2; Wicket 10.11.0 |
| Banco de jogos | Entidade `Jogo`: identificador, times, placares, status e data/hora | PostgreSQL `16-alpine`; driver JDBC 42.7.4; JPA/EclipseLink |
| Cache de placares | Chave `jogo:{id}:placar`, valor JSON com `placarA` e `placarB` | Redis `7-alpine`; Jedis 5.2.0; sem TTL definido no código |
| Broker de eventos | Exchange `placar.exchange`, routing key `placar.atualizado`, fila `placar.atualizado.queue` | RabbitMQ `3-management`; cliente AMQP 5.25.0 |

Swagger UI é uma página estática do WAR, com assets WebJar locais. Consome o documento `/openapi` gerado pelo Payara e permite chamar a API. Não constitui uma aplicação implantada separadamente.

## 4. C3 — Componentes da aplicação Placar

```mermaid
flowchart TB
    navegador["Navegador<br/>HTTP: páginas, formulários, Ajax"]
    cliente["Cliente REST / Swagger UI<br/>HTTP/JSON"]
    db[("PostgreSQL<br/>Fonte de verdade")]
    redis[("Redis<br/>Cache do placar")]
    rabbit["RabbitMQ<br/>Exchange direct + fila durável"]

    subgraph war["Contêiner: desafio-placar.war / Payara"]
        web["Interface Wicket<br/>JogosPage, CriarJogoPanel, PlacarPanel<br/>Listagem, filtro, formulários e polling"]
        api["API REST<br/>JogoResource + DTOs + ExceptionMappers<br/>Contrato JSON e respostas HTTP"]
        service["JogoService<br/>CDI + JTA<br/>Casos de uso, validações e transações"]
        repo["JogoRepository<br/>JPA / EntityManager<br/>Persistência e consultas de Jogo"]
        domain["Modelo de domínio<br/>Jogo, Status e exceções<br/>Estado da partida e falhas de negócio"]
        observer["PlacarAtualizadoObserver<br/>CDI AFTER_SUCCESS<br/>Invalida cache e solicita publicação após commit"]
        publisher["PlacarEventPublisher<br/>Cliente AMQP + JSON-P<br/>Publica EventoPlacar"]
        consumer["PlacarEventConsumer<br/>EJB Singleton / Startup<br/>Dois consumidores na mesma fila e no mesmo canal"]
        cache["PlacarCache<br/>JedisPool + JSON-P<br/>Leitura, invalidação e gravação do placar"]
        config["Bootstrap e configuração<br/>PlacarWebApplication, JaxRsApplication<br/>ConfiguracaoExterna, Redis, RabbitMq e BancoDados"]
    end

    navegador -->|"HTTP"| web
    cliente -->|"/api/jogos"| api
    web -->|"Chamadas Java via CDI"| service
    api -->|"Chamadas Java via CDI"| service
    service -->|"Consulta e salva"| repo
    service -->|"Usa Jogo, Status e exceções"| domain
    repo -->|"Mapeia Jogo"| domain
    repo -->|"JPA → jdbc/placar → JDBC"| db
    service -->|"obterPlacarAtual: leitura Redis primeiro"| cache
    service -.->|"PlacarAtualizadoEvent; execução após commit"| observer
    observer -->|"1. invalidar"| cache
    observer -->|"2. publicar; independente da invalidação"| publisher
    publisher -->|"AMQP / EventoPlacar JSON"| rabbit
    rabbit -->|"Entrega assíncrona; auto-ack"| consumer
    consumer -->|"atualizar"| cache
    cache -->|"GET / DEL / SET via RESP"| redis
    config -.->|"Inicializa Wicket e CDI"| web
    config -.->|"Registra base /api"| api
    config -.->|"Host, porta e senha Redis"| cache
    config -.->|"Conexão AMQP"| publisher
    config -.->|"Conexão AMQP"| consumer
```

O polling chama `JogoService.obterPlacarAtual` por jogo já exibido. O serviço retorna o cache quando disponível e consulta o PostgreSQL quando a chave está ausente, o JSON é inválido ou o Redis falha. Esse fallback **não repopula o cache**. A API REST de listagem e consulta acessa o repositório pelo serviço; não usa essa leitura de cache.

### Inventário de componentes e arquivos

Todos os pacotes Java abaixo estão sob `src/main/java/com/desafio/placar/`.

| Pacote ou recurso | Elementos | Responsabilidade |
| --- | --- | --- |
| `web` | `PlacarWebApplication`, `JogosPage`, `CriarJogoPanel`, `PlacarPanel` | Bootstrap Wicket/CDI; listagem e filtro; criação; edição de placar e encerramento |
| `src/main/resources/com/desafio/placar/web/` | `JogosPage.html`, `CriarJogoPanel.html`, `PlacarPanel.html`, `placar.css` | Markup e estilo dos componentes Wicket |
| `api` | `JogoResource` | Recebe requisições, valida presença de dados e converte `Jogo` em `JogoResponse` |
| `api.dto` | `CriarJogoRequest`, `AtualizarPlacarRequest`, `AtualizarStatusRequest`, `JogoResponse`, `ErroResponse` | Contratos de entrada e saída |
| `api.mapper` | `EntradaInvalidaExceptionMapper`, `NaoEncontradoExceptionMapper`, `JogoEncerradoExceptionMapper`, `ErroGenericoExceptionMapper` | Converte falhas em HTTP 400, 404, 409 e tratamento de erros genéricos |
| `service` | `JogoService` | Valida regras e coordena repositório, leitura do cache e emissão do evento CDI |
| `domain` | `Jogo`, `Status` | Entidade JPA e estados `EM_ANDAMENTO` / `ENCERRADO` |
| `domain.excecao` | `EntradaInvalidaException`, `NaoEncontradoException`, `JogoEncerradoException` | Erros de entrada, ausência de jogo e restrição de alteração |
| `persistence` | `JogoRepository` | `persist`, `merge`, busca por ID, listagem total e filtro por status |
| `cache` | `PlacarCache`, `PlacarAtual` | Adaptador Redis e record com os dois placares |
| `messaging` | `PlacarAtualizadoEvent`, `PlacarAtualizadoObserver` | Evento CDI interno e reação transacional após commit |
| `messaging` | `EventoPlacar`, `PlacarEventPublisher`, `PlacarEventConsumer` | Mensagem externa JSON, publicação e consumo AMQP |
| `config` | `JaxRsApplication` | Ativa JAX-RS em `/api` e declara metadados OpenAPI |
| `config` | `ConfiguracaoExterna`, `ConfiguracaoRedis`, `ConfiguracaoRabbitMq` | Resolve propriedade da JVM → variável de ambiente → padrão |
| `config` | `ConfiguracaoBancoDados` | Documenta a constante JNDI `jdbc/placar`; não cria o DataSource |
| `src/main/resources/META-INF/persistence.xml` | Unidade `placarPU` | JTA, recurso `jdbc/placar`, entidade `Jogo` e geração/extensão de schema pelo EclipseLink |
| `src/main/webapp/WEB-INF/web.xml` | `WicketFilter` em `/*` | Configuração Servlet da interface |
| `src/main/webapp/swagger/index.html` | Swagger UI | Documentação interativa, com assets WebJar e documento `/openapi` |

## 5. C4 — Código

O detalhamento de código é dividido em duas vistas para manter a leitura. Exibe as classes centrais e suas dependências; DTOs, mappers, configuração e recursos complementares estão inventariados acima. Operações são resumidas, sem reproduzir getters, setters ou construtores auxiliares.

### 5.1. Casos de uso, apresentação e persistência

```mermaid
classDiagram
    class PlacarWebApplication {
        +getHomePage() Class
        #init() void
    }
    class JogosPage
    class CriarJogoPanel
    class PlacarPanel
    class JogoResource {
        +criar(request) Response
        +listar(status) Response
        +buscarPorId(id) Response
        +atualizarPlacar(id, request) Response
        +atualizarStatus(id, request) Response
    }
    class JogoService {
        +criar(timeA, timeB, dataHoraPartida) Jogo
        +listar(filtroOpcional) List~Jogo~
        +buscarPorId(id) Jogo
        +encerrar(id) Jogo
        +atualizarPlacar(id, placarA, placarB) Jogo
        +obterPlacarAtual(id) PlacarAtual
    }
    class JogoRepository {
        -EntityManager em
        +salvar(jogo) Jogo
        +buscarPorId(id) Optional~Jogo~
        +listarTodos() List~Jogo~
        +listarPorStatus(status) List~Jogo~
    }
    class Jogo {
        -Long id
        -String timeA
        -String timeB
        -int placarA
        -int placarB
        -Status status
        -OffsetDateTime dataHoraPartida
    }
    class Status {
        <<enumeration>>
        EM_ANDAMENTO
        ENCERRADO
    }
    class PlacarCache
    class PlacarAtual {
        <<record>>
        int placarA
        int placarB
    }
    class PlacarAtualizadoEvent

    PlacarWebApplication ..> JogosPage : define página inicial
    JogosPage *-- CriarJogoPanel : contém
    JogosPage *-- PlacarPanel : contém por jogo
    JogosPage --> JogoService : lista e consulta placar
    CriarJogoPanel --> JogoService : cria
    PlacarPanel --> JogoService : atualiza e encerra
    JogoResource --> JogoService : delega casos de uso
    JogoService --> JogoRepository : consulta e persiste
    JogoService ..> Jogo : valida e altera
    JogoRepository ..> Jogo : mapeia via JPA
    Jogo --> Status : estado
    JogoService --> PlacarCache : lê placar
    JogoService ..> PlacarAtual : retorna
    JogoService ..> PlacarAtualizadoEvent : dispara via CDI
```

As regras ficam em `JogoService`: criação com campos obrigatórios, placar inicial 0 × 0 e status `EM_ANDAMENTO`; rejeição de placares negativos; bloqueio de alteração de placar de jogo encerrado; encerramento idempotente. `Jogo` mantém o estado e o mapeamento JPA. Criar, alterar placar e encerrar usam `@Transactional`; somente alterar placar dispara o evento CDI.

### 5.2. Eventos e cache

```mermaid
classDiagram
    class JogoService
    class PlacarAtualizadoEvent {
        <<record>>
        Long jogoId
        int placarA
        int placarB
    }
    class PlacarAtualizadoObserver {
        +observar(evento) void
    }
    class EventoPlacar {
        <<record>>
        Long jogoId
        int placarA
        int placarB
    }
    class PlacarEventPublisher {
        -Connection connection
        +publicar(evento) void
    }
    class PlacarEventConsumer {
        -Connection connection
        -Channel channel
        ~iniciar() void
        ~processar(body) void
        ~encerrar() void
    }
    class PlacarCache {
        -JedisPool pool
        +ler(jogoId) Optional~PlacarAtual~
        +invalidar(jogoId) void
        +atualizar(jogoId, placarA, placarB) void
    }
    class PlacarAtual {
        <<record>>
        int placarA
        int placarB
    }

    JogoService ..> PlacarAtualizadoEvent : Event.fire dentro da transação
    PlacarAtualizadoObserver ..> PlacarAtualizadoEvent : observa em AFTER_SUCCESS
    PlacarAtualizadoObserver --> PlacarCache : invalida
    PlacarAtualizadoObserver --> PlacarEventPublisher : solicita publicação
    PlacarAtualizadoObserver ..> EventoPlacar : cria mensagem externa
    PlacarEventPublisher ..> EventoPlacar : serializa em JSON
    PlacarEventConsumer ..> EventoPlacar : desserializa JSON recebido
    PlacarEventConsumer ..> PlacarEventPublisher : reutiliza constantes de topologia
    PlacarEventConsumer --> PlacarCache : grava placar recebido
    JogoService --> PlacarCache : leitura Redis primeiro
    PlacarCache ..> PlacarAtual : retorna valor em cache
```

`PlacarAtualizadoEvent` é um evento em memória no processo. `EventoPlacar` cruza a fronteira do processo via RabbitMQ, com corpo `{"jogoId":10,"placarA":2,"placarB":1}`. O consumidor não chama o publicador para processar mensagens: a dependência de código entre eles serve apenas para compartilhar as constantes de exchange, fila e routing key.

## 6. Vistas dinâmicas

### 6.1. Alteração de placar e propagação após commit

```mermaid
sequenceDiagram
    actor Operador
    participant Entrada as JogoResource ou PlacarPanel
    participant S as JogoService / JTA
    participant DB as JogoRepository / PostgreSQL
    participant CDI as Evento CDI / Payara
    participant O as PlacarAtualizadoObserver
    participant R as PlacarCache / Redis
    participant P as PlacarEventPublisher
    participant MQ as RabbitMQ
    participant C as PlacarEventConsumer

    Operador->>Entrada: Informar jogo e novo placar
    Entrada->>S: atualizarPlacar(id, placarA, placarB)
    S->>DB: Buscar jogo
    DB-->>S: Jogo
    Note over S: Validar placares, existência e status antes de salvar
    S->>DB: salvar(jogo)
    S->>CDI: fire(PlacarAtualizadoEvent), dentro da transação
    Note over S,CDI: O observer fica adiado até AFTER_SUCCESS
    S->>DB: Commit JTA coordenado pelo Payara
    DB-->>S: Transação confirmada
    CDI->>O: observar(evento), após commit bem-sucedido
    O->>R: invalidar(jogoId) / DEL
    Note over O,R: Falha é registrada e não impede publicação
    O->>P: publicar(EventoPlacar)
    P->>MQ: basicPublish / JSON
    Note over O,MQ: Falha é registrada e não desfaz o commit
    S-->>Entrada: Jogo atualizado
    Entrada-->>Operador: HTTP 200 na API ou página Wicket atualizada
    MQ-->>C: Entrega assíncrona com auto-ack
    C->>R: atualizar(jogoId, placarA, placarB) / SET
```

A entrega pelo broker pode ocorrer antes ou depois da resposta ao operador; o desenho apresenta uma ordem possível. O observer transacional não é um worker separado: usa `@Observes`, e a assincronia entre publicação e consumo ocorre no RabbitMQ. Se a transação sofrer rollback, o observer `AFTER_SUCCESS` não executa.

### 6.2. Consulta e polling da interface

```mermaid
sequenceDiagram
    participant B as Navegador
    participant W as JogosPage / Wicket
    participant S as JogoService
    participant R as PlacarCache / Redis
    participant DB as JogoRepository / PostgreSQL

    B->>W: Abrir página ou enviar filtro
    W->>S: listar(statusSelecionado)
    S->>DB: listarTodos() ou listarPorStatus(status)
    DB-->>W: Jogos completos, por meio do serviço
    W-->>B: HTML com jogos, status e formulários
    loop A cada 5 segundos, por jogo já exibido
        B->>W: Callback Ajax do pollingTrigger
        W->>S: obterPlacarAtual(id)
        S->>R: ler(id)
        alt Cache válido disponível
            R-->>S: PlacarAtual
        else Chave ausente ou falha de leitura/parse
            R-->>S: Optional.empty()
            S->>DB: buscarPorId(id)
            DB-->>S: Jogo persistido
            Note over S,R: Fallback não grava no Redis
        end
        S-->>W: PlacarAtual
        W-->>B: Atualizar rótulos de placar que mudaram
    end
```

O timer não recarrega a lista, não cria novas linhas, não atualiza status e não sobrescreve os campos de edição. Falhas por jogo preservam o último placar visível e permitem continuar os próximos ciclos. Mudanças de status ou novos jogos vindos de outro cliente exigem nova carga da listagem para aparecerem.

## 7. Implantação — Docker Compose

```mermaid
flowchart TB
    browser["Navegador / cliente REST"]
    dev["Desenvolvedor / operador técnico"]

    subgraph host["Host Docker — ambiente definido em compose.yaml"]
        subgraph rede["Rede bridge: placar"]
            subgraph appnode["Serviço app · imagem construída pelo Dockerfile"]
                payara["Payara Server 7.2026.2<br/>HTTP :8080 · administração :4848 · JDWP :5005"]
                war["desafio-placar.war<br/>Contexto /desafio-placar"]
                ds["PlacarPool → jdbc/placar<br/>Driver PostgreSQL no lib do domínio"]
                payara -->|"Hospeda"| war
                war -->|"JPA / JTA"| ds
            end
            pg[("Serviço postgres<br/>postgres:16-alpine · :5432")]
            redis[("Serviço redis<br/>redis:7-alpine · :6379")]
            rabbit["Serviço rabbitmq<br/>rabbitmq:3-management<br/>AMQP :5672 · gestão :15672"]
            ds -->|"JDBC"| pg
            war -->|"RESP"| redis
            war -->|"Publicação AMQP"| rabbit
            rabbit -->|"Consumo AMQP no mesmo WAR"| war
        end
        volume[("Volume nomeado postgres_data")]
        pg -->|"/var/lib/postgresql/data"| volume
    end

    browser -->|"HTTP · APP_PORT, padrão 8080 → 8080"| payara
    dev -->|"Admin · PAYARA_ADMIN_PORT, padrão 4848 → 4848"| payara
    dev -->|"Debug JDWP · 5005 → 5005"| payara
    dev -->|"Gestão HTTP · RABBITMQ_MANAGEMENT_PORT, padrão 15672 → 15672"| rabbit
```

As portas 5432, 6379 e 5672 ficam internas à rede Compose. O `compose.yaml` consultado também publica a porta de depuração 5005. Apenas PostgreSQL tem volume nomeado configurado; Redis e RabbitMQ não têm volumes de persistência declarados no Compose.

O Dockerfile usa um estágio Maven/JDK 21 para executar `mvn clean package`, incluindo os testes, e copia o WAR e o driver JDBC para a imagem Payara. O script [`01-gerar-datasource.sh`](../../docker/payara/init.d/01-gerar-datasource.sh) gera comandos de post-boot na ordem pool → recurso JNDI → deploy, respeitando recursos já existentes no domínio.

O serviço `app` depende dos healthchecks de PostgreSQL, Redis e RabbitMQ. Seu próprio healthcheck exige HTTP 200 em `/desafio-placar/api/jogos`. Redis e RabbitMQ são configurados pela aplicação via propriedades JVM ou variáveis de ambiente. As variáveis de banco alimentam o script de configuração do Payara; a aplicação recebe a conexão pelo DataSource JNDI.

| Interface | Caminho ou porta padrão |
| --- | --- |
| Interface Wicket | `http://localhost:8080/desafio-placar/` |
| API REST | `http://localhost:8080/desafio-placar/api/jogos` |
| Swagger UI | `http://localhost:8080/desafio-placar/swagger/` |
| Documento OpenAPI do Payara | `http://localhost:8080/openapi` — fora do contexto do WAR |
| Administração Payara | `http://localhost:4848` |
| Gestão RabbitMQ | `http://localhost:15672` |
| Depuração JVM | `localhost:5005` |

## 8. Contrato e regras relevantes para a arquitetura

Base: `/desafio-placar/api/jogos`. Os DTOs usam JSON.

| Operação | Endpoint | Caminho interno e comportamento |
| --- | --- | --- |
| Criar partida | `POST /` | `JogoResource → JogoService.criar → JogoRepository`; retorna 201 com placar 0 × 0 e status `EM_ANDAMENTO` |
| Listar partidas | `GET /?status=...` | Serviço → repositório; filtro opcional `EM_ANDAMENTO` ou `ENCERRADO`; retorna 200 |
| Buscar partida | `GET /{id}` | Serviço → repositório; retorna 200 ou 404 |
| Substituir placar | `PUT /{id}/placar` | Transação → evento CDI → pós-commit → Redis/RabbitMQ; retorna 200, 400, 404 ou 409 |
| Encerrar partida | `PUT /{id}/status` | Aceita somente `ENCERRADO`; transação sem evento de placar; operação idempotente; retorna 200, 400 ou 404 |

As operações da interface Wicket chamam os mesmos métodos do serviço diretamente via CDI. Não existe chamada HTTP interna entre Wicket e REST.

## 9. Consistência e limites da implementação

- **Fonte de verdade:** PostgreSQL mantém o jogo completo. Redis contém uma projeção dos dois placares e pode ficar atrasado.
- **Fronteira transacional:** somente a persistência do jogo participa da transação JTA. Invalidação de cache e publicação ocorrem depois do commit e falham de forma independente.
- **Entrega de mensagens:** exchange e fila são duráveis, mas a publicação usa propriedades `null`, sem marcar a mensagem como persistente e sem publisher confirms. O consumidor usa auto-ack; não há garantia de processamento bem-sucedido no Redis.
- **Recuperação:** não há Outbox, retry de negócio, dead-letter, deduplicação ou transação distribuída. O publisher tenta obter conexão no uso quando necessário. O consumer não possui agendador próprio de reconexão se falhar ao abrir a conexão inicial; a recuperação nativa do cliente não substitui esse primeiro estabelecimento.
- **Ordem e cache:** mensagens não carregam versão nem timestamp e a gravação Redis é um `SET` sem comparação. Não há proteção explícita contra um evento antigo sobrescrever um placar mais recente. Sem TTL ou reconciliação periódica, um cache válido porém desatualizado pode continuar sendo servido; o fallback só ocorre na ausência ou falha de leitura do cache.
- **Atualização visual:** o polling é de cinco segundos e atualiza apenas os rótulos dos placares dos jogos já carregados. Esse intervalo não constitui garantia de latência de ponta a ponta.
- **Acesso:** o código e a configuração inspecionados não definem autenticação, autorização por perfil ou terminação TLS. Os atores do C1 não implicam controle de permissões.

## 10. Rastreabilidade e verificação

| Fonte | Parte do modelo sustentada |
| --- | --- |
| [`pom.xml`](../../pom.xml) | WAR, Java e versões das dependências |
| [`compose.yaml`](../../compose.yaml) e [`Dockerfile`](../../Dockerfile) | Serviços, rede, volume, portas, healthchecks e build/deploy |
| [`JogoService.java`](../../src/main/java/com/desafio/placar/service/JogoService.java) | Casos de uso, transações, evento CDI e fallback de leitura |
| [`JogoResource.java`](../../src/main/java/com/desafio/placar/api/JogoResource.java) | Endpoints, validações HTTP e DTOs |
| [`JogosPage.java`](../../src/main/java/com/desafio/placar/web/JogosPage.java) | Polling, intervalo e atualização parcial da interface |
| [`JogoRepository.java`](../../src/main/java/com/desafio/placar/persistence/JogoRepository.java) e [`Jogo.java`](../../src/main/java/com/desafio/placar/domain/Jogo.java) | Consultas e modelo persistido |
| [`PlacarCache.java`](../../src/main/java/com/desafio/placar/cache/PlacarCache.java) | Chaves, payload, ausência de TTL e tratamento de falhas |
| [`PlacarAtualizadoObserver.java`](../../src/main/java/com/desafio/placar/messaging/PlacarAtualizadoObserver.java) | Fase `AFTER_SUCCESS` e independência entre invalidação e publicação |
| [`PlacarEventPublisher.java`](../../src/main/java/com/desafio/placar/messaging/PlacarEventPublisher.java) e [`PlacarEventConsumer.java`](../../src/main/java/com/desafio/placar/messaging/PlacarEventConsumer.java) | Topologia AMQP, conexões, dois consumidores e auto-ack |
| [`persistence.xml`](../../src/main/resources/META-INF/persistence.xml) | `placarPU`, JTA, `jdbc/placar` e schema |
| [`src/test/java/com/desafio/placar/`](../../src/test/java/com/desafio/placar/) | Testes unitários de serviço, API, observer, consumer e componentes Wicket, com fakes e WicketTester |

Este modelo descreve o conteúdo do repositório, sem comprovar disponibilidade de serviços em execução. Os testes unitários não representam um teste integrado de Payara, PostgreSQL, Redis e RabbitMQ. Para manter a documentação, revisar estes diagramas quando houver mudança de fronteira do WAR, componentes, payload de evento, estratégia de cache ou implantação.
