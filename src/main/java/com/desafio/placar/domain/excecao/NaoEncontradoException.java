package com.desafio.placar.domain.excecao;

/**
 * Indica que um identificador nao corresponde a nenhum jogo persistido.
 *
 * <p>Sera mapeada para HTTP 404 por um ExceptionMapper em tarefa posterior da Spec.
 * A mensagem descreve o motivo e alimenta o corpo de erro
 * {@code {"mensagem": ...}}.</p>
 */
public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
