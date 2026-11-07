package br.edu.lab2.carros.repository;

import br.edu.lab2.carros.domain.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {
    Optional<RentalContract> findByRentalRequest_Id(UUID requestId);
}
