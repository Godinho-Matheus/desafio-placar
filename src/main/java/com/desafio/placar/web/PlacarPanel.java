package com.desafio.placar.web;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;
import com.desafio.placar.domain.excecao.JogoEncerradoException;
import com.desafio.placar.domain.excecao.NaoEncontradoException;
import com.desafio.placar.service.JogoService;

import jakarta.inject.Inject;

/**
 * Painel Wicket com as acoes por Jogo na Interface_Web: atualizar placar e
 * encerrar.
 *
 * <p>Todas as operacoes chamam o {@link JogoService} diretamente no mesmo
 * WAR/JVM (nunca a API REST): a atualizacao de placar via
 * {@link JogoService#atualizarPlacar(Long, int, int)} e o encerramento via
 * {@link JogoService#encerrar(Long)}. Os formularios usam submit sincrono
 * simples (sem Ajax, sem timer, sem polling).</p>
 *
 * <p>Regra de negocio (Requisito 6.2): para um Jogo {@link Status#ENCERRADO} o
 * controle de atualizacao de placar fica desabilitado (campos e botao com
 * {@code setEnabled(false)}), impedindo a tentativa antes de qualquer chamada.
 * Para Jogos {@link Status#EM_ANDAMENTO} o controle fica habilitado. O botao de
 * encerrar tambem e desabilitado quando o Jogo ja esta {@link Status#ENCERRADO};
 * a regra autoritativa de idempotencia, porem, permanece no
 * {@link JogoService#encerrar(Long)}.</p>
 *
 * <p>Tratamento de erro (Requisito 9.5): as chamadas ao servico sao envolvidas;
 * excecoes de dominio ({@link EntradaInvalidaException},
 * {@link NaoEncontradoException}, {@link JogoEncerradoException}) sao exibidas
 * via {@code getPage().error(...)} e erros inesperados sao registrados em log e
 * exibidos de forma generica, sem expor detalhes internos. As mensagens sao
 * registradas na {@link JogosPage} (dona do {@code FeedbackPanel}) e nao no
 * proprio painel, pois este e recriado pelo {@code ListView} a cada render — uma
 * mensagem de nivel de componente se perderia. Em caso de erro os valores
 * anteriores sao mantidos, pois a pagina so e re-renderizada em caso de sucesso.</p>
 */
