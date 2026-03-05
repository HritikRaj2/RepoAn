package com.devintel.smartrepo.Repository;

import com.devintel.smartrepo.Entity.RepoAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import com.devintel.smartrepo.Entity.User;

import java.util.List;
import java.util.Optional;

public interface RepoAnalysisRepository extends JpaRepository<RepoAnalysis, Integer> {
    List<RepoAnalysis> findByUser(User user);
    Optional<RepoAnalysis> findTopByRepoUrlOrderByIdDesc(String repoUrl);
}
