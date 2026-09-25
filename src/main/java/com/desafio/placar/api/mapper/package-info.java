/**
 * Tratamento padronizado de erros da Camada REST.
 *
 * <p>Reune os {@code ExceptionMapper} (JAX-RS) que traduzem as excecoes de dominio
 * e os erros inesperados em respostas HTTP com o corpo de erro padronizado
 * {@link com.desafio.placar.api.dto.ErroResponse} (Requisito 11): 400 para
 * {@link com.desafio.placar.domain.excecao.EntradaInvalidaException}, 404 para
 * {@link com.desafio.placar.domain.excecao.NaoEncontradoException}, 409 para
 * {@link com.desafio.placar.domain.excecao.JogoEncerradoException} e 500 para os
 * demais erros nao previstos.</p>
 */
package com.desafio.placar.api.mapper;
