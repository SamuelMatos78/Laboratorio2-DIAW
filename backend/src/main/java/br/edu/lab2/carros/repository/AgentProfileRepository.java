package br.edu.lab2.carros.repository;

import br.edu.lab2.carros.domain.AgentProfile;
import br.edu.lab2.carros.domain.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AgentProfileRepository extends JpaRepository<AgentProfile, UUID> {
    Optional<AgentProfile> findByUserAccount_Id(UUID userId);
    Optional<AgentProfile> findByIdAndType(UUID id, AgentType type);
    List<AgentProfile> findAllByTypeOrderByEntityNameAsc(AgentType type);
}
