package br.edu.lab2.carros.repository;

import br.edu.lab2.carros.domain.RentalRequest;
import br.edu.lab2.carros.domain.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentalRequestRepository extends JpaRepository<RentalRequest, UUID> {
    List<RentalRequest> findAllByClientProfile_UserAccount_IdOrderBySubmittedAtDesc(UUID userId);
    Optional<RentalRequest> findByIdAndClientProfile_UserAccount_Id(UUID id, UUID userId);
    List<RentalRequest> findAllByOrderBySubmittedAtDesc();

    @Query("""
            select case when count(r) > 0 then true else false end
            from RentalRequest r
            where r.car.id = :carId
              and r.status not in :closedStatuses
              and r.startDate < :endDate
              and r.endDate > :startDate
              and (:ignoredRequestId is null or r.id <> :ignoredRequestId)
            """)
    boolean hasConflictingRequest(@Param("carId") UUID carId,
                                  @Param("startDate") java.time.LocalDate startDate,
                                  @Param("endDate") java.time.LocalDate endDate,
                                  @Param("ignoredRequestId") UUID ignoredRequestId,
                                  @Param("closedStatuses") List<RequestStatus> closedStatuses);
}
