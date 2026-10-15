package com.desafio.placar.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.desafio.placar.cache.PlacarAtual;

/**
 * Testes de internacionalizacao (i18n) da Interface_Web.
 *
 * <p>Comprova que a camada de apresentacao (Apache Wicket) exibe os textos em
 * portugues por padrao e em ingles quando o locale da sessao e trocado, usando
 * os resource bundles de componente ({@code JogosPage.properties}/{@code _en},
 * etc.). A verificacao inspeciona o HTML renderizado pela {@link JogosPage} para
 * textos estaticos (cabecalho, tabela), o rotulo traduzido de {@code Status}, os
 * botoes do {@link PlacarPanel} e o rotulo da opcao "Todos"/"All" do filtro.</p>
 *
 * <p>O {@link WicketTester} usa uma aplicacao mock; os bundles sao resolvidos
 * por serem de COMPONENTE (ao lado das classes), independentemente da classe de
 * Application. O {@link ServicoControlavel} fornece um Jogo determinístico, sem
 * banco, REST, Redis ou RabbitMQ.</p>
 */
class I18nInterfaceTest {

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
    void interface_devePrincipaisTextosEmPortuguesPorPadrao() {
        // Exercita o fluxo REAL de criacao de sessao da aplicacao (newSession ->
        // PlacarSession), que deve iniciar em pt-BR sem o teste forcar o locale.
        tester.destroy();
        tester = new WicketTester(new TestPlacarApplication());

        ServicoControlavel servico = new ServicoControlavel();
        servico.definirPlacarAtual(new PlacarAtual(0, 0));

        tester.startPage(new JogosPage(servico));
        tester.assertRenderedPage(JogosPage.class);

        // Comprova o comportamento real: a nova sessao iniciou em pt-BR (o teste
        // NAO definiu o locale manualmente).
        assertEquals(Locale.of("pt", "BR"), tester.getSession().getLocale(),
                "Uma nova sessao deve iniciar com Locale pt-BR por padrao");

        String html = tester.getLastResponseAsString();

        // Cabecalho e tabela (textos estaticos) em portugues.
        assertTrue(html.contains("Gerenciamento de placares"),
                "Subtitulo deve estar em portugues por padrao");
        assertTrue(html.contains("Novo jogo"),
                "Titulo do card de criacao deve estar em portugues por padrao");
        assertTrue(html.contains("Partida"),
                "Cabecalho da tabela deve estar em portugues por padrao");
        // Status exibido traduzido. Observacao: o nome cru do enum (EM_ANDAMENTO)
        // ainda aparece no HTML como VALOR das <option> do filtro (atributo value),
        // o que e correto: e o valor de submit do formulario. O que importa e que o
        // TEXTO VISIVEL do status esteja traduzido, verificado abaixo.
        assertTrue(html.contains("Em andamento"),
                "Status deve ser exibido traduzido em portugues");
        // A badge de status (texto visivel, nao atributo) deve conter o rotulo
        // traduzido, nunca o nome cru do enum como conteudo exibido.
        assertTrue(html.contains(">Em andamento<"),
                "O texto visivel do status deve ser o rotulo traduzido, nao o nome do enum");
        // Botoes do PlacarPanel e opcao do filtro em portugues.
        assertTrue(html.contains("Atualizar placar"),
                "Botao de atualizar placar deve estar em portugues");
        assertTrue(html.contains("Encerrar"),
                "Botao de encerrar deve estar em portugues");
        assertTrue(html.contains(">Todos<"),
                "A opcao vazia do filtro deve ser 'Todos' em portugues");
    }

    @Test
    void interface_deveExibirTextosEmInglesAoTrocarLocaleParaIngles() {
        ServicoControlavel servico = new ServicoControlavel();
        servico.definirPlacarAtual(new PlacarAtual(0, 0));

        // Troca o idioma da sessao para ingles antes de renderizar.
        tester.getSession().setLocale(Locale.ENGLISH);

        tester.startPage(new JogosPage(servico));
        tester.assertRenderedPage(JogosPage.class);

        String html = tester.getLastResponseAsString();

        // Cabecalho e tabela (textos estaticos) em ingles.
        assertTrue(html.contains("Real-time management"),
                "Subtitulo deve estar em ingles apos trocar o locale");
        assertTrue(html.contains("New match"),
                "Titulo do card de criacao deve estar em ingles");
        assertTrue(html.contains("Match"),
                "Cabecalho da tabela deve estar em ingles");
        // Status traduzido para ingles.
        assertTrue(html.contains("In progress"),
                "Status deve ser exibido traduzido em ingles");
        // Botoes do PlacarPanel e opcao do filtro em ingles.
        assertTrue(html.contains("Update score"),
                "Botao de atualizar placar deve estar em ingles");
        assertTrue(html.contains("Finish"),
                "Botao de encerrar deve estar em ingles");
        assertTrue(html.contains(">All<"),
                "A opcao vazia do filtro deve ser 'All' em ingles");
    }
}
