# Requirements Document

## Introduction

O Sistema gerencia placares de jogos de futebol e atualiza esses placares em tempo real. O sistema expõe uma API REST sob o recurso `/jogos` que permite criar jogos, listar jogos, filtrar jogos por status, buscar um jogo por identificador, atualizar o placar de um jogo e encerrar um jogo.

Cada Jogo possui identificador gerado automaticamente, nome do time da casa (timeA), nome do time visitante (timeB), placar do time da casa (placarA, iniciado em 0), placar do time visitante (placarB, iniciado em 0), Status pertencente ao conjunto {EM_ANDAMENTO, ENCERRADO} e data e hora programada da partida (dataHoraPartida).

A regra de negócio principal é que um Jogo com Status ENCERRADO não pode ter o placar alterado.

Ao atualizar o placar de um Jogo com sucesso, o Sistema publica um Evento_Placar no RabbitMQ; um Consumidor processa o evento e atualiza no Redis o placar atual do Jogo. A Interface_Web construída com Apache Wicket permite operar os jogos e reflete as alterações de placar sem recarregamento manual da página.

A persistência é feita em PostgreSQL por meio de JPA, a documentação da API é publicada em OpenAPI/Swagger e a aplicação é executada no Payara. A estratégia de leitura do placar exibido (Redis ou PostgreSQL) e o mecanismo de atualização automática da Interface_Web são decisões de Design e não são fixados neste documento. As interfaces Angular e a documentação C4 Model são opcionais e estão fora do escopo obrigatório.

## Glossary

- **Sistema**: aplicação completa de gerenciamento de placares, composta por API REST, interface web, mensageria, cache de placar e banco de dados relacional.
- **Jogo**: registro de uma partida de futebol com identificador único, timeA, timeB, placarA, placarB, Status e dataHoraPartida.
- **timeA**: nome do primeiro time do Jogo.
- **timeB**: nome do segundo time do Jogo.
- **placarA**: quantidade de gols do timeA, representada por inteiro maior ou igual a 0.
- **placarB**: quantidade de gols do timeB, representada por inteiro maior ou igual a 0.
- **Status**: valor pertencente ao conjunto {EM_ANDAMENTO, ENCERRADO}.
- **dataHoraPartida**: data e hora programada da partida.
- **API_Jogos**: interface REST que expõe as operações sobre Jogos sob o recurso `/jogos`.
- **Evento_Placar**: evento publicado no RabbitMQ quando o placar de um Jogo é atualizado, contendo o identificador do Jogo, placarA e placarB.
- **Consumidor**: componente que consome o Evento_Placar do RabbitMQ e atualiza no Redis o placar atual do Jogo.
- **Interface_Web**: interface de usuário construída com Apache Wicket.

## Requirements

### Requisito 1: Criar jogo

**User Story:** Como operador de placar, quero criar um jogo informando os dois times e a data e hora programada, para que o acompanhamento da partida possa começar.

#### Critérios de Aceitação

1. WHEN a API_Jogos recebe requisição `POST /jogos` com timeA, timeB e dataHoraPartida informados, THE API_Jogos SHALL criar um Jogo com placarA igual a 0, placarB igual a 0, Status EM_ANDAMENTO e identificador gerado automaticamente e retornar HTTP 201 com o Jogo criado.
2. WHEN um Jogo é criado, THE Sistema SHALL persistir o Jogo no PostgreSQL com timeA, timeB, dataHoraPartida informados, placarA igual a 0, placarB igual a 0 e Status EM_ANDAMENTO.
3. IF a requisição de criação não informar timeA, não informar timeB ou não informar dataHoraPartida, THEN THE API_Jogos SHALL retornar HTTP 400 com mensagem que identifica o campo obrigatório ausente e não persistir Jogo no PostgreSQL.

### Requisito 2: Listar jogos e filtrar por status

**User Story:** Como torcedor, quero consultar a lista de jogos e filtrar por status, para acompanhar todos os jogos ou apenas os que estão em andamento ou encerrados.

