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
 * Сущность для хранения времени старта подов для Deployments в БД
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pod_startup_times", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "namespace", "deployment_name"}))
public class PodStartupTime {

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

    @Column(name = "startup_time_seconds", nullable = false)
    private Long startupTimeSeconds;

    @UpdateTimestamp
    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
