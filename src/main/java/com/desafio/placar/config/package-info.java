/**
 * Configuracao do Sistema.
 *
 * <p>Reune a ativacao do JAX-RS ({@link com.desafio.placar.config.JaxRsApplication})
 * e o ponto central de leitura de configuracao externa
 * ({@link com.desafio.placar.config.ConfiguracaoExterna}), que resolve valores na
 * ordem propriedade de sistema da JVM &rarr; variavel de ambiente &rarr; valor
 * padrao. A conexao com o PostgreSQL e gerenciada pelo Payara (DataSource) e
 * referenciada apenas por JNDI na unidade de persistencia; esse nome JNDI e fixo
 * e fica documentado em {@link com.desafio.placar.config.ConfiguracaoBancoDados},
 * sem credenciais no codigo-fonte. A configuracao externa do Redis (host, porta e
 * senha opcional) fica centralizada em
 * {@link com.desafio.placar.config.ConfiguracaoRedis} e a do RabbitMQ (host, porta,
 * usuario, senha e virtual host) em
 * {@link com.desafio.placar.config.ConfiguracaoRabbitMq}. Essas configuracoes
 * podem ser fornecidas por propriedade de sistema da JVM ou variavel de ambiente
 * e, quando ausentes, podem usar defaults adequados ao desenvolvimento local: o
 * Redis nao possui senha default, enquanto o RabbitMQ usa {@code guest/guest}
 * apenas como default conhecido para desenvolvimento local. Nenhuma credencial
 * real de producao fica fixada no codigo-fonte (Requisito 13.2).</p>
 */
package com.desafio.placar.config;
