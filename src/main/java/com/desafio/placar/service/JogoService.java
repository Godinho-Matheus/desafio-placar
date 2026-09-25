package com.desafio.placar.service;

import java.time.OffsetDateTime;
import java.util.List;

import com.desafio.placar.domain.Jogo;
import com.desafio.placar.domain.Status;
import com.desafio.placar.domain.excecao.EntradaInvalidaException;
import com.desafio.placar.domain.excecao.JogoEncerradoException;
import com.desafio.placar.domain.excecao.NaoEncontradoException;
import com.desafio.placar.persistence.JogoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Servico de aplicacao que orquestra os casos de uso de Jogo.
 *
 * <p>Concentra as regras de negocio compartilhadas pela Camada REST e pela
 * Interface_Web: validacao dos dados de criacao, filtro de listagem, busca por
 * identificador e transicao de status. O acesso ao PostgreSQL e feito
 * exclusivamente por meio do {@link JogoRepository} (fonte de verdade); nenhuma
 * consulta ou regra vive na camada de persistencia.</p>
 *
 * <p>E um bean CDI {@link ApplicationScoped}, coerente com o {@link JogoRepository},
 * e recebe suas dependencias via {@link Inject}.</p>
 */
@ApplicationScoped
public class JogoService {

    private final JogoRepository repository;

    /**
     * Construtor padrao exigido pelo CDI.
     */
    protected JogoService() {
        this.repository = null;
    }

    /**
     * Cria o servico com o repositorio de Jogos.
     *
     * @param repository repositorio JPA de Jogos
     */
    @Inject
    public JogoService(JogoRepository repository) {
        this.repository = repository;
    }

    /**
     * Cria um novo Jogo com placar inicial 0 x 0 e status
     * {@link Status#EM_ANDAMENTO}.
     *
     * <p>Valida os campos obrigatorios: {@code timeA} e {@code timeB} nao podem
     * ser nulos nem em branco e {@code dataHoraPartida} nao pode ser nulo. Quando
     * algum campo obrigatorio esta ausente, lanca {@link EntradaInvalidaException}
     * com mensagem que identifica o campo e nao persiste nada.</p>
     *
     * @param timeA           nome do time da casa (obrigatorio)
     * @param timeB           nome do time visitante (obrigatorio)
     * @param dataHoraPartida data e hora programada da partida (obrigatorio)
     * @return o Jogo persistido, com identificador gerado
     * @throws EntradaInvalidaException se algum campo obrigatorio estiver ausente
     */
    @Transactional
    public Jogo criar(String timeA, String timeB, OffsetDateTime dataHoraPartida) {
        if (timeA == null || timeA.isBlank()) {
            throw new EntradaInvalidaException("O campo obrigatorio 'timeA' nao foi informado.");
        }
        if (timeB == null || timeB.isBlank()) {
            throw new EntradaInvalidaException("O campo obrigatorio 'timeB' nao foi informado.");
        }
        if (dataHoraPartida == null) {
            throw new EntradaInvalidaException(
                    "O campo obrigatorio 'dataHoraPartida' nao foi informado.");
        }

        Jogo jogo = new Jogo();
        jogo.setTimeA(timeA);
        jogo.setTimeB(timeB);
        jogo.setDataHoraPartida(dataHoraPartida);
        jogo.setPlacarA(0);
        jogo.setPlacarB(0);
        jogo.setStatus(Status.EM_ANDAMENTO);

        return repository.salvar(jogo);
    }

    /**
     * Lista os Jogos, opcionalmente filtrando por status.
     *
     * @param filtroOpcional status usado como filtro; {@code null} retorna todos
     *                       os Jogos
     * @return a lista de Jogos (vazia se nenhum corresponder)
     */
    public List<Jogo> listar(Status filtroOpcional) {
        if (filtroOpcional == null) {
            return repository.listarTodos();
        }
        return repository.listarPorStatus(filtroOpcional);
    }

