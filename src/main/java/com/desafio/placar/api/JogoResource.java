package com.desafio.placar.api;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

import com.desafio.placar.api.dto.AtualizarPlacarRequest;
import com.desafio.placar.api.dto.AtualizarStatusRequest;
import com.desafio.placar.api.dto.CriarJogoRequest;
import com.desafio.placar.api.dto.ErroResponse;
import com.desafio.placar.api.dto.JogoResponse;
import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;
import com.desafio.placar.service.JogoService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Recurso REST (JAX-RS) sob {@code /jogos}, usado por clientes REST externos.
 *
 * <p>Traduz as requisicoes HTTP em chamadas ao {@link JogoService} e mapeia o
 * {@link Jogo} resultante para {@link JogoResponse}. As regras de negocio
 * (validacao de placar negativo, existencia do Jogo, transicao de status e
 * idempotencia) permanecem no {@link JogoService}. Este recurso cuida apenas de
 * preocupacoes HTTP: guardas de corpo/campo nulo ou em branco, parse do valor de
 * status, delegacao e mapeamento da resposta.</p>
 *
 * <p>A Interface_Web nao passa por este recurso: ela chama o {@link JogoService}
 * diretamente. O tratamento padronizado de erros e feito pelos
 * {@code ExceptionMapper} do pacote {@code api.mapper}.</p>
 */
