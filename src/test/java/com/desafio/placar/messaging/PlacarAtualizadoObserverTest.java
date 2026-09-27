package com.desafio.placar.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes unitarios de comportamento do {@link PlacarAtualizadoObserver}.
 *
 * <p>Usa fakes em memoria ({@link FakePlacarCache} e {@link FakePlacarEventPublisher})
 * e chama {@link PlacarAtualizadoObserver#observar} diretamente, sem CDI, JTA nem
 * conexoes com Redis/RabbitMQ. O observador e criado pelo construtor publico
 * {@code new PlacarAtualizadoObserver(cache, publisher)}.</p>
 */
class PlacarAtualizadoObserverTest {

    /**
     * Cenario feliz: invalida o cache e publica o evento com os dados corretos.
     *
     * <p>Valida: Requisitos 7.1, 7.5 (Requisito 12.3)</p>
     */
    @Test
    @DisplayName("observar deve invalidar o cache e publicar o evento")
    void observer_deveInvalidarCacheEPublicarEvento() {
        // Arrange
        FakePlacarCache fakeCache = new FakePlacarCache();
        FakePlacarEventPublisher fakePublisher = new FakePlacarEventPublisher();
        PlacarAtualizadoObserver observer = new PlacarAtualizadoObserver(fakeCache, fakePublisher);
        PlacarAtualizadoEvent evento = new PlacarAtualizadoEvent(10L, 2, 1);

        // Act
        observer.observar(evento);

        // Assert
        assertTrue(fakeCache.invalidarFoiChamado(), "invalidar deveria ter sido chamado");
        assertEquals(1, fakeCache.getInvalidados().size(), "invalidar deveria ter sido chamado uma vez");
        assertEquals(10L, fakeCache.getInvalidados().get(0), "invalidar deveria receber o jogoId 10");

        assertTrue(fakePublisher.publicarFoiChamado(), "publicar deveria ter sido chamado");
        EventoPlacar publicado = fakePublisher.ultimoPublicado();
        assertNotNull(publicado, "um EventoPlacar deveria ter sido publicado");
        assertEquals(10L, publicado.jogoId(), "jogoId do EventoPlacar publicado");
        assertEquals(2, publicado.placarA(), "placarA do EventoPlacar publicado");
        assertEquals(1, publicado.placarB(), "placarB do EventoPlacar publicado");
    }

    /**
     * Falha na invalidacao do Redis nao impede a publicacao e nao propaga excecao.
     *
     * <p>Valida: Requisitos 7.1, 7.5 (Requisito 12.3)</p>
     */
    @Test
    @DisplayName("observar deve publicar mesmo quando a invalidacao falhar")
    void observer_devePublicarMesmoQuandoInvalidacaoFalhar() {
        // Arrange
        FakePlacarCache fakeCache = new FakePlacarCache();
        fakeCache.setFalharInvalidacao(true);
        FakePlacarEventPublisher fakePublisher = new FakePlacarEventPublisher();
        PlacarAtualizadoObserver observer = new PlacarAtualizadoObserver(fakeCache, fakePublisher);
        PlacarAtualizadoEvent evento = new PlacarAtualizadoEvent(10L, 2, 1);

        // Act + Assert (nao deve propagar a falha da invalidacao)
        assertDoesNotThrow(() -> observer.observar(evento),
                "falha na invalidacao nao deveria escapar do observador");

        // Assert
        assertTrue(fakeCache.invalidarFoiChamado(), "a invalidacao deveria ter sido tentada");
        assertEquals(10L, fakeCache.getInvalidados().get(0), "a invalidacao deveria receber o jogoId 10");

        assertTrue(fakePublisher.publicarFoiChamado(), "publicar deveria ter sido chamado mesmo com falha na invalidacao");
        EventoPlacar publicado = fakePublisher.ultimoPublicado();
        assertNotNull(publicado, "um EventoPlacar deveria ter sido publicado");
        assertEquals(10L, publicado.jogoId(), "jogoId do EventoPlacar publicado");
        assertEquals(2, publicado.placarA(), "placarA do EventoPlacar publicado");
        assertEquals(1, publicado.placarB(), "placarB do EventoPlacar publicado");
    }

    /**
     * Falha na publicacao no RabbitMQ nao propaga excecao ao chamador.
     *
     * <p>Valida: Requisitos 7.1, 7.5 (Requisito 12.3)</p>
     */
    @Test
    @DisplayName("observar nao deve propagar falha do publisher")
    void observer_naoDevePropagarFalhaDoPublisher() {
        // Arrange
        FakePlacarCache fakeCache = new FakePlacarCache();
        FakePlacarEventPublisher fakePublisher = new FakePlacarEventPublisher();
        fakePublisher.setFalharPublicacao(true);
        PlacarAtualizadoObserver observer = new PlacarAtualizadoObserver(fakeCache, fakePublisher);
        PlacarAtualizadoEvent evento = new PlacarAtualizadoEvent(10L, 2, 1);

        // Act + Assert (a falha do publisher nao deve escapar)
        assertDoesNotThrow(() -> observer.observar(evento),
                "falha na publicacao nao deveria escapar do observador");

        // Assert
        assertTrue(fakeCache.invalidarFoiChamado(), "a invalidacao do cache deveria ter ocorrido");
        assertEquals(10L, fakeCache.getInvalidados().get(0), "a invalidacao deveria receber o jogoId 10");

        assertTrue(fakePublisher.publicarFoiChamado(), "a publicacao deveria ter sido tentada antes da falha");
        EventoPlacar publicado = fakePublisher.ultimoPublicado();
        assertNotNull(publicado, "o EventoPlacar deveria ter sido registrado antes da falha simulada");
        assertEquals(10L, publicado.jogoId(), "jogoId do EventoPlacar tentado");
        assertEquals(2, publicado.placarA(), "placarA do EventoPlacar tentado");
        assertEquals(1, publicado.placarB(), "placarB do EventoPlacar tentado");
    }
}
