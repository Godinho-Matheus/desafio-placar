/**
 * Servico de aplicacao do Sistema.
 *
 * <p>Reune o {@link com.desafio.placar.service.JogoService}, que orquestra os casos
 * de uso de Jogo compartilhados pela Camada REST e pela Interface_Web. E o unico
 * ponto que concentra validacoes, filtro de listagem, busca por identificador e a
 * transicao de status, delegando a persistencia ao
 * {@link com.desafio.placar.persistence.JogoRepository} (PostgreSQL, fonte de
 * verdade). A leitura do placar atual (cache Redis com fallback), a atualizacao
 * transacional de placar e o disparo do evento CDI serao introduzidos em tarefas
 * posteriores da Spec.</p>
 */
package com.desafio.placar.service;
