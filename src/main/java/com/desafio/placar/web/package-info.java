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
 * <p>A atualizacao automatica dos placares via polling ja esta implementada: a
 * {@link com.desafio.placar.web.JogosPage} hospeda um
 * {@code AjaxSelfUpdatingTimerBehavior} que, em intervalo configuravel (sem SLA),
 * chama {@code JogoService.obterPlacarAtual(jogoId)} (Redis primeiro, com fallback
 * no PostgreSQL dentro do servico) e repinta somente os placares exibidos, sem
 * recarregamento manual, sem recriar a lista e sem tocar nos campos editaveis do
 * {@code PlacarPanel}. O status continua vindo do estado completo do Jogo no
 * PostgreSQL, atualizado pelas operacoes da interface, nao pelo polling.</p>
 */
package com.desafio.placar.web;
