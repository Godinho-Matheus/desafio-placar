package com.desafio.placar.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.desafio.placar.cache.PlacarAtual;

/**
 * Testes de UI da {@link JogosPage} com {@link WicketTester} (Tarefa 11.4).
 *
 * <p>Verifica o polling automatico dos placares exibidos (Requisito 8.1): o
 * {@link AjaxSelfUpdatingTimerBehavior} hospedado no componente auxiliar
 * {@code pollingTrigger} atualiza apenas os rotulos de exibicao de placar quando
 * o placar atual muda. O teste substitui o {@link com.desafio.placar.service.JogoService}
 * por um stub controlavel ({@link ServicoControlavel}) via o construtor de teste
 * package-private, localiza o behavior real e o executa com
 * {@link WicketTester#executeBehavior(org.apache.wicket.behavior.AbstractAjaxBehavior)},
 * sem esperar tempo real, sem reflexao e sem chamar o metodo privado de
 * atualizacao diretamente.</p>
 */
class JogosPageTest {

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
    void polling_deveAtualizarPlacaresExibidos() {
        ServicoControlavel servico = new ServicoControlavel();
        // Placar inicial exibido pelo polling: 0x0 (igual ao Jogo listado).
        servico.definirPlacarAtual(new PlacarAtual(0, 0));

        tester.startPage(new JogosPage(servico));
        tester.assertRenderedPage(JogosPage.class);

        // Valores iniciais renderizados nos rotulos de exibicao da linha 0.
        assertEquals("0",
                tester.getComponentFromLastRenderedPage("jogos:0:placarA")
                        .getDefaultModelObjectAsString(),
                "Placar A inicial deve ser 0");
        assertEquals("0",
                tester.getComponentFromLastRenderedPage("jogos:0:placarB")
                        .getDefaultModelObjectAsString(),
                "Placar B inicial deve ser 0");

        // O placar atual muda no servico (ex.: Redis atualizado).
        servico.definirPlacarAtual(new PlacarAtual(2, 1));

        // Localiza o behavior real de polling hospedado no pollingTrigger e o executa.
        AjaxSelfUpdatingTimerBehavior polling = localizarBehaviorDePolling();
        tester.executeBehavior(polling);

        // O ciclo do polling deve ter atualizado apenas os rotulos de exibicao.
        assertEquals("2",
                tester.getComponentFromLastRenderedPage("jogos:0:placarA")
                        .getDefaultModelObjectAsString(),
                "Placar A deve ter sido atualizado para 2 pelo polling");
        assertEquals("1",
                tester.getComponentFromLastRenderedPage("jogos:0:placarB")
                        .getDefaultModelObjectAsString(),
                "Placar B deve ter sido atualizado para 1 pelo polling");

        // Complementar: os rotulos atualizados entram na resposta Ajax e o
        // PlacarPanel (campos editaveis) NAO e re-renderizado pelo polling.
        tester.assertComponentOnAjaxResponse("jogos:0:placarA");
        tester.assertComponentOnAjaxResponse("jogos:0:placarB");
    }

    /**
     * Localiza o {@link AjaxSelfUpdatingTimerBehavior} real hospedado no
     * componente auxiliar {@code pollingTrigger} da pagina renderizada.
     */
    private AjaxSelfUpdatingTimerBehavior localizarBehaviorDePolling() {
        Component pollingTrigger =
                tester.getComponentFromLastRenderedPage("pollingTrigger");
        assertNotNull(pollingTrigger, "pollingTrigger deve existir na pagina");
        assertEquals(WebMarkupContainer.class, pollingTrigger.getClass(),
                "pollingTrigger deve ser um WebMarkupContainer neutro");

        List<AjaxSelfUpdatingTimerBehavior> behaviors =
                pollingTrigger.getBehaviors(AjaxSelfUpdatingTimerBehavior.class);
        assertEquals(1, behaviors.size(),
                "pollingTrigger deve hospedar exatamente um AjaxSelfUpdatingTimerBehavior");

        AjaxSelfUpdatingTimerBehavior polling = behaviors.get(0);
        assertNotNull(polling, "O behavior de polling deve estar presente");
        return polling;
    }
}
