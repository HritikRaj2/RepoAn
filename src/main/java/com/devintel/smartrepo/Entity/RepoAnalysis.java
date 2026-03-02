package com.devintel.smartrepo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
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

    private Integer riskLevel;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;
}