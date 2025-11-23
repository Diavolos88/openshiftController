package com.openshift.controller.repository;

import com.openshift.controller.entity.OpenShiftConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository для работы с конфигурациями подключения к OpenShift
 */
@Repository
public interface OpenShiftConnectionRepository extends JpaRepository<OpenShiftConnection, Long> {

    /**
     * Найти первое активное подключение (для обратной совместимости)
     * Теперь может быть несколько активных подключений одновременно
     */
    Optional<OpenShiftConnection> findFirstByActiveTrueOrderByIdAsc();

    /**
     * Проверить, существует ли хотя бы одно активное подключение
     */
    boolean existsByActiveTrue();
}

