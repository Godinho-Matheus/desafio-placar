package com.desafio.placar.messaging;

/**
 * Mensagem de mensageria publicada no RabbitMQ quando o placar de um Jogo muda.
 *
 * <p>Este e o <strong>Evento_Placar</strong> do Design: o artefato serializado em
 * JSON e enviado para a fila externa. Carrega <strong>somente</strong>
 * {@code jogoId}, {@code placarA} e {@code placarB} (Requisito 7.1); nao inclui a
 * entidade Jogo, times, status, {@code dataHoraPartida} nem qualquer detalhe do
 * Redis. Sua serializacao JSON e exatamente
 * {@code {"jogoId":10,"placarA":2,"placarB":1}}.</p>
 *
 * <p><strong>Distincao importante:</strong> {@code EventoPlacar} e a MENSAGEM
 * RabbitMQ (fila externa), enquanto {@link PlacarAtualizadoEvent} e o evento CDI
 * <strong>interno</strong> disparado dentro da transacao de atualizacao de placar.
 * Sao artefatos distintos, com responsabilidades diferentes: o evento CDI interno
 * dispara a reacao no processo (observer AFTER_SUCCESS); esta mensagem cruza o
 * limite do processo via RabbitMQ para ser consumida e refletida no Redis.</p>
 *
 * @param jogoId  identificador do Jogo cujo placar foi atualizado
 * @param placarA novo placar do time da casa (inteiro maior ou igual a 0)
 * @param placarB novo placar do time visitante (inteiro maior ou igual a 0)
 */
public record EventoPlacar(Long jogoId, int placarA, int placarB) {
}
