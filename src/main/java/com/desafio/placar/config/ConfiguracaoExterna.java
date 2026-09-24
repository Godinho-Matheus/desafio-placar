package com.desafio.placar.config;

import java.util.Optional;

/**
 * Ponto central para leitura de configuracao externa do Sistema.
 *
 * <p>Esqueleto minimo que centraliza, para as tarefas futuras, a leitura de
 * propriedades e variaveis de ambiente (por exemplo, dados de conexao com
 * PostgreSQL, Redis e RabbitMQ). Nenhuma credencial, host, porta ou valor
 * sensivel e fixado no codigo: os valores sao sempre resolvidos em tempo de
 * execucao a partir do ambiente.</p>
 *
 * <p>A resolucao usa recursos nativos da plataforma (variaveis de ambiente e
 * propriedades de sistema da JVM), mantendo a solucao simples e compativel com
 * Jakarta EE/Payara, sem dependencias adicionais. A ordem de precedencia e:
 * propriedade de sistema ({@link System#getProperty(String)}) e, na ausencia
 * dela, variavel de ambiente ({@link System#getenv(String)}).</p>
 *
 * <p>As chaves concretas de configuracao (PostgreSQL, Redis, RabbitMQ) serao
 * definidas nas tarefas correspondentes da Spec.</p>
 */
public final class ConfiguracaoExterna {

    private ConfiguracaoExterna() {
        // Classe utilitaria: nao deve ser instanciada.
    }

    /**
     * Le um valor de configuracao a partir do ambiente.
     *
     * <p>Consulta primeiro a propriedade de sistema com a chave informada e, se
     * ausente ou vazia, a variavel de ambiente de mesmo nome.</p>
     *
     * @param chave nome da propriedade de sistema / variavel de ambiente
     * @return o valor encontrado, ou {@link Optional#empty()} se nao definido
     */
    public static Optional<String> ler(String chave) {
        String valor = System.getProperty(chave);
        if (valor == null || valor.isBlank()) {
            valor = System.getenv(chave);
        }
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(valor);
    }

    /**
     * Le um valor de configuracao, retornando um valor padrao quando ausente.
     *
     * @param chave        nome da propriedade de sistema / variavel de ambiente
     * @param valorPadrao  valor retornado quando a chave nao estiver definida
     * @return o valor configurado, ou {@code valorPadrao} se ausente
     */
    public static String ler(String chave, String valorPadrao) {
        return ler(chave).orElse(valorPadrao);
    }
}
