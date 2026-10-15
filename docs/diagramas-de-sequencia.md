# Diagramas de sequência

Fluxos principais do Desafio Placar, desenhados a partir do código atual (nomes reais de classes e métodos). Os caminhos HTTP são relativos a `http://localhost:8080/desafio-placar`.

Como ler os diagramas:

| Elemento | Significado |
|---|---|
| Linha cheia com seta | Chamada síncrona |
| Linha tracejada com seta | Retorno |
| Linha cheia com seta aberta | Mensagem assíncrona: quem envia não espera resposta |
| Linha tracejada terminando em X | Exceção propagada |
| `break` | Caminho de erro: o fluxo termina ali |
| `alt` / `opt` / `loop` / `par` | Alternativas, trecho opcional, repetição |
| Contêiner (CDI + JTA) | Proxy CDI, interceptor `@Transactional` e despacho de eventos CDI, fornecidos pelo Payara |

## 1. Visão geral: do placar atualizado até a tela

```mermaid
sequenceDiagram
    autonumber
    actor Op as Operador
    participant Nav as Navegador<br/>(JogosPage aberta)
    box Payara - WAR único
        participant App as JogoService<br/>+ observer
        participant Cons as PlacarEventConsumer
    end
    box Infraestrutura
        participant PG as PostgreSQL
        participant Redis
        participant MQ as RabbitMQ
    end

    Op->>App: atualizar o placar do jogo 1 para 2 x 1
    App->>PG: UPDATE jogo e COMMIT
    Note over App,PG: PostgreSQL é a fonte de verdade
    App->>Redis: DEL jogo:1:placar (observer AFTER_SUCCESS)
    App-)MQ: publica {"jogoId":1,"placarA":2,"placarB":1}
    App-->>Op: 200 (REST) ou página renderizada de novo (Wicket)

    opt polling cai entre o DEL e o SET do consumidor
        Nav->>App: polling AJAX (JogosPage chama obterPlacarAtual(1))
        App->>Redis: GET jogo:1:placar
        Redis-->>App: nil (MISS)
        App->>PG: busca o jogo 1 (fallback)
        PG-->>App: 2 x 1
        App-->>Nav: 2 x 1. Sem o DEL, viria o placar antigo do Redis
    end

    MQ-)Cons: entrega a mensagem
    Cons->>Redis: SET jogo:1:placar {"placarA":2,"placarB":1}

    Nav->>App: próximo polling, 5 s depois
    App->>Redis: GET jogo:1:placar
    Redis-->>App: {"placarA":2,"placarB":1} (HIT)
    App-->>Nav: 2 x 1, sem consultar o PostgreSQL
```

- O PostgreSQL é gravado e confirmado antes de qualquer integração. Redis e RabbitMQ só entram depois do commit.
- O `DEL` existe para a tela não ler o placar antigo do Redis entre o commit e o `SET` do consumidor (Requisito 7.5).
- Sem Redis, a leitura cai no PostgreSQL. Sem RabbitMQ, o cache só deixa de ser preenchido. Nenhum dado oficial se perde.

## 2. Atualizar placar pela API REST

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente REST
    box Payara - WAR único
        participant R as JogoResource
        participant TX as Contêiner<br/>(CDI + JTA)
        participant S as JogoService
        participant Repo as JogoRepository
        participant O as PlacarAtualizadoObserver
    end
    participant PG as PostgreSQL

    Cli->>R: PUT /api/jogos/{id}/placar<br/>{"placarA": 2, "placarB": 1}
    break corpo, placarA ou placarB nulos
        R-->>Cli: 400 {"mensagem": "..."} via EntradaInvalidaExceptionMapper
    end
    R->>TX: atualizarPlacar(id, 2, 1) pelo proxy CDI
    TX->>TX: @Transactional inicia a transação JTA
    TX->>S: atualizarPlacar(id, 2, 1)
    break placarA ou placarB negativo
        S--xR: EntradaInvalidaException (rollback)
        R-->>Cli: 400 via EntradaInvalidaExceptionMapper
    end
    S->>Repo: buscarPorId(id)
    Repo->>PG: em.find(Jogo.class, id)
    PG-->>Repo: jogo ou nenhum
    Repo-->>S: Optional de Jogo
    break jogo inexistente
        S--xR: NaoEncontradoException (rollback)
        R-->>Cli: 404 via NaoEncontradoExceptionMapper
    end
    break status ENCERRADO
        S--xR: JogoEncerradoException (rollback)
        R-->>Cli: 409 via JogoEncerradoExceptionMapper
    end
    S->>S: setPlacarA(2) e setPlacarB(1) na entidade gerenciada
    S->>Repo: salvar(jogo)
    Repo->>Repo: em.merge(jogo), o UPDATE só sai no flush
    Repo-->>S: jogo
    S->>TX: placarAtualizadoEvent.fire(PlacarAtualizadoEvent)
    TX-->>S: notificação adiada para AFTER_SUCCESS
    S-->>TX: retorna o jogo atualizado
    TX->>PG: flush (UPDATE jogo) e COMMIT
    alt commit confirmado
        PG-->>TX: ok
        TX->>O: observar(evento) na fase AFTER_SUCCESS
        Note over O: Invalida o Redis e publica no RabbitMQ (diagrama 3).<br/>Falhas vão só para o log.
        O-->>TX: retorna sem relançar exceção
        TX-->>R: jogo atualizado
        R-->>Cli: 200 JogoResponse
    else erro no flush ou no commit
        PG-->>TX: erro
        TX->>PG: ROLLBACK
        Note over TX,O: Observer AFTER_SUCCESS não é chamado:<br/>nada vai para o Redis nem para o RabbitMQ
        TX--xR: TransactionalException
        R-->>Cli: 500 via ErroGenericoExceptionMapper
    end
