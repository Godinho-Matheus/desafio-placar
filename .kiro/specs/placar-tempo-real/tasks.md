# Implementation Plan

## Overview

Este plano converte o design aprovado em tarefas de codificaÃ§Ã£o incrementais para um monÃ³lito Jakarta EE empacotado como um Ãºnico WAR no Payara. Cada tarefa constrÃ³i sobre as anteriores e termina integrando o cÃ³digo ao fluxo jÃ¡ existente, sem cÃ³digo Ã³rfÃ£o. A ordem segue as dependÃªncias do design: primeiro estrutura e configuraÃ§Ã£o, depois domÃ­nio/persistÃªncia, regras de negÃ³cio no `JogoService`, API REST, documentaÃ§Ã£o, cache Redis, evento CDI e publisher RabbitMQ seguidos do observer AFTER_SUCCESS (`PlacarAtualizadoEvent` â†’ `PlacarEventPublisher` â†’ `PlacarAtualizadoObserver` â†’ `PlacarEventConsumer`), consumer, interface Wicket (bootstrap antes das pÃ¡ginas/painÃ©is), polling automÃ¡tico e, por fim, configuraÃ§Ã£o/execuÃ§Ã£o. Os testes com JUnit sÃ£o diferenciais/opcionais (Requisito 12), marcados com `*`, e nenhuma tarefa de implementaÃ§Ã£o principal depende deles.

## Tasks

- [x] 1. Estrutura inicial do projeto Maven/Jakarta EE e configuraÃ§Ãµes
  - [x] 1.1 Configurar o `pom.xml` e o pacote base
    - Criar `pom.xml` com empacotamento `war` e build via Maven.
    - Declarar dependÃªncias: Jakarta EE API (JAX-RS, JPA, CDI), Apache Wicket, o mÃ³dulo de integraÃ§Ã£o Wicket-CDI (para que as pÃ¡ginas/componentes Wicket acessem beans CDI, como `JogoService`, no mesmo WAR), driver PostgreSQL, cliente RabbitMQ, cliente Redis, OpenAPI/Swagger e JUnit (escopo de teste). NÃ£o introduzir Spring.
    - As versÃµes concretas devem ser mutuamente compatÃ­veis entre Java, Jakarta EE, Payara e Apache Wicket, conforme a stack definida no Design; exigir compatibilidade sem fixar nÃºmeros de versÃ£o especÃ­ficos nem inventar uma stack diferente.
    - Criar o pacote base `com.desafio.placar` e a estrutura de diretÃ³rios `src/main/java`, `src/main/resources`, `src/main/webapp` e `src/test/java`.
    - _Requisitos: 13.1, 13.2_
  - [x] 1.2 Ativar JAX-RS e criar o esqueleto de configuraÃ§Ã£o externa
    - Criar a classe de ativaÃ§Ã£o JAX-RS com `@ApplicationPath` no pacote `config`.
    - Criar o esqueleto do pacote `config` para as configuraÃ§Ãµes externas (leitura de propriedades/variÃ¡veis de ambiente, sem credenciais no cÃ³digo).
    - _Requisitos: 13.1, 13.2_

- [ ] 2. Modelo de domÃ­nio e persistÃªncia JPA/PostgreSQL
  - [x] 2.1 Criar o enum `Status` e a entidade JPA `Jogo`
    - Criar o enum `Status` com `EM_ANDAMENTO` e `ENCERRADO` no pacote `domain`.
    - Criar a entidade `Jogo` (`id` identity `Long`, `timeA`, `timeB`, `placarA`/`placarB` default 0, `status` como `EnumType.STRING`, `dataHoraPartida`) no pacote `domain`.
    - _Requisitos: 1.2, 3.1, 13.3_
  - [x] 2.2 Criar as exceÃ§Ãµes de domÃ­nio
    - Criar `EntradaInvalidaException`, `NaoEncontradoException` e `JogoEncerradoException` no pacote `domain.excecao`.
    - _Requisitos: 1.3, 3.2, 4.4, 4.5, 5.5, 6.1_
  - [x] 2.3 Implementar o `JogoRepository` (JPA/PostgreSQL)
    - Criar `JogoRepository` no pacote `persistence` com `salvar`, `buscarPorId`, `listarTodos` e `listarPorStatus` usando `EntityManager`/JPA.
    - _Requisitos: 1.2, 2.1, 2.2, 2.3, 3.1, 4.2, 5.2, 13.3_
  - [ ] 2.4 Configurar `persistence.xml` e a conexÃ£o PostgreSQL
    - Criar `persistence.xml` com a unidade de persistÃªncia e a geraÃ§Ã£o de esquema por JPA na inicializaÃ§Ã£o.
    - Externalizar a configuraÃ§Ã£o da conexÃ£o PostgreSQL (sem credenciais no cÃ³digo), integrada ao pacote `config`.
    - _Requisitos: 13.2, 13.3_

