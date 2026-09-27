package com.desafio.placar.api;

import java.lang.annotation.Annotation;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Implementacao no-op de {@link Event} para os testes de contrato REST.
 *
 * <p>O {@link com.desafio.placar.service.JogoService} real dispara um evento CDI
 * interno ao atualizar o placar; nos testes REST esse disparo nao interessa, entao
 * {@link #fire(Object)} apenas descarta o evento. Os demais metodos da interface
 * ({@code fireAsync} e as sobrecargas de {@code select}) nao sao exercitados e
 * lancam {@link UnsupportedOperationException}.</p>
 *
 * <p>Publico e no pacote {@code com.desafio.placar.api} para ser reutilizado pela
 * suite REST sem alterar a visibilidade das fakes ja existentes.</p>
 *
 * @param <T> tipo do evento
 */
public final class NoOpEvent<T> implements Event<T> {

    @Override
    public void fire(T evento) {
        // no-op: os testes REST nao verificam o disparo do evento interno.
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U evento) {
        throw new UnsupportedOperationException("fireAsync nao e usado nos testes");
    }

    @Override
    public <U extends T> CompletionStage<U> fireAsync(U evento, NotificationOptions options) {
        throw new UnsupportedOperationException("fireAsync nao e usado nos testes");
    }

    @Override
    public Event<T> select(Annotation... qualifiers) {
        throw new UnsupportedOperationException("select nao e usado nos testes");
    }

    @Override
    public <U extends T> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException("select nao e usado nos testes");
    }

    @Override
    public <U extends T> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException("select nao e usado nos testes");
    }
}