```

- Ordem das validações: campos nulos no `JogoResource` (400), placar negativo (400), jogo inexistente (404), jogo `ENCERRADO` (409). Em todos esses casos o evento nem chega a ser disparado.
- O `fire` acontece dentro da transação, mas o observer só roda depois do `COMMIT`. Se o commit falhar, nada é publicado. É por isso que se usa `AFTER_SUCCESS` em vez de publicar direto no método.
- O observer roda na mesma thread da requisição, antes do 200. A latência de Redis e RabbitMQ entra no tempo de resposta, mas falhas deles não mudam o status HTTP.

## 3. Depois do commit: invalidação, publicação e consumo

```mermaid
sequenceDiagram
    autonumber
    box Payara - WAR único
        participant TX as Contêiner<br/>(CDI + JTA)
        participant O as PlacarAtualizadoObserver
        participant C as PlacarCache
        participant P as PlacarEventPublisher
        participant Cons as PlacarEventConsumer
    end
    box Infraestrutura
        participant Redis
        participant MQ as RabbitMQ
    end

    Note over TX: COMMIT já confirmado no PostgreSQL
    TX->>O: observar(PlacarAtualizadoEvent) na fase AFTER_SUCCESS

    rect rgba(120, 160, 255, 0.12)
        Note right of O: Passo 1: invalidar o cache (try/catch próprio)
        O->>C: invalidar(jogoId)
        C->>Redis: DEL jogo:{id}:placar
        alt Redis disponível
            Redis-->>C: ok
            C-->>O: ok
        else Redis indisponível
            C--xO: exceção do Jedis (invalidar propaga)
            O->>O: log WARNING e segue para o passo 2
        end
    end

    rect rgba(120, 200, 140, 0.12)
        Note right of O: Passo 2: publicar o EventoPlacar (try/catch próprio)
        O->>P: publicar(EventoPlacar(jogoId, placarA, placarB))
        alt RabbitMQ disponível
            P->>MQ: garantirConexao() e createChannel()
            P->>MQ: exchangeDeclare, queueDeclare e queueBind (idempotentes)
            P-)MQ: basicPublish(placar.exchange, placar.atualizado, JSON)
            P->>MQ: fecha o Channel (try-with-resources)
            P-->>O: ok
        else IOException ou TimeoutException
            P--xO: RuntimeException
            O->>O: log WARNING
        end
    end

    O-->>TX: retorna sem relançar exceção
    Note over TX: Só agora o JogoResource devolve o 200 (diagrama 2)

    Note over Cons,MQ: Em paralelo, numa thread do cliente RabbitMQ.<br/>Pode acontecer antes ou depois do 200.
    MQ-)Cons: entrega a mensagem de placar.atualizado.queue (auto-ack)
    Cons->>Cons: processar(body): JSON para EventoPlacar
    alt JSON válido
        Cons->>C: atualizar(jogoId, placarA, placarB)
        C->>Redis: SET jogo:{id}:placar {"placarA":2,"placarB":1}
        Note over C,Redis: Se o Redis falhar, atualizar só registra log
    else mensagem malformada
        Cons->>Cons: log WARNING e descarta a mensagem
    end
