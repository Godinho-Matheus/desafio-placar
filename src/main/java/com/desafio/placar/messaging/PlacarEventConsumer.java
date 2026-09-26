package com.desafio.placar.messaging;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.desafio.placar.cache.PlacarCache;
import com.desafio.placar.config.ConfiguracaoRabbitMq;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * Consumidor do {@code Evento_Placar} no RabbitMQ que reflete o placar no Redis.
 *
 * <p>Bean de inicializacao {@link Singleton} anotado com {@link Startup}, de modo
 * que o container o instancia <strong>ansiosamente</strong> no deploy (diferente
 * de um bean CDI {@code @ApplicationScoped}, que seria preguicoso e so comecaria a
 * consumir no primeiro uso). Isso garante que o consumo da fila comece assim que a
 * aplicacao sobe no Payara.</p>
 *
 * <p><strong>Responsabilidade unica:</strong> conectar ao RabbitMQ, consumir
 * mensagens da fila {@link PlacarEventPublisher#QUEUE}, desserializar o JSON em
 * {@link EventoPlacar} e gravar {@code placarA}/{@code placarB} no Redis via
 * {@link PlacarCache#atualizar(Long, int, int)}. Nao aplica regras de negocio e
 * <strong>nunca</strong> acessa o {@code JogoRepository} nem o PostgreSQL: sua
 * responsabilidade termina em {@code EventoPlacar -> PlacarCache.atualizar(...)}.</p>
 *
 * <p><strong>Ciclo de vida:</strong> o {@link PostConstruct} abre uma unica
 * {@link Connection} e um unico {@link Channel} dedicados, mantidos abertos por
 * toda a vida da aplicacao (um consumidor precisa continuar ouvindo, entao, ao
 * contrario do publisher, o canal permanece aberto). A topologia e declarada de
 * forma idempotente reutilizando as mesmas constantes do
 * {@link PlacarEventPublisher} (exchange, fila e binding), evitando literais
 * duplicados. Em seguida inicia o consumo com {@code basicConsume(...)}. O
 * {@link PreDestroy} fecha canal e conexao, liberando os recursos.</p>
 *
 * <p><strong>Fluxo da mensageria:</strong> {@link PlacarAtualizadoEvent} &rarr;
 * AFTER_SUCCESS (observer) &rarr; {@link PlacarEventPublisher} &rarr; RabbitMQ
 * &rarr; {@code PlacarEventConsumer} &rarr; {@link PlacarCache} &rarr; Redis.</p>
 *
 * <p><strong>Ack:</strong> usa {@code auto-ack}, mantendo o consumidor simples e
 * coerente com o modelo de consistencia eventual do Design (sem retry,
 * dead-letter, deduplicacao, reprocessamento nem Outbox). Como
 * {@link PlacarCache#atualizar(Long, int, int)} ja trata falhas de Redis
 * internamente (loga e nao propaga), a perda de uma mensagem em uma falha rara de
 * Redis e aceitavel: a proxima publicacao bem-sucedida atualiza o Redis e o
 * PostgreSQL permanece como fonte de verdade.</p>
 *
 * <p><strong>Mensagem invalida:</strong> uma mensagem malformada nao derruba o
 * consumidor. O processamento e envolvido em {@code try/catch}: qualquer
 * {@link RuntimeException} (incluindo erros de parse do JSON-P) e apenas
 * registrada em log e o consumidor continua recebendo as proximas mensagens.</p>
 *
 * <p><strong>Falha na inicializacao:</strong> se o RabbitMQ estiver indisponivel
 * no deploy, a falha e apenas registrada em log (WARNING) e a conexao/canal ficam
 * nulos, sem relancar de forma a inviabilizar o deploy. Nao ha agendador de
 * reconexao, retry ou backoff customizados alem do que o proprio cliente RabbitMQ
 * oferece nativamente.</p>
 */
@Singleton
@Startup
public class PlacarEventConsumer {

    private static final Logger LOG = Logger.getLogger(PlacarEventConsumer.class.getName());

    private final PlacarCache placarCache;

    private Connection connection;

    private Channel channel;

    /**
     * Construtor padrao exigido pelo container.
     */
    public PlacarEventConsumer() {
        this.placarCache = null;
    }

    /**
     * Cria o consumidor com o cache de placar.
     *
     * @param placarCache cache Redis onde o placar recebido sera gravado
     */
    @Inject
    public PlacarEventConsumer(PlacarCache placarCache) {
        this.placarCache = placarCache;
    }

    /**
     * Abre a conexao/canal dedicados, declara a topologia de forma idempotente
     * (reutilizando as constantes do {@link PlacarEventPublisher}) e inicia o
     * consumo com {@code auto-ack}.
     *
     * <p>Se o RabbitMQ estiver indisponivel, apenas registra a falha em log e
     * mantem conexao/canal nulos, sem inviabilizar o deploy. Nao ha mecanismo de
     * reconexao customizado.</p>
     */
    @PostConstruct
    void iniciar() {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(ConfiguracaoRabbitMq.host());
            factory.setPort(ConfiguracaoRabbitMq.porta());
            factory.setUsername(ConfiguracaoRabbitMq.usuario());
            factory.setPassword(ConfiguracaoRabbitMq.senha());
            factory.setVirtualHost(ConfiguracaoRabbitMq.virtualHost());

            this.connection = factory.newConnection();
            this.channel = connection.createChannel();

            channel.exchangeDeclare(PlacarEventPublisher.EXCHANGE, BuiltinExchangeType.DIRECT, true);
            channel.queueDeclare(PlacarEventPublisher.QUEUE, true, false, false, null);
            channel.queueBind(PlacarEventPublisher.QUEUE, PlacarEventPublisher.EXCHANGE,
                    PlacarEventPublisher.ROUTING_KEY);

            boolean autoAck = true;
            DeliverCallback deliverCallback = (consumerTag, delivery) -> processar(delivery.getBody());
            channel.basicConsume(PlacarEventPublisher.QUEUE, autoAck, deliverCallback,
                    consumerTag -> LOG.log(Level.INFO, "Consumidor de placar cancelado: {0}", consumerTag));
        } catch (IOException | TimeoutException e) {
            LOG.log(Level.WARNING,
                    "RabbitMQ indisponivel na inicializacao; o consumidor de placar nao foi iniciado",
                    e);
            this.connection = null;
            this.channel = null;
        }
    }

    /**
     * Fecha o canal e a conexao com o RabbitMQ no encerramento da aplicacao,
     * liberando os recursos. Cada fechamento e protegido contra nulo e estado
     * fechado, com eventuais falhas apenas registradas em log.
     */
    @PreDestroy
    void encerrar() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                LOG.log(Level.WARNING, "Falha ao fechar o canal do RabbitMQ", e);
            }
        }
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Falha ao fechar a conexao com o RabbitMQ", e);
            }
        }
    }

    /**
     * Processa o corpo de uma mensagem recebida: desserializa o JSON em
     * {@link EventoPlacar} e grava o placar no Redis via
     * {@link PlacarCache#atualizar(Long, int, int)}.
     *
     * <p>Qualquer falha (mensagem malformada, erro de parse ou de integracao) e
     * apenas registrada em log, sem propagar, para que o consumidor continue
     * recebendo as proximas mensagens.</p>
     *
     * @param body corpo bruto da mensagem, JSON em UTF-8
     */
    private void processar(byte[] body) {
        try {
            String json = new String(body, StandardCharsets.UTF_8);
            EventoPlacar evento = desserializar(json);
            placarCache.atualizar(evento.jogoId(), evento.placarA(), evento.placarB());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Mensagem de placar invalida ignorada", e);
        }
    }

    /**
     * Desserializa o JSON {@code {"jogoId":10,"placarA":2,"placarB":1}} em
     * {@link EventoPlacar}, via JSON-P.
     */
    private static EventoPlacar desserializar(String json) {
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            JsonObject obj = reader.readObject();
            Long jogoId = obj.getJsonNumber("jogoId").longValue();
            int placarA = obj.getInt("placarA");
            int placarB = obj.getInt("placarB");
            return new EventoPlacar(jogoId, placarA, placarB);
        }
    }
}
