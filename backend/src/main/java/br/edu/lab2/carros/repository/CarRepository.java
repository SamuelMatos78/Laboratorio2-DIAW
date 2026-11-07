package br.edu.lab2.carros.repository;

import br.edu.lab2.carros.domain.Car;
import br.edu.lab2.carros.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {
    boolean existsByAvailableTrue();

    List<Car> findAllByAvailableTrueOrderByDailyPriceAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Car c where c.id = :carId and c.available = true")
    Optional<Car> lockAvailableCar(@Param("carId") UUID carId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Car c where c.id = :carId")
    Optional<Car> lockCar(@Param("carId") UUID carId);

    @Query("""
            select c from Car c
            where c.available = true
              and not exists (
                  select r.id from RentalRequest r
                  where r.car = c
                    and r.status not in :closedStatuses
                    and r.startDate < :endDate
                    and r.endDate > :startDate
                    and (:ignoredRequestId is null or r.id <> :ignoredRequestId)
              )
            order by c.dailyPrice asc, c.make asc, c.model asc
            """)
    List<Car> findAvailableForPeriod(@Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate,
                                     @Param("ignoredRequestId") UUID ignoredRequestId,
                                     @Param("closedStatuses") Collection<RequestStatus> closedStatuses);
}
