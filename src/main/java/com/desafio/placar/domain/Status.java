package com.desafio.placar.domain;

/**
 * Situacao de uma partida.
 *
 * <p>Uma partida comeca {@link #EM_ANDAMENTO} e pode transitar para
 * {@link #ENCERRADO}. Nao existe operacao de reabrir uma partida encerrada.</p>
 */
public enum Status {
    EM_ANDAMENTO,
    ENCERRADO
}