public class PlacarPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PlacarPanel.class.getName());

    @Inject
    private JogoService jogoService;

    private final Long jogoId;

    private final Model<Integer> placarAModel;
    private final Model<Integer> placarBModel;

    /**
     * Construtor de conveniencia que cria o painel sem informar o {@link JogoService}
     * explicitamente, delegando ao construtor
     * {@link #PlacarPanel(String, Jogo, JogoService)} com {@code null}. Nesse caso o
     * campo {@code @Inject} {@link #jogoService} e preenchido pela integracao
     * Wicket-CDI apos a construcao.
     *
     * <p>Observacao: a {@link JogosPage} nao usa este construtor; ela repassa o
     * servico explicitamente (ver o outro construtor). Ele permanece como forma
     * simples de instanciar o painel contando apenas com a injecao de CDI.</p>
     *
     * @param id   id do componente Wicket
     * @param jogo Jogo exibido na linha
     */
    public PlacarPanel(String id, Jogo jogo) {
        this(id, jogo, null);
    }

    /**
     * Construtor que recebe o {@link JogoService} explicitamente. E o usado pela
     * {@link JogosPage}, que, ao montar cada linha, repassa o proprio servico ao
     * painel: em producao esse servico ja foi injetado na pagina pelo CDI no
     * momento do {@code populateItem} (tempo de render); em testes de UI com
     * WicketTester (sem container CDI), e o servico fornecido ao construtor de
     * teste da pagina.
     *
     * <p>Quando o {@code jogoService} informado e {@code null} (caso do construtor
     * de conveniencia sem servico), o campo {@code @Inject} nao e sobrescrito e
     * sera preenchido pela injecao Wicket-CDI apos a construcao. Package-private
     * para permitir repasse pela pagina e uso em testes no mesmo pacote.</p>
     *
     * @param id          id do componente Wicket
     * @param jogo        Jogo exibido na linha
     * @param jogoService servico a ser usado; quando {@code null}, o campo e
     *                    preenchido depois pela injecao de CDI
     */
    PlacarPanel(String id, Jogo jogo, JogoService jogoService) {
        super(id);

        // Quando chega null (construtor de conveniencia sem servico), NAO sobrescreve
        // o campo, deixando a injecao Wicket-CDI preencher jogoService apos a
        // construcao. Quando a JogosPage (ou um teste) repassa um servico concreto,
        // ele e atribuido aqui.
        if (jogoService != null) {
            this.jogoService = jogoService;
        }

        this.jogoId = jogo.getId();
        this.placarAModel = Model.of(jogo.getPlacarA());
        this.placarBModel = Model.of(jogo.getPlacarB());

        boolean encerrado = jogo.getStatus() == Status.ENCERRADO;

        // Formulario de atualizacao de placar (submit sincrono, sem Ajax).
        Form<Void> atualizarPlacarForm = new Form<Void>("atualizarPlacarForm") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit() {
                atualizarPlacar();
            }
        };

        // A regra de placar >= 0 e autoritativa no JogoService.atualizarPlacar
        // (Requisito 4.4). A interface NAO replica essa validacao: o valor negativo
        // flui ate o servico, que lanca EntradaInvalidaException, tratada abaixo com
        // getPage().error(e.getMessage()) para que o FeedbackPanel exiba a mensagem
        // do backend. Mantem-se apenas setRequired(true) (obrigatoriedade do campo
        // no envio).
        NumberTextField<Integer> placarA = new NumberTextField<>("placarA", placarAModel, Integer.class);
        placarA.setRequired(true);
        NumberTextField<Integer> placarB = new NumberTextField<>("placarB", placarBModel, Integer.class);
        placarB.setRequired(true);

        Button atualizarBotao = new Button("atualizarBotao");

        // Requisito 6.2: controle de atualizacao desabilitado para jogo ENCERRADO.
        placarA.setEnabled(!encerrado);
        placarB.setEnabled(!encerrado);
        atualizarBotao.setEnabled(!encerrado);

        atualizarPlacarForm.add(placarA);
        atualizarPlacarForm.add(placarB);
        atualizarPlacarForm.add(atualizarBotao);
        add(atualizarPlacarForm);

        // Formulario de encerramento (submit sincrono, sem Ajax).
        Form<Void> encerrarForm = new Form<Void>("encerrarForm") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit() {
                encerrarJogo();
            }
        };

        Button encerrarBotao = new Button("encerrarBotao");
        // Ja encerrado: desabilita o botao (a idempotencia autoritativa fica no servico).
        encerrarBotao.setEnabled(!encerrado);
        encerrarForm.add(encerrarBotao);
        add(encerrarForm);
    }

    /**
     * Trata o submit sincrono da atualizacao de placar.
     *
     * <p>Chama {@link JogoService#atualizarPlacar(Long, int, int)} e, em sucesso,
     * re-renderiza a pagina inicial para refletir o novo placar. Em erro, exibe a
     * mensagem via {@code getPage().error(...)} (registrada na {@link JogosPage},
     * dona do {@code FeedbackPanel}) e mantem os valores anteriores (a pagina nao
     * e re-renderizada).</p>
     */
    private void atualizarPlacar() {
        try {
            Jogo atualizado = jogoService.atualizarPlacar(
                    jogoId,
                    placarAModel.getObject(),
                    placarBModel.getObject());

            info("Placar do jogo " + atualizado.getId() + " atualizado para "
                    + atualizado.getPlacarA() + " x " + atualizado.getPlacarB() + ".");

            setResponsePage(JogosPage.class);
        } catch (EntradaInvalidaException | NaoEncontradoException | JogoEncerradoException e) {
            // Registra na pagina (dona do FeedbackPanel), e nao no componente: o
            // PlacarPanel e recriado pelo ListView a cada render, entao uma mensagem
            // de nivel de componente se perderia. getPage().error(...) garante que a
            // mensagem do backend chegue ao FeedbackPanel da JogosPage.
            getPage().error(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falha inesperada ao atualizar placar", e);
            getPage().error("Nao foi possivel atualizar o placar no momento. Tente novamente.");
        }
    }

    /**
     * Trata o submit sincrono do encerramento do Jogo.
     *
     * <p>Chama {@link JogoService#encerrar(Long)} e, em sucesso, re-renderiza a
     * pagina inicial para refletir o status ENCERRADO. Em erro, exibe a mensagem
     * via {@code getPage().error(...)} (registrada na {@link JogosPage}, dona do
     * {@code FeedbackPanel}) e mantem o estado anterior (a pagina nao e
     * re-renderizada).</p>
     */
    private void encerrarJogo() {
        try {
            Jogo encerrado = jogoService.encerrar(jogoId);

            info("Jogo " + encerrado.getId() + " encerrado (status "
                    + encerrado.getStatus().name() + ").");

            setResponsePage(JogosPage.class);
        } catch (EntradaInvalidaException | NaoEncontradoException | JogoEncerradoException e) {
            // Registra na pagina (dona do FeedbackPanel); ver nota em atualizarPlacar().
            getPage().error(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falha inesperada ao encerrar jogo", e);
            getPage().error("Nao foi possivel encerrar o jogo no momento. Tente novamente.");
        }
    }
}
