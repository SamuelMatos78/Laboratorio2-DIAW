package br.edu.lab2.carros.api;

import br.edu.lab2.carros.api.ApiDtos.AvailableCarResponse;
import br.edu.lab2.carros.security.AuthPrincipal;
import br.edu.lab2.carros.service.CarService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cars")
public class CarController {
    private final CarService cars;

    public CarController(CarService cars) { this.cars = cars; }

    @GetMapping("/available")
    public List<AvailableCarResponse> available(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID excludeRequestId) {
        return cars.available(principal, startDate, endDate, excludeRequestId);
    }
}
