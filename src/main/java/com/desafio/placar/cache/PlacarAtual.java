package com.desafio.placar.cache;

/**
 * Placar atual de uma partida, refletindo exatamente o que o Redis guarda por jogo.
 *
 * <p>Contem <strong>somente</strong> {@code placarA} e {@code placarB}. Times,
 * status e data/hora da partida nao sao armazenados no cache: eles permanecem
 * no PostgreSQL, unica fonte de verdade do estado completo do Jogo.</p>
 *
 * @param placarA gols do timeA (inteiro maior ou igual a 0)
 * @param placarB gols do timeB (inteiro maior ou igual a 0)
 */
public record PlacarAtual(int placarA, int placarB) {
}
