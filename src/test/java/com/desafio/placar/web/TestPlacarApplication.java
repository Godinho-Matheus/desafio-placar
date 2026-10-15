package com.desafio.placar.web;

/**
 * Aplicacao de teste que reaproveita a {@link PlacarWebApplication} real para
 * exercitar o fluxo verdadeiro de criacao de sessao ({@code newSession} ->
 * {@link PlacarSession}), sem iniciar a integracao CDI.
 *
 * <p>Existe apenas para que os testes de i18n validem o comportamento REAL da
 * aplicacao: uma nova sessao inicia em pt-BR. O {@code init()} e sobrescrito para
 * NAO chamar {@code CdiConfiguration().configure(...)}, pois nao ha container CDI
 * disponivel no {@code WicketTester}. Nenhuma regra de negocio, locale ou texto e
 * alterado aqui; apenas se evita a configuracao de CDI, irrelevante para o idioma.</p>
 */
public class TestPlacarApplication extends PlacarWebApplication {

    @Override
    protected void init() {
        // Intencionalmente NAO chama super.init(): evita a configuracao CDI
        // (indisponivel em teste). O newSession()/PlacarSession herdados — que
        // definem pt-BR como locale padrao — permanecem inalterados.
    }
}
