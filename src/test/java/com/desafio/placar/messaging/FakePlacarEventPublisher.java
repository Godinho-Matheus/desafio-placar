package com.desafio.placar.messaging;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake de teste de {@link PlacarEventPublisher} que registra os eventos publicados
 * em memoria, sem abrir conexao com o RabbitMQ.
 *
 * <p>Instanciado com {@code new} simples: como o {@code @PostConstruct} nao e
 * invocado, nenhuma conexao com o broker e criada.</p>
 */
public class FakePlacarEventPublisher extends PlacarEventPublisher {

    private final List<EventoPlacar> publicados = new ArrayList<>();

    private boolean falharPublicacao;

    /**
     * Registra o {@link EventoPlacar} <strong>antes</strong> de qualquer falha
     * simulada, de modo que os testes possam verificar que a publicacao foi
     * tentada com os dados corretos mesmo no cenario de erro.
     */
    @Override
    public void publicar(EventoPlacar evento) {
        publicados.add(evento);
        if (falharPublicacao) {
            throw new RuntimeException("falha simulada na publicacao");
        }
    }

    /**
     * @return {@code true} se {@link #publicar(EventoPlacar)} foi chamado ao menos uma vez
     */
    public boolean publicarFoiChamado() {
        return !publicados.isEmpty();
    }

    /**
     * @return lista, em ordem, dos eventos passados a {@link #publicar(EventoPlacar)}
     */
    public List<EventoPlacar> getPublicados() {
        return publicados;
    }

    /**
     * @return o ultimo {@link EventoPlacar} publicado, ou {@code null} se nenhum
     */
    public EventoPlacar ultimoPublicado() {
        if (publicados.isEmpty()) {
            return null;
        }
        return publicados.get(publicados.size() - 1);
    }

    /**
     * Configura se a proxima publicacao deve simular falha de integracao.
     *
     * @param falharPublicacao {@code true} para lancar excecao ao publicar
     */
    public void setFalharPublicacao(boolean falharPublicacao) {
        this.falharPublicacao = falharPublicacao;
    }
}
