package com.desafio.placar.web;

import java.time.OffsetDateTime;
import java.util.List;

import com.desafio.placar.cache.PlacarAtual;
import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.service.JogoService;

/**
 * Stub controlavel do {@link JogoService} para os testes de UI (Tarefa 11.4).
 *
 * <p>Subclasse do {@link JogoService} real usando o construtor sem argumentos
 * {@code protected} (acessivel a subclasses). Sobrescreve apenas os metodos que
 * a pagina renderizada realmente chama: {@link #listar(Status)} (carga inicial
 * da tabela) e {@link #obterPlacarAtual(Long)} (usado pelo polling). Nao duplica
 * regras de negocio: apenas retorna valores controlaveis pelo teste.</p>
 */
final class ServicoControlavel extends JogoService {

    private final Jogo jogo;
    private PlacarAtual placarAtual;

    /**
     * Cria o stub com um unico Jogo EM_ANDAMENTO (placar inicial 0x0) e um
     * {@link PlacarAtual} inicial tambem 0x0 (mutavel pelo teste).
     */
    ServicoControlavel() {
        super();
        this.jogo = new Jogo();
        this.jogo.setId(1L);
        this.jogo.setTimeA("Palmeiras");
        this.jogo.setTimeB("Corinthians");
        this.jogo.setPlacarA(0);
        this.jogo.setPlacarB(0);
        this.jogo.setStatus(Status.EM_ANDAMENTO);
        this.jogo.setDataHoraPartida(OffsetDateTime.parse("2026-01-01T20:00:00Z"));
        this.placarAtual = new PlacarAtual(0, 0);
    }

    /** Permite ao teste alterar o placar retornado pelo polling. */
    void definirPlacarAtual(PlacarAtual placarAtual) {
        this.placarAtual = placarAtual;
    }

    @Override
    public List<Jogo> listar(Status filtroOpcional) {
        return List.of(jogo);
    }

    @Override
    public PlacarAtual obterPlacarAtual(Long id) {
        return placarAtual;
    }
}
