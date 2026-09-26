package com.desafio.placar.config;

/**
 * Centraliza, de forma documentada, a configuracao externa do RabbitMQ.
 *
 * <p>O RabbitMQ e usado para propagar o {@code EventoPlacar} de forma assincrona.
 * Host, porta, usuario, senha e virtual host ficam <strong>fora do codigo-fonte</strong>
 * (Requisito 13.2): sao resolvidos em tempo de execucao por
 * {@link ConfiguracaoExterna}, que consulta primeiro a propriedade de sistema da
 * JVM e, na ausencia dela, a variavel de ambiente de mesmo nome.</p>
 *
 * <p>Chaves de configuracao e valores padrao (adequados apenas para
 * desenvolvimento local):</p>
 * <ul>
 *   <li>{@value #CHAVE_HOST} &mdash; host do RabbitMQ; padrao {@value #HOST_PADRAO};</li>
 *   <li>{@value #CHAVE_PORTA} &mdash; porta do RabbitMQ; padrao {@value #PORTA_PADRAO};</li>
 *   <li>{@value #CHAVE_USUARIO} &mdash; usuario; padrao {@value #USUARIO_PADRAO};</li>
 *   <li>{@value #CHAVE_SENHA} &mdash; senha; padrao {@value #SENHA_PADRAO};</li>
 *   <li>{@value #CHAVE_VIRTUAL_HOST} &mdash; virtual host; padrao {@value #VIRTUAL_HOST_PADRAO}.</li>
 * </ul>
 *
 * <p><strong>Nota sobre a senha padrao:</strong> {@value #USUARIO_PADRAO}/{@value #SENHA_PADRAO}
 * e a credencial <em>default</em> conhecida do RabbitMQ, usada aqui apenas como
 * conveniencia de <strong>desenvolvimento local</strong>. Nao e uma senha real de
 * producao nem um segredo fixado no codigo: em qualquer ambiente real, as
 * credenciais sao fornecidas pelo ambiente por meio das chaves acima.</p>
 *
 * <p>Classe utilitaria minima, sem novas abstracoes: apenas documenta as chaves
 * em um unico ponto e fornece acessores tipados.</p>
 */
public final class ConfiguracaoRabbitMq {

    /** Chave da propriedade/variavel de ambiente para o host do RabbitMQ. */
    public static final String CHAVE_HOST = "RABBITMQ_HOST";

    /** Chave da propriedade/variavel de ambiente para a porta do RabbitMQ. */
    public static final String CHAVE_PORTA = "RABBITMQ_PORT";

    /** Chave da propriedade/variavel de ambiente para o usuario do RabbitMQ. */
    public static final String CHAVE_USUARIO = "RABBITMQ_USERNAME";

    /** Chave da propriedade/variavel de ambiente para a senha do RabbitMQ. */
    public static final String CHAVE_SENHA = "RABBITMQ_PASSWORD";

    /** Chave da propriedade/variavel de ambiente para o virtual host do RabbitMQ. */
    public static final String CHAVE_VIRTUAL_HOST = "RABBITMQ_VIRTUAL_HOST";

    /** Host padrao para desenvolvimento local. */
    public static final String HOST_PADRAO = "localhost";

    /** Porta padrao para desenvolvimento local. */
    public static final String PORTA_PADRAO = "5672";

    /** Usuario padrao (default conhecido do RabbitMQ) para desenvolvimento local. */
    public static final String USUARIO_PADRAO = "guest";

    /** Senha padrao (default conhecido do RabbitMQ) apenas para desenvolvimento local. */
    public static final String SENHA_PADRAO = "guest";

    /** Virtual host padrao para desenvolvimento local. */
    public static final String VIRTUAL_HOST_PADRAO = "/";

    private ConfiguracaoRabbitMq() {
        // Classe utilitaria: nao deve ser instanciada.
    }

    /**
     * Host do RabbitMQ.
     *
     * @return o host configurado, ou {@value #HOST_PADRAO} quando ausente
     */
    public static String host() {
        return ConfiguracaoExterna.ler(CHAVE_HOST, HOST_PADRAO);
    }

    /**
     * Porta do RabbitMQ.
     *
     * @return a porta configurada, ou {@value #PORTA_PADRAO} quando ausente
     */
    public static int porta() {
        return Integer.parseInt(ConfiguracaoExterna.ler(CHAVE_PORTA, PORTA_PADRAO));
    }

    /**
     * Usuario do RabbitMQ.
     *
     * @return o usuario configurado, ou {@value #USUARIO_PADRAO} quando ausente
     */
    public static String usuario() {
        return ConfiguracaoExterna.ler(CHAVE_USUARIO, USUARIO_PADRAO);
    }

    /**
     * Senha do RabbitMQ (default de desenvolvimento local; credenciais reais vem
     * do ambiente).
     *
     * @return a senha configurada, ou {@value #SENHA_PADRAO} quando ausente
     */
    public static String senha() {
        return ConfiguracaoExterna.ler(CHAVE_SENHA, SENHA_PADRAO);
    }

    /**
     * Virtual host do RabbitMQ.
     *
     * @return o virtual host configurado, ou {@value #VIRTUAL_HOST_PADRAO} quando ausente
     */
    public static String virtualHost() {
        return ConfiguracaoExterna.ler(CHAVE_VIRTUAL_HOST, VIRTUAL_HOST_PADRAO);
    }
}
