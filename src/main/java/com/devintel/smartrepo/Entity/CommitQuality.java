package com.devintel.smartrepo.Entity;


import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "commitquality")
public class CommitQuality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "analysis_id" , nullable = false)
    private com.devintel.smartrepo.Entity.RepoAnalysis analysis;

    private Integer total_commits;

    private Integer good_messages;

    private Integer bad_messages;

    private Integer avg_commit_size;

    private Integer quality_score;


}
