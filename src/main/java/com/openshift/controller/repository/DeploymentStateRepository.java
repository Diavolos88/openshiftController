package com.openshift.controller.repository;

import com.openshift.controller.entity.DeploymentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentStateRepository extends JpaRepository<DeploymentState, Long> {
    
    /**
     * Найти состояние для конкретного deployment
     */
    Optional<DeploymentState> findByConnectionIdAndNamespaceAndDeploymentName(
            Long connectionId, String namespace, String deploymentName);
    
    /**
     * Получить все состояния для namespace подключения
     */
    List<DeploymentState> findByConnectionIdAndNamespace(Long connectionId, String namespace);
    
    /**
     * Проверить, есть ли сохраненные состояния для namespace подключения
     */
    boolean existsByConnectionIdAndNamespace(Long connectionId, String namespace);
    
    /**
     * Удалить все состояния для namespace подключения
     */
    @Modifying
    @Query("DELETE FROM DeploymentState ds WHERE ds.connection.id = :connectionId AND ds.namespace = :namespace")
    void deleteByConnectionIdAndNamespace(@Param("connectionId") Long connectionId, @Param("namespace") String namespace);
    
    /**
     * Удалить состояние для конкретного deployment
     */
    @Modifying
    void deleteByConnectionIdAndNamespaceAndDeploymentName(Long connectionId, String namespace, String deploymentName);
}