- [ ] 3. Regras de negÃ³cio e `JogoService` (parte sÃ­ncrona)
  - [ ] 3.1 Implementar criaÃ§Ã£o, listagem, busca e encerramento no `JogoService`
    - Criar `JogoService` no pacote `service`.
    - `criar`: validar obrigatÃ³rios (timeA, timeB, dataHoraPartida), iniciar placar 0x0 e status EM_ANDAMENTO, persistindo via `JogoRepository`.
    - `listar(filtroOpcional)`: null retorna todos; filtro por status retorna somente os correspondentes.
    - `buscarPorId`: lanÃ§ar `NaoEncontradoException` (404) quando nÃ£o existir.
    - `encerrar`: transiÃ§Ã£o EM_ANDAMENTO â†’ ENCERRADO idempotente, preservando placarA/placarB.
    - _Requisitos: 1.1, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 5.1, 5.2, 5.3, 5.4_
  - [ ] 3.2 Implementar `atualizarPlacar` transacional no `JogoService`
    - MÃ©todo `@Transactional`: validar placarA/placarB inteiros `>= 0` (400); 404 se inexistente; 409 se ENCERRADO; persistir o novo placar no PostgreSQL.
    - Deixar o ponto de disparo do evento CDI reservado (o disparo Ã© adicionado na tarefa 7.1).
    - _Requisitos: 4.1, 4.2, 4.4, 4.5, 6.1_

- [ ] 4. API REST (JAX-RS) e tratamento de erros
  - [ ] 4.1 Criar os DTOs de request/response
    - Criar `CriarJogoRequest`, `AtualizarPlacarRequest` (usando `Integer` para distinguir ausente de 0), `AtualizarStatusRequest`, `JogoResponse` e `ErroResponse` no pacote `api.dto`.
    - _Requisitos: 1.1, 4.1, 5.1, 11.1_
  - [ ] 4.2 Implementar o `JogoResource` com os cinco endpoints
    - `POST /jogos` (201; 400 campo obrigatÃ³rio ausente).
    - `GET /jogos` com filtro `status` opcional (200; 400 para status vazio ou invÃ¡lido com mensagem informando EM_ANDAMENTO e ENCERRADO).
    - `GET /jogos/{id}` (200; 404).
    - `PUT /jogos/{id}/placar` (200; 400; 404; 409).
    - `PUT /jogos/{id}/status` com corpo `{"status":"ENCERRADO"}` (200 transiÃ§Ã£o; 200 idempotente; 400 para qualquer outro valor).
    - Delegar toda a lÃ³gica ao `JogoService`.
    - _Requisitos: 1.1, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 4.1, 4.4, 4.5, 5.1, 5.3, 5.4, 5.5, 6.1_
  - [ ] 4.3 Implementar os ExceptionMappers padronizados
    - Criar mappers no pacote `api.mapper` para 400 (`EntradaInvalidaException`), 404 (`NaoEncontradoException`), 409 (`JogoEncerradoException`) e um mapper genÃ©rico 500.
    - Todos devem produzir o corpo `{"mensagem": ...}` com descriÃ§Ã£o do motivo.
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 5. DocumentaÃ§Ã£o OpenAPI/Swagger
  - [ ] 5.1 Anotar os endpoints com OpenAPI e expor a Swagger UI
    - Adicionar anotaÃ§Ãµes OpenAPI no `JogoResource` cobrindo os cinco endpoints.
    - Habilitar a exposiÃ§Ã£o da Swagger UI no Payara.
    - _Requisitos: 10.1, 10.2_