#### Critérios de Aceitação

1. WHEN a API_Jogos recebe requisição `GET /jogos` sem o parâmetro de status (parâmetro ausente ou não informado), THE API_Jogos SHALL retornar HTTP 200 com a lista de todos os Jogos persistidos.
2. WHEN a API_Jogos recebe requisição `GET /jogos` com parâmetro de status igual a EM_ANDAMENTO, THE API_Jogos SHALL retornar HTTP 200 contendo somente os Jogos com Status EM_ANDAMENTO.
3. WHEN a API_Jogos recebe requisição `GET /jogos` com parâmetro de status igual a ENCERRADO, THE API_Jogos SHALL retornar HTTP 200 contendo somente os Jogos com Status ENCERRADO.
4. WHEN não existir Jogo que atenda à requisição de listagem, THE API_Jogos SHALL retornar HTTP 200 com lista vazia.
5. IF o parâmetro de status for informado na requisição `GET /jogos` com valor vazio, THEN THE API_Jogos SHALL retornar HTTP 400 com mensagem que informa os valores aceitos EM_ANDAMENTO e ENCERRADO e sem retornar lista de Jogos.
6. IF o parâmetro de status for informado com valor não vazio que não corresponda a EM_ANDAMENTO nem a ENCERRADO, THEN THE API_Jogos SHALL retornar HTTP 400 com mensagem que informa os valores aceitos EM_ANDAMENTO e ENCERRADO e sem retornar lista de Jogos.

### Requisito 3: Buscar jogo por identificador

**User Story:** Como torcedor, quero consultar um jogo específico pelo seu identificador, para ver os detalhes e o placar atual daquele jogo.

#### Critérios de Aceitação

1. WHEN a API_Jogos recebe requisição `GET /jogos/{id}` com identificador que corresponde a Jogo persistido, THE API_Jogos SHALL retornar HTTP 200 com o Jogo, incluindo identificador, timeA, timeB, placarA, placarB, Status e dataHoraPartida.
2. IF o identificador informado em `GET /jogos/{id}` não corresponder a Jogo persistido, THEN THE API_Jogos SHALL retornar HTTP 404 com mensagem que informa o identificador não encontrado.

### Requisito 4: Atualizar placar

**User Story:** Como operador de placar, quero atualizar o placar de um jogo em andamento, para que o resultado exibido corresponda à partida.

#### Critérios de Aceitação

1. WHILE um Jogo possuir Status EM_ANDAMENTO, WHEN a API_Jogos recebe requisição `PUT /jogos/{id}/placar` com placarA e placarB informados como inteiros maiores ou iguais a 0, THE API_Jogos SHALL substituir placarA e placarB do Jogo pelos valores informados e retornar HTTP 200 com o Jogo atualizado.
2. WHEN o placar de um Jogo é atualizado, THE Sistema SHALL persistir os novos valores de placarA e placarB no PostgreSQL.
3. WHEN a atualização de placar é confirmada no PostgreSQL, THE Sistema SHALL publicar um Evento_Placar no RabbitMQ conforme os critérios do Requisito 7.
4. IF a requisição de atualização de placar não informar placarA, não informar placarB, ou informar placarA ou placarB com valor não inteiro ou negativo, THEN THE API_Jogos SHALL retornar HTTP 400 com mensagem que identifica o campo inválido, manter o placar persistido anteriormente e não publicar Evento_Placar.
5. IF o identificador informado em `PUT /jogos/{id}/placar` não corresponder a Jogo persistido, THEN THE API_Jogos SHALL retornar HTTP 404 com mensagem que informa o identificador não encontrado, não alterar placar persistido e não publicar Evento_Placar.

### Requisito 5: Encerrar jogo

**User Story:** Como operador de placar, quero encerrar um jogo, para registrar o resultado final da partida.

#### Critérios de Aceitação

