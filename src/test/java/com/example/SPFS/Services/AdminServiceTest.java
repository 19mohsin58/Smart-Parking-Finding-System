package com.example.SPFS.Services;

import com.example.SPFS.Entities.ParkingLot;
import com.example.SPFS.Repositories.ParkingLotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getAverageParkingDuration_Success() {
        // Arrange
        // 1. Mock Aggregation Results
        Map<String, Object> rawResult = new HashMap<>();
        rawResult.put("_id", "lot1");
        rawResult.put("avgDuration", 2.5);

        AggregationResults<Map> aggregationResults = new AggregationResults<>(
                Collections.singletonList(rawResult),
                new org.bson.Document());

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("reservations"), eq(Map.class)))
                .thenReturn(aggregationResults);

        // 2. Mock Parking Lot Fetch for Enrichment
        ParkingLot lot = new ParkingLot();
        lot.setId("lot1");
        lot.setParkingName("Mall Parking");
        lot.setFullAddress("123 Main St");

        when(parkingLotRepository.findAllById(anyList()))
                .thenReturn(Collections.singletonList(lot));

        // Act
        List<Map> results = adminService.getAverageParkingDuration();

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        Map<String, Object> result = results.get(0);

        // Check Enrichment
        assertEquals("lot1", result.get("parkingLotId"));
        assertEquals("Mall Parking", result.get("parkingName"));
        assertEquals(2.5, result.get("avgDuration"));
    }
}
