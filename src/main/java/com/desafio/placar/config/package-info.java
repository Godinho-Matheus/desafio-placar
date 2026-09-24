/**
 * Configuracao do Sistema.
 *
 * <p>Reune a ativacao do JAX-RS ({@link com.desafio.placar.config.JaxRsApplication})
 * e o ponto central de leitura de configuracao externa
 * ({@link com.desafio.placar.config.ConfiguracaoExterna}). A configuracao de
 * PostgreSQL, Redis e RabbitMQ sera integrada aqui nas tarefas seguintes da Spec,
 * sempre a partir do ambiente e sem credenciais no codigo-fonte.</p>
 */
package com.desafio.placar.config;
