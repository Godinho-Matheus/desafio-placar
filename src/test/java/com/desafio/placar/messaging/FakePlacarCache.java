package com.desafio.placar.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.desafio.placar.cache.PlacarAtual;
import com.desafio.placar.cache.PlacarCache;

/**
 * Fake de teste de {@link PlacarCache} que registra as chamadas em memoria, sem
 * abrir conexao com o Redis.
 *
 * <p>Instanciado com {@code new} simples: como o {@code @PostConstruct} nao e
 * invocado, o {@code JedisPool} permanece nulo e nunca e usado. Todas as operacoes
 * sao sobrescritas para operar apenas sobre estruturas em memoria.</p>
 */
public class FakePlacarCache extends PlacarCache {

    private final List<Long> invalidados = new ArrayList<>();

    private final List<Atualizacao> atualizacoes = new ArrayList<>();

    private boolean falharInvalidacao;

    /**
     * Registro em memoria de uma chamada a {@link #atualizar(Long, int, int)},
     * incluindo o {@code jogoId}, para permitir asserts precisos nos testes.
     */
    public record Atualizacao(Long jogoId, int placarA, int placarB) {
    }

    /**
     * Registra o {@code jogoId} invalidado e, se configurado, simula uma falha de
     * integracao com o Redis lancando {@link RuntimeException}.
     */
    @Override
    public void invalidar(Long jogoId) {
        invalidados.add(jogoId);
        if (falharInvalidacao) {
            throw new RuntimeException("falha simulada na invalidacao");
        }
    }

    /**
     * Registra os argumentos da atualizacao; no-op quanto ao Redis.
     */
    @Override
    public void atualizar(Long jogoId, int placarA, int placarB) {
        atualizacoes.add(new Atualizacao(jogoId, placarA, placarB));
    }

    /**
     * Sempre retorna vazio (sem acesso ao Redis).
     */
    @Override
    public Optional<PlacarAtual> ler(Long jogoId) {
        return Optional.empty();
    }

    /**
     * @return {@code true} se {@link #invalidar(Long)} foi chamado ao menos uma vez
     */
    public boolean invalidarFoiChamado() {
        return !invalidados.isEmpty();
    }

    /**
     * @return lista, em ordem, dos {@code jogoId} passados a {@link #invalidar(Long)}
     */
    public List<Long> getInvalidados() {
        return invalidados;
    }

    /**
     * Configura se a proxima invalidacao deve simular falha de integracao.
     *
     * @param falharInvalidacao {@code true} para lancar excecao ao invalidar
     */
    public void setFalharInvalidacao(boolean falharInvalidacao) {
        this.falharInvalidacao = falharInvalidacao;
    }

    /**
     * @return quantidade de chamadas registradas a {@link #atualizar(Long, int, int)}
     */
    public int getAtualizarCount() {
        return atualizacoes.size();
    }

    /**
     * @return lista, em ordem, das chamadas registradas a
     *         {@link #atualizar(Long, int, int)}
     */
    public List<Atualizacao> getAtualizacoes() {
        return atualizacoes;
    }

    /**
     * @return a ultima {@link Atualizacao} registrada
     * @throws IndexOutOfBoundsException se {@link #atualizar(Long, int, int)} nunca
     *                                   foi chamado
     */
    public Atualizacao ultimaAtualizacao() {
        return atualizacoes.get(atualizacoes.size() - 1);
    }
}
