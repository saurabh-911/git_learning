package com.example.trainbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
public class Seat {

    @Id
    private Long id;

    @Column(nullable = false)
    private String trainId;

    @Column(nullable = false)
    private String coach;

    @Column(nullable = false)
    private String seatNumber;

    @Column(nullable = false)
    private boolean booked;

    @Version
    private Long version;
}
