package com.desafio.placar.api.mapper;

import com.desafio.placar.api.dto.ErroResponse;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapeia {@link EntradaInvalidaException} para HTTP 400 (Requisitos 11.1, 11.2).
 *
 * <p>Produz o corpo de erro padronizado {@link ErroResponse} usando a propria
 * mensagem da excecao, que descreve o campo ausente ou o valor invalido.</p>
 */
@Provider
public class EntradaInvalidaExceptionMapper implements ExceptionMapper<EntradaInvalidaException> {

    @Override
    public Response toResponse(EntradaInvalidaException excecao) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErroResponse(excecao.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
