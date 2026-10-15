package com.desafio.placar.web;

import java.util.Locale;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

/**
 * Sessao da Interface_Web que persiste o idioma escolhido pelo usuario.
 *
 * <p>O Wicket resolve os textos (resource bundles) e o formato de data conforme
 * o {@link #getLocale()} da sessao. Esta subclasse apenas inicializa esse locale
 * com um padrao explicito (portugues) no momento da criacao da sessao; a troca de
 * idioma pela interface e feita com {@link #setLocale(Locale)} (herdado), que o
 * Wicket mantem na sessao entre as requisicoes. Nao ha estado adicional: a
 * responsabilidade e unica (guardar o idioma corrente).</p>
 *
 * <p>Instanciada por {@link PlacarWebApplication#newSession(Request, org.apache.wicket.request.Response)}.</p>
 */
public class PlacarSession extends WebSession {

    private static final long serialVersionUID = 1L;

    /**
     * Cria a sessao e define o locale inicial.
     *
     * @param request requisicao corrente (exigida pelo construtor do Wicket)
     * @param localePadrao idioma inicial da sessao (ex.: portugues do Brasil)
     */
    public PlacarSession(Request request, Locale localePadrao) {
        super(request);
        setLocale(localePadrao);
    }
}
