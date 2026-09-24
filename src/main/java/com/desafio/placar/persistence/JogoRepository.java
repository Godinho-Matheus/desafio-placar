package com.desafio.placar.persistence;

import java.util.List;
import java.util.Optional;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Repositorio JPA de {@link Jogo}.
 *
 * <p>Encapsula o acesso ao PostgreSQL por meio de um {@link EntityManager}, sendo
 * a unica porta de persistencia e consulta do estado completo do Jogo (fonte de
 * verdade). Responsabilidade unica: operacoes de CRUD e consultas de {@code Jogo}.
 * Nao contem regras de negocio nem validacoes — estas ficam a cargo do servico de
 * aplicacao.</p>
 *
 * <p>E um bean CDI {@link ApplicationScoped}, coerente com o restante do design,
 * e recebe o {@code EntityManager} via {@link PersistenceContext}.</p>
 */
@ApplicationScoped
public class JogoRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Persiste um novo Jogo ou atualiza um existente.
     *
     * <p>Quando o {@code id} e {@code null}, o Jogo e inserido via
     * {@link EntityManager#persist(Object)} e a propria instancia (agora
     * gerenciada) e retornada. Caso contrario, e feito {@link EntityManager#merge(Object)}
     * e a instancia gerenciada resultante e retornada.</p>
     *
     * @param jogo o Jogo a persistir ou atualizar
     * @return a instancia gerenciada/atualizada
     */
    public Jogo salvar(Jogo jogo) {
        if (jogo.getId() == null) {
            em.persist(jogo);
            return jogo;
        }
        return em.merge(jogo);
    }

    /**
     * Busca um Jogo pelo identificador.
     *
     * @param id identificador do Jogo
     * @return o Jogo encontrado, ou {@link Optional#empty()} se nao existir
     */
    public Optional<Jogo> buscarPorId(Long id) {
        return Optional.ofNullable(em.find(Jogo.class, id));
    }

    /**
     * Lista todos os Jogos persistidos.
     *
     * @return a lista de Jogos (vazia se nao houver nenhum)
     */
    public List<Jogo> listarTodos() {
        return em.createQuery("SELECT j FROM Jogo j", Jogo.class)
                .getResultList();
    }

    /**
     * Lista os Jogos que possuem o status informado.
     *
     * @param status status usado como filtro
     * @return a lista de Jogos com o status informado (vazia se nao houver nenhum)
     */
    public List<Jogo> listarPorStatus(Status status) {
        return em.createQuery("SELECT j FROM Jogo j WHERE j.status = :status", Jogo.class)
                .setParameter("status", status)
                .getResultList();
    }
}