```

Pontos de atenção:

- Os dois passos do observer são independentes, cada um com seu `try/catch`. No `PlacarCache`, só `invalidar` propaga falha; `ler` e `atualizar` apenas registram log.
- A entrega não é garantida, por escolha de escopo: não há publisher confirms, a mensagem é publicada sem propriedades (não persistente, embora a fila seja durável) e o consumo usa `auto-ack`. Uma perda deixa o cache vazio (a leitura vai ao PostgreSQL) ou, se a invalidação também falhou, desatualizado. O PostgreSQL nunca é afetado.
- Os 2 consumidores dividem o mesmo `Channel`. O cliente Java do RabbitMQ entrega as mensagens de um canal em ordem, uma de cada vez ([guia do cliente Java](https://www.rabbitmq.com/client-libraries/java-api-guide)). Na prática não há processamento paralelo, mas a ordem da fila é preservada.
- O publisher abre um `Channel` e redeclara a topologia a cada publicação. É simples e seguro entre threads, mas custa idas e voltas extras ao broker. O mesmo guia desaconselha abrir um canal por mensagem.
- Duas atualizações concorrentes do mesmo jogo podem ser publicadas fora de ordem. Sem versionamento nem TTL na chave, o Redis pode ficar com o valor anterior até a próxima atualização. É uma limitação aceita no design.

## 4. Atualização automática da tela (polling)

```mermaid
sequenceDiagram
    autonumber
    participant Nav as Navegador
    box Payara - WAR único
        participant Page as JogosPage
        participant S as JogoService
        participant C as PlacarCache
        participant Repo as JogoRepository
    end
    box Infraestrutura
        participant Redis
        participant PG as PostgreSQL
    end

    Note over Nav,Page: A página já foi renderizada com os dados completos do PostgreSQL (listar)
    loop a cada 5 s (INTERVALO_ATUALIZACAO)
        Nav->>Page: requisição AJAX do AjaxSelfUpdatingTimerBehavior (span pollingTrigger)
        Page->>Page: onPostProcessTarget chama atualizarPlacaresExibidos()
        loop para cada linha já exibida
            Page->>S: obterPlacarAtual(jogoId)
            S->>C: ler(jogoId)
            C->>Redis: GET jogo:{id}:placar
            alt cache HIT
                Redis-->>C: {"placarA":2,"placarB":1}
                C-->>S: Optional com PlacarAtual
                S-->>Page: PlacarAtual, sem consultar o PostgreSQL
            else cache MISS ou Redis indisponível
                Redis-->>C: nil ou falha de conexão
                C-->>S: Optional vazio (a falha só vai para o log)
                S->>Repo: buscarPorId(jogoId)
                Repo->>PG: em.find(Jogo.class, id)
                PG-->>Repo: jogo
                Repo-->>S: Optional de Jogo
                S-->>Page: new PlacarAtual(placarA, placarB)
            end
            opt placar diferente do exibido
                Page->>Page: atualiza o Model do rótulo e faz target.add(rótulo)
            end
        end
        Page-->>Nav: resposta AJAX só com os rótulos que mudaram
        Nav->>Nav: troca os spans no DOM, sem recarregar a página
    end
```

- O timer fica num `span` neutro (`pollingTrigger`), fora da tabela e dos formulários. Só os rótulos que mudaram entram na resposta AJAX, então o que o usuário está digitando não é apagado.
- A `JogosPage` não sabe se o valor veio do Redis ou do PostgreSQL. Essa decisão é do `JogoService.obterPlacarAtual`. Uma falha em uma linha só vai para o log e o último valor exibido é mantido.
- O polling atualiza apenas `placarA` e `placarB`. Status e jogos criados por outro cliente só aparecem na próxima renderização completa (filtro, criação ou ação na tela).
- Custo: uma leitura por jogo exibido a cada 5 s, por aba aberta.

## 5. Operações pela interface Wicket

```mermaid
sequenceDiagram
    autonumber
    actor U as Operador
    participant Nav as Navegador
    box Payara - WAR único
        participant Panel as PlacarPanel
        participant Page as JogosPage
        participant S as JogoService
    end
    participant PG as PostgreSQL

    Note over Nav,Panel: Jogo ENCERRADO: campos e botões já vêm com setEnabled(false)
    U->>Nav: digita 2 x 1 e clica em Atualizar
    Nav->>Panel: POST do atualizarPlacarForm (submit síncrono, sem AJAX)
    Panel->>Panel: validação do Wicket (campos obrigatórios e numéricos)
    break campo vazio ou não numérico
        Panel-->>Nav: mesma página com o erro de validação, sem chamar o serviço
    end
    Panel->>S: atualizarPlacar(jogoId, 2, 1), chamada Java direta, sem HTTP
    Note over S,PG: Mesmo fluxo do diagrama 2: validações, UPDATE, COMMIT e observer
    alt sucesso
        S-->>Panel: jogo atualizado
        Panel->>Page: setResponsePage(JogosPage.class), nova instância da página
    else EntradaInvalida, NaoEncontrado ou JogoEncerrado
        S--xPanel: exceção de domínio
        Panel->>Page: getPage().error(mensagem), mesma instância da página
    end
    Page->>S: listar(filtro) durante a renderização
    S->>PG: SELECT via JogoRepository
    PG-->>S: jogos
    S-->>Page: List de Jogo
    Page-->>Nav: HTML com o placar do PostgreSQL e o FeedbackPanel
