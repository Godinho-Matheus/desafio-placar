package com.desafio.placar.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes de UI do {@link CriarJogoPanel} com {@link WicketTester}.
 *
 * <p>Foca na melhoria de UX do campo de data/hora: a entrada passou a ser um
 * {@code <input type="datetime-local">} (data/hora local, sem offset) mais um
 * seletor de offset UTC com {@code -03:00} pre-selecionado. Estes testes
 * validam, sem tocar {@code JogoService}, banco ou REST:</p>
 * <ul>
 *   <li>a conversao da data/hora local + offset em {@link OffsetDateTime}
 *       (metodo {@link CriarJogoPanel#combinarDataHora(String, String)}), que
 *       produz exatamente o mesmo valor que antes era digitado como texto
 *       ISO-8601 com offset;</li>
 *   <li>que o formulario renderiza o campo de data/hora e o seletor de offset e
 *       que o offset vem pre-selecionado em {@value CriarJogoPanel#OFFSET_PADRAO}.</li>
 * </ul>
 *
 * <p>O painel usa {@code @Inject JogoService}, porem o servico so e usado no
 * submit; a renderizacao e a selecao padrao nao dependem dele, entao os testes
 * de renderizacao dispensam CDI.</p>
 */
class CriarJogoPanelTest {

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
    void combinarDataHora_deveProduzirOffsetDateTimeEquivalenteAoIsoComOffset() {
        // Data/hora local do <input type="datetime-local"> (sem segundos) + offset.
        OffsetDateTime combinado = CriarJogoPanel.combinarDataHora("2025-01-01T20:00", "-03:00");

        // Deve ser exatamente o mesmo OffsetDateTime que antes era digitado como
        // texto ISO-8601 com offset e convertido por OffsetDateTime.parse(...).
        assertEquals(OffsetDateTime.parse("2025-01-01T20:00:00-03:00"), combinado,
                "A combinacao data/hora local + offset deve igualar o ISO-8601 com offset");
    }

    @Test
    void combinarDataHora_deveUsarOffsetPadraoDeBrasilia() {
        OffsetDateTime combinado =
                CriarJogoPanel.combinarDataHora("2025-06-15T09:30", CriarJogoPanel.OFFSET_PADRAO);

        assertEquals(OffsetDateTime.parse("2025-06-15T09:30:00-03:00"), combinado,
                "Com o offset padrao (-03:00), o resultado deve refletir UTC-03:00");
    }

    @Test
    void combinarDataHora_deveAceitarSegundosNaDataHoraLocal() {
        OffsetDateTime combinado =
                CriarJogoPanel.combinarDataHora("2025-01-01T20:00:45", "+00:00");

        assertEquals(OffsetDateTime.parse("2025-01-01T20:00:45+00:00"), combinado,
                "Deve aceitar data/hora local com segundos");
    }

    @Test
    void combinarDataHora_deveRejeitarDataHoraLocalInvalida() {
        assertThrows(DateTimeParseException.class,
                () -> CriarJogoPanel.combinarDataHora("data-invalida", "-03:00"),
                "Data/hora local invalida deve lancar DateTimeParseException");
    }

    @Test
    void formulario_deveRenderizarDatetimeLocalEOffsetComPadraoBrasilia() {
        // jogoService nao e usado na renderizacao; dispensa CDI.
        tester.startComponentInPage(new CriarJogoPanel("criarJogoPanel"));

        // Campo de data/hora (mesmo wicket:id de antes) permanece presente.
        tester.assertComponent(
                "criarJogoPanel:criarJogoForm:dataHoraPartida", TextField.class);

        // Novo seletor de offset presente e com o valor padrao -03:00 selecionado.
        tester.assertComponent(
                "criarJogoPanel:criarJogoForm:offset", DropDownChoice.class);
        assertEquals(CriarJogoPanel.OFFSET_PADRAO,
                tester.getComponentFromLastRenderedPage(
                        "criarJogoPanel:criarJogoForm:offset").getDefaultModelObject(),
                "O offset deve vir pre-selecionado em -03:00");

        // O markup renderizado deve usar o input HTML datetime-local (melhoria de UX).
        assertTrue(tester.getLastResponseAsString().contains("type=\"datetime-local\""),
                "O campo de data/hora deve ser renderizado como <input type=\"datetime-local\">");
    }

    @Test
    void formulario_deveOferecerTodosOsOffsetsDisponiveis() {
        tester.startComponentInPage(new CriarJogoPanel("criarJogoPanel"));

        // Todas as opcoes de offset configuradas devem aparecer no <select>.
        String html = tester.getLastResponseAsString();
        for (String offset : CriarJogoPanel.OFFSETS_DISPONIVEIS) {
            assertTrue(html.contains(offset),
                    "O seletor de offset deve oferecer a opcao " + offset);
        }
    }
}
