package concurrent_reservation_service.service;
import concurrent_reservation_service.model.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class ReservationServiceTest {
    private ReservationService service;
    @BeforeEach
    void setUp() {
        service = new ReservationService();
    }
    @Test
    void shouldBookAvailableAppointment() {
        Reservation reservation =
                service.bookAppointment("SLOT-001", "Student A");
        assertNotNull(reservation.id());
        assertEquals("SLOT-001", reservation.appointmentSlotId());
        assertEquals("Student A", reservation.customerName());
    }
    @Test
    void shouldPreventDoubleBooking() {
        service.bookAppointment("SLOT-001", "Student A");
        assertThrows(IllegalStateException.class,
                () -> service.bookAppointment("SLOT-001", "Student B"));
    }
    @Test
    void shouldRejectInvalidSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bookAppointment("INVALID", "Student A"));
    }
    @Test
    void shouldRejectBlankCustomerName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.bookAppointment("SLOT-001", " "));
    }
    @Test
    void shouldReleaseSlotAfterCancellation() {
        Reservation reservation =
                service.bookAppointment("SLOT-001", "Student A");
        service.cancelReservation(reservation.id());
        Reservation newReservation =
                service.bookAppointment("SLOT-001", "Student B");
        assertNotNull(newReservation);
        assertEquals("Student B", newReservation.customerName());
    }
    @Test
    void shouldAllowOnlyOneConcurrentReservation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> bookingTask = () -> {
            ready.countDown();
            start.await();
            try {
                service.bookAppointment(
                        "SLOT-001",
                        Thread.currentThread().getName()
                );
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        };
        try {
            Future<Boolean> first = executor.submit(bookingTask);
            Future<Boolean> second = executor.submit(bookingTask);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            if (first.get(5, TimeUnit.SECONDS)) {
                successes++;
            }
            if (second.get(5, TimeUnit.SECONDS)) {
                successes++;
            }
            assertEquals(1, successes,
                    "Only one client should reserve the final slot");
            assertEquals(1, service.getAllReservations().size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}