```

- A interface chama o `JogoService` direto, na mesma JVM, sem passar pela API REST. Por isso as regras são as mesmas nos dois caminhos.
- Para jogo `ENCERRADO` os controles já vêm desabilitados (Requisito 6.2), mas a garantia real é o `JogoService`: se outro cliente encerrar o jogo com a página aberta, a tentativa recebe `JogoEncerradoException` e a mensagem aparece no `FeedbackPanel`.
- Criar (`CriarJogoPanel`, que combina `datetime-local` e offset em um `OffsetDateTime`) e Encerrar seguem o mesmo padrão: submit síncrono, `criar(...)` ou `encerrar(id)`, `setResponsePage` no sucesso e `error(...)` na falha.

## 6. Criar jogo pela API REST

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente REST
    box Payara - WAR único
        participant R as JogoResource
        participant TX as Contêiner<br/>(CDI + JTA)
        participant S as JogoService
        participant Repo as JogoRepository
    end
    participant PG as PostgreSQL

    Cli->>R: POST /api/jogos<br/>{"timeA": "Time A", "timeB": "Time B",<br/>"dataHoraPartida": "2026-09-27T20:00:00-03:00"}
    break corpo ausente
        R-->>Cli: 400 via EntradaInvalidaExceptionMapper
    end
    R->>TX: criar(timeA, timeB, dataHoraPartida)
    TX->>TX: @Transactional inicia a transação JTA
    TX->>S: criar(timeA, timeB, dataHoraPartida)
    break timeA ou timeB em branco, ou dataHoraPartida nula
        S--xR: EntradaInvalidaException com o nome do campo (rollback)
        R-->>Cli: 400 via EntradaInvalidaExceptionMapper
    end
    S->>S: new Jogo com placar 0 x 0 e status EM_ANDAMENTO
    S->>Repo: salvar(jogo)
    Repo->>Repo: em.persist(jogo), id ainda nulo
    Repo-->>S: jogo
    S-->>TX: jogo
    TX->>PG: flush (INSERT INTO jogo) e COMMIT
    PG-->>TX: id gerado pela coluna IDENTITY
    TX-->>R: jogo com id preenchido
    R-->>Cli: 201 Created com JogoResponse
```

- Com `GenerationType.IDENTITY`, o id é gerado pelo PostgreSQL no `INSERT`. O EclipseLink só envia o `INSERT` no flush, então o id fica disponível depois do commit, que é quando o `JogoResource` monta a resposta.

## 7. Encerrar jogo pela API REST

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente REST
    box Payara - WAR único
        participant R as JogoResource
        participant TX as Contêiner<br/>(CDI + JTA)
        participant S as JogoService
        participant Repo as JogoRepository
    end
    participant PG as PostgreSQL

    Cli->>R: PUT /api/jogos/{id}/status<br/>{"status": "ENCERRADO"}
    break status ausente, em branco ou diferente de "ENCERRADO"
        R-->>Cli: 400 (não existe reabertura de jogo)
    end
    R->>TX: encerrar(id)
    TX->>TX: @Transactional inicia a transação JTA
    TX->>S: encerrar(id)
    S->>Repo: buscarPorId(id)
    Repo->>PG: em.find(Jogo.class, id)
    PG-->>Repo: jogo ou nenhum
    Repo-->>S: Optional de Jogo
    break jogo inexistente
        S--xR: NaoEncontradoException (rollback)
        R-->>Cli: 404 via NaoEncontradoExceptionMapper
    end
    alt já está ENCERRADO
        S->>S: não altera nada (idempotente)
    else EM_ANDAMENTO
        S->>S: setStatus(ENCERRADO), placar intocado
        S->>Repo: salvar(jogo)
        Repo-->>S: jogo
    end
    S-->>TX: jogo
    TX->>PG: COMMIT (com UPDATE do status só se ele mudou)
    PG-->>TX: ok
    TX-->>R: jogo
    R-->>Cli: 200 JogoResponse com status ENCERRADO
    Note over R,PG: Nenhum evento é disparado: o Redis guarda só o placar, que não muda ao encerrar
