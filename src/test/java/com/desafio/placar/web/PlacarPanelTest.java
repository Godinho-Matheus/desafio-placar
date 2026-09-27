package com.desafio.placar.web;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.OffsetDateTime;

import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;

/**
 * Testes de UI do {@link PlacarPanel} com {@link WicketTester} (Tarefa 11.4).
 *
 * <p>Verifica a regra da interface (Requisito 6.2): quando o Jogo esta
 * {@link Status#ENCERRADO}, o controle de atualizacao de placar (campos e botao)
 * e o botao de encerrar ficam desabilitados. O teste renderiza o painel isolado
 * via {@link WicketTester#startComponentInPage(org.apache.wicket.Component)} e
 * inspeciona {@code isEnabled()} pelos caminhos reais dos componentes, sem
 * submeter formularios, sem {@code JogoService} e sem reflexao.</p>
 */
class PlacarPanelTest {

    private WicketTester tester;

    @BeforeEach
    void setUp() {
        tester = new WicketTester();
    }

    @AfterEach
    void tearDown() {
        tester.destroy();
    }

    @Test
    void placarPanel_deveDesabilitarAtualizacaoQuandoJogoEncerrado() {
        Jogo jogo = new Jogo();
        jogo.setId(1L);
        jogo.setTimeA("Palmeiras");
        jogo.setTimeB("Corinthians");
        jogo.setPlacarA(2);
        jogo.setPlacarB(1);
        jogo.setStatus(Status.ENCERRADO);
        jogo.setDataHoraPartida(OffsetDateTime.parse("2026-01-01T20:00:00Z"));

        // startComponentInPage envolve o componente numa pagina; o id do painel
        // ("placarPanel") prefixa os caminhos internos dos formularios/campos.
        tester.startComponentInPage(new PlacarPanel("placarPanel", jogo));

        // Controle de atualizacao desabilitado (Requisito 6.2).
        assertFalse(
                tester.getComponentFromLastRenderedPage(
                        "placarPanel:atualizarPlacarForm:placarA").isEnabled(),
                "Campo placarA deve estar desabilitado para jogo ENCERRADO");
        assertFalse(
                tester.getComponentFromLastRenderedPage(
                        "placarPanel:atualizarPlacarForm:placarB").isEnabled(),
                "Campo placarB deve estar desabilitado para jogo ENCERRADO");
        assertFalse(
                tester.getComponentFromLastRenderedPage(
                        "placarPanel:atualizarPlacarForm:atualizarBotao").isEnabled(),
                "Botao de atualizar deve estar desabilitado para jogo ENCERRADO");

        // Complementar: botao de encerrar tambem desabilitado quando ja ENCERRADO.
        assertFalse(
                tester.getComponentFromLastRenderedPage(
                        "placarPanel:encerrarForm:encerrarBotao").isEnabled(),
                "Botao de encerrar deve estar desabilitado para jogo ENCERRADO");
    }
}
