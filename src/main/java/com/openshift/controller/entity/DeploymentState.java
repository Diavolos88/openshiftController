package com.openshift.controller.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность для хранения стартового состояния Deployments в БД
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deployment_states", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "namespace", "deployment_name"}))
public class DeploymentState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private OpenShiftConnection connection;

    @Column(name = "namespace", nullable = false, length = 255)
    private String namespace;

    @Column(name = "deployment_name", nullable = false, length = 255)
    private String deploymentName;

    @Column(name = "original_replicas", nullable = false)
    private Integer originalReplicas;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

