package com.desafio.placar.config;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Classe de ativacao do JAX-RS.
 *
 * <p>Registra a aplicacao REST e define o caminho base {@code /api} para todos os
 * recursos JAX-RS do Sistema. Nao declara nenhum recurso ou endpoint: o Payara
 * detecta os recursos anotados com {@code @Path} automaticamente.</p>
 *
 * <p>A {@code @OpenAPIDefinition} fornece os metadados gerais do documento
 * OpenAPI (Requisito 10.1). O Payara implementa MicroProfile OpenAPI e, a partir
 * das anotacoes do {@code JogoResource}, gera e expoe nativamente o documento
 * OpenAPI (formato do documento, nao a interface visual) em {@code /openapi},
 * no raiz do servidor, sem exigir dependencia ou configuracao adicional
 * (Requisito 10.2).</p>
 *
 * <p>A Swagger UI (interface visual) NAO e fornecida nativamente pelo Payara.
 * Ela e servida pela propria aplicacao como pagina estatica dentro do contexto
 * do WAR, em {@code /desafio-placar/swagger/}, e consome o documento OpenAPI
 * exposto em {@code /openapi} (Requisito 10.2).</p>
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Desafio Placar - API",
                version = "1.0.0",
                description = "API REST de gerenciamento de placares de partidas de futebol em tempo real."))
@ApplicationPath("/api")
public class JaxRsApplication extends Application {
    // Esqueleto de ativacao. Sem endpoints ou metodos funcionais nesta task.
}
