package com.desafio.placar.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.desafio.placar.api.dto.AtualizarPlacarRequest;
import com.desafio.placar.api.dto.AtualizarStatusRequest;
import com.desafio.placar.api.dto.CriarJogoRequest;
import com.desafio.placar.api.dto.ErroResponse;
import com.desafio.placar.api.dto.JogoResponse;
import com.desafio.placar.api.mapper.EntradaInvalidaExceptionMapper;
import com.desafio.placar.api.mapper.JogoEncerradoExceptionMapper;
import com.desafio.placar.api.mapper.NaoEncontradoExceptionMapper;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;
import com.desafio.placar.domain.excecao.JogoEncerradoException;
import com.desafio.placar.domain.excecao.NaoEncontradoException;
import com.desafio.placar.messaging.FakePlacarCache;
import com.desafio.placar.messaging.PlacarAtualizadoEvent;
import com.desafio.placar.service.JogoService;

import jakarta.ws.rs.core.Response;

/**
 * Testes unitarios do CONTRATO da camada REST ({@link JogoResource}).
 *
 * <p><strong>Escopo:</strong> estes testes exercitam o {@link JogoResource} chamando
 * seus metodos de endpoint diretamente (injecao de construtor, sem CDI e sem
 * reflexao), ligados ao {@link JogoService} <em>real</em> apoiado por fakes em
 * memoria ({@link FakeJogoRepositoryRest}, {@link NoOpEvent} e o publico
 * {@link FakePlacarCache}). As regras de negocio permanecem no {@link JogoService}
 * real — nenhuma regra e duplicada nas fakes, que apenas armazenam/leem.</p>
 *
 * <p><strong>NAO e um teste ponta-a-ponta HTTP.</strong> Nao ha servidor, roteamento
 * HTTP, serializacao JSON de rede, negociacao de conteudo, OpenAPI/Swagger nem
 * Wicket envolvidos. Verificam-se o codigo de status ({@link Response#getStatus()})
 * e a entidade ({@link Response#getEntity()}) que cada endpoint constroi, alem do
 * mapeamento das excecoes de dominio para 400/404/409 pelos {@code ExceptionMapper}.</p>
 *
 * <p>Como o {@link JogoResource} propaga as excecoes de dominio (em producao
 * capturadas pelos {@code ExceptionMapper} do container), os casos de erro seguem o
 * padrao: (1) {@code assertThrows} no metodo do recurso, (2) passar a excecao ao
 * mapper correspondente via {@code toResponse(ex)}, (3) afirmar o status, e (4)
 * quando relevante, afirmar que o corpo {@link ErroResponse} traz mensagem
 * significativa.</p>
 */
@DisplayName("JogoResource - contrato da camada REST")
class JogoResourceTest {

    private static final OffsetDateTime DATA_PARTIDA =
            OffsetDateTime.parse("2026-01-01T20:00:00Z");

    private JogoResource resource;

    private final EntradaInvalidaExceptionMapper mapper400 = new EntradaInvalidaExceptionMapper();
    private final NaoEncontradoExceptionMapper mapper404 = new NaoEncontradoExceptionMapper();
    private final JogoEncerradoExceptionMapper mapper409 = new JogoEncerradoExceptionMapper();

    @BeforeEach
    void setUp() {
        // Recurso REST -> JogoService real -> fakes em memoria (sem CDI, sem reflexao).
        FakeJogoRepositoryRest repositorio = new FakeJogoRepositoryRest();
        FakePlacarCache cache = new FakePlacarCache();
        NoOpEvent<PlacarAtualizadoEvent> evento = new NoOpEvent<>();
        JogoService service = new JogoService(repositorio, cache, evento);
        resource = new JogoResource(service);
    }

    /** Cria um jogo EM_ANDAMENTO via endpoint e retorna o id gerado. */
    private Long criarJogoEmAndamento(String timeA, String timeB) {
        Response response = resource.criar(new CriarJogoRequest(timeA, timeB, DATA_PARTIDA));
        return ((JogoResponse) response.getEntity()).id();
    }

    /** Cria e encerra um jogo, retornando o id de um jogo ENCERRADO. */
    private Long criarJogoEncerrado(String timeA, String timeB) {
        Long id = criarJogoEmAndamento(timeA, timeB);
        resource.atualizarStatus(id, new AtualizarStatusRequest("ENCERRADO"));
        return id;
    }

