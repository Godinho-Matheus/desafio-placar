package com.desafio.placar.config;

/**
 * Centraliza, de forma documentada, o nome JNDI fixo do DataSource do PostgreSQL.
 *
 * <p>A conexao com o PostgreSQL <strong>nao</strong> e aberta pelo codigo da
 * aplicacao: ela e gerenciada pelo Payara por meio de um <em>DataSource</em>
 * (JDBC Connection Pool + JDBC Resource) e referenciada apenas por JNDI na
 * unidade de persistencia ({@code src/main/resources/META-INF/persistence.xml},
 * elemento {@code <jta-data-source>}). Assim, host, porta, nome do banco,
 * usuario e senha ficam <strong>fora do codigo-fonte</strong> (Requisito 13.2),
 * configurados no servidor de aplicacao.</p>
 *
 * <p>O nome JNDI do DataSource e <strong>fixo</strong> na aplicacao
 * ({@value #JNDI_DATASOURCE}); e o mesmo valor referenciado pelo
 * {@code <jta-data-source>} do {@code persistence.xml}. Esta classe apenas o
 * documenta em um unico ponto, sem novas abstracoes.</p>
 *
 * <p>Configuracao esperada no Payara (mantendo credenciais externas):</p>
 * <ul>
 *   <li>criar um <em>JDBC Connection Pool</em> para o PostgreSQL com host,
 *       porta, nome do banco, usuario e senha do ambiente;</li>
 *   <li>criar um <em>JDBC Resource</em> apontando para esse pool com o nome
 *       JNDI {@value #JNDI_DATASOURCE}, o mesmo referenciado pelo
 *       {@code <jta-data-source>} do {@code persistence.xml}.</li>
 * </ul>
 */
public final class ConfiguracaoBancoDados {

    /**
     * Nome JNDI fixo do DataSource do PostgreSQL, referenciado pelo
     * {@code <jta-data-source>} do {@code persistence.xml}.
     */
    public static final String JNDI_DATASOURCE = "jdbc/placar";

    private ConfiguracaoBancoDados() {
        // Classe utilitaria: nao deve ser instanciada.
    }
}
