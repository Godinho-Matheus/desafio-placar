package com.desafio.placar.config;

import java.util.Optional;

/**
 * Centraliza, de forma documentada, a configuracao externa do Redis.
 *
 * <p>O Redis e usado apenas como cache do placar atual (placarA/placarB). Host,
 * porta e eventual senha ficam <strong>fora do codigo-fonte</strong>
 * (Requisito 13.2): sao resolvidos em tempo de execucao por
 * {@link ConfiguracaoExterna}, que consulta primeiro a propriedade de sistema
 * da JVM e, na ausencia dela, a variavel de ambiente de mesmo nome.</p>
 *
 * <p>Chaves de configuracao e valores padrao (adequados para desenvolvimento
 * local, sem credenciais fixadas no codigo):</p>
 * <ul>
 *   <li>{@value #CHAVE_HOST} &mdash; host do Redis; padrao {@value #HOST_PADRAO};</li>
 *   <li>{@value #CHAVE_PORTA} &mdash; porta do Redis; padrao {@value #PORTA_PADRAO};</li>
 *   <li>{@value #CHAVE_SENHA} &mdash; senha do Redis; <strong>opcional</strong>.
 *       Se ausente, a conexao e feita sem senha. Nao ha senha fixada no codigo.</li>
 * </ul>
 *
 * <p>Classe utilitaria minima, sem novas abstracoes: apenas documenta as chaves
 * em um unico ponto e fornece acessores tipados.</p>
 */
public final class ConfiguracaoRedis {

    /** Chave da propriedade/variavel de ambiente para o host do Redis. */
    public static final String CHAVE_HOST = "REDIS_HOST";

    /** Chave da propriedade/variavel de ambiente para a porta do Redis. */
    public static final String CHAVE_PORTA = "REDIS_PORT";

    /** Chave da propriedade/variavel de ambiente para a senha do Redis (opcional). */
    public static final String CHAVE_SENHA = "REDIS_PASSWORD";

    /** Host padrao para desenvolvimento local. */
    public static final String HOST_PADRAO = "localhost";

    /** Porta padrao para desenvolvimento local. */
    public static final String PORTA_PADRAO = "6379";

    private ConfiguracaoRedis() {
        // Classe utilitaria: nao deve ser instanciada.
    }

    /**
     * Host do Redis.
     *
     * @return o host configurado, ou {@value #HOST_PADRAO} quando ausente
     */
    public static String host() {
        return ConfiguracaoExterna.ler(CHAVE_HOST, HOST_PADRAO);
    }

    /**
     * Porta do Redis.
     *
     * @return a porta configurada, ou {@value #PORTA_PADRAO} quando ausente
     */
    public static int porta() {
        return Integer.parseInt(ConfiguracaoExterna.ler(CHAVE_PORTA, PORTA_PADRAO));
    }

    /**
     * Senha do Redis (opcional).
     *
     * @return a senha configurada, ou {@link Optional#empty()} quando ausente
     */
    public static Optional<String> senha() {
        return ConfiguracaoExterna.ler(CHAVE_SENHA);
    }
}