1. WHILE um Jogo possuir Status EM_ANDAMENTO, WHEN a API_Jogos recebe requisição `PUT /jogos/{id}/status` solicitando o encerramento desse Jogo, THE API_Jogos SHALL alterar o Status do Jogo de EM_ANDAMENTO para ENCERRADO, manter placarA e placarB inalterados e retornar HTTP 200 com o Jogo atualizado.
2. WHEN um Jogo é encerrado, THE Sistema SHALL persistir o Status ENCERRADO do Jogo no PostgreSQL mantendo placarA e placarB inalterados.
3. IF a API_Jogos receber requisição `PUT /jogos/{id}/status` de encerramento para um Jogo cujo Status persistido já é ENCERRADO, THEN THE API_Jogos SHALL retornar HTTP 200 com o Jogo, manter o Status ENCERRADO e manter placarA e placarB inalterados.
4. THE API_Jogos SHALL suportar por meio de `PUT /jogos/{id}/status` somente a transição de Status de EM_ANDAMENTO para ENCERRADO.
5. IF o identificador informado em `PUT /jogos/{id}/status` não corresponder a Jogo persistido, THEN THE API_Jogos SHALL retornar HTTP 404 com mensagem que informa o identificador não encontrado.

### Requisito 6: Proteger o placar de jogos encerrados

**User Story:** Como responsável pelo resultado oficial, quero que o placar de um jogo encerrado permaneça imutável, para preservar a integridade do resultado final.

#### Critérios de Aceitação

1. IF a API_Jogos receber requisição de atualização de placar de um Jogo cujo Status persistido é ENCERRADO, THEN THE API_Jogos SHALL recusar a operação, retornar HTTP 409 com mensagem que informa que o Jogo está encerrado, manter placarA e placarB inalterados e não publicar Evento_Placar.
2. WHILE um Jogo exibido na Interface_Web possuir Status ENCERRADO, THE Interface_Web SHALL não permitir a atualização do placar desse Jogo.

### Requisito 7: Fluxo assíncrono de atualização de placar

**User Story:** Como integrador, quero que cada atualização de placar gere um evento que atualiza o estado atual no Redis, para que o placar atual seja propagado de forma desacoplada.

#### Critérios de Aceitação

1. WHEN uma atualização de placar é confirmada no PostgreSQL, THE Sistema SHALL publicar no RabbitMQ um Evento_Placar contendo o identificador do Jogo, placarA e placarB.
2. WHEN o Consumidor recebe um Evento_Placar do RabbitMQ, THE Consumidor SHALL atualizar no Redis o placarA e o placarB do Jogo identificado no Evento_Placar.

### Requisito 8: Atualizar automaticamente a interface web

**User Story:** Como torcedor, quero ver as alterações de placar na tela sem recarregar a página, para acompanhar o jogo em tempo real.

#### Critérios de Aceitação

1. WHEN o placar de um Jogo exibido é alterado, THE Interface_Web SHALL exibir o novo placar sem recarregamento manual da página.

2. WHILE um usuário estiver visualizando a página de jogos, THE Interface_Web SHALL atualizar automaticamente os placares exibidos sem ação manual do usuário.

### Requisito 9: Operar jogos pela interface web

**User Story:** Como operador de placar, quero criar, atualizar, encerrar, listar e filtrar jogos pela interface web, para executar o trabalho sem usar ferramentas de API.

#### Critérios de Aceitação

1. WHEN o usuário confirma a criação de um Jogo na Interface_Web informando timeA, timeB e dataHoraPartida, THE Interface_Web SHALL solicitar a criação do Jogo ao Sistema e exibir o Jogo criado com placar 0 a 0 e Status EM_ANDAMENTO.
2. WHEN o usuário confirma a atualização de placar de um Jogo com Status EM_ANDAMENTO informando placarA e placarB, THE Interface_Web SHALL solicitar o registro do novo placar ao Sistema e exibir o placar informado no Jogo correspondente.
3. WHEN o usuário confirma o encerramento de um Jogo, THE Interface_Web SHALL solicitar o encerramento ao Sistema e exibir o Status ENCERRADO no Jogo correspondente.
4. WHEN o usuário seleciona um valor de status no filtro da Interface_Web, THE Interface_Web SHALL exibir somente os Jogos que possuem o Status selecionado.
5. IF o Sistema recusar uma operação solicitada pela Interface_Web, THEN THE Interface_Web SHALL exibir a mensagem de erro recebida e manter os valores de placar e de Status exibidos antes da operação.

