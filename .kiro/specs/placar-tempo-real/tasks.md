# Implementation Plan

## Overview

Este plano converte o design aprovado em tarefas de codificação incrementais para um monólito Jakarta EE empacotado como um único WAR no Payara. Cada tarefa constrói sobre as anteriores e termina integrando o código ao fluxo já existente, sem código órfão. A ordem segue as dependências do design: primeiro estrutura e configuração, depois domínio/persistência, regras de negócio no `JogoService`, API REST, documentação, cache Redis, evento CDI e publisher RabbitMQ seguidos do observer AFTER_SUCCESS (`PlacarAtualizadoEvent` → `PlacarEventPublisher` → `PlacarAtualizadoObserver` → `PlacarEventConsumer`), consumer, interface Wicket (bootstrap antes das páginas/painéis), polling automático e, por fim, configuração/execução. Os testes com JUnit são diferenciais/opcionais (Requisito 12), marcados com `*`, e nenhuma tarefa de implementação principal depende deles.

## Tasks

- [x] 1. Estrutura inicial do projeto Maven/Jakarta EE e configurações
  - [x] 1.1 Configurar o `pom.xml` e o pacote base
    - Criar `pom.xml` com empacotamento `war` e build via Maven.
    - Declarar dependências: Jakarta EE API (JAX-RS, JPA, CDI), Apache Wicket, o módulo de integração Wicket-CDI (para que as páginas/componentes Wicket acessem beans CDI, como `JogoService`, no mesmo WAR), driver PostgreSQL, cliente RabbitMQ, cliente Redis, OpenAPI/Swagger e JUnit (escopo de teste). Não introduzir Spring.
    - As versões concretas devem ser mutuamente compatíveis entre Java, Jakarta EE, Payara e Apache Wicket, conforme a stack definida no Design; exigir compatibilidade sem fixar números de versão específicos nem inventar uma stack diferente.
    - Criar o pacote base `com.desafio.placar` e a estrutura de diretórios `src/main/java`, `src/main/resources`, `src/main/webapp` e `src/test/java`.
    - _Requisitos: 13.1, 13.2_
  - [x] 1.2 Ativar JAX-RS e criar o esqueleto de configuração externa
    - Criar a classe de ativação JAX-RS com `@ApplicationPath` no pacote `config`.
    - Criar o esqueleto do pacote `config` para as configurações externas (leitura de propriedades/variáveis de ambiente, sem credenciais no código).
    - _Requisitos: 13.1, 13.2_

- [ ] 2. Modelo de domínio e persistência JPA/PostgreSQL
  - [ ] 2.1 Criar o enum `Status` e a entidade JPA `Jogo`
    - Criar o enum `Status` com `EM_ANDAMENTO` e `ENCERRADO` no pacote `domain`.
    - Criar a entidade `Jogo` (`id` identity `Long`, `timeA`, `timeB`, `placarA`/`placarB` default 0, `status` como `EnumType.STRING`, `dataHoraPartida`) no pacote `domain`.
    - _Requisitos: 1.2, 3.1, 13.3_
  - [ ] 2.2 Criar as exceções de domínio
    - Criar `EntradaInvalidaException`, `NaoEncontradoException` e `JogoEncerradoException` no pacote `domain.excecao`.
    - _Requisitos: 1.3, 3.2, 4.4, 4.5, 5.5, 6.1_
  - [ ] 2.3 Implementar o `JogoRepository` (JPA/PostgreSQL)
    - Criar `JogoRepository` no pacote `persistence` com `salvar`, `buscarPorId`, `listarTodos` e `listarPorStatus` usando `EntityManager`/JPA.
    - _Requisitos: 1.2, 2.1, 2.2, 2.3, 3.1, 4.2, 5.2, 13.3_
  - [ ] 2.4 Configurar `persistence.xml` e a conexão PostgreSQL
    - Criar `persistence.xml` com a unidade de persistência e a geração de esquema por JPA na inicialização.
    - Externalizar a configuração da conexão PostgreSQL (sem credenciais no código), integrada ao pacote `config`.
    - _Requisitos: 13.2, 13.3_

- [ ] 3. Regras de negócio e `JogoService` (parte síncrona)
  - [ ] 3.1 Implementar criação, listagem, busca e encerramento no `JogoService`
    - Criar `JogoService` no pacote `service`.
    - `criar`: validar obrigatórios (timeA, timeB, dataHoraPartida), iniciar placar 0x0 e status EM_ANDAMENTO, persistindo via `JogoRepository`.
    - `listar(filtroOpcional)`: null retorna todos; filtro por status retorna somente os correspondentes.
    - `buscarPorId`: lançar `NaoEncontradoException` (404) quando não existir.
    - `encerrar`: transição EM_ANDAMENTO → ENCERRADO idempotente, preservando placarA/placarB.
    - _Requisitos: 1.1, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 5.1, 5.2, 5.3, 5.4_
  - [ ] 3.2 Implementar `atualizarPlacar` transacional no `JogoService`
    - Método `@Transactional`: validar placarA/placarB inteiros `>= 0` (400); 404 se inexistente; 409 se ENCERRADO; persistir o novo placar no PostgreSQL.
    - Deixar o ponto de disparo do evento CDI reservado (o disparo é adicionado na tarefa 7.1).
    - _Requisitos: 4.1, 4.2, 4.4, 4.5, 6.1_

- [ ] 4. API REST (JAX-RS) e tratamento de erros
  - [ ] 4.1 Criar os DTOs de request/response
    - Criar `CriarJogoRequest`, `AtualizarPlacarRequest` (usando `Integer` para distinguir ausente de 0), `AtualizarStatusRequest`, `JogoResponse` e `ErroResponse` no pacote `api.dto`.
    - _Requisitos: 1.1, 4.1, 5.1, 11.1_
  - [ ] 4.2 Implementar o `JogoResource` com os cinco endpoints
    - `POST /jogos` (201; 400 campo obrigatório ausente).
    - `GET /jogos` com filtro `status` opcional (200; 400 para status vazio ou inválido com mensagem informando EM_ANDAMENTO e ENCERRADO).
    - `GET /jogos/{id}` (200; 404).
    - `PUT /jogos/{id}/placar` (200; 400; 404; 409).
    - `PUT /jogos/{id}/status` com corpo `{"status":"ENCERRADO"}` (200 transição; 200 idempotente; 400 para qualquer outro valor).
    - Delegar toda a lógica ao `JogoService`.
    - _Requisitos: 1.1, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 3.1, 3.2, 4.1, 4.4, 4.5, 5.1, 5.3, 5.4, 5.5, 6.1_
  - [ ] 4.3 Implementar os ExceptionMappers padronizados
    - Criar mappers no pacote `api.mapper` para 400 (`EntradaInvalidaException`), 404 (`NaoEncontradoException`), 409 (`JogoEncerradoException`) e um mapper genérico 500.
    - Todos devem produzir o corpo `{"mensagem": ...}` com descrição do motivo.
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5_

- [ ] 5. Documentação OpenAPI/Swagger
  - [ ] 5.1 Anotar os endpoints com OpenAPI e expor a Swagger UI
    - Adicionar anotações OpenAPI no `JogoResource` cobrindo os cinco endpoints.
    - Habilitar a exposição da Swagger UI no Payara.
    - _Requisitos: 10.1, 10.2_

- [ ] 6. Redis e `PlacarCache`
  - [ ] 6.1 Implementar `PlacarAtual` e `PlacarCache` sobre Redis
    - Criar o record `PlacarAtual` (placarA, placarB) e a classe `PlacarCache` no pacote `cache` com `atualizar`, `ler` e `invalidar`, usando a chave `jogo:{id}:placar` e valor JSON `{"placarA","placarB"}`.
    - `ler(jogoId)` retorna `Optional` com o placar quando encontrado e vazio quando não houver cache OU o Redis não puder ser usado; registrar em log as falhas de integração com Redis. Sem novos tipos de retorno, sem circuit breaker e sem retry.
    - Externalizar a configuração do Redis (integrada ao pacote `config`).
    - _Requisitos: 7.2, 7.3, 7.4, 13.2_
  - [ ] 6.2 Integrar leitura de placar em `JogoService.obterPlacarAtual`
    - Implementar `obterPlacarAtual(jogoId)` no `JogoService`: chamar `PlacarCache.ler(jogoId)` e, se retornar vazio, buscar o placar no PostgreSQL. Não distinguir cache MISS de indisponibilidade do Redis.
    - _Requisitos: 7.3, 7.4_

- [ ] 7. Evento CDI, publicação RabbitMQ e processamento AFTER_SUCCESS
  - [ ] 7.1 Criar `PlacarAtualizadoEvent` e dispará-lo em `atualizarPlacar`
    - Criar o evento CDI interno `PlacarAtualizadoEvent` (jogoId, placarA, placarB) no pacote `service`.
    - Disparar o evento via `Event.fire(...)` dentro da transação de `JogoService.atualizarPlacar` após a persistência.
    - _Requisitos: 4.3, 7.1_
  - [ ] 7.2 Implementar `EventoPlacar` e `PlacarEventPublisher`
    - Criar o record `EventoPlacar` (jogoId, placarA, placarB) no pacote `messaging` com serialização JSON.
    - Criar `PlacarEventPublisher` publicando em `placar.exchange` (direct) com routing key `placar.atualizado`; declarar a topologia (exchange, queue `placar.atualizado.queue`, binding).
    - Externalizar a configuração do RabbitMQ (integrada ao pacote `config`).
    - _Requisitos: 7.1, 13.2_
  - [ ] 7.3 Implementar o `PlacarAtualizadoObserver` (AFTER_SUCCESS)
    - Criar `PlacarAtualizadoObserver` no pacote `service` com `@Observes(during = TransactionPhase.AFTER_SUCCESS)`; depende de `PlacarCache` e `PlacarEventPublisher`.
    - Tentar invalidar o Redis (`PlacarCache.invalidar`) com tratamento de erro em log; independentemente, tentar publicar via `PlacarEventPublisher` com tratamento de erro em log; erros apenas em log, não desfazer o commit.
    - _Requisitos: 7.1, 7.5_

- [ ] 8. Consumer RabbitMQ e atualização do Redis
  - [ ] 8.1 Implementar o `PlacarEventConsumer`
    - Criar `PlacarEventConsumer` no pacote `messaging` como bean `@Startup`/`@PreDestroy`: abrir conexão/canal, consumir `placar.atualizado.queue`, desserializar `EventoPlacar` e gravar placarA/placarB no Redis via `PlacarCache.atualizar`; liberar recursos no encerramento.
    - _Requisitos: 7.2_

- [ ] 9. Interface Apache Wicket (operações)
  - [ ] 9.1 Configurar a aplicação Apache Wicket (bootstrap)
    - Criar/configurar a `WebApplication` do Wicket e registrar/configurar o Wicket na aplicação web (filtro/servlet no `web.xml` ou equivalente).
    - Habilitar o uso de beans CDI nas páginas/componentes Wicket (especialmente `JogoService`) via a integração Wicket-CDI; integração simples e compatível com Jakarta EE/Payara.
    - Criar um esqueleto mínimo de `JogosPage` (página Wicket vazia/placeholder com seu markup mínimo) apenas para que o projeto compile e a aplicação Wicket tenha uma home page válida, e defini-la como página inicial (home page).
    - Deixar explícito que este esqueleto NÃO implementa listagem, filtro, criação, atualização de placar, encerramento nem polling — essas funcionalidades permanecem exclusivamente nas subtasks 9.2, 9.3, 9.4 e 10.1.
    - Objetivo: ao concluir a 9.1, o projeto continua compilável e a aplicação Wicket tem uma home page válida.
    - _Requisitos: 8, 9_
  - [ ] 9.2 Criar a `JogosPage` com listagem e filtro
    - Criar `JogosPage` no pacote `web` listando jogos via `JogoService` (dados completos do PostgreSQL) e oferecendo filtro por status. Wicket chama `JogoService` diretamente (nunca a API REST).
    - _Requisitos: 9.4_
  - [ ] 9.3 Criar o `CriarJogoPanel`
    - Criar `CriarJogoPanel` no pacote `web` para criar jogo informando timeA, timeB e dataHoraPartida, exibindo o jogo criado com placar 0x0 e EM_ANDAMENTO.
    - _Requisitos: 9.1_
  - [ ] 9.4 Criar o `PlacarPanel` e a ação de encerrar com tratamento de erro
    - Criar `PlacarPanel` no pacote `web` para atualizar placar de jogos EM_ANDAMENTO, com o controle desabilitado para jogos ENCERRADO.
    - Adicionar a ação de encerrar jogo (exibindo status ENCERRADO após sucesso).
    - Tratar erro exibindo a mensagem recebida (via `FeedbackPanel`) e mantendo os valores anteriores; todas as chamadas via `JogoService`.
    - _Requisitos: 6.2, 9.2, 9.3, 9.5_

