/**
 * DTOs de request/response da API REST.
 *
 * <p>Reune os objetos de transferencia usados pela Camada REST para receber e
 * devolver JSON: {@link com.desafio.placar.api.dto.CriarJogoRequest},
 * {@link com.desafio.placar.api.dto.AtualizarPlacarRequest},
 * {@link com.desafio.placar.api.dto.AtualizarStatusRequest},
 * {@link com.desafio.placar.api.dto.JogoResponse} e o corpo de erro padronizado
 * {@link com.desafio.placar.api.dto.ErroResponse}. Sao records simples, sem regras
 * de negocio nem validacoes; o mapeamento a partir/para
 * {@link com.desafio.placar.domain.Jogo} e a validacao serao introduzidos em
 * tarefas posteriores da Spec.</p>
 */
package com.desafio.placar.api.dto;
