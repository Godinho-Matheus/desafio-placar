package com.desafio.placar.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA que representa uma partida de futebol.
 *
 * <p>O PostgreSQL guarda o estado completo do Jogo por meio desta entidade, sendo a
 * fonte de verdade do Sistema: identificador, times, placar, status e data/hora da
 * partida. O placar comeca em 0 x 0 e o status inicia como
 * {@link Status#EM_ANDAMENTO}. Nao ha restricao de igualdade entre {@code timeA} e
 * {@code timeB}.</p>
 */
@Entity
@Table(name = "jogo")
public class Jogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String timeA;

    @Column(nullable = false)
    private String timeB;

    @Column(nullable = false)
    private int placarA = 0;

    @Column(nullable = false)
    private int placarB = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.EM_ANDAMENTO;

    @Column(nullable = false)
    private OffsetDateTime dataHoraPartida;

    /**
     * Construtor padrao exigido pela JPA.
     */
    public Jogo() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTimeA() {
        return timeA;
    }

    public void setTimeA(String timeA) {
        this.timeA = timeA;
    }

    public String getTimeB() {
        return timeB;
    }

    public void setTimeB(String timeB) {
        this.timeB = timeB;
    }

    public int getPlacarA() {
        return placarA;
    }

    public void setPlacarA(int placarA) {
        this.placarA = placarA;
    }

    public int getPlacarB() {
        return placarB;
    }

    public void setPlacarB(int placarB) {
        this.placarB = placarB;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OffsetDateTime getDataHoraPartida() {
        return dataHoraPartida;
    }

    public void setDataHoraPartida(OffsetDateTime dataHoraPartida) {
        this.dataHoraPartida = dataHoraPartida;
    }
}
