package com.desafio.placar.web;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;
import com.desafio.placar.domain.excecao.JogoEncerradoException;
import com.desafio.placar.domain.excecao.NaoEncontradoException;
import com.desafio.placar.service.JogoService;

import jakarta.inject.Inject;

/**
 * Pagina inicial da Interface_Web: listagem de Jogos e filtro por status.
 *
 * <p>Lista os Jogos com dados completos vindos do PostgreSQL por meio do
 * {@link JogoService}, chamado diretamente no mesmo WAR/JVM (nunca a API REST).
 * Oferece um filtro por status com as opcoes "Todos" (todos os Jogos),
 * {@link Status#EM_ANDAMENTO} e {@link Status#ENCERRADO}. A selecao "Todos" e
 * modelada como {@code null}, que {@link JogoService#listar(Status)} interpreta
 * como "sem filtro".</p>
 *
 * <p>O filtro usa um formulario com submit sincrono simples (sem Ajax): ao
 * submeter, a pagina e re-renderizada e a lista e recarregada com o status
 * selecionado. Nao ha polling nem {@code AjaxSelfUpdatingTimerBehavior} (isso
 * pertence a Task 10), e nao se usa {@code obterPlacarAtual}: a listagem le
 * apenas via {@link JogoService#listar(Status)}.</p>
 *
 * <p>Um {@link FeedbackPanel} exibe mensagens ao usuario. As chamadas ao
 * servico sao envolvidas para que excecoes de dominio
 * ({@link EntradaInvalidaException}, {@link NaoEncontradoException},
 * {@link JogoEncerradoException}) sejam exibidas via {@code error(...)} em vez
 * de quebrar a pagina; erros inesperados sao registrados em log e exibidos de
 * forma generica, sem expor detalhes internos.</p>
 *
 * <p>A pagina hospeda o {@link CriarJogoPanel} (criacao de Jogo) e, por linha da
 * listagem, um {@link PlacarPanel} (atualizacao manual de placar e encerramento),
 * alem do {@link FeedbackPanel} para mensagens.</p>
 */
public class JogosPage extends WebPage {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(JogosPage.class.getName());

    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    private JogoService jogoService;

    /** Status selecionado no filtro; {@code null} representa "Todos". */
    private Status statusSelecionado;

    public JogosPage() {

        // Painel de mensagens (erros de dominio e mensagens genericas).
        // Tambem sera reutilizado pelas subtasks 9.3 e 9.4.
        add(new FeedbackPanel("feedback"));

        // Painel de criacao de Jogo (subtask 9.3). Chama JogoService.criar
        // diretamente; ao criar com sucesso, re-renderiza a pagina e a listagem
        // reflete o novo Jogo.
        add(new CriarJogoPanel("criarJogoPanel"));

        // Modelo carregado sob demanda: a cada renderizacao le a lista atual
        // via JogoService.listar(statusSelecionado) (null = Todos).
        IModel<List<Jogo>> jogosModel = new LoadableDetachableModel<List<Jogo>>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected List<Jogo> load() {
                return listarComTratamentoDeErro(statusSelecionado);
            }
        };

        // Formulario de filtro com submit sincrono simples (sem Ajax).
        Form<Void> filtroForm = new Form<>("filtroForm");

        List<Status> opcoesStatus = new ArrayList<>(Arrays.asList(Status.values()));
        DropDownChoice<Status> filtroStatus = new DropDownChoice<>(
                "filtroStatus",
                new PropertyModel<>(this, "statusSelecionado"),
                opcoesStatus);
        // null valido => opcao vazia renderizada como "Todos".
        filtroStatus.setNullValid(true);
        filtroForm.add(filtroStatus);
        add(filtroForm);

        // Tabela de jogos.
        ListView<Jogo> listaJogos = new ListView<Jogo>("jogos", jogosModel) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<Jogo> item) {
                Jogo jogo = item.getModelObject();
                item.add(new Label("id", jogo.getId()));
                item.add(new Label("timeA", jogo.getTimeA()));
                item.add(new Label("timeB", jogo.getTimeB()));
                item.add(new Label("placarA", jogo.getPlacarA()));
                item.add(new Label("placarB", jogo.getPlacarB()));
                item.add(new Label("status", jogo.getStatus().name()));
                item.add(new Label("dataHoraPartida", formatarDataHora(jogo.getDataHoraPartida())));
                // Acoes por Jogo: atualizar placar e encerrar (subtask 9.4).
                item.add(new PlacarPanel("placarPanel", jogo));
            }
        };
        add(listaJogos);
    }

    /**
     * Lista os Jogos tratando excecoes de dominio e erros inesperados.
     *
     * <p>Excecoes de dominio sao exibidas via {@code error(...)} (FeedbackPanel);
     * erros inesperados sao registrados em log e exibidos de forma generica.
     * Em qualquer falha retorna uma lista vazia para manter a pagina estavel.</p>
     *
     * @param filtro status usado como filtro; {@code null} = todos
     * @return a lista de Jogos ou lista vazia em caso de falha
     */
    private List<Jogo> listarComTratamentoDeErro(Status filtro) {
        try {
            return jogoService.listar(filtro);
        } catch (EntradaInvalidaException | NaoEncontradoException | JogoEncerradoException e) {
            error(e.getMessage());
            return new ArrayList<>();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falha inesperada ao listar jogos", e);
            error("Nao foi possivel carregar os jogos no momento. Tente novamente.");
            return new ArrayList<>();
        }
    }

    private static String formatarDataHora(OffsetDateTime dataHora) {
        if (dataHora == null) {
            return "";
        }
        return dataHora.format(FORMATO_DATA_HORA);
    }

    public Status getStatusSelecionado() {
        return statusSelecionado;
    }

    public void setStatusSelecionado(Status statusSelecionado) {
        this.statusSelecionado = statusSelecionado;
    }
}
