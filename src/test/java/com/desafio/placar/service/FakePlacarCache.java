package com.desafio.placar.service;

import java.util.Optional;

import com.desafio.placar.cache.PlacarAtual;
import com.desafio.placar.cache.PlacarCache;

/**
 * Dublê de {@link PlacarCache} para os testes unitarios do
 * {@link com.desafio.placar.service.JogoService}.
 *
 * <p>Os metodos sob teste ({@code criar}, {@code atualizarPlacar}, {@code encerrar},
 * {@code listar}) nao acessam o cache. Ainda assim, os metodos publicos sao
 * sobrescritos como no-ops para garantir que nenhum acesso ao Redis ocorra caso o
 * fluxo mude. O ciclo de vida CDI ({@code @PostConstruct}) nao e executado aqui, entao
 * o {@code JedisPool} permanece {@code null} e nunca e utilizado.</p>
 */
final class FakePlacarCache extends PlacarCache {

    @Override
    public void atualizar(Long jogoId, int placarA, int placarB) {
        // no-op: nao ha Redis nos testes unitarios
    }

    @Override
    public Optional<PlacarAtual> ler(Long jogoId) {
        return Optional.empty();
    }

    @Override
    public void invalidar(Long jogoId) {
        // no-op: nao ha Redis nos testes unitarios
    }
}
