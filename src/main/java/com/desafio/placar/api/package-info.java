/**
 * Camada REST (JAX-RS) do Sistema.
 *
 * <p>Reune o recurso {@link com.desafio.placar.api.JogoResource}, exposto sob
 * {@code /jogos}, que traduz requisicoes HTTP em chamadas ao
 * {@link com.desafio.placar.service.JogoService} e devolve JSON. As regras de
 * negocio permanecem no servico de aplicacao; o recurso cuida apenas de
 * preocupacoes HTTP e do mapeamento para os DTOs de
 * {@link com.desafio.placar.api.dto}. O tratamento padronizado de erros fica no
 * subpacote {@link com.desafio.placar.api.mapper}.</p>
 */
package com.desafio.placar.api;
