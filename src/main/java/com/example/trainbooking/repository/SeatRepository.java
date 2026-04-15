package com.example.trainbooking.repository;

import com.example.trainbooking.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :seatId")
    Optional<Seat> findSeatForUpdate(@Param("seatId") Long seatId);

    @Modifying
@Query("UPDATE Seat s SET s.status = 'AVAILABLE', s.heldByUserId = null, s.holdExpiresAt = null " +
       "WHERE s.status = 'HELD' AND s.holdExpiresAt < :now")
int releaseExpiredHolds(@Param("now") Instant now);
}
