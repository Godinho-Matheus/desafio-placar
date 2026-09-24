package com.desafio.placar.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Classe de ativacao do JAX-RS.
 *
 * <p>Registra a aplicacao REST e define o caminho base {@code /api} para todos os
 * recursos JAX-RS do Sistema. Nao declara nenhum recurso ou endpoint: o Payara
 * detecta os recursos anotados com {@code @Path} automaticamente. Recursos como o
 * {@code JogoResource} serao introduzidos em tarefas posteriores da Spec.</p>
 */
@ApplicationPath("/api")
public class JaxRsApplication extends Application {
    // Esqueleto de ativacao. Sem endpoints ou metodos funcionais nesta task.
}
