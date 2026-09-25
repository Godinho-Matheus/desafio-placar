package com.desafio.placar.api.dto;

/**
 * Corpo de erro padronizado da API REST (Requisito 11).
 *
 * <p>Transporta uma unica mensagem descritiva do motivo da rejeicao, usada por
 * todas as respostas de erro (400, 404, 409, 500).</p>
 */
public record ErroResponse(String mensagem) {
}
