package unipi.lsmdb.SPFS.Controller;

import unipi.lsmdb.SPFS.Services.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class AnalyticsController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/duration")
    public ResponseEntity<List<Map>> getAverageDuration() {
        return ResponseEntity.ok(adminService.getAverageParkingDuration());
    }

    @GetMapping("/peak-hours")
    public ResponseEntity<List<Map>> getPeakHours() {
        return ResponseEntity.ok(adminService.getPeakBookingHours());
    }

    @GetMapping("/user-loyalty")
    public ResponseEntity<List<Map>> getUserLoyalty() {
        return ResponseEntity.ok(adminService.getUserLoyaltyDistribution());
    }
}