- [ ] 6. Redis e `PlacarCache`
  - [ ] 6.1 Implementar `PlacarAtual` e `PlacarCache` sobre Redis
    - Criar o record `PlacarAtual` (placarA, placarB) e a classe `PlacarCache` no pacote `cache` com `atualizar`, `ler` e `invalidar`, usando a chave `jogo:{id}:placar` e valor JSON `{"placarA","placarB"}`.
    - `ler(jogoId)` retorna `Optional` com o placar quando encontrado e vazio quando nÃ£o houver cache OU o Redis nÃ£o puder ser usado; registrar em log as falhas de integraÃ§Ã£o com Redis. Sem novos tipos de retorno, sem circuit breaker e sem retry.
    - Externalizar a configuraÃ§Ã£o do Redis (integrada ao pacote `config`).
    - _Requisitos: 7.2, 7.3, 7.4, 13.2_
  - [ ] 6.2 Integrar leitura de placar em `JogoService.obterPlacarAtual`
    - Implementar `obterPlacarAtual(jogoId)` no `JogoService`: chamar `PlacarCache.ler(jogoId)` e, se retornar vazio, buscar o placar no PostgreSQL. NÃ£o distinguir cache MISS de indisponibilidade do Redis.
    - _Requisitos: 7.3, 7.4_

- [ ] 7. Evento CDI, publicaÃ§Ã£o RabbitMQ e processamento AFTER_SUCCESS
  - [ ] 7.1 Criar `PlacarAtualizadoEvent` e disparÃ¡-lo em `atualizarPlacar`
    - Criar o evento CDI interno `PlacarAtualizadoEvent` (jogoId, placarA, placarB) no pacote `service`.
    - Disparar o evento via `Event.fire(...)` dentro da transaÃ§Ã£o de `JogoService.atualizarPlacar` apÃ³s a persistÃªncia.
    - _Requisitos: 4.3, 7.1_
  - [ ] 7.2 Implementar `EventoPlacar` e `PlacarEventPublisher`
    - Criar o record `EventoPlacar` (jogoId, placarA, placarB) no pacote `messaging` com serializaÃ§Ã£o JSON.
    - Criar `PlacarEventPublisher` publicando em `placar.exchange` (direct) com routing key `placar.atualizado`; declarar a topologia (exchange, queue `placar.atualizado.queue`, binding).
    - Externalizar a configuraÃ§Ã£o do RabbitMQ (integrada ao pacote `config`).
    - _Requisitos: 7.1, 13.2_
  - [ ] 7.3 Implementar o `PlacarAtualizadoObserver` (AFTER_SUCCESS)
    - Criar `PlacarAtualizadoObserver` no pacote `service` com `@Observes(during = TransactionPhase.AFTER_SUCCESS)`; depende de `PlacarCache` e `PlacarEventPublisher`.
    - Tentar invalidar o Redis (`PlacarCache.invalidar`) com tratamento de erro em log; independentemente, tentar publicar via `PlacarEventPublisher` com tratamento de erro em log; erros apenas em log, nÃ£o desfazer o commit.
    - _Requisitos: 7.1, 7.5_

- [ ] 8. Consumer RabbitMQ e atualizaÃ§Ã£o do Redis
  - [ ] 8.1 Implementar o `PlacarEventConsumer`
    - Criar `PlacarEventConsumer` no pacote `messaging` como bean `@Startup`/`@PreDestroy`: abrir conexÃ£o/canal, consumir `placar.atualizado.queue`, desserializar `EventoPlacar` e gravar placarA/placarB no Redis via `PlacarCache.atualizar`; liberar recursos no encerramento.
    - _Requisitos: 7.2_

