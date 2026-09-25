/**
 * Cache do placar atual sobre Redis.
 *
 * <p>Reune o valor de cache {@link com.desafio.placar.cache.PlacarAtual}
 * (somente placarA/placarB) e o componente
 * {@link com.desafio.placar.cache.PlacarCache}, que abstrai o acesso ao Redis
 * (gravacao, leitura e invalidacao) usando a chave {@code jogo:{id}:placar}. O
 * Redis e apenas um cache do placar; o PostgreSQL permanece como unica fonte de
 * verdade. Falhas de integracao com o Redis sao registradas em log e nunca
 * propagadas ao chamador que apenas le o cache.</p>
 */
package com.desafio.placar.cache;
