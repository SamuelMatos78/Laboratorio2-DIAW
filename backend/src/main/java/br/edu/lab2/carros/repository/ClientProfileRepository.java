package br.edu.lab2.carros.repository;

import br.edu.lab2.carros.domain.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {
    Optional<ClientProfile> findByUserAccount_Id(UUID userId);
    boolean existsByCpf(String cpf);
    boolean existsByRg(String rg);
    boolean existsByCpfAndIdNot(String cpf, UUID id);
    boolean existsByRgAndIdNot(String rg, UUID id);
}
