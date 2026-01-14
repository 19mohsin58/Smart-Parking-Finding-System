package unipi.lsmdb.SPFS.Controller;

import unipi.lsmdb.SPFS.Entities.Reservations;
import unipi.lsmdb.SPFS.Services.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class ReservationController {

    @Autowired
    private ReservationService reservationService;

    @PostMapping("/book")
    public ResponseEntity<?> bookSlot(@RequestBody unipi.lsmdb.SPFS.DTO.BookingRequestDTO payload) {
        try {
            Reservations reservation = reservationService.bookSlot(
                    payload.getUserId(),
                    payload.getParkingLotId(),
                    payload.getVehicleNumber(),
                    payload.getHours());
            return ResponseEntity.ok(reservation);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/cancel/{id}")
    public ResponseEntity<?> cancelReservation(@PathVariable String id) {
        try {
            reservationService.cancelReservation(id);
            return ResponseEntity.ok("Reservation cancelled and slot released.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<?> getActiveReservation(@PathVariable String userId) {
        Reservations active = reservationService.getUserActiveReservation(userId);
        if (active == null) {
            return ResponseEntity.ok().build(); // Return 200 OK with empty body if no active
        }
        return ResponseEntity.ok(active);
    }

    @GetMapping("/user/{userId}/history")
    public ResponseEntity<?> getReservationHistory(@PathVariable String userId) {
        return ResponseEntity.ok(reservationService.getUserReservationHistory(userId));
    }
}

