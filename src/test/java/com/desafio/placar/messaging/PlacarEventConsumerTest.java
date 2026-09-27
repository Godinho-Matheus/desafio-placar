package com.desafio.placar.messaging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.desafio.placar.messaging.FakePlacarCache.Atualizacao;

/**
 * Testes do {@link PlacarEventConsumer} focados no processamento de mensagens.
 *
 * <p>Exercitam apenas {@link PlacarEventConsumer#processar(byte[])} diretamente,
 * sem tocar RabbitMQ: o consumidor e criado com {@code new} passando um
 * {@link FakePlacarCache}, de modo que {@code iniciar()} ({@code @PostConstruct})
 * nunca e chamado e nenhuma conexao externa e aberta. O comportamento e observado
 * pelo efeito colateral em {@link com.desafio.placar.cache.PlacarCache#atualizar},
 * nunca inspecionando o metodo privado {@code desserializar} nem o Logger.</p>
 */
class PlacarEventConsumerTest {

    @Test
    @DisplayName("processar deve atualizar o cache com o evento recebido")
    void consumer_deveAtualizarCacheComEventoRecebido() {
        // Arrange
        FakePlacarCache cache = new FakePlacarCache();
        PlacarEventConsumer consumer = new PlacarEventConsumer(cache);
        byte[] body = "{\"jogoId\":10,\"placarA\":2,\"placarB\":1}".getBytes(StandardCharsets.UTF_8);

        // Act
        consumer.processar(body);

        // Assert
        assertEquals(1, cache.getAtualizarCount(), "deve registrar exatamente uma atualizacao");
        Atualizacao atualizacao = cache.ultimaAtualizacao();
        assertEquals(10L, atualizacao.jogoId(), "jogoId atualizado");
        assertEquals(2, atualizacao.placarA(), "placarA atualizado");
        assertEquals(1, atualizacao.placarB(), "placarB atualizado");
    }

    @Test
    @DisplayName("processar deve ignorar mensagem invalida sem propagar nem atualizar")
    void consumer_deveIgnorarMensagemInvalida() {
        // Arrange
        FakePlacarCache cache = new FakePlacarCache();
        PlacarEventConsumer consumer = new PlacarEventConsumer(cache);
        byte[] body = "{ not valid json".getBytes(StandardCharsets.UTF_8);

        // Act / Assert
        assertDoesNotThrow(() -> consumer.processar(body), "mensagem invalida nao deve propagar excecao");
        assertEquals(0, cache.getAtualizarCount(), "atualizar nao deve ser chamado para mensagem invalida");
    }
}
