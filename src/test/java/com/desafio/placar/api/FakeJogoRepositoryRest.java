package com.desafio.placar.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.persistence.JogoRepository;

/**
 * Repositorio em memoria para os testes unitarios do contrato REST
 * ({@link JogoResource}).
 *
 * <p>Estende o {@link JogoRepository} real e sobrescreve os quatro metodos
 * publicos, apenas armazenando e lendo Jogos em um mapa. Nao contem qualquer
 * regra de negocio: essas permanecem no {@link com.desafio.placar.service.JogoService}
 * real, que os testes REST exercitam de ponta a ponta. O {@code EntityManager}
 * herdado nunca e acessado (permanece {@code null}); nenhum PostgreSQL esta
 * envolvido.</p>
 *
 * <p>Publico e no pacote {@code com.desafio.placar.api} para ser reutilizado
 * pela suite REST sem alterar a visibilidade das fakes ja existentes no pacote
 * {@code com.desafio.placar.service}.</p>
 */
public final class FakeJogoRepositoryRest extends JogoRepository {

    private final Map<Long, Jogo> armazenados = new LinkedHashMap<>();
    private final AtomicLong sequencia = new AtomicLong(0);

    @Override
    public Jogo salvar(Jogo jogo) {
        if (jogo.getId() == null) {
            jogo.setId(sequencia.incrementAndGet());
        }
        armazenados.put(jogo.getId(), jogo);
        return jogo;
    }

    @Override
    public Optional<Jogo> buscarPorId(Long id) {
        return Optional.ofNullable(armazenados.get(id));
    }

    @Override
    public List<Jogo> listarTodos() {
        return new ArrayList<>(armazenados.values());
    }

    @Override
    public List<Jogo> listarPorStatus(Status status) {
        List<Jogo> filtrados = new ArrayList<>();
        for (Jogo jogo : armazenados.values()) {
            if (jogo.getStatus() == status) {
                filtrados.add(jogo);
            }
        }
        return filtrados;
    }
}
