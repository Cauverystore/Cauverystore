package com.cauverystore.repository;

import com.cauverystore.entities.ImpersonationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSession, Long> {

    List<ImpersonationSession> findByActiveTrue();

    List<ImpersonationSession> findByImpersonatorId(Long impersonatorId);
}
