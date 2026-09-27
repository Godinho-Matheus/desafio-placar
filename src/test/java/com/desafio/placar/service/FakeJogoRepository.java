package com.desafio.placar.service;

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
 * Repositorio em memoria para os testes unitarios do {@link com.desafio.placar.service.JogoService}.
 *
 * <p>Estende o {@link JogoRepository} real e sobrescreve todos os quatro metodos
 * publicos, de modo que o {@code EntityManager} herdado nunca e acessado (permanece
 * {@code null}). Nao ha PostgreSQL nem qualquer container envolvido.</p>
 */
final class FakeJogoRepository extends JogoRepository {

    private final Map<Long, Jogo> armazenados = new LinkedHashMap<>();
    private final AtomicLong sequencia = new AtomicLong(0);

    /** Numero de vezes que {@link #salvar(Jogo)} foi invocado. */
    private int salvarCount = 0;

    @Override
    public Jogo salvar(Jogo jogo) {
        salvarCount++;
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

    /** Quantidade de chamadas a {@link #salvar(Jogo)}, para asserts de persistencia. */
    int getSalvarCount() {
        return salvarCount;
    }

    /** Le diretamente o Jogo armazenado, sem passar pelas regras do servico. */
    Jogo armazenado(Long id) {
        return armazenados.get(id);
    }
}
