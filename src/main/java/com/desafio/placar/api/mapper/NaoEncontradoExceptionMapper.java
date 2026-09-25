package com.desafio.placar.api.mapper;

import com.desafio.placar.api.dto.ErroResponse;
import com.desafio.placar.domain.excecao.NaoEncontradoException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapeia {@link NaoEncontradoException} para HTTP 404 (Requisitos 11.1, 11.3).
 *
 * <p>Produz o corpo de erro padronizado {@link ErroResponse} usando a propria
 * mensagem da excecao, que informa o identificador nao encontrado.</p>
 */
@Provider
public class NaoEncontradoExceptionMapper implements ExceptionMapper<NaoEncontradoException> {

    @Override
    public Response toResponse(NaoEncontradoException excecao) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErroResponse(excecao.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
