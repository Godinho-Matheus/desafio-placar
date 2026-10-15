package com.desafio.placar.web;

import java.util.Locale;

import org.apache.wicket.Session;
import org.apache.wicket.cdi.CdiConfiguration;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;

/**
 * Aplicacao Apache Wicket do Sistema (bootstrap).
 *
 * <p>Ponto de entrada da Interface_Web. Define a {@link JogosPage} como pagina
 * inicial e habilita a integracao Wicket-CDI, permitindo que paginas e
 * componentes Wicket recebam beans CDI via {@code @Inject} (especialmente o
 * {@code JogoService}), sempre chamando o servico de aplicacao diretamente,
 * nunca a API REST.</p>
 *
 * <p>Esta classe apenas inicializa o Wicket e a integracao com CDI.</p>
 *
 * <p><strong>Internacionalizacao (i18n).</strong> A interface suporta portugues
 * (padrao e fallback) e ingles. Os textos ficam em resource bundles da aplicacao
 * ({@code PlacarWebApplication.properties} = PT, {@code PlacarWebApplication_en.properties}
 * = EN), resolvidos automaticamente pelo Wicket conforme o locale da sessao. O
 * locale escolhido e persistido na {@link PlacarSession} (padrao {@link #LOCALE_PADRAO}),
 * e um seletor na interface permite a troca manual. Nenhuma camada alem da
 * apresentacao e afetada: backend, API REST e documentacao permanecem inalterados.</p>
 */
public class PlacarWebApplication extends WebApplication {

    /**
     * Locale padrao da interface (portugues do Brasil). Usado como idioma inicial
     * de cada sessao e como fallback quando uma chave nao existir no bundle do
     * idioma selecionado.
     */
    static final Locale LOCALE_PADRAO = Locale.of("pt", "BR");

    @Override
    public Class<? extends WebPage> getHomePage() {
        return JogosPage.class;
    }

    @Override
    protected void init() {
        super.init();
        // Habilita @Inject de beans CDI (ex.: JogoService) em paginas/componentes Wicket.
        new CdiConfiguration().configure(this);
    }

    /**
     * Cria a sessao da Interface_Web ja com o locale padrao (portugues),
     * permitindo que o usuario troque o idioma durante a navegacao com persistencia
     * na sessao.
     */
    @Override
    public Session newSession(Request request, Response response) {
        return new PlacarSession(request, LOCALE_PADRAO);
    }
}
