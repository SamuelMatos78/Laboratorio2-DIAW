package br.edu.lab2.carros.repository;

import br.edu.lab2.carros.domain.CreditContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditContractRepository extends JpaRepository<CreditContract, UUID> {
    java.util.Optional<CreditContract> findByRentalContract_Id(UUID contractId);
}
