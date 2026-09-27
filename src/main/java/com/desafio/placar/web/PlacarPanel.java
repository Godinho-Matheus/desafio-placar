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
 * via {@code error(...)} (naturalmente exibidas pelo {@code FeedbackPanel} da
 * pagina) e erros inesperados sao registrados em log e exibidos de forma
 * generica, sem expor detalhes internos. Em caso de erro os valores anteriores
 * sao mantidos, pois a pagina so e re-renderizada em caso de sucesso.</p>
 */
public class PlacarPanel extends Panel {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(PlacarPanel.class.getName());

    @Inject
    private JogoService jogoService;

    private final Long jogoId;

    private final Model<Integer> placarAModel;
    private final Model<Integer> placarBModel;

    public PlacarPanel(String id, Jogo jogo) {
        super(id);

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
     * mensagem via {@code error(...)} e mantem os valores anteriores (a pagina
     * nao e re-renderizada).</p>
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
            error(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falha inesperada ao atualizar placar", e);
            error("Nao foi possivel atualizar o placar no momento. Tente novamente.");
        }
    }

    /**
     * Trata o submit sincrono do encerramento do Jogo.
     *
     * <p>Chama {@link JogoService#encerrar(Long)} e, em sucesso, re-renderiza a
     * pagina inicial para refletir o status ENCERRADO. Em erro, exibe a mensagem
     * via {@code error(...)} e mantem o estado anterior (a pagina nao e
     * re-renderizada).</p>
     */
    private void encerrarJogo() {
        try {
            Jogo encerrado = jogoService.encerrar(jogoId);

            info("Jogo " + encerrado.getId() + " encerrado (status "
                    + encerrado.getStatus().name() + ").");

            setResponsePage(JogosPage.class);
        } catch (EntradaInvalidaException | NaoEncontradoException | JogoEncerradoException e) {
            error(e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Falha inesperada ao encerrar jogo", e);
            error("Nao foi possivel encerrar o jogo no momento. Tente novamente.");
        }
    }
}