    @Nested
    @DisplayName("POST /jogos")
    class Criar {

        @Test
        @DisplayName("valido -> 201 com JogoResponse 0x0 EM_ANDAMENTO")
        void criarValido() {
            // Arrange
            CriarJogoRequest request = new CriarJogoRequest("Time A", "Time B", DATA_PARTIDA);

            // Act
            Response response = resource.criar(request);

            // Assert
            assertEquals(201, response.getStatus());
            JogoResponse corpo = assertInstanceOf(JogoResponse.class, response.getEntity());
            assertEquals(0, corpo.placarA());
            assertEquals(0, corpo.placarB());
            assertEquals("EM_ANDAMENTO", corpo.status());
            assertEquals("Time A", corpo.timeA());
            assertEquals("Time B", corpo.timeB());
        }
    }

    @Nested
    @DisplayName("GET /jogos")
    class Listar {

        @Test
        @DisplayName("sem filtro -> 200 com lista")
        void listarTodos() {
            // Arrange
            criarJogoEmAndamento("A", "B");

            // Act
            Response response = resource.listar(null);

            // Assert
            assertEquals(200, response.getStatus());
            List<?> corpo = assertInstanceOf(List.class, response.getEntity());
            assertEquals(1, corpo.size());
        }

        @Test
        @DisplayName("status EM_ANDAMENTO -> 200 com apenas o jogo EM_ANDAMENTO")
        void listarEmAndamento() {
            // Arrange
            Long idEmAndamento = criarJogoEmAndamento("A", "B");
            Long idEncerrado = criarJogoEncerrado("C", "D");

            // Act
            Response response = resource.listar("EM_ANDAMENTO");

            // Assert
            assertEquals(200, response.getStatus());
            List<?> corpo = assertInstanceOf(List.class, response.getEntity());
            assertEquals(1, corpo.size());
            JogoResponse item = assertInstanceOf(JogoResponse.class, corpo.get(0));
            assertEquals(idEmAndamento, item.id());
            assertEquals("EM_ANDAMENTO", item.status());
        }

        @Test
        @DisplayName("status ENCERRADO -> 200 com apenas o jogo ENCERRADO")
        void listarEncerrado() {
            // Arrange
            Long idEmAndamento = criarJogoEmAndamento("A", "B");
            Long idEncerrado = criarJogoEncerrado("C", "D");

            // Act
            Response response = resource.listar("ENCERRADO");

            // Assert
            assertEquals(200, response.getStatus());
            List<?> corpo = assertInstanceOf(List.class, response.getEntity());
            assertEquals(1, corpo.size());
            JogoResponse item = assertInstanceOf(JogoResponse.class, corpo.get(0));
            assertEquals(idEncerrado, item.id());
            assertEquals("ENCERRADO", item.status());
        }

        @Test
        @DisplayName("status em branco -> 400 com mensagem")
        void listarStatusEmBranco() {
            // Act
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> resource.listar(""));

            // Assert
            Response response = mapper400.toResponse(ex);
            assertEquals(400, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
        }

        @Test
        @DisplayName("status invalido -> 400 com mensagem sobre valores aceitos")
        void listarStatusInvalido() {
            // Act
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> resource.listar("XPTO"));

            // Assert
            Response response = mapper400.toResponse(ex);
            assertEquals(400, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
            assertTrue(erro.mensagem().contains("EM_ANDAMENTO"));
            assertTrue(erro.mensagem().contains("ENCERRADO"));
        }
    }

    @Nested
    @DisplayName("GET /jogos/{id}")
    class BuscarPorId {

        @Test
        @DisplayName("id existente -> 200 com JogoResponse")
        void buscarExistente() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            Response response = resource.buscarPorId(id);

            // Assert
            assertEquals(200, response.getStatus());
            JogoResponse corpo = assertInstanceOf(JogoResponse.class, response.getEntity());
            assertEquals(id, corpo.id());
        }

        @Test
        @DisplayName("id inexistente -> 404")
        void buscarInexistente() {
            // Act
            NaoEncontradoException ex = assertThrows(NaoEncontradoException.class,
                    () -> resource.buscarPorId(999L));

            // Assert
            Response response = mapper404.toResponse(ex);
            assertEquals(404, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
        }
    }

    @Nested
    @DisplayName("PUT /jogos/{id}/placar")
    class AtualizarPlacar {