@RequestScoped
@Path("/jogos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class JogoResource {

    @Inject
    private JogoService jogoService;

    /**
     * Cria um novo Jogo.
     *
     * <p>{@code POST /jogos}. Delega a validacao dos campos obrigatorios ao
     * {@link JogoService}. Retorna HTTP 201 com o Jogo criado.</p>
     *
     * @param request corpo com timeA, timeB e dataHoraPartida
     * @return HTTP 201 com o {@link JogoResponse} criado
     * @throws EntradaInvalidaException se o corpo da requisicao estiver ausente
     */
    @POST
    @Operation(summary = "Criar jogo",
            description = "Cria um jogo com placar 0x0 e status EM_ANDAMENTO.")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Jogo criado.",
                    content = @Content(schema = @Schema(implementation = JogoResponse.class))),
            @APIResponse(responseCode = "400", description = "Campo obrigatorio ausente.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public Response criar(
            @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = CriarJogoRequest.class)))
            CriarJogoRequest request) {
        if (request == null) {
            throw new EntradaInvalidaException(
                    "O corpo da requisicao de criacao de Jogo nao foi informado.");
        }
        Jogo jogo = jogoService.criar(request.timeA(), request.timeB(), request.dataHoraPartida());
        return Response.status(Response.Status.CREATED).entity(toResponse(jogo)).build();
    }

    /**
     * Lista os Jogos, com filtro opcional por status.
     *
     * <p>{@code GET /jogos?status=}. Sem o parametro, lista todos os Jogos. Com o
     * parametro vazio/em branco ou com valor diferente de {@code EM_ANDAMENTO}
     * e {@code ENCERRADO}, retorna HTTP 400 informando os valores aceitos.</p>
     *
     * @param status filtro opcional de status
     * @return HTTP 200 com a lista de {@link JogoResponse}
     * @throws EntradaInvalidaException se o status informado for vazio ou invalido
     */
    @GET
    @Operation(summary = "Listar jogos",
            description = "Lista os jogos. Sem o parametro 'status' lista todos; "
                    + "com 'status' aceita apenas EM_ANDAMENTO ou ENCERRADO.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Lista de jogos.",
                    content = @Content(schema = @Schema(type = SchemaType.ARRAY,
                            implementation = JogoResponse.class))),
            @APIResponse(responseCode = "400", description = "Status vazio ou invalido.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public Response listar(
            @Parameter(description = "Filtro opcional de status. Valores aceitos: EM_ANDAMENTO, ENCERRADO.")
            @QueryParam("status") String status) {
        Status filtro = null;
        if (status != null) {
            if (status.isBlank()) {
                throw new EntradaInvalidaException(
                        "O parametro 'status' esta vazio. Valores aceitos: EM_ANDAMENTO e ENCERRADO.");
            }
            filtro = converterStatus(status);
        }

        List<JogoResponse> jogos = jogoService.listar(filtro).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return Response.ok(jogos).build();
    }

    /**
     * Busca um Jogo pelo identificador.
     *
     * <p>{@code GET /jogos/{id}}. Delega ao {@link JogoService}; a inexistencia do
     * Jogo (HTTP 404) e tratada pelo ExceptionMapper.</p>
     *
     * @param id identificador do Jogo
     * @return HTTP 200 com o {@link JogoResponse}
     */
    @GET
    @Path("/{id}")
    @Operation(summary = "Buscar jogo por id",
            description = "Retorna o jogo correspondente ao identificador informado.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Jogo encontrado.",
                    content = @Content(schema = @Schema(implementation = JogoResponse.class))),
            @APIResponse(responseCode = "404", description = "Jogo nao encontrado.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public Response buscarPorId(
            @Parameter(description = "Identificador do jogo.", required = true)
            @PathParam("id") Long id) {
        Jogo jogo = jogoService.buscarPorId(id);
        return Response.ok(toResponse(jogo)).build();
    }

    /**
     * Atualiza o placar de um Jogo.
     *
     * <p>{@code PUT /jogos/{id}/placar}. Valida a presenca do corpo e dos campos
     * {@code placarA}/{@code placarB} antes de encaminhar. A validacao de valores
     * negativos, a existencia (404) e a regra de jogo encerrado (409) permanecem
     * no {@link JogoService} e sao tratadas pelos ExceptionMappers.</p>
     *
     * @param id      identificador do Jogo
     * @param request corpo com placarA e placarB
     * @return HTTP 200 com o {@link JogoResponse} atualizado
     * @throws EntradaInvalidaException se o corpo ou algum campo obrigatorio estiver ausente
     */
    @PUT
    @Path("/{id}/placar")
    @Operation(summary = "Atualizar placar",
            description = "Substitui placarA e placarB de um jogo EM_ANDAMENTO. "
                    + "Um jogo ENCERRADO nao pode ter o placar alterado.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Placar atualizado.",
                    content = @Content(schema = @Schema(implementation = JogoResponse.class))),
            @APIResponse(responseCode = "400", description = "Entrada invalida.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @APIResponse(responseCode = "404", description = "Jogo nao encontrado.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @APIResponse(responseCode = "409", description = "Jogo encerrado.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public Response atualizarPlacar(
            @Parameter(description = "Identificador do jogo.", required = true)
            @PathParam("id") Long id,
            @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AtualizarPlacarRequest.class)))
            AtualizarPlacarRequest request) {
        if (request == null) {
            throw new EntradaInvalidaException(
                    "O corpo da requisicao de atualizacao de placar nao foi informado.");
        }
        if (request.placarA() == null) {
            throw new EntradaInvalidaException("O campo obrigatorio 'placarA' nao foi informado.");
        }
        if (request.placarB() == null) {
            throw new EntradaInvalidaException("O campo obrigatorio 'placarB' nao foi informado.");
        }
        Jogo jogo = jogoService.atualizarPlacar(id, request.placarA(), request.placarB());
        return Response.ok(toResponse(jogo)).build();
    }

    /**
     * Encerra um Jogo.
     *
     * <p>{@code PUT /jogos/{id}/status}. O unico valor aceito e {@code "ENCERRADO"};
     * qualquer outro valor, inclusive {@code "EM_ANDAMENTO"}, resulta em HTTP 400,
     * pois nao existe operacao de reabertura. Quando o valor e {@code "ENCERRADO"},
     * delega ao {@link JogoService#encerrar(Long)}, preservando o comportamento
     * idempotente do servico.</p>
     *
     * @param id      identificador do Jogo
     * @param request corpo com o status desejado
     * @return HTTP 200 com o {@link JogoResponse} atualizado
     * @throws EntradaInvalidaException se o corpo/status estiver ausente ou for diferente de ENCERRADO
     */
    @PUT
    @Path("/{id}/status")
    @Operation(summary = "Encerrar jogo",
            description = "Encerra um jogo. O unico valor aceito e \"ENCERRADO\"; qualquer outro valor "
                    + "resulta em HTTP 400, pois nao existe reabertura. Repetir \"ENCERRADO\" em um jogo "
                    + "ja encerrado retorna HTTP 200 (idempotente).")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Jogo encerrado (ou ja encerrado).",
                    content = @Content(schema = @Schema(implementation = JogoResponse.class))),
            @APIResponse(responseCode = "400", description = "Status invalido.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @APIResponse(responseCode = "404", description = "Jogo nao encontrado.",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public Response atualizarStatus(
            @Parameter(description = "Identificador do jogo.", required = true)
            @PathParam("id") Long id,
            @RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = AtualizarStatusRequest.class)))
            AtualizarStatusRequest request) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new EntradaInvalidaException(
                    "O campo 'status' nao foi informado. Unico valor suportado: ENCERRADO.");
        }
        if (!Status.ENCERRADO.name().equals(request.status())) {
            throw new EntradaInvalidaException(
                    "Transicao de status invalida. Unico valor suportado: ENCERRADO.");
        }
        Jogo jogo = jogoService.encerrar(id);
        return Response.ok(toResponse(jogo)).build();
    }

    /**
     * Converte o valor textual do filtro de status para o enum de dominio.
     *
     * @param status valor textual nao nulo e nao em branco
     * @return o {@link Status} correspondente
     * @throws EntradaInvalidaException se o valor nao corresponder a um status valido
     */
    private Status converterStatus(String status) {
        try {
            return Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new EntradaInvalidaException(
                    "Status invalido '" + status + "'. Valores aceitos: EM_ANDAMENTO e ENCERRADO.");
        }
    }

    /**
     * Mapeia a entidade de dominio {@link Jogo} para o DTO {@link JogoResponse}.
     *
     * @param jogo entidade de dominio
     * @return o DTO de resposta correspondente, com o status como texto
     */
    private JogoResponse toResponse(Jogo jogo) {
        return new JogoResponse(
                jogo.getId(),
                jogo.getTimeA(),
                jogo.getTimeB(),
                jogo.getPlacarA(),
                jogo.getPlacarB(),
                jogo.getStatus().name(),
                jogo.getDataHoraPartida());
    }
}
