package com.desafio.placar.web;

import org.apache.wicket.cdi.CdiConfiguration;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;

/**
 * Aplicacao Apache Wicket do Sistema (bootstrap).
 *
 * <p>Ponto de entrada da Interface_Web. Define a {@link JogosPage} como pagina
 * inicial e habilita a integracao Wicket-CDI, permitindo que paginas e
 * componentes Wicket recebam beans CDI via {@code @Inject} (especialmente o
 * {@code JogoService}), sempre chamando o servico de aplicacao diretamente,
 * nunca a API REST.</p>
 *
 * <p>Esta classe apenas inicializa o Wicket e a integracao com CDI. A listagem,
 * o filtro, a criacao, a atualizacao de placar e o encerramento ja existem
 * (Task 9 concluida); resta apenas o polling automatico, previsto para a
 * Task 10 (10.1).</p>
 */
public class PlacarWebApplication extends WebApplication {

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
}
