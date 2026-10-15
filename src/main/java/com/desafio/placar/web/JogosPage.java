package com.desafio.placar.web;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;

import com.desafio.placar.cache.PlacarAtual;
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
 * selecionado. A carga inicial da tabela (e cada re-renderizacao completa) le
 * o estado completo dos Jogos via {@link JogoService#listar(Status)} (fonte de
 * verdade PostgreSQL).</p>
 *
 * <p>Atualizacao automatica dos placares (polling): a pagina cria um componente
 * auxiliar dedicado e neutro ({@code pollingTrigger}, um
 * {@link org.apache.wicket.markup.html.WebMarkupContainer} fora da tabela e fora
 * de todos os formularios) cuja unica funcao e hospedar o
 * {@link AjaxSelfUpdatingTimerBehavior}. O behavior adiciona/re-renderiza
 * automaticamente o seu componente hospedeiro a cada ciclo; por isso ele fica em
 * {@code pollingTrigger} (e <strong>nao</strong> no {@link ListView}), garantindo
 * que a lista, as linhas e os {@link PlacarPanel} nunca sejam re-renderizados pelo
 * polling. A cada ciclo de {@link #INTERVALO_ATUALIZACAO} (intervalo configuravel,
 * centralizado em constante e sem SLA numerico), o timer atualiza
 * <strong>apenas</strong> os placares exibidos, sem recarregamento manual da
 * pagina. Para cada linha ja renderizada chama {@link JogoService#obterPlacarAtual(Long)}
 * (Redis primeiro, com fallback no PostgreSQL <em>dentro</em> do servico) e
 * adiciona ao {@link AjaxRequestTarget} somente os rotulos de exibicao de
 * {@code placarA}/{@code placarB} que mudaram. O polling <strong>nao</strong>
 * chama {@code listar(...)}, nao recria linhas nem o {@link PlacarPanel}, nao
 * altera o filtro selecionado e nunca acessa Redis/{@code PlacarCache}
 * diretamente. O status faz parte do estado completo do Jogo no PostgreSQL e e
 * atualizado pelas operacoes da interface (criacao, atualizacao de placar,
 * encerramento), nunca pelo polling.</p>
 *
 * <p>Como o polling repinta somente os rotulos de exibicao (somente leitura),
 * os campos editaveis de placar do {@link PlacarPanel} nao sao recriados nem
 * sobrescritos: valores digitados e ainda nao submetidos permanecem intactos.
 * Se {@link JogoService#obterPlacarAtual(Long)} falhar para um Jogo em um ciclo,
 * o erro e apenas registrado em log e o ultimo placar visivel e mantido, sem
 * interromper o timer nos ciclos seguintes.</p>
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

    /** Locale do portugues (Brasil) oferecido no seletor de idioma. */
    private static final Locale LOCALE_PT = Locale.of("pt", "BR");

    /**
     * Intervalo (configuravel, sem SLA) entre os ciclos de atualizacao automatica
     * dos placares exibidos. Centralizado aqui como constante simples e legivel;
     * o {@link AjaxSelfUpdatingTimerBehavior} e o dono do ciclo periodico (nao ha
     * scheduler proprio). O valor e um padrao razoavel, nao uma exigencia de SLA.
     */
    private static final Duration INTERVALO_ATUALIZACAO = Duration.ofSeconds(5);

    @Inject
    private JogoService jogoService;

    /** Status selecionado no filtro; {@code null} representa "Todos". */
    private Status statusSelecionado;

    /**
     * Construtor publico sem argumentos usado pelo Wicket em producao.
     *
     * <p>O Wicket cria a pagina por este construtor e, em seguida, a integracao
     * Wicket-CDI injeta o campo {@link #jogoService} (anotado com {@code @Inject})
     * <em>apos</em> a construcao. Portanto {@code jogoService} pode ser {@code null}
     * durante a construcao: o corpo do construtor nao o dereferencia; o
     * {@link LoadableDetachableModel} e o callback do timer so o utilizam depois,
     * em tempo de renderizacao/Ajax, quando o CDI ja injetou a dependencia.</p>
     */
    public JogosPage() {
        this(null);
    }

    /**
     * Construtor de teste (package-private, sem {@code @Inject}) que recebe o
     * {@link JogoService} diretamente, permitindo testes de UI com WicketTester
     * sem um container CDI. Nao altera o comportamento da pagina: em producao o
     * Wicket usa o construtor publico sem argumentos e o CDI injeta o campo
     * {@link #jogoService} apos a construcao.
     *
     * @param jogoService o servico a ser usado pela pagina (em testes); em
     *                    producao chega {@code null} aqui e o campo e preenchido
     *                    depois pela injecao de CDI
     */
    JogosPage(JogoService jogoService) {
        // Em producao chega null aqui (via JogosPage()); nesse caso NAO sobrescreve
        // o campo, deixando a injecao Wicket-CDI preencher jogoService apos a
        // construcao. Em testes chega um servico concreto e ele e atribuido.
        if (jogoService != null) {
            this.jogoService = jogoService;
        }

        // Seletor de idioma (i18n): troca o locale da sessao (persistido na
        // PlacarSession) e re-renderiza a pagina para refletir o novo idioma em
        // toda a interface. Submit sincrono simples, no mesmo padrao dos demais
        // formularios da pagina (sem Ajax).
        add(criarFormularioIdioma());

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

        // Renderer que exibe o Status TRADUZIDO (chave status.<NOME>), mantendo o
        // enum do dominio como valor real do modelo (sem alterar o backend).
        IChoiceRenderer<Status> statusRenderer = new ChoiceRenderer<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Object getDisplayValue(Status status) {
                return getString("status." + status.name());
            }

            @Override
            public String getIdValue(Status status, int index) {
                return status.name();
            }
        };

        DropDownChoice<Status> filtroStatus = new DropDownChoice<>(
                "filtroStatus",
                new PropertyModel<>(this, "statusSelecionado"),
                opcoesStatus,
                statusRenderer);
        // null valido => opcao vazia renderizada como "Todos"/"All". O texto da
        // opcao nula e resolvido pelo Wicket via a chave de recurso "nullValid"
        // (definida nos bundles da aplicacao), portanto segue o locale corrente.
        filtroStatus.setNullValid(true);
        filtroForm.add(filtroStatus);
        add(filtroForm);

        // Tabela de jogos.
        //
        // A carga inicial e cada re-renderizacao completa leem o estado completo
        // (incluindo status) via JogoService.listar(...). Os rotulos de EXIBICAO
        // dos placares sao respaldados por modelos mutaveis por linha (holders),
        // atualizados apenas pelo polling; assim o timer (hospedado no
        // pollingTrigger) repinta somente esses rotulos sem recriar a linha nem o
        // PlacarPanel. O ListView nao e re-renderizado pelo ciclo Ajax.
        ListView<Jogo> listaJogos = new ListView<Jogo>("jogos", jogosModel) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<Jogo> item) {
                Jogo jogo = item.getModelObject();
                item.add(new Label("id", jogo.getId()));
                item.add(new Label("timeA", jogo.getTimeA()));
                item.add(new Label("timeB", jogo.getTimeB()));

                // Holders mutaveis por linha para os placares exibidos. O polling
                // atualiza o valor destes modelos e repinta apenas seus rotulos.
                Model<Integer> placarAModel = Model.of(jogo.getPlacarA());
                Model<Integer> placarBModel = Model.of(jogo.getPlacarB());

                Label placarALabel = new Label("placarA", placarAModel);
                Label placarBLabel = new Label("placarB", placarBModel);
                // Necessario para que o AjaxRequestTarget consiga referenciar/repintar.
                placarALabel.setOutputMarkupId(true);
                placarBLabel.setOutputMarkupId(true);
                item.add(placarALabel);
                item.add(placarBLabel);

                // Status exibido TRADUZIDO (camada de apresentacao). A chave segue
                // o padrao "status.<NOME_DO_ENUM>" (ex.: status.EM_ANDAMENTO); o enum
                // do dominio permanece inalterado.
                item.add(new Label("status", getString("status." + jogo.getStatus().name())));
                item.add(new Label("dataHoraPartida", formatarDataHora(jogo.getDataHoraPartida())));
                // Acoes por Jogo: atualizar placar e encerrar (subtask 9.4).
                // Repassa o jogoService da pagina ao painel. Em producao, no momento
                // do populateItem (tempo de render) o campo ja foi injetado pelo CDI;
                // em testes, foi atribuido pelo construtor de teste da JogosPage.
                item.add(new PlacarPanel("placarPanel", jogo, jogoService));
            }
        };
        add(listaJogos);

        // Polling automatico dos placares exibidos (Requisito 8.1).
        //
        // O AjaxSelfUpdatingTimerBehavior adiciona/re-renderiza automaticamente o
        // seu componente hospedeiro no AjaxRequestTarget a cada ciclo (antes de
        // onPostProcessTarget). Por isso ele NAO pode ficar no ListView: isso
        // repintaria as linhas e os PlacarPanel, apagando valores que o usuario
        // ainda esteja digitando nos campos editaveis. Hospedamos o timer num
        // componente auxiliar dedicado e neutro (pollingTrigger), fora da tabela e
        // fora dos formularios; assim o ciclo re-renderiza apenas esse componente
        // e, em onPostProcessTarget, adicionamos ao alvo APENAS os rotulos de
        // exibicao de placar que mudaram (nunca a lista, o PlacarPanel, os campos
        // editaveis ou os formularios).
        WebMarkupContainer pollingTrigger = new WebMarkupContainer("pollingTrigger");
        pollingTrigger.setOutputMarkupId(true);
        pollingTrigger.add(new AjaxSelfUpdatingTimerBehavior(INTERVALO_ATUALIZACAO) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onPostProcessTarget(AjaxRequestTarget target) {
                super.onPostProcessTarget(target);
                atualizarPlacaresExibidos(listaJogos, target);
            }
        });
        add(pollingTrigger);
    }

    /**
     * Atualiza, para cada linha ja renderizada, apenas os rotulos de exibicao dos
     * placares, sem recarregar a lista, sem tocar o {@link PlacarPanel} e sem
     * alterar o filtro selecionado.
     *
     * <p>Para cada Jogo exibido chama {@link JogoService#obterPlacarAtual(Long)}
     * (Redis primeiro, com fallback no PostgreSQL <em>dentro</em> do servico) e,
     * em caso de mudanca, atualiza o holder e adiciona o rotulo ao
     * {@link AjaxRequestTarget}. Falhas por Jogo sao registradas em log e o ultimo
     * placar visivel e mantido, sem interromper o timer.</p>
     *
     * @param listaJogos a listagem cujos itens renderizados serao inspecionados
     * @param target o alvo Ajax do ciclo corrente
     */
    private void atualizarPlacaresExibidos(ListView<Jogo> listaJogos, AjaxRequestTarget target) {
        listaJogos.visitChildren(ListItem.class, (item, visit) -> {
            @SuppressWarnings("unchecked")
            ListItem<Jogo> linha = (ListItem<Jogo>) item;
            Jogo jogo = linha.getModelObject();
            if (jogo == null || jogo.getId() == null) {
                return;
            }

            Label placarALabel = (Label) linha.get("placarA");
            Label placarBLabel = (Label) linha.get("placarB");
            if (placarALabel == null || placarBLabel == null) {
                return;
            }

            try {
                PlacarAtual placar = jogoService.obterPlacarAtual(jogo.getId());

                @SuppressWarnings("unchecked")
                IModel<Integer> modeloA = (IModel<Integer>) placarALabel.getDefaultModel();
                @SuppressWarnings("unchecked")
                IModel<Integer> modeloB = (IModel<Integer>) placarBLabel.getDefaultModel();

                Integer atualA = modeloA.getObject();
                Integer atualB = modeloB.getObject();

                if (atualA == null || atualA.intValue() != placar.placarA()) {
                    modeloA.setObject(placar.placarA());
                    target.add(placarALabel);
                }
                if (atualB == null || atualB.intValue() != placar.placarB()) {
                    modeloB.setObject(placar.placarB());
                    target.add(placarBLabel);
                }
            } catch (RuntimeException e) {
                // Falha transitoria em um Jogo: mantem o ultimo placar visivel e
                // segue para as proximas linhas/ciclos. Sem expor detalhes ao usuario.
                LOGGER.log(Level.FINE,
                        () -> "Falha ao obter placar atual do jogo " + jogo.getId()
                                + " durante o polling; mantendo o ultimo valor exibido.");
            }
        });
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
            error(getString("feedback.listarErroGenerico"));
            return new ArrayList<>();
        }
    }

    /**
     * Formata a data/hora da partida conforme o idioma corrente da sessao.
     *
     * <p>O padrao de formatacao vem do resource bundle (chave {@code formato.dataHora}):
     * portugues usa {@code dd/MM/yyyy HH:mm} e ingles usa {@code MM/dd/yyyy hh:mm a}.
     * O {@link DateTimeFormatter} e construido com o {@link Locale} da sessao para
     * que elementos sensiveis ao idioma (ex.: AM/PM) sejam renderizados corretamente.</p>
     *
     * @param dataHora data/hora a formatar; {@code null} resulta em texto vazio
     * @return a data/hora formatada no idioma corrente, ou {@code ""} se nula
     */
    private String formatarDataHora(OffsetDateTime dataHora) {
        if (dataHora == null) {
            return "";
        }
        Locale locale = getLocale();
        if (locale == null) {
            locale = LOCALE_PT;
        }
        DateTimeFormatter formatador =
                DateTimeFormatter.ofPattern(getString("formato.dataHora"), locale);
        return dataHora.format(formatador);
    }

    /**
     * Cria o formulario do seletor de idioma (PT/EN).
     *
     * <p>O idioma corrente e lido de {@link #getLocale()} (persistido na
     * {@link PlacarSession}) e, ao submeter, {@link #getSession()} recebe o novo
     * locale e a pagina e re-renderizada, aplicando o idioma a toda a interface. As
     * opcoes sao renderizadas com rotulos legiveis vindos do bundle
     * ({@code idioma.pt}/{@code idioma.en}).</p>
     *
     * @return o formulario de troca de idioma pronto para ser adicionado a pagina
     */
    private Form<Void> criarFormularioIdioma() {
        IModel<Locale> idiomaModel = Model.of(getLocale());

        List<Locale> idiomas = List.of(LOCALE_PT, Locale.ENGLISH);

        IChoiceRenderer<Locale> renderer = new ChoiceRenderer<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Object getDisplayValue(Locale locale) {
                String chave = Locale.ENGLISH.getLanguage().equals(locale.getLanguage())
                        ? "idioma.en"
                        : "idioma.pt";
                return getString(chave);
            }

            @Override
            public String getIdValue(Locale locale, int index) {
                return locale.getLanguage();
            }
        };

        DropDownChoice<Locale> idioma =
                new DropDownChoice<>("idioma", idiomaModel, idiomas, renderer);
        idioma.setNullValid(false);

        Form<Void> idiomaForm = new Form<Void>("idiomaForm") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit() {
                Locale selecionado = idiomaModel.getObject();
                if (selecionado != null) {
                    getSession().setLocale(selecionado);
                }
                setResponsePage(JogosPage.class);
            }
        };
        idiomaForm.add(idioma);
        return idiomaForm;
    }

    public Status getStatusSelecionado() {
        return statusSelecionado;
    }

    public void setStatusSelecionado(Status statusSelecionado) {
        this.statusSelecionado = statusSelecionado;
    }
}
