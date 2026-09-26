/**
 * Mensageria e eventos internos do Sistema.
 *
 * <p>Reune, por ora, o {@link com.desafio.placar.messaging.PlacarAtualizadoEvent}, o
 * evento CDI <strong>interno</strong> disparado dentro da transacao de atualizacao de
 * placar, a ser observado em AFTER_SUCCESS por um observador introduzido em tarefa
 * posterior.</p>
 *
 * <p>Este pacote tambem abrigara, em tarefas futuras da Spec, as classes de mensageria
 * RabbitMQ (mensagem {@code EventoPlacar}, publisher e consumer). No momento apenas o
 * evento CDI interno existe.</p>
 */
package com.desafio.placar.messaging;
