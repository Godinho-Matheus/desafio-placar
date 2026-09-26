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
 * que a publica no exchange {@code placar.exchange}).</p>
 *
 * <p>O Consumer que le a fila e reflete o placar no Redis pertence a uma tarefa posterior.</p>
 *
 * <p>O evento CDI interno e a mensagem RabbitMQ sao artefatos distintos: o primeiro
 * dispara a reacao dentro do processo; a segunda cruza o limite do processo via RabbitMQ.</p>
 */
package com.desafio.placar.messaging;
