package com.desafio.placar.web;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * {@code timeA}, {@code timeB} e {@code dataHoraPartida}. Ao confirmar, solicita
 * a criacao ao {@link JogoService#criar(String, String, OffsetDateTime)},
 * chamado diretamente no mesmo WAR/JVM (nunca a API REST). Em caso de sucesso, o
 * Jogo e criado com placar 0 x 0 e Status {@code EM_ANDAMENTO}, uma mensagem de
 * confirmacao e exibida e a pagina e re-renderizada para que a listagem reflita
 * o novo Jogo.</p>
 *
 * <p>O campo {@code dataHoraPartida} e capturado como texto ISO-8601 com offset
 * (por exemplo {@code 2025-01-01T20:00:00-03:00}) e convertido em
 * {@link OffsetDateTime} via {@link OffsetDateTime#parse(CharSequence)}. Quando o
 * texto e ausente ou nao esta no formato esperado, uma mensagem amigavel e
 * exibida e o servico nao e chamado. Essa validacao e apenas de UX; as regras
 * definitivas de obrigatoriedade permanecem no {@link JogoService}.</p>
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

    @Inject
    private JogoService jogoService;

    private final Model<String> timeAModel = Model.of("");
    private final Model<String> timeBModel = Model.of("");
    private final Model<String> dataHoraModel = Model.of("");

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

        // dataHoraPartida capturada como texto ISO-8601 com offset e convertida
        // em OffsetDateTime no submit (abordagem simples e sem dependencias extra).
        TextField<String> dataHoraPartida = new TextField<>("dataHoraPartida", dataHoraModel);
        form.add(dataHoraPartida);

        add(form);
    }

    /**
     * Trata o submit sincrono do formulario de criacao.
     *
     * <p>Converte o texto da data/hora em {@link OffsetDateTime}, solicita a
     * criacao ao {@link JogoService} e exibe o resultado no FeedbackPanel. Em
     * falha de conversao ou excecao de dominio, exibe mensagem amigavel e nao
     * quebra a pagina.</p>
     */
    private void criarJogo() {
        OffsetDateTime dataHoraPartida;
        try {
            dataHoraPartida = OffsetDateTime.parse(dataHoraModel.getObject().trim());
        } catch (DateTimeParseException | NullPointerException e) {
            error("Informe a data/hora da partida no formato ISO-8601 com offset, "
                    + "por exemplo 2025-01-01T20:00:00-03:00.");
            return;
        }

        try {
            Jogo jogo = jogoService.criar(
                    timeAModel.getObject(),
                    timeBModel.getObject(),
                    dataHoraPartida);

            info("Jogo criado (id " + jogo.getId() + "): " + jogo.getTimeA()
                    + " x " + jogo.getTimeB() + " (0 x 0, EM_ANDAMENTO).");

            // Limpa os campos para uma nova criacao.
            timeAModel.setObject("");
            timeBModel.setObject("");
            dataHoraModel.setObject("");

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
}
