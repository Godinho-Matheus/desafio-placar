package com.desafio.placar.web;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;
import com.desafio.placar.domain.excecao.JogoEncerradoException;
import com.desafio.placar.domain.excecao.NaoEncontradoException;
import com.desafio.placar.service.JogoService;

import jakarta.inject.Inject;

/**
 * Painel Wicket para criacao de um Jogo na Interface_Web.
 *
 * <p>Oferece um formulario com submit sincrono simples (sem Ajax) para informar
 * {@code timeA}, {@code timeB} e a data/hora da partida. Ao confirmar, solicita
 * a criacao ao {@link JogoService#criar(String, String, OffsetDateTime)},
 * chamado diretamente no mesmo WAR/JVM (nunca a API REST). Em caso de sucesso, o
 * Jogo e criado com placar 0 x 0 e Status {@code EM_ANDAMENTO}, uma mensagem de
 * confirmacao e exibida e a pagina e re-renderizada para que a listagem reflita
 * o novo Jogo.</p>
 *
 * <p><strong>Entrada amigavel de data/hora (apenas UX).</strong> Em vez de exigir
 * que o usuario digite uma string ISO-8601 com offset, a data e a hora sao
 * capturadas por um campo {@code <input type="datetime-local">} (formato local
 * {@code yyyy-MM-ddTHH:mm}, sem offset) e o fuso e escolhido em um seletor de
 * offset UTC separado, com {@code -03:00} pre-selecionado. No submit os dois
 * valores sao combinados em um {@link OffsetDateTime} — exatamente o mesmo tipo
 * enviado ao {@link JogoService} anteriormente. A conversao e feita com
 * {@link LocalDateTime#parse(CharSequence)} + {@link ZoneOffset#of(String)} via
 * {@link OffsetDateTime#of(LocalDateTime, ZoneOffset)}. Quando a data/hora e
 * ausente ou nao esta no formato esperado, uma mensagem amigavel e exibida e o
 * servico nao e chamado. Essa validacao e apenas de UX; as regras definitivas de
 * obrigatoriedade permanecem no {@link JogoService}.</p>
 *
 * <p>As mensagens de sucesso ({@code info}) e de erro ({@code error}) sao
 * emitidas no nivel do componente e naturalmente exibidas pelo
 * {@code FeedbackPanel} da pagina. Excecoes de dominio sao tratadas e exibidas
 * sem expor detalhes internos; erros inesperados sao registrados em log e
 * exibidos de forma generica.</p>
 */
