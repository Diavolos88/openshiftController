package com.openshift.controller.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity для хранения конфигурации подключения к OpenShift кластеру
 */
@Entity
@Table(name = "openshift_connections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenShiftConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * URL кластера OpenShift
     */
    @Column(nullable = false, length = 500)
    private String masterUrl;

    /**
     * Токен для аутентификации
     */
    @Column(nullable = false, length = 1000)
    private String token;

    /**
     * Namespace по умолчанию
     */
    @Column(length = 100)
    private String defaultNamespace;

    /**
     * Название подключения (для удобства пользователя)
     */
    @Column(length = 200)
    private String name;

    /**
     * Активно ли это подключение (может быть только одно активное)
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Дата создания
     */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Дата последнего обновления
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

