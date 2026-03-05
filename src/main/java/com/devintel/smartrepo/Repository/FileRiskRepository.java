package com.devintel.smartrepo.Repository;

import com.devintel.smartrepo.Entity.FileRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devintel.smartrepo.Entity.RepoAnalysis;
import java.util.List;

public interface FileRiskRepository extends JpaRepository<FileRisk, Integer> {
    List<FileRisk> findByAnalysis(RepoAnalysis analysis);
    List<FileRisk> findByAnalysisOrderByRiskScoreDesc(RepoAnalysis analysis);
}
