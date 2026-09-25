/**
 * Configuracao do Sistema.
 *
 * <p>Reune a ativacao do JAX-RS ({@link com.desafio.placar.config.JaxRsApplication})
 * e o ponto central de leitura de configuracao externa
 * ({@link com.desafio.placar.config.ConfiguracaoExterna}). A conexao com o
 * PostgreSQL e gerenciada pelo Payara (DataSource) e referenciada apenas por
 * JNDI na unidade de persistencia; esse nome JNDI e fixo e fica documentado em
 * {@link com.desafio.placar.config.ConfiguracaoBancoDados}, sem credenciais no
 * codigo-fonte. A configuracao externa do Redis (host, porta e senha opcional)
 * fica centralizada em {@link com.desafio.placar.config.ConfiguracaoRedis},
 * tambem sem valores sensiveis fixados no codigo. A configuracao de RabbitMQ
 * sera integrada aqui nas tarefas seguintes da Spec.</p>
 */
package com.desafio.placar.config;