- [ ] 9. Interface Apache Wicket (operaÃ§Ãµes)
  - [ ] 9.1 Configurar a aplicaÃ§Ã£o Apache Wicket (bootstrap)
    - Criar/configurar a `WebApplication` do Wicket e registrar/configurar o Wicket na aplicaÃ§Ã£o web (filtro/servlet no `web.xml` ou equivalente).
    - Habilitar o uso de beans CDI nas pÃ¡ginas/componentes Wicket (especialmente `JogoService`) via a integraÃ§Ã£o Wicket-CDI; integraÃ§Ã£o simples e compatÃ­vel com Jakarta EE/Payara.
    - Criar um esqueleto mÃ­nimo de `JogosPage` (pÃ¡gina Wicket vazia/placeholder com seu markup mÃ­nimo) apenas para que o projeto compile e a aplicaÃ§Ã£o Wicket tenha uma home page vÃ¡lida, e defini-la como pÃ¡gina inicial (home page).
    - Deixar explÃ­cito que este esqueleto NÃƒO implementa listagem, filtro, criaÃ§Ã£o, atualizaÃ§Ã£o de placar, encerramento nem polling â€” essas funcionalidades permanecem exclusivamente nas subtasks 9.2, 9.3, 9.4 e 10.1.
    - Objetivo: ao concluir a 9.1, o projeto continua compilÃ¡vel e a aplicaÃ§Ã£o Wicket tem uma home page vÃ¡lida.
    - _Requisitos: 8, 9_
  - [ ] 9.2 Criar a `JogosPage` com listagem e filtro
    - Criar `JogosPage` no pacote `web` listando jogos via `JogoService` (dados completos do PostgreSQL) e oferecendo filtro por status. Wicket chama `JogoService` diretamente (nunca a API REST).
    - _Requisitos: 9.4_
  - [ ] 9.3 Criar o `CriarJogoPanel`
    - Criar `CriarJogoPanel` no pacote `web` para criar jogo informando timeA, timeB e dataHoraPartida, exibindo o jogo criado com placar 0x0 e EM_ANDAMENTO.
    - _Requisitos: 9.1_
  - [ ] 9.4 Criar o `PlacarPanel` e a aÃ§Ã£o de encerrar com tratamento de erro
    - Criar `PlacarPanel` no pacote `web` para atualizar placar de jogos EM_ANDAMENTO, com o controle desabilitado para jogos ENCERRADO.
    - Adicionar a aÃ§Ã£o de encerrar jogo (exibindo status ENCERRADO apÃ³s sucesso).
    - Tratar erro exibindo a mensagem recebida (via `FeedbackPanel`) e mantendo os valores anteriores; todas as chamadas via `JogoService`.
    - _Requisitos: 6.2, 9.2, 9.3, 9.5_

- [ ] 10. Polling automÃ¡tico dos placares
  - [ ] 10.1 Adicionar `AjaxSelfUpdatingTimerBehavior` Ã  `JogosPage`
    - Registrar o `AjaxSelfUpdatingTimerBehavior` na `JogosPage` chamando `JogoService.obterPlacarAtual(jogoId)` com intervalo configurÃ¡vel (sem SLA), atualizando apenas os placares exibidos sem recarregamento manual.
    - O status faz parte do estado completo do Jogo no PostgreSQL e Ã© atualizado normalmente pelas operaÃ§Ãµes da interface (criaÃ§Ã£o, atualizaÃ§Ã£o de placar, encerramento), nÃ£o pelo polling.
    - _Requisitos: 8.1_