public class CriarJogoPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CriarJogoPanel.class.getName());

    /**
     * Offset UTC padrao pre-selecionado no formulario (horario de Brasilia sem
     * horario de verao). Mantido como texto no formato aceito por
     * {@link ZoneOffset#of(String)}.
     */
    static final String OFFSET_PADRAO = "-03:00";

    /**
     * Opcoes de offset UTC oferecidas no seletor. Lista curta e legivel, cobrindo
     * os fusos brasileiros mais comuns e alguns de referencia. Todos os valores
     * sao aceitos por {@link ZoneOffset#of(String)}.
     */
    static final List<String> OFFSETS_DISPONIVEIS = List.of(
            "-05:00", "-04:00", "-03:00", "-02:00", "+00:00", "+01:00");

    @Inject
    private JogoService jogoService;

    private final Model<String> timeAModel = Model.of("");
    private final Model<String> timeBModel = Model.of("");

    /**
     * Data/hora local (sem offset), no formato de {@code <input type="datetime-local">}:
     * {@code yyyy-MM-ddTHH:mm} (ou com segundos). Combinada com {@link #offsetModel}
     * no submit.
     */
    private final Model<String> dataHoraLocalModel = Model.of("");

    /** Offset UTC selecionado; {@link #OFFSET_PADRAO} por padrao. */
    private final Model<String> offsetModel = Model.of(OFFSET_PADRAO);

    public CriarJogoPanel(String id) {
        super(id);

        Form<Void> form = new Form<Void>("criarJogoForm") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit() {
                criarJogo();
            }
        };

        TextField<String> timeA = new TextField<>("timeA", timeAModel);
        timeA.setRequired(true);
        form.add(timeA);

        TextField<String> timeB = new TextField<>("timeB", timeBModel);
        timeB.setRequired(true);
        form.add(timeB);

        // Data/hora local via <input type="datetime-local"> (o navegador oferece o
        // seletor nativo de data/hora). O TextField padrao do Wicket so aceita os
        // tipos "text"/"search"; por isso sobrescrevemos getInputTypes() para
        // permitir "datetime-local", mantendo o valor como texto no modelo.
        TextField<String> dataHoraPartida = new TextField<String>("dataHoraPartida", dataHoraLocalModel) {
            private static final long serialVersionUID = 1L;

            @Override
            protected String[] getInputTypes() {
                return new String[] { "datetime-local" };
            }
        };
        form.add(dataHoraPartida);

        // Seletor de offset UTC, com -03:00 pre-selecionado (via offsetModel).
        DropDownChoice<String> offset = new DropDownChoice<>(
                "offset", offsetModel, OFFSETS_DISPONIVEIS);
        // Sempre ha um offset selecionado (default -03:00); nao permitir vazio.
        offset.setNullValid(false);
        form.add(offset);

        add(form);
    }

    /**
     * Trata o submit sincrono do formulario de criacao.
     *
     * <p>Combina a data/hora local com o offset selecionado em um
     * {@link OffsetDateTime}, solicita a criacao ao {@link JogoService} e exibe o
     * resultado no FeedbackPanel. Em falha de conversao ou excecao de dominio,
     * exibe mensagem amigavel e nao quebra a pagina.</p>
     */
    private void criarJogo() {
        OffsetDateTime dataHoraPartida;
        try {
            dataHoraPartida = combinarDataHora(dataHoraLocalModel.getObject(), offsetModel.getObject());
        } catch (DateTimeException | NullPointerException e) {
            error("Informe a data e a hora da partida e selecione o fuso (offset UTC).");
            return;
        }

        try {
            Jogo jogo = jogoService.criar(
                    timeAModel.getObject(),
                    timeBModel.getObject(),
                    dataHoraPartida);

            info("Jogo criado (id " + jogo.getId() + "): " + jogo.getTimeA()
                    + " x " + jogo.getTimeB() + " (0 x 0, EM_ANDAMENTO).");

            // Limpa os campos para uma nova criacao (offset volta ao padrao).
            timeAModel.setObject("");
            timeBModel.setObject("");
            dataHoraLocalModel.setObject("");
            offsetModel.setObject(OFFSET_PADRAO);

            // Re-renderiza a pagina inicial: a listagem usa LoadableDetachableModel
            // e naturalmente refletira o novo Jogo.
            setResponsePage(JogosPage.class);
        } catch (EntradaInvalidaException | NaoEncontradoException | JogoEncerradoException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falha inesperada ao criar jogo", e);
            error("Nao foi possivel criar o jogo no momento. Tente novamente.");
        }
    }

    /**
     * Combina a data/hora local (formato de {@code datetime-local}) com o offset
     * UTC selecionado em um {@link OffsetDateTime}.
     *
     * <p>Produz o mesmo tipo/semantica que antes era obtido de
     * {@code OffsetDateTime.parse("...T...-03:00")}: por exemplo,
     * {@code "2025-01-01T20:00"} + {@code "-03:00"} resulta em
     * {@code 2025-01-01T20:00-03:00}. Aceita tanto {@code yyyy-MM-ddTHH:mm} quanto
     * {@code yyyy-MM-ddTHH:mm:ss} (ambos parseaveis por
     * {@link LocalDateTime#parse(CharSequence)}).</p>
     *
     * @param dataHoraLocal texto da data/hora local (sem offset)
     * @param offset        texto do offset UTC (ex.: {@code -03:00})
     * @return o {@link OffsetDateTime} combinado
     * @throws DateTimeParseException se a data/hora local for invalida
     * @throws DateTimeException      se o offset for invalido
     * @throws NullPointerException   se algum valor for nulo
     */
    static OffsetDateTime combinarDataHora(String dataHoraLocal, String offset) {
        LocalDateTime local = LocalDateTime.parse(dataHoraLocal.trim());
        ZoneOffset zoneOffset = ZoneOffset.of(offset.trim());
        return OffsetDateTime.of(local, zoneOffset);
    }
}