        @Test
        @DisplayName("valido -> 200 com placar atualizado")
        void atualizarValido() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            Response response = resource.atualizarPlacar(id, new AtualizarPlacarRequest(2, 1));

            // Assert
            assertEquals(200, response.getStatus());
            JogoResponse corpo = assertInstanceOf(JogoResponse.class, response.getEntity());
            assertEquals(2, corpo.placarA());
            assertEquals(1, corpo.placarB());
        }

        @Test
        @DisplayName("campo ausente -> 400")
        void atualizarCampoAusente() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> resource.atualizarPlacar(id, new AtualizarPlacarRequest(null, 1)));

            // Assert
            Response response = mapper400.toResponse(ex);
            assertEquals(400, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
        }

        @Test
        @DisplayName("placar negativo -> 400")
        void atualizarNegativo() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> resource.atualizarPlacar(id, new AtualizarPlacarRequest(-1, 0)));

            // Assert
            Response response = mapper400.toResponse(ex);
            assertEquals(400, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
        }

        @Test
        @DisplayName("id inexistente -> 404")
        void atualizarInexistente() {
            // Act
            NaoEncontradoException ex = assertThrows(NaoEncontradoException.class,
                    () -> resource.atualizarPlacar(999L, new AtualizarPlacarRequest(1, 1)));

            // Assert
            Response response = mapper404.toResponse(ex);
            assertEquals(404, response.getStatus());
        }

        @Test
        @DisplayName("jogo encerrado -> 409")
        void atualizarEncerrado() {
            // Arrange
            Long id = criarJogoEncerrado("A", "B");

            // Act
            JogoEncerradoException ex = assertThrows(JogoEncerradoException.class,
                    () -> resource.atualizarPlacar(id, new AtualizarPlacarRequest(1, 1)));

            // Assert
            Response response = mapper409.toResponse(ex);
            assertEquals(409, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
        }
    }

    @Nested
    @DisplayName("PUT /jogos/{id}/status")
    class AtualizarStatus {

        @Test
        @DisplayName("ENCERRADO em jogo EM_ANDAMENTO -> 200 com status ENCERRADO")
        void encerrar() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            Response response = resource.atualizarStatus(id, new AtualizarStatusRequest("ENCERRADO"));

            // Assert
            assertEquals(200, response.getStatus());
            JogoResponse corpo = assertInstanceOf(JogoResponse.class, response.getEntity());
            assertEquals("ENCERRADO", corpo.status());
        }

        @Test
        @DisplayName("ENCERRADO repetido -> ainda 200 e ENCERRADO (idempotente)")
        void encerrarIdempotente() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");
            resource.atualizarStatus(id, new AtualizarStatusRequest("ENCERRADO"));

            // Act
            Response response = resource.atualizarStatus(id, new AtualizarStatusRequest("ENCERRADO"));

            // Assert
            assertEquals(200, response.getStatus());
            JogoResponse corpo = assertInstanceOf(JogoResponse.class, response.getEntity());
            assertEquals("ENCERRADO", corpo.status());
        }

        @Test
        @DisplayName("EM_ANDAMENTO -> 400 (sem reabertura)")
        void statusEmAndamento() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> resource.atualizarStatus(id, new AtualizarStatusRequest("EM_ANDAMENTO")));

            // Assert
            Response response = mapper400.toResponse(ex);
            assertEquals(400, response.getStatus());
            ErroResponse erro = assertInstanceOf(ErroResponse.class, response.getEntity());
            assertFalse(erro.mensagem() == null || erro.mensagem().isBlank());
        }

        @Test
        @DisplayName("valor qualquer -> 400")
        void statusQualquer() {
            // Arrange
            Long id = criarJogoEmAndamento("A", "B");

            // Act
            EntradaInvalidaException ex = assertThrows(EntradaInvalidaException.class,
                    () -> resource.atualizarStatus(id, new AtualizarStatusRequest("QUALQUER")));

            // Assert
            Response response = mapper400.toResponse(ex);
            assertEquals(400, response.getStatus());
        }

        @Test
        @DisplayName("id inexistente -> 404")
        void statusInexistente() {
            // Act
            NaoEncontradoException ex = assertThrows(NaoEncontradoException.class,
                    () -> resource.atualizarStatus(999L, new AtualizarStatusRequest("ENCERRADO")));

            // Assert
            Response response = mapper404.toResponse(ex);
            assertEquals(404, response.getStatus());
        }
    }
}
