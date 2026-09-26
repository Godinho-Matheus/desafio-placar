/**
 * Interface_Web do Sistema (Apache Wicket).
 *
 * <p>Reune a aplicacao Wicket ({@link com.desafio.placar.web.PlacarWebApplication})
 * e as paginas/componentes que operam os Jogos. A Interface_Web roda no mesmo
 * WAR/JVM e chama o {@code JogoService} diretamente via CDI, nunca a API REST.
 * A integracao Wicket-CDI e habilitada no bootstrap da aplicacao.</p>
 *
 * <p>O pacote contem a aplicacao {@link com.desafio.placar.web.PlacarWebApplication},
 * a pagina inicial {@link com.desafio.placar.web.JogosPage} e os paineis
 * {@link com.desafio.placar.web.CriarJogoPanel} e
 * {@link com.desafio.placar.web.PlacarPanel}. A interface sincrona da Task 9
 * esta completa: listagem de Jogos, filtro por status, criacao de Jogo,
 * atualizacao manual de placar e encerramento, com um {@code FeedbackPanel} para
 * exibir mensagens ao usuario.</p>
 *
 * <p>Resta apenas a atualizacao automatica via polling
 * ({@code AjaxSelfUpdatingTimerBehavior}), prevista para a Task 10.</p>
 */
package com.desafio.placar.web;
