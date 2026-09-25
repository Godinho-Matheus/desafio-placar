package com.desafio.placar.api.dto;

import java.time.OffsetDateTime;

/**
 * Resposta padrao de jogo exposta pela API REST.
 *
 * <p>Reflete o estado completo do jogo. O campo {@code status} e exposto como texto
 * (nome do enum de dominio), nao como o enum em si. O mapeamento a partir da
 * entidade de dominio e feito em camada posterior.</p>
 */
public record JogoResponse(
        Long id, String timeA, String timeB,
        int placarA, int placarB, String status, OffsetDateTime dataHoraPartida) {
}
