package com.desafio.placar.cache;

import java.io.StringReader;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.desafio.placar.config.ConfiguracaoRedis;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Cache do placar atual sobre Redis.
 *
 * <p>Guarda <strong>apenas</strong> {@code placarA} e {@code placarB} por jogo.
 * A chave segue o padrao {@code jogo:{id}:placar} (ex.: {@code jogo:42:placar}) e
 * o valor e um JSON contendo somente os dois inteiros
 * (ex.: {@code {"placarA":2,"placarB":1}}). Status, times e data/hora nunca sao
 * gravados aqui: o PostgreSQL permanece como unica fonte de verdade.</p>
 *
 * <p>Bean CDI {@link ApplicationScoped} que mantem um unico {@link JedisPool}
 * durante todo o ciclo de vida da aplicacao. O pool e criado uma vez no
 * {@link PostConstruct} (a partir de {@link ConfiguracaoRedis}) e fechado no
 * {@link PreDestroy}. Cada operacao pega uma conexao emprestada do pool com
 * try-with-resources, o que e adequado para requisicoes concorrentes no Payara,
 * ja que o {@code JedisPool} e thread-safe.</p>
 *
 * <p>Sem circuit breaker, retry ou qualquer mecanismo distribuido. Falhas de
leitura e atualizacao sao registradas em log; falhas de invalidacao propagam
ao chamador para tratamento pelo fluxo de orquestracao.</p>
 */
@ApplicationScoped
public class PlacarCache {

    private static final Logger LOG = Logger.getLogger(PlacarCache.class.getName());

    private JedisPool pool;

    /**
     * Cria o {@link JedisPool} unico da aplicacao a partir da configuracao externa
     * do Redis. Executado uma vez, na inicializacao do bean.
     */
    @PostConstruct
    void iniciar() {
        String host = ConfiguracaoRedis.host();
        int porta = ConfiguracaoRedis.porta();
        Optional<String> senha = ConfiguracaoRedis.senha();
        if (senha.isPresent()) {
            this.pool = new JedisPool(host, porta, null, senha.get());
        } else {
            this.pool = new JedisPool(host, porta);
        }
    }

    /**
     * Fecha o {@link JedisPool} liberando as conexoes, no encerramento da aplicacao.
     */
    @PreDestroy
    void encerrar() {
        if (pool != null) {
            pool.close();
        }
    }

    /**
     * Grava o placar atual do jogo no Redis (chamado pelo Consumer).
     *
     * <p>Falhas de integracao com o Redis sao apenas registradas em log, sem
     * propagar excecao.</p>
     *
     * @param jogoId  identificador do jogo
     * @param placarA gols do timeA
     * @param placarB gols do timeB
     */
    public void atualizar(Long jogoId, int placarA, int placarB) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(chave(jogoId), serializar(placarA, placarB));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao gravar placar no Redis para o jogo " + jogoId, e);
        }
    }

    /**
     * Le o placar atual do jogo no Redis.
     *
     * <p>Comportamento:</p>
     * <ul>
     *   <li>chave existe e valor e JSON valido &rarr; {@link Optional} com o placar;</li>
     *   <li>chave ausente &rarr; {@link Optional#empty()};</li>
     *   <li>Redis indisponivel ou erro de integracao/parse &rarr; loga e retorna
     *       {@link Optional#empty()}.</li>
     * </ul>
     *
     * <p>Nao propaga falhas do Redis ao chamador nem distingue cache MISS de
     * indisponibilidade: ambos retornam {@link Optional#empty()}.</p>
     *
     * @param jogoId identificador do jogo
     * @return o placar quando encontrado; vazio quando ausente ou indisponivel
     */
    public Optional<PlacarAtual> ler(Long jogoId) {
        try (Jedis jedis = pool.getResource()) {
            String valor = jedis.get(chave(jogoId));
            if (valor == null) {
                return Optional.empty();
            }
            return Optional.of(desserializar(valor));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Falha ao ler placar no Redis para o jogo " + jogoId, e);
            return Optional.empty();
        }
    }

    /**
     * Remove a chave {@code jogo:{id}:placar} do Redis (invalidacao do cache).
     *
     * <p>Falhas de integracao com o Redis <strong>propagam</strong> ao chamador,
     * para que o observador possa trata-las de forma independente (conforme o
     * Design).</p>
     *
     * @param jogoId identificador do jogo
     */
    public void invalidar(Long jogoId) {
        try (Jedis jedis = pool.getResource()) {
            jedis.del(chave(jogoId));
        }
    }

    /**
     * Monta a chave do Redis para o placar de um jogo: {@code jogo:{id}:placar}.
     */
    private static String chave(Long jogoId) {
        return "jogo:" + jogoId + ":placar";
    }

    /**
     * Serializa o placar em JSON contendo somente os dois inteiros, via JSON-P.
     */
    private static String serializar(int placarA, int placarB) {
        return Json.createObjectBuilder()
                .add("placarA", placarA)
                .add("placarB", placarB)
                .build()
                .toString();
    }

    /**
     * Desserializa o JSON do cache em {@link PlacarAtual}, via JSON-P.
     */
    private static PlacarAtual desserializar(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            JsonObject obj = reader.readObject();
            return new PlacarAtual(obj.getInt("placarA"), obj.getInt("placarB"));
        }
    }
}
