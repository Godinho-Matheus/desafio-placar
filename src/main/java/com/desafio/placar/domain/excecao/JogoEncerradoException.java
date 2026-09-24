package com.desafio.placar.domain.excecao;

/**
 * Indica tentativa de alterar o placar de um jogo ja ENCERRADO.
 *
 * <p>Reflete a regra de negocio de que uma partida encerrada nao pode ter seu
 * placar alterado. Sera mapeada para HTTP 409 por um ExceptionMapper em tarefa
 * posterior da Spec. A mensagem descreve o motivo e alimenta o corpo de erro
 * {@code {"mensagem": ...}}.</p>
 */
public class JogoEncerradoException extends RuntimeException {

    public JogoEncerradoException(String mensagem) {
        super(mensagem);
    }
}
