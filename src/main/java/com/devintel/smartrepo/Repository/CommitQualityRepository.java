package com.devintel.smartrepo.Repository;

import com.devintel.smartrepo.Entity.CommitQuality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.devintel.smartrepo.Entity.RepoAnalysis;

import java.util.Optional;

@Repository
public interface CommitQualityRepository extends JpaRepository<CommitQuality, Integer> {
    Optional<CommitQuality> findByAnalysis(RepoAnalysis analysis);
}