### Requisito 10: Documentar a API

**User Story:** Como desenvolvedor integrador, quero consultar a documentação da API, para usar os recursos do sistema sem ler o código-fonte.

#### Critérios de Aceitação

1. THE Sistema SHALL publicar a documentação da API_Jogos em formato OpenAPI/Swagger descrevendo as operações `POST /jogos`, `GET /jogos`, `GET /jogos/{id}`, `PUT /jogos/{id}/placar` e `PUT /jogos/{id}/status`.
2. WHILE o Sistema estiver em execução no Payara, WHEN um desenvolvedor acessa o endereço da documentação, THE Sistema SHALL apresentar a interface Swagger UI listando as operações da API_Jogos.

### Requisito 11: Padronizar respostas de erro da API

**User Story:** Como desenvolvedor integrador, quero respostas de erro previsíveis, para tratar falhas de forma consistente.

#### Critérios de Aceitação

1. WHEN a API_Jogos responde a uma requisição com erro, THE API_Jogos SHALL retornar corpo de erro contendo uma mensagem descritiva do motivo da rejeição.
2. IF uma requisição à API_Jogos contiver entrada inválida ou campo obrigatório ausente, THEN THE API_Jogos SHALL retornar HTTP 400 com o corpo de erro definido no critério 1.
3. IF uma requisição à API_Jogos informar identificador de Jogo que não corresponda a Jogo persistido, THEN THE API_Jogos SHALL retornar HTTP 404 com o corpo de erro definido no critério 1.
4. IF uma requisição à API_Jogos solicitar a atualização de placar de um Jogo com Status ENCERRADO, THEN THE API_Jogos SHALL retornar HTTP 409 com o corpo de erro definido no critério 1.
5. IF ocorrer erro não previsto durante o processamento de uma requisição, THEN THE API_Jogos SHALL retornar HTTP 500 com o corpo de erro definido no critério 1.

### Requisito 12: Testes

**User Story:** Como avaliador do desafio, quero que o sistema possua testes automatizados, para verificar a corretude das funcionalidades.

#### Critérios de Aceitação

1. WHERE testes unitários com JUnit são fornecidos como diferencial, THE Sistema SHALL incluir testes unitários das principais regras de negócio dos Jogos.
2. WHERE testes de API são fornecidos como diferencial, THE Sistema SHALL incluir testes das operações da API_Jogos.
3. WHERE testes do fluxo assíncrono são fornecidos de forma opcional, THE Sistema SHALL incluir testes da publicação do Evento_Placar no RabbitMQ e da atualização do placar no Redis.

### Requisito 13: Configurar e executar o sistema

**User Story:** Como avaliador do desafio, quero executar o sistema com passos claros de build e configuração, para validar as funcionalidades no meu ambiente.

#### Critérios de Aceitação

1. WHEN o build do projeto é executado com Maven, THE Sistema SHALL concluir o build e produzir um artefato implantável no Payara.
2. THE Sistema SHALL permitir configurar a conexão com PostgreSQL, Redis e RabbitMQ sem que credenciais sensíveis sejam armazenadas diretamente no código-fonte.
3. WHEN o Sistema é iniciado e o PostgreSQL está disponível, THE Sistema SHALL disponibilizar o esquema de tabelas necessário aos Jogos por meio de JPA.
4. THE Sistema SHALL fornecer um README com instruções de execução com Maven, configuração do PostgreSQL, do Redis e do RabbitMQ, instruções do servidor de aplicação Payara e exemplos de requisições cURL para as operações da API_Jogos.
