/**
 * Persistencia do Sistema.
 *
 * <p>Reune o repositorio JPA {@link com.desafio.placar.persistence.JogoRepository},
 * que encapsula o acesso ao PostgreSQL via {@code EntityManager}. E a unica porta
 * de entrada para persistir e consultar {@link com.desafio.placar.domain.Jogo}
 * (estado completo, fonte de verdade), sem regras de negocio nem validacoes. A
 * configuracao da unidade de persistencia e da conexao PostgreSQL sera introduzida
 * em tarefa posterior da Spec.</p>
 */
package com.desafio.placar.persistence;