- [ ] 11. Testes (diferenciais/opcionais â€” JUnit)
  - [ ]* 11.1 Testes unitÃ¡rios das regras de negÃ³cio do `JogoService`
    - CriaÃ§Ã£o inicia 0x0/EM_ANDAMENTO; atualizaÃ§Ã£o persiste o novo placar; atualizaÃ§Ã£o dispara `PlacarAtualizadoEvent` com jogoId/placarA/placarB corretos (observando o evento CDI, sem publicar no RabbitMQ); jogo ENCERRADO rejeita alteraÃ§Ã£o; encerramento preserva placar; encerramento idempotente; filtro por status retorna os jogos corretos.
    - _Requisitos: 12.1_
  - [ ]* 11.2 Testes do observer e do consumer
    - Observer tenta invalidar e publicar mesmo com falha na invalidaÃ§Ã£o (cache/publisher falsos, verificando log); Consumer atualiza o Redis ao receber `EventoPlacar`.
    - _Requisitos: 12.3_
  - [ ]* 11.3 Testes da API REST
    - Exercitar contratos e cÃ³digos (201/200/400/404/409), o corpo `{"status":"ENCERRADO"}` do `PUT /status` e a idempotÃªncia do encerramento.
    - _Requisitos: 12.2_
  - [ ]* 11.4 Testes de UI com WicketTester
    - Controle de atualizaÃ§Ã£o desabilitado em jogo ENCERRADO; um ciclo do `AjaxSelfUpdatingTimerBehavior` rerenderiza o placar com o novo valor.
    - _Requisitos: 6.2, 8, 9_

- [ ] 12. Checkpoint â€” Garantir que a implementaÃ§Ã£o esteja integrada
  - Executar o build Maven; executar os testes existentes, se houver; verificar se os componentes implementados estÃ£o integrados; corrigir apenas problemas de integraÃ§Ã£o encontrados.

- [ ] 13. ConfiguraÃ§Ã£o, execuÃ§Ã£o e README
  - [ ] 13.1 Consolidar a configuraÃ§Ã£o externa e o build WAR
    - Consolidar a configuraÃ§Ã£o externa de PostgreSQL/Redis/RabbitMQ no pacote `config` (sem credenciais no cÃ³digo); garantir que o build Maven produza o WAR implantÃ¡vel no Payara.
    - _Requisitos: 13.1, 13.2_
  - [ ] 13.2 Escrever o README
    - Criar o README com instruÃ§Ãµes de build/execuÃ§Ã£o no Payara, configuraÃ§Ã£o das dependÃªncias (PostgreSQL, Redis, RabbitMQ) e exemplos cURL dos cinco endpoints.
    - _Requisitos: 13.4_

## Notes

- Tarefas marcadas com `*` sÃ£o diferenciais/opcionais (Requisito 12) e podem ser puladas; nenhuma tarefa de implementaÃ§Ã£o principal depende delas.
- Testes usam JUnit; nÃ£o hÃ¡ property-based testing, jqwik nem novas bibliotecas de teste.
- Cada tarefa referencia os requisitos especÃ­ficos para rastreabilidade.
- Os checkpoints garantem validaÃ§Ã£o incremental sem bloquear a implementaÃ§Ã£o principal.
- Arquitetura: monÃ³lito Jakarta EE, WAR Ãºnico no Payara, PostgreSQL como fonte de verdade, Redis como cache do placar, RabbitMQ para propagaÃ§Ã£o assÃ­ncrona, evento CDI interno + observer AFTER_SUCCESS, Wicket chamando `JogoService` diretamente e polling com `AjaxSelfUpdatingTimerBehavior`. Sem Outbox, CQRS, Event Sourcing, WebSocket, microserviÃ§os, autenticaÃ§Ã£o, Angular, C4 ou SLAs numÃ©ricos.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "2.4"] },
    { "id": 3, "tasks": ["3.1", "3.2"] },
    { "id": 4, "tasks": ["4.1", "4.3", "6.1"] },
    { "id": 5, "tasks": ["4.2", "6.2", "7.1"] },
    { "id": 6, "tasks": ["5.1", "7.2"] },
    { "id": 7, "tasks": ["7.3"] },
    { "id": 8, "tasks": ["8.1", "9.1"] },
    { "id": 9, "tasks": ["9.2", "9.3"] },
    { "id": 10, "tasks": ["9.4"] },
    { "id": 11, "tasks": ["10.1", "13.1"] },
    { "id": 12, "tasks": ["13.2", "11.1", "11.2", "11.3", "11.4"] }
  ]
}
```
