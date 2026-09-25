package com.desafio.placar.api.dto;

/**
 * Corpo da requisicao de atualizacao de placar (PUT /jogos/{id}/placar).
 *
 * <p>Usa {@link Integer} (e nao {@code int}) para distinguir "ausente/null" de 0,
 * permitindo retornar 400 quando um dos campos nao for informado. A validacao e
 * feita em camada posterior.</p>
 */
public record AtualizarPlacarRequest(Integer placarA, Integer placarB) {
}
