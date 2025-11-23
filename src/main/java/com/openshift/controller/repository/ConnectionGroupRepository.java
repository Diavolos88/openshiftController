package com.openshift.controller.repository;

import com.openshift.controller.entity.ConnectionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository для работы с группами подключений
 */
@Repository
public interface ConnectionGroupRepository extends JpaRepository<ConnectionGroup, Long> {
    
    /**
     * Найти все группы, отсортированные по имени
     */
    List<ConnectionGroup> findAllByOrderByNameAsc();
}

