package com.desafio.placar.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.desafio.placar.config.ConfiguracaoRabbitMq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;

/**
 * Publicador do {@code Evento_Placar} no RabbitMQ.
 *
 * <p>Bean CDI {@link ApplicationScoped} cuja unica responsabilidade e publicar um
 * {@link EventoPlacar} na topologia direct do Design:</p>
 * <ul>
 *   <li>exchange {@value #EXCHANGE} (tipo {@code direct});</li>
 *   <li>routing key {@value #ROUTING_KEY};</li>
 *   <li>queue {@value #QUEUE}, vinculada ao exchange pela routing key.</li>
 * </ul>
 *
 * <p><strong>Ciclo de vida da conexao:</strong> mantem uma unica
 * {@link Connection} reutilizada durante toda a vida da aplicacao. A conexao e
 * criada no {@link PostConstruct}; se o broker estiver indisponivel na
 * inicializacao, a falha e apenas registrada em log e a conexao fica nula (para
 * nao inviabilizar o deploy), sendo (re)estabelecida de forma preguicosa no
 * primeiro {@link #publicar(EventoPlacar)}. A conexao e fechada no
 * {@link PreDestroy}. Nao ha retry/backoff.</p>
 *
 * <p>Cada publicacao abre seu proprio {@link Channel} com try-with-resources: a
 * {@code Connection} do RabbitMQ e thread-safe e os canais nao precisam ser
 * compartilhados entre threads, o que mantem a solucao simples e segura no Payara.
 * As declaracoes de exchange/queue/binding sao idempotentes e feitas no canal
 * antes de publicar.</p>
 *
 * <p><strong>Tratamento de erro:</strong> falhas na publicacao <strong>nao</strong>
 * sao silenciadas. As excecoes verificadas do cliente RabbitMQ sao encapsuladas em
 * {@link RuntimeException} e <strong>propagadas</strong> ao chamador (o observer
 * AFTER_SUCCESS as tratara de forma independente, conforme Tarefa 7.3). Nao ha
 * confirmacao de publicacao, dead-letter, TTL nem Outbox.</p>
 */
@ApplicationScoped
public class PlacarEventPublisher {

    private static final Logger LOG = Logger.getLogger(PlacarEventPublisher.class.getName());

    /** Exchange direct de destino das publicacoes de placar. */
    public static final String EXCHANGE = "placar.exchange";

    /** Routing key usada para publicar e vincular a fila ao exchange. */
    public static final String ROUTING_KEY = "placar.atualizado";

    /** Fila que recebe as mensagens de placar atualizado. */
    public static final String QUEUE = "placar.atualizado.queue";

    private Connection connection;

    /**
     * Tenta abrir a conexao unica com o RabbitMQ na inicializacao do bean.
     *
     * <p>Se o broker estiver inacessivel, apenas registra a falha em log e mantem
     * a conexao nula, sem relancar: assim o deploy nao e inviabilizado. A conexao
     * sera criada de forma preguicosa na primeira publicacao.</p>
     */
    @PostConstruct
    void iniciar() {
        try {
            this.connection = novaConexao();
        } catch (IOException | TimeoutException e) {
            LOG.log(Level.WARNING,
                    "RabbitMQ indisponivel na inicializacao; a conexao sera criada na primeira publicacao",
                    e);
            this.connection = null;
        }
    }

    /**
     * Fecha a conexao com o RabbitMQ no encerramento da aplicacao.
     */
    @PreDestroy
    void encerrar() {
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Falha ao fechar a conexao com o RabbitMQ", e);
            }
        }
    }

    /**
     * Publica o {@link EventoPlacar} no RabbitMQ.
     *
     * <p>Garante uma conexao viva (criando-a de forma preguicosa se necessario),
     * abre um canal dedicado, declara a topologia de forma idempotente, serializa
     * o evento em JSON UTF-8 e publica com a routing key {@value #ROUTING_KEY}.</p>
     *
     * @param evento evento de placar a publicar
     * @throws RuntimeException se a publicacao falhar (falha propagada ao chamador)
     */
    public void publicar(EventoPlacar evento) {
        try {
            Connection conexao = garantirConexao();
            try (Channel channel = conexao.createChannel()) {
                channel.exchangeDeclare(EXCHANGE, BuiltinExchangeType.DIRECT, true);
                channel.queueDeclare(QUEUE, true, false, false, null);
                channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);

                byte[] corpo = serializar(evento).getBytes(StandardCharsets.UTF_8);
                channel.basicPublish(EXCHANGE, ROUTING_KEY, null, corpo);
            }
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException("Falha ao publicar EventoPlacar no RabbitMQ", e);
        }
    }

    /**
     * Garante uma {@link Connection} viva, (re)criando-a se estiver nula ou fechada.
     */
    private synchronized Connection garantirConexao() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = novaConexao();
        }
        return connection;
    }

    /**
     * Cria uma nova {@link Connection} a partir da configuracao externa do RabbitMQ.
     */
    private static Connection novaConexao() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(ConfiguracaoRabbitMq.host());
        factory.setPort(ConfiguracaoRabbitMq.porta());
        factory.setUsername(ConfiguracaoRabbitMq.usuario());
        factory.setPassword(ConfiguracaoRabbitMq.senha());
        factory.setVirtualHost(ConfiguracaoRabbitMq.virtualHost());
        return factory.newConnection();
    }

    /**
     * Serializa o {@link EventoPlacar} em JSON via JSON-P, no formato
     * {@code {"jogoId":10,"placarA":2,"placarB":1}}.
     */
    private static String serializar(EventoPlacar evento) {
        return Json.createObjectBuilder()
                .add("jogoId", evento.jogoId())
                .add("placarA", evento.placarA())
                .add("placarB", evento.placarB())
                .build()
                .toString();
    }
}
