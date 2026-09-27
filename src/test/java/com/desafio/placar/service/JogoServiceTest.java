package com.desafio.placar.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.domain.excecao.JogoEncerradoException;
import com.desafio.placar.messaging.PlacarAtualizadoEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes unitarios das regras de negocio do {@link JogoService} (Tarefa 11.1).
 *
 * <p>Usa dubles em memoria ({@link FakeJogoRepository}, {@link FakePlacarCache} e
 * {@link CapturingEvent}) — nenhum PostgreSQL, Redis, RabbitMQ ou Payara e necessario.
 * Cada teste foca em uma regra de negocio, no formato Arrange/Act/Assert.</p>
 */
class JogoServiceTest {

    private FakeJogoRepository repository;
    private CapturingEvent<PlacarAtualizadoEvent> eventos;
    private JogoService service;

    @BeforeEach
    void setUp() {
        repository = new FakeJogoRepository();
        eventos = new CapturingEvent<>();
        service = new JogoService(repository, new FakePlacarCache(), eventos);
    }

    /** Cria um Jogo ja persistido no fake com um status e placar conhecidos. */
    private Jogo preInserir(String timeA, String timeB, int placarA, int placarB, Status status) {
        Jogo jogo = new Jogo();
        jogo.setTimeA(timeA);
        jogo.setTimeB(timeB);
        jogo.setPlacarA(placarA);
        jogo.setPlacarB(placarB);
        jogo.setStatus(status);
        jogo.setDataHoraPartida(OffsetDateTime.parse("2026-01-01T20:00:00Z"));
        return repository.salvar(jogo);
    }

    @Test
    void criar_deveIniciarJogoComPlacarZeroEStatusEmAndamento() {
        // Arrange
        OffsetDateTime dataHora = OffsetDateTime.parse("2026-05-10T16:30:00Z");

        // Act
        Jogo criado = service.criar("Palmeiras", "Corinthians", dataHora);

        // Assert
        assertNotNull(criado.getId(), "o repositorio deve atribuir um id ao novo Jogo");
        assertEquals(0, criado.getPlacarA(), "placar inicial do timeA deve ser 0");
        assertEquals(0, criado.getPlacarB(), "placar inicial do timeB deve ser 0");
        assertEquals(Status.EM_ANDAMENTO, criado.getStatus(), "novo Jogo deve iniciar EM_ANDAMENTO");
        assertEquals("Palmeiras", criado.getTimeA());
        assertEquals("Corinthians", criado.getTimeB());
        assertEquals(dataHora, criado.getDataHoraPartida());
    }

    @Test
    void atualizarPlacar_devePersistirNovoPlacar() {
        // Arrange
        Jogo jogo = preInserir("Santos", "Sao Paulo", 0, 0, Status.EM_ANDAMENTO);
        int salvamentosAntes = repository.getSalvarCount();

        // Act
        Jogo atualizado = service.atualizarPlacar(jogo.getId(), 2, 1);

        // Assert
        assertEquals(2, atualizado.getPlacarA());
        assertEquals(1, atualizado.getPlacarB());
        assertEquals(2, repository.armazenado(jogo.getId()).getPlacarA(),
                "o novo placar deve estar persistido no repositorio");
        assertEquals(1, repository.armazenado(jogo.getId()).getPlacarB());
        assertTrue(repository.getSalvarCount() > salvamentosAntes,
                "a atualizacao deve registrar um salvamento");
    }

    @Test
    void atualizarPlacar_deveDispararEventoComDadosCorretos() {
        // Arrange
        Jogo jogo = preInserir("Flamengo", "Fluminense", 0, 0, Status.EM_ANDAMENTO);

        // Act
        service.atualizarPlacar(jogo.getId(), 2, 1);

        // Assert
        assertEquals(1, eventos.getEventos().size(), "deve disparar exatamente um evento");
        PlacarAtualizadoEvent evento = eventos.ultimo();
        assertEquals(jogo.getId(), evento.jogoId());
        assertEquals(2, evento.placarA());
        assertEquals(1, evento.placarB());
    }

    @Test
    void atualizarPlacar_deveRecusarJogoEncerrado() {
        // Arrange
        Jogo jogo = preInserir("Gremio", "Internacional", 1, 0, Status.ENCERRADO);
        int salvamentosAntes = repository.getSalvarCount();

        // Act + Assert
        assertThrows(JogoEncerradoException.class,
                () -> service.atualizarPlacar(jogo.getId(), 3, 3));

        // Placar preservado, sem salvamento extra e sem evento
        assertEquals(1, repository.armazenado(jogo.getId()).getPlacarA(),
                "placar de Jogo encerrado nao pode ser alterado");
        assertEquals(0, repository.armazenado(jogo.getId()).getPlacarB());
        assertEquals(salvamentosAntes, repository.getSalvarCount(),
                "nenhum salvamento deve ocorrer para Jogo encerrado");
        assertTrue(eventos.getEventos().isEmpty(), "nenhum evento deve ser disparado");
    }

    @Test
    void encerrar_devePreservarPlacarFinal() {
        // Arrange
        Jogo jogo = preInserir("Bahia", "Vitoria", 3, 2, Status.EM_ANDAMENTO);

        // Act
        Jogo encerrado = service.encerrar(jogo.getId());

        // Assert
        assertEquals(Status.ENCERRADO, encerrado.getStatus());
        assertEquals(3, encerrado.getPlacarA(), "placar final do timeA deve ser preservado");
        assertEquals(2, encerrado.getPlacarB(), "placar final do timeB deve ser preservado");
    }

    @Test
    void encerrar_deveSerIdempotente() {
        // Arrange
        Jogo jogo = preInserir("Cruzeiro", "Atletico", 1, 1, Status.ENCERRADO);

        // Act
        Jogo resultado = service.encerrar(jogo.getId());

        // Assert
        assertEquals(Status.ENCERRADO, resultado.getStatus());
        assertEquals(1, resultado.getPlacarA(), "placar permanece inalterado");
        assertEquals(1, resultado.getPlacarB(), "placar permanece inalterado");
    }

    @Test
    void listar_deveFiltrarPorStatus() {
        // Arrange
        Jogo emAndamento = preInserir("Time A", "Time B", 0, 0, Status.EM_ANDAMENTO);
        Jogo encerrado = preInserir("Time C", "Time D", 2, 1, Status.ENCERRADO);

        // Act
        List<Jogo> todos = service.listar(null);
        List<Jogo> apenasEmAndamento = service.listar(Status.EM_ANDAMENTO);
        List<Jogo> apenasEncerrados = service.listar(Status.ENCERRADO);

        // Assert
        assertEquals(2, todos.size(), "listar(null) deve retornar todos os Jogos");

        assertEquals(1, apenasEmAndamento.size());
        assertTrue(apenasEmAndamento.contains(emAndamento));
        assertFalse(apenasEmAndamento.contains(encerrado));

        assertEquals(1, apenasEncerrados.size());
        assertTrue(apenasEncerrados.contains(encerrado));
        assertFalse(apenasEncerrados.contains(emAndamento));
    }
}
