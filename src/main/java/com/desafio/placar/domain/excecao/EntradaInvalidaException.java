package com.desafio.placar.domain.excecao;

/**
 * Indica entrada invalida em uma operacao de dominio.
 *
 * <p>Situacoes tipicas: campo obrigatorio ausente, placar nao inteiro ou negativo,
 * status vazio ou invalido. Sera mapeada para HTTP 400 por um ExceptionMapper em
 * tarefa posterior da Spec. A mensagem descreve o motivo e alimenta o corpo de erro
 * {@code {"mensagem": ...}}.</p>
 */
public class EntradaInvalidaException extends RuntimeException {

    public EntradaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
