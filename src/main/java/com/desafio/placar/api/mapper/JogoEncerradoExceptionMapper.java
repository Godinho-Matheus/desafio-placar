package com.desafio.placar.api.mapper;

import com.desafio.placar.api.dto.ErroResponse;
import com.desafio.placar.domain.excecao.JogoEncerradoException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapeia {@link JogoEncerradoException} para HTTP 409 (Requisitos 11.1, 11.4).
 *
 * <p>Produz o corpo de erro padronizado {@link ErroResponse} usando a propria
 * mensagem da excecao, que informa que o Jogo esta encerrado.</p>
 */
@Provider
public class JogoEncerradoExceptionMapper implements ExceptionMapper<JogoEncerradoException> {

    @Override
    public Response toResponse(JogoEncerradoException excecao) {
        return Response.status(Response.Status.CONFLICT)
                .entity(new ErroResponse(excecao.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