    /**
     * Busca um Jogo pelo identificador.
     *
     * @param id identificador do Jogo
     * @return o Jogo encontrado
     * @throws NaoEncontradoException se nao existir Jogo com o identificador
     */
    public Jogo buscarPorId(Long id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException(
                        "Jogo nao encontrado para o identificador " + id + "."));
    }

    /**
     * Encerra um Jogo, transitando o status de {@link Status#EM_ANDAMENTO} para
     * {@link Status#ENCERRADO}.
     *
     * <p>A operacao e idempotente: se o Jogo ja estiver {@link Status#ENCERRADO},
     * nada e alterado e o proprio Jogo e retornado. Em ambos os casos {@code placarA}
     * e {@code placarB} permanecem inalterados.</p>
     *
     * @param id identificador do Jogo
     * @return o Jogo com status {@link Status#ENCERRADO}
     * @throws NaoEncontradoException se nao existir Jogo com o identificador
     */
    @Transactional
    public Jogo encerrar(Long id) {
        Jogo jogo = buscarPorId(id);
        if (jogo.getStatus() == Status.ENCERRADO) {
            return jogo;
        }
        jogo.setStatus(Status.ENCERRADO);
        return repository.salvar(jogo);
    }

    /**
     * Atualiza o placar de um Jogo {@link Status#EM_ANDAMENTO}, substituindo
     * {@code placarA} e {@code placarB} pelos valores informados e persistindo o
     * novo placar no PostgreSQL.
     *
     * <p>Valida que {@code placarA} e {@code placarB} sao maiores ou iguais a 0;
     * quando qualquer um for negativo, lanca {@link EntradaInvalidaException}
     * (mapeada para HTTP 400 em tarefa posterior) e nao altera o placar
     * persistido. Se o identificador nao corresponder a Jogo persistido, lanca
     * {@link NaoEncontradoException} (HTTP 404). Se o Jogo estiver
     * {@link Status#ENCERRADO}, lanca {@link JogoEncerradoException} (HTTP 409),
     * preservando o placar e sem persistir, conforme a regra de que uma partida
     * encerrada nao pode ter o placar alterado.</p>
     *
     * <p>A operacao e transacional (JTA): a carga, a validacao da regra de
     * negocio e a persistencia do novo placar ocorrem na mesma transacao.</p>
     *
     * @param id      identificador do Jogo
     * @param placarA novo placar do time da casa (inteiro maior ou igual a 0)
     * @param placarB novo placar do time visitante (inteiro maior ou igual a 0)
     * @return o Jogo com o placar atualizado
     * @throws EntradaInvalidaException se {@code placarA} ou {@code placarB} for negativo
     * @throws NaoEncontradoException   se nao existir Jogo com o identificador
     * @throws JogoEncerradoException   se o Jogo estiver {@link Status#ENCERRADO}
     */
    @Transactional
    public Jogo atualizarPlacar(Long id, int placarA, int placarB) {
        if (placarA < 0) {
            throw new EntradaInvalidaException(
                    "O campo 'placarA' deve ser um inteiro maior ou igual a 0.");
        }
        if (placarB < 0) {
            throw new EntradaInvalidaException(
                    "O campo 'placarB' deve ser um inteiro maior ou igual a 0.");
        }

        Jogo jogo = buscarPorId(id);

        if (jogo.getStatus() == Status.ENCERRADO) {
            throw new JogoEncerradoException(
                    "O Jogo " + id + " esta encerrado e nao pode ter o placar alterado.");
        }

        jogo.setPlacarA(placarA);
        jogo.setPlacarB(placarB);
        Jogo atualizado = repository.salvar(jogo);

        // Ponto de disparo do evento CDI reservado: o Event.fire(...) do
        // PlacarAtualizadoEvent (dentro desta transacao, apos a persistencia)
        // sera adicionado na tarefa 7.1.

        return atualizado;
    }
}
