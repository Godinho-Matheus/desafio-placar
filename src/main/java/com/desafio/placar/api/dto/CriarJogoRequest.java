package com.desafio.placar.api.dto;

import java.time.OffsetDateTime;

/**
 * Corpo da requisicao de criacao de jogo (POST /jogos).
 *
 * <p>Transporta os dados informados pelo cliente: nomes dos times e a data/hora
 * da partida. A validacao dos campos obrigatorios e feita em camada posterior.</p>
 */
public record CriarJogoRequest(String timeA, String timeB, OffsetDateTime dataHoraPartida) {
}