```

- Única transição suportada: `EM_ANDAMENTO` para `ENCERRADO`. Repetir o pedido devolve 200 sem alterar nada (Requisito 5.3).

## 8. Listar e filtrar pela API REST

```mermaid
sequenceDiagram
    autonumber
    actor Cli as Cliente REST
    box Payara - WAR único
        participant R as JogoResource
        participant S as JogoService
        participant Repo as JogoRepository
    end
    participant PG as PostgreSQL

    Cli->>R: GET /api/jogos?status=EM_ANDAMENTO
    break status vazio ou diferente de EM_ANDAMENTO e ENCERRADO
        R-->>Cli: 400 com os valores aceitos
    end
    R->>R: converterStatus(status). Sem o parâmetro, o filtro fica null
    R->>S: listar(filtro)
    alt filtro null
        S->>Repo: listarTodos()
        Repo->>PG: SELECT j FROM Jogo j
    else filtro informado
        S->>Repo: listarPorStatus(status)
        Repo->>PG: SELECT j FROM Jogo j WHERE j.status = :status
    end
    PG-->>Repo: linhas
    Repo-->>S: List de Jogo
    S-->>R: List de Jogo
    R->>R: toResponse em cada jogo
    R-->>Cli: 200 com a lista de JogoResponse (pode ser vazia)
```

- `listar` e `buscarPorId` não são `@Transactional`: são leituras simples, sem transação.
- `GET /api/jogos/{id}` faz o mesmo caminho até `JogoRepository.buscarPorId` e devolve 404 quando o jogo não existe, como no diagrama 7.

## 9. Ciclo de vida das integrações

```mermaid
sequenceDiagram
    autonumber
    box Payara - WAR único
        participant Ctr as Payara<br/>(contêiner EJB/CDI)
        participant Cons as PlacarEventConsumer<br/>(@Singleton @Startup)
        participant P as PlacarEventPublisher<br/>(@ApplicationScoped)
        participant C as PlacarCache<br/>(@ApplicationScoped)
    end
    participant MQ as RabbitMQ

    Note over Ctr,Cons: Deploy do WAR: o Singleton com Startup é criado na hora
    Ctr->>Cons: cria a instância e chama iniciar() (@PostConstruct)
    Cons->>MQ: newConnection() com ConfiguracaoRabbitMq
    alt RabbitMQ disponível
        MQ-->>Cons: Connection
        Cons->>MQ: createChannel()
        Cons->>MQ: exchangeDeclare(placar.exchange, direct, durável)
        Cons->>MQ: queueDeclare(placar.atualizado.queue, durável)
        Cons->>MQ: queueBind(fila, exchange, placar.atualizado)
        loop 2 vezes (NUMERO_CONSUMIDORES)
            Cons->>MQ: basicConsume(fila, autoAck=true, processar)
        end
        Note over Cons,MQ: Connection e Channel ficam abertos enquanto a aplicação roda
    else RabbitMQ indisponível
        MQ--xCons: IOException ou TimeoutException
        Cons->>Cons: log WARNING. O consumidor não inicia e não tenta de novo
    end

    Note over Ctr,C: ApplicationScoped é preguiçoso: o bean nasce no primeiro uso
    Ctr->>P: primeiro publicar() dispara iniciar() (@PostConstruct)
    P->>MQ: newConnection(). Se falhar, tenta de novo no próximo publicar()
    Ctr->>C: primeiro ler(), invalidar() ou atualizar() dispara iniciar() (@PostConstruct)
    C->>C: cria o JedisPool (conexões com o Redis sob demanda)

    Note over Ctr,MQ: Undeploy ou desligamento
    Ctr->>Cons: encerrar() (@PreDestroy)
    Cons->>MQ: channel.close() e connection.close()
    Ctr->>P: encerrar() (@PreDestroy)
    P->>MQ: connection.close()
    Ctr->>C: encerrar() (@PreDestroy)
    C->>C: pool.close()
```

- O consumidor é EJB `@Singleton` + `@Startup` para começar a ouvir a fila já no deploy. Publisher e cache são `@ApplicationScoped`, criados no primeiro uso.
- Se o RabbitMQ estiver fora no deploy, o consumidor não inicia: a recuperação automática do cliente não vale para a primeira conexão ([guia do cliente Java](https://www.rabbitmq.com/client-libraries/java-api-guide)). No Docker Compose isso é evitado porque o Payara só sobe depois do healthcheck do RabbitMQ.
- Depois de conectado, quedas de rede são cobertas pela recuperação automática do cliente, que reabre conexão, canal e consumidores.
