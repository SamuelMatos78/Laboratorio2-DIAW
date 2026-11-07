package br.edu.lab2.carros.service;

import br.edu.lab2.carros.api.ApiDtos.AvailableCarResponse;
import br.edu.lab2.carros.domain.Car;
import br.edu.lab2.carros.domain.RequestStatus;
import br.edu.lab2.carros.repository.CarRepository;
import br.edu.lab2.carros.repository.RentalRequestRepository;
import br.edu.lab2.carros.security.AuthPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class CarService {
    private static final List<RequestStatus> CLOSED_STATUSES = List.of(RequestStatus.CANCELADO, RequestStatus.REPROVADO);

    private final CarRepository cars;
    private final RentalRequestRepository requests;

    public CarService(CarRepository cars, RentalRequestRepository requests) {
        this.cars = cars;
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public List<AvailableCarResponse> available(AuthPrincipal principal, LocalDate startDate, LocalDate endDate,
                                                UUID ignoredRequestId) {
        if (principal.role() != br.edu.lab2.carros.domain.UserRole.CLIENT) {
            throw ApiException.forbidden("A busca de veículos é exclusiva para clientes.");
        }
        validatePeriod(startDate, endDate);
        if (!cars.existsByAvailableTrue()) {
            throw ApiException.serviceUnavailable("A frota ainda não foi carregada no banco. Execute ./start.sh para aplicar a migration do catálogo de veículos.");
        }

        if (ignoredRequestId != null) {
            var request = requests.findByIdAndClientProfile_UserAccount_Id(ignoredRequestId, principal.userId())
                    .orElseThrow(() -> ApiException.notFound("Pedido não encontrado."));
            if (request.status != RequestStatus.PENDENTE_ANALISE) {
                throw ApiException.conflict("Somente pedidos pendentes podem ser editados.");
            }
        }

        return cars.findAvailableForPeriod(startDate, endDate, ignoredRequestId, CLOSED_STATUSES)
                .stream().map(this::toResponse).toList();
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start.isBefore(LocalDate.now())) throw ApiException.badRequest("A data de início não pode estar no passado.");
        if (!end.isAfter(start)) throw ApiException.badRequest("A data final deve ser posterior à data inicial.");
    }

    private AvailableCarResponse toResponse(Car car) {
        return new AvailableCarResponse(car.id, car.make, car.model, car.year, car.category,
                car.transmission, car.fuel, car.dailyPrice, car.imageUrl);
    }
}
