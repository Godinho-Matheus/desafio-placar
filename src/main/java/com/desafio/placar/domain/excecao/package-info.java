/**
 * Excecoes de dominio do Sistema.
 *
 * <p>Reune as excecoes que expressam violacoes de regras de negocio e situacoes
 * de erro previstas: {@link com.desafio.placar.domain.excecao.EntradaInvalidaException},
 * {@link com.desafio.placar.domain.excecao.NaoEncontradoException} e
 * {@link com.desafio.placar.domain.excecao.JogoEncerradoException}. Sao excecoes de
 * dominio puras (nao dependem de JAX-RS); o mapeamento para codigos HTTP e o corpo
 * padronizado {@code {"mensagem": ...}} sera feito por ExceptionMappers em tarefa
 * posterior da Spec.</p>
 */
package com.desafio.placar.domain.excecao;
