package com.openshift.controller.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity для групп подключений
 * Группа объединяет несколько подключений для одновременного управления
 */
@Entity
@Table(name = "connection_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название группы
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * Описание группы (необязательно)
     */
    @Column(length = 500)
    private String description;

    /**
     * Подключения в этой группе
     */
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OpenShiftConnection> connections = new ArrayList<>();

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