- [ ] 10. Polling automático dos placares
  - [ ] 10.1 Adicionar `AjaxSelfUpdatingTimerBehavior` à `JogosPage`
    - Registrar o `AjaxSelfUpdatingTimerBehavior` na `JogosPage` chamando `JogoService.obterPlacarAtual(jogoId)` com intervalo configurável (sem SLA), atualizando apenas os placares exibidos sem recarregamento manual.
    - O status faz parte do estado completo do Jogo no PostgreSQL e é atualizado normalmente pelas operações da interface (criação, atualização de placar, encerramento), não pelo polling.
    - _Requisitos: 8.1_

- [ ] 11. Testes (diferenciais/opcionais — JUnit)
  - [ ]* 11.1 Testes unitários das regras de negócio do `JogoService`
    - Criação inicia 0x0/EM_ANDAMENTO; atualização persiste o novo placar; atualização dispara `PlacarAtualizadoEvent` com jogoId/placarA/placarB corretos (observando o evento CDI, sem publicar no RabbitMQ); jogo ENCERRADO rejeita alteração; encerramento preserva placar; encerramento idempotente; filtro por status retorna os jogos corretos.
    - _Requisitos: 12.1_
  - [ ]* 11.2 Testes do observer e do consumer
    - Observer tenta invalidar e publicar mesmo com falha na invalidação (cache/publisher falsos, verificando log); Consumer atualiza o Redis ao receber `EventoPlacar`.
    - _Requisitos: 12.3_
  - [ ]* 11.3 Testes da API REST
    - Exercitar contratos e códigos (201/200/400/404/409), o corpo `{"status":"ENCERRADO"}` do `PUT /status` e a idempotência do encerramento.
    - _Requisitos: 12.2_
  - [ ]* 11.4 Testes de UI com WicketTester
    - Controle de atualização desabilitado em jogo ENCERRADO; um ciclo do `AjaxSelfUpdatingTimerBehavior` rerenderiza o placar com o novo valor.
    - _Requisitos: 6.2, 8, 9_

- [ ] 12. Checkpoint — Garantir que a implementação esteja integrada
  - Executar o build Maven; executar os testes existentes, se houver; verificar se os componentes implementados estão integrados; corrigir apenas problemas de integração encontrados.

- [ ] 13. Configuração, execução e README
  - [ ] 13.1 Consolidar a configuração externa e o build WAR
    - Consolidar a configuração externa de PostgreSQL/Redis/RabbitMQ no pacote `config` (sem credenciais no código); garantir que o build Maven produza o WAR implantável no Payara.
    - _Requisitos: 13.1, 13.2_
  - [ ] 13.2 Escrever o README
    - Criar o README com instruções de build/execução no Payara, configuração das dependências (PostgreSQL, Redis, RabbitMQ) e exemplos cURL dos cinco endpoints.
    - _Requisitos: 13.4_

## Notes

- Tarefas marcadas com `*` são diferenciais/opcionais (Requisito 12) e podem ser puladas; nenhuma tarefa de implementação principal depende delas.
- Testes usam JUnit; não há property-based testing, jqwik nem novas bibliotecas de teste.
- Cada tarefa referencia os requisitos específicos para rastreabilidade.
- Os checkpoints garantem validação incremental sem bloquear a implementação principal.
- Arquitetura: monólito Jakarta EE, WAR único no Payara, PostgreSQL como fonte de verdade, Redis como cache do placar, RabbitMQ para propagação assíncrona, evento CDI interno + observer AFTER_SUCCESS, Wicket chamando `JogoService` diretamente e polling com `AjaxSelfUpdatingTimerBehavior`. Sem Outbox, CQRS, Event Sourcing, WebSocket, microserviços, autenticação, Angular, C4 ou SLAs numéricos.

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
