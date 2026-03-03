package com.devintel.smartrepo.Entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "filerisk")
public class FileRisk {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "analysis_id", nullable = false)
    private com.devintel.smartrepo.entity.RepoAnalysis analysis;

    @Column(nullable = false)
    private String file_path;
    private Integer lines_of_code;
    private Integer complexity_score;
    private Integer security_smells;
    private Integer riskScore;
    private String riskLevel;




}
