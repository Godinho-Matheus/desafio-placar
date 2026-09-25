package com.desafio.placar.api.mapper;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.desafio.placar.api.dto.ErroResponse;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Mapeia erros nao previstos para HTTP 500 (Requisitos 11.1, 11.5).
 *
 * <p>Captura qualquer {@link Exception} que nao tenha um mapper mais especifico.
 * As excecoes de dominio ({@code EntradaInvalidaException},
 * {@code NaoEncontradoException} e {@code JogoEncerradoException}) possuem mappers
 * dedicados e, por serem mais especificos, tem prioridade sobre este.</p>
 *
 * <p>Nao expoe stack traces nem detalhes internos ao cliente: registra o erro no
 * log do servidor (JDK logging) e devolve uma mensagem generica no corpo de erro
 * padronizado {@link ErroResponse}. As {@link WebApplicationException} (fluxos HTTP
 * padrao do JAX-RS, como 404 de rota ou 405) sao repassadas ao container e nao sao
 * convertidas em 500.</p>
 */
@Provider
public class ErroGenericoExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOGGER = Logger.getLogger(ErroGenericoExceptionMapper.class.getName());

    @Override
    public Response toResponse(Exception excecao) {
        if (excecao instanceof WebApplicationException webException) {
            return webException.getResponse();
        }

        LOGGER.log(Level.SEVERE, "Erro interno inesperado ao processar a requisicao.", excecao);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErroResponse("Erro interno inesperado ao processar a requisicao."))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
