package com.desafio.placar.messaging;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.desafio.placar.cache.PlacarCache;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Observador do {@link PlacarAtualizadoEvent} que reage apenas apos o commit.
 *
 * <p>Bean CDI {@link ApplicationScoped} que observa o evento CDI interno
 * {@link PlacarAtualizadoEvent} em
 * {@link TransactionPhase#AFTER_SUCCESS}, ou seja, somente depois que a transacao
 * de atualizacao de placar no PostgreSQL foi confirmada (commit). Em rollback este
 * fluxo nao executa.</p>
 *
 * <p>Ao reagir, executa dois passos <strong>independentes</strong>:</p>
 * <ol>
 *   <li>tenta invalidar o placar no Redis via {@link PlacarCache#invalidar(Long)};
 *       qualquer falha e apenas registrada em log, nao e relancada e nao impede o
 *       passo seguinte;</li>
 *   <li>independentemente do resultado do passo anterior, tenta publicar o
 *       {@link EventoPlacar} no RabbitMQ via
 *       {@link PlacarEventPublisher#publicar(EventoPlacar)}; qualquer falha e
 *       apenas registrada em log e nao e relancada.</li>
 * </ol>
 *
 * <p>Como a reacao ocorre depois do commit, eventuais falhas de Redis ou RabbitMQ
 * <strong>nunca</strong> desfazem a transacao ja confirmada no PostgreSQL: sao
 * tratadas como consistencia eventual e apenas registradas em log. Nao ha retry,
 * Outbox, dead-letter, compensacao nem transacao distribuida.</p>
 */
@ApplicationScoped
public class PlacarAtualizadoObserver {

    private static final Logger LOG = Logger.getLogger(PlacarAtualizadoObserver.class.getName());

    private final PlacarCache placarCache;

    private final PlacarEventPublisher placarEventPublisher;

    /**
     * Construtor padrao exigido pelo CDI.
     */
    protected PlacarAtualizadoObserver() {
        this.placarCache = null;
        this.placarEventPublisher = null;
    }

    /**
     * Cria o observador com o cache de placar e o publicador de mensageria.
     *
     * @param placarCache          cache Redis de placar atual
     * @param placarEventPublisher publicador do {@link EventoPlacar} no RabbitMQ
     */
    @Inject
    public PlacarAtualizadoObserver(PlacarCache placarCache,
            PlacarEventPublisher placarEventPublisher) {
        this.placarCache = placarCache;
        this.placarEventPublisher = placarEventPublisher;
    }

    /**
     * Reage a {@link PlacarAtualizadoEvent} apos o commit da transacao.
     *
     * <p>Primeiro tenta invalidar o Redis; em seguida, de forma independente,
     * tenta publicar o {@link EventoPlacar} no RabbitMQ. Falhas em qualquer um dos
     * passos sao apenas registradas em log e nao desfazem o commit.</p>
     *
     * @param evento evento CDI interno com {@code jogoId}, {@code placarA} e
     *               {@code placarB}
     */
    public void observar(@Observes(during = TransactionPhase.AFTER_SUCCESS) PlacarAtualizadoEvent evento) {
        try {
            placarCache.invalidar(evento.jogoId());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao invalidar placar no Redis; seguindo para publicacao", e);
        }

        try {
            placarEventPublisher.publicar(new EventoPlacar(evento.jogoId(), evento.placarA(), evento.placarB()));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao publicar EventoPlacar no RabbitMQ", e);
        }
    }
}
