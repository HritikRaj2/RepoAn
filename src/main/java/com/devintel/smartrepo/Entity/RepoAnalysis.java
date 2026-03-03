package com.devintel.smartrepo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Table(name = "repo_analysis")
@NoArgsConstructor
@AllArgsConstructor
public class RepoAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private com.devintel.smartrepo.entity.User user;

    private String repoUrl;

    private Integer healthScore;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    @Column(columnDefinition = "TEXT")
    private String aiSummary;
}