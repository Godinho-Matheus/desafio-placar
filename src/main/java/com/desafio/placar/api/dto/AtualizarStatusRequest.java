package com.desafio.placar.api.dto;

/**
 * Corpo da requisicao de atualizacao de status (PUT /jogos/{id}/status).
 *
 * <p>O unico valor aceito e {@code "ENCERRADO"}. A validacao do valor e feita em
 * camada posterior.</p>
 */
public record AtualizarStatusRequest(String status) {
}
