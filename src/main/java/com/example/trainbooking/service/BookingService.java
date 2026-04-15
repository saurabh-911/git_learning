package com.example.trainbooking.service;

import com.example.trainbooking.dto.BookingRequest;
import com.example.trainbooking.dto.BookingResponse;
import com.example.trainbooking.entity.Booking;
import com.example.trainbooking.entity.IdempotencyRecord;
import com.example.trainbooking.entity.Seat;
import com.example.trainbooking.exception.BookingFailedException;
import com.example.trainbooking.exception.QueueNotReadyException;
import com.example.trainbooking.kafka.BookingConfirmedEvent;
import com.example.trainbooking.kafka.BookingEventProducer;
import com.example.trainbooking.queue.QueueService;
import com.example.trainbooking.repository.BookingRepository;
import com.example.trainbooking.repository.IdempotencyRecordRepository;
import com.example.trainbooking.repository.SeatRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final BookingEventProducer bookingEventProducer;
    private final QueueService queueService;

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        queueService.enqueueIfAbsent(request.userId());

        if (!queueService.isAdmitted(request.userId())) {
            throw new QueueNotReadyException("User is in waiting room. Poll /queue-status.");
        }

        var existing = idempotencyRecordRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return new BookingResponse(existing.get().getBookingId(), "CONFIRMED", "Idempotent replay served");
        }

        Seat seat = seatRepository.findSeatForUpdate(request.seatId())
                .orElseThrow(() -> new BookingFailedException("Seat not found"));

        if (seat.isBooked()) {
            throw new BookingFailedException("Seat already booked");
        }
        if (!seat.getTrainId().equals(request.trainId())) {
            throw new BookingFailedException("Seat does not belong to requested train");
        }

        seat.setBooked(true);
        seatRepository.save(seat);

        Booking booking = new Booking();
        booking.setUserId(request.userId());
        booking.setSeatId(request.seatId());
        booking.setTrainId(request.trainId());
        booking.setCreatedAt(Instant.now());
        bookingRepository.save(booking);

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(request.idempotencyKey());
        record.setUserId(request.userId());
        record.setBookingId(booking.getId());
        idempotencyRecordRepository.save(record);

        queueService.consumeAdmission(request.userId());
        bookingEventProducer.publish(new BookingConfirmedEvent(
                booking.getId(), request.userId(), request.seatId(), request.trainId(), booking.getCreatedAt()
        ));
        log.info("Booking confirmed bookingId={} user={} seat={}", booking.getId(), request.userId(), request.seatId());

        return new BookingResponse(booking.getId(), "CONFIRMED", "Seat booked successfully");
    }
}
