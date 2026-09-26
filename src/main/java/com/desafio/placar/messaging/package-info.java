/**
 * Mensageria e eventos internos do Sistema.
 *
 * <p>Reune o {@link com.desafio.placar.messaging.PlacarAtualizadoEvent}, o evento CDI
 * <strong>interno</strong> disparado dentro da transacao de atualizacao de placar (a ser
 * observado em AFTER_SUCCESS por um observador introduzido em tarefa posterior), e os
 * artefatos de mensageria RabbitMQ: a mensagem
 * {@link com.desafio.placar.messaging.EventoPlacar} (publicada na fila externa) e o
 * {@link com.desafio.placar.messaging.PlacarEventPublisher}, que a publica no exchange
 * {@code placar.exchange}.</p>
 *
 * <p>O evento CDI interno e a mensagem RabbitMQ sao artefatos distintos: o primeiro
 * dispara a reacao dentro do processo; a segunda cruza o limite do processo via RabbitMQ.</p>
 */
package com.desafio.placar.messaging;
