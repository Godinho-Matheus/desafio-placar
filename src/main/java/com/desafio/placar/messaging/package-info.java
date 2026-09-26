/**
 * Mensageria e eventos internos do Sistema.
 *
 * <p>Reune o {@link com.desafio.placar.messaging.PlacarAtualizadoEvent}, o evento CDI
 * <strong>interno</strong> disparado dentro da transacao de atualizacao de placar e
 * observado em AFTER_SUCCESS pelo
 * {@link com.desafio.placar.messaging.PlacarAtualizadoObserver} (que reage apos o commit,
 * invalidando o Redis e publicando a mensagem RabbitMQ), e os artefatos de mensageria
 * RabbitMQ: a mensagem {@link com.desafio.placar.messaging.EventoPlacar} (publicada na
 * fila externa pelo observer via {@link com.desafio.placar.messaging.PlacarEventPublisher},
 * que a publica no exchange {@code placar.exchange}) e o
 * {@link com.desafio.placar.messaging.PlacarEventConsumer} (bean {@code @Startup} que
 * consome a fila e reflete o placar no Redis via
 * {@link com.desafio.placar.cache.PlacarCache}).</p>
 *
 * <p>O fluxo completo da mensageria e:
 * {@link com.desafio.placar.messaging.PlacarAtualizadoEvent} &rarr; AFTER_SUCCESS (observer)
 * &rarr; {@link com.desafio.placar.messaging.PlacarEventPublisher} &rarr; RabbitMQ &rarr;
 * {@link com.desafio.placar.messaging.PlacarEventConsumer} &rarr;
 * {@link com.desafio.placar.cache.PlacarCache} &rarr; Redis.</p>
 *
 * <p>O evento CDI interno e a mensagem RabbitMQ sao artefatos distintos: o primeiro
 * dispara a reacao dentro do processo; a segunda cruza o limite do processo via RabbitMQ.</p>
 */
package com.desafio.placar.messaging;
