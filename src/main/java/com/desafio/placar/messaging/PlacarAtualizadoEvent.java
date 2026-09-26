package com.desafio.placar.messaging;

/**
 * Evento CDI <strong>interno</strong> que sinaliza a atualizacao do placar de um Jogo.
 *
 * <p>E disparado por {@code JogoService.atualizarPlacar(...)} via
 * {@link jakarta.enterprise.event.Event#fire(Object)} <strong>dentro da transacao</strong>,
 * logo apos a persistencia do novo placar. O {@link PlacarAtualizadoObserver} reage a ele
 * apenas em {@link jakarta.enterprise.event.TransactionPhase#AFTER_SUCCESS}, ou seja,
 * somente depois do commit.</p>
 *
 * <p>Carrega <strong>somente</strong> {@code jogoId}, {@code placarA} e {@code placarB}:
 * o minimo necessario para reagir a mudanca de placar. Nao inclui a entidade Jogo, times,
 * status, data/hora, nem detalhes de Redis ou RabbitMQ.</p>
 *
 * <p><strong>Importante:</strong> este NAO e a mensagem publicada no RabbitMQ.
 * A mensagem de mensageria ({@code EventoPlacar}) e um artefato distinto.</p>
 *
 * @param jogoId  identificador do Jogo cujo placar foi atualizado
 * @param placarA novo placar do time da casa (inteiro maior ou igual a 0)
 * @param placarB novo placar do time visitante (inteiro maior ou igual a 0)
 */
public record PlacarAtualizadoEvent(Long jogoId, int placarA, int placarB) {
}
