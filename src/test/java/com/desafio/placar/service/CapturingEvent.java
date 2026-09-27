package com.desafio.placar.service;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;

/**
 * Implementacao de {@link Event} que apenas captura os objetos disparados via
 * {@link #fire(Object)}, isolando a implementacao verbosa da interface em um unico
 * helper de teste.
 *
 * <p>Os testes exercitam somente {@code fire}; os demais metodos da interface
 * ({@code fireAsync} e as sobrecargas de {@code select}) lancam
 * {@link UnsupportedOperationException} por nao serem necessarios.</p>
 *
 * @param <T> tipo do evento capturado
 */
final class CapturingEvent<T> implements Event<T> {

    private final List<T> eventos = new ArrayList<>();

    @Override
    public void fire(T evento) {
        eventos.add(evento);
    }

    /** Todos os eventos disparados, na ordem de disparo. */
    List<T> getEventos() {
        return eventos;
    }

    /** Ultimo evento disparado. */
    T ultimo() {
        return eventos.get(eventos.size() - 1);
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
