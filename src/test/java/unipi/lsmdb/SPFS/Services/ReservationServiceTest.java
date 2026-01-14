package unipi.lsmdb.SPFS.Services;

import unipi.lsmdb.SPFS.Entities.ParkingLot;
import unipi.lsmdb.SPFS.Entities.Reservations;
import unipi.lsmdb.SPFS.Entities.Slot;
import unipi.lsmdb.SPFS.Repositories.ParkingLotRepository;
import unipi.lsmdb.SPFS.Repositories.ReservationRepository;
import unipi.lsmdb.SPFS.Repositories.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        // Lenient mocks for Redis operations to avoid unnecessary stubbing errors
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void bookSlot_Success() {
        // Arrange
        String userId = "user123";
        String lotId = "lot1";
        String slotNumber = "A-1";

        // 1. Mock Redic Locking (Success)
        when(valueOperations.setIfAbsent(eq("user:active_booking:" + userId), eq("ACTIVE")))
                .thenReturn(true);

        // 2. Mock Redis SPOP (Returning a slot)
        when(setOperations.pop("lot:slots:" + lotId)).thenReturn(slotNumber);

        // 3. Mock DB interactions
        ParkingLot lot = new ParkingLot();
        lot.setId(lotId);
        lot.setSlotIds(Collections.singletonList("slot1"));
        when(parkingLotRepository.findById(lotId)).thenReturn(Optional.of(lot));

        Slot dbSlot = new Slot();
        dbSlot.setId("slot1");
        dbSlot.setSlotNumber(slotNumber);
        dbSlot.setStatus("AVAILABLE");
        when(slotRepository.findAllById(lot.getSlotIds())).thenReturn(Collections.singletonList(dbSlot));

        when(slotRepository.save(any(Slot.class))).thenAnswer(i -> i.getArguments()[0]);
        when(reservationRepository.save(any(Reservations.class))).thenAnswer(i -> {
            Reservations r = (Reservations) i.getArguments()[0];
            r.setId("res1");
            return r;
        });

        // Act
        Reservations result = reservationService.bookSlot(userId, lotId, "ABC-123", 2);

        // Assert
        assertNotNull(result);
        assertEquals("ACTIVE", result.getReservationStatus());
        assertEquals("slot1", result.getSlotId());

        // Verify Redis Lock was attempted
        verify(valueOperations).setIfAbsent(eq("user:active_booking:" + userId), eq("ACTIVE"));
        // Verify Slot was popped
        verify(setOperations).pop("lot:slots:" + lotId);
        // Verify DB updates
        verify(slotRepository).save(argThat(s -> "BOOKED".equals(s.getStatus())));
    }

    @Test
    void bookSlot_UserAlreadyActive_ShouldFail() {
        // Arrange
        String userId = "user123";
        // Mock Redis Locking failing (User already active)
        when(valueOperations.setIfAbsent(eq("user:active_booking:" + userId), eq("ACTIVE")))
                .thenReturn(false);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            reservationService.bookSlot(userId, "lot1", "ABC-123", 2);
        });

        assertEquals("User already has an active booking. Please cancel it before booking again.",
                exception.getMessage());

        // Verify we NEVER tried to pop a slot
        verify(setOperations, never()).pop(anyString());
        // Verify we NEVER saved to repo
        verify(reservationRepository, never()).save(any());
    }
}

