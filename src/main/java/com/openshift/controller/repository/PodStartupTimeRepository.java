package com.openshift.controller.repository;

import com.openshift.controller.entity.PodStartupTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PodStartupTimeRepository extends JpaRepository<PodStartupTime, Long> {
    
    /**
     * Найти время старта для конкретного deployment
     */
    Optional<PodStartupTime> findByConnectionIdAndNamespaceAndDeploymentName(
            Long connectionId, String namespace, String deploymentName);
    
    /**
     * Удалить время старта для конкретного deployment
     */
    @Modifying
    void deleteByConnectionIdAndNamespaceAndDeploymentName(Long connectionId, String namespace, String deploymentName);
}
