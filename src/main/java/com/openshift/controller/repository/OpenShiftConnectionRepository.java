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
     * Найти активное подключение
     */
    Optional<OpenShiftConnection> findByActiveTrue();

    /**
     * Проверить, существует ли активное подключение
     */
    boolean existsByActiveTrue();
}

