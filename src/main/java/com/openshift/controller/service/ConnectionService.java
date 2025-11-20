package com.openshift.controller.service;

import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.repository.OpenShiftConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления конфигурациями подключения к OpenShift
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final OpenShiftConnectionRepository repository;

    /**
     * Получить активное подключение
     */
    public Optional<OpenShiftConnection> getActiveConnection() {
        return repository.findByActiveTrue();
    }

    /**
     * Проверить, настроено ли подключение
     */
    public boolean hasActiveConnection() {
        return repository.existsByActiveTrue();
    }

    /**
     * Сохранить новое подключение
     * Если это первое подключение или указано active=true, оно станет активным
     */
    @Transactional
    public OpenShiftConnection saveConnection(OpenShiftConnection connection) {
        // Если это подключение должно быть активным, деактивируем все остальные
        if (connection.getActive() == null || connection.getActive()) {
            deactivateAllConnections();
            connection.setActive(true);
        }
        
        OpenShiftConnection saved = repository.save(connection);
        log.info("Сохранена конфигурация подключения: {} (ID: {})", saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Обновить существующее подключение
     */
    @Transactional
    public OpenShiftConnection updateConnection(Long id, OpenShiftConnection updatedConnection) {
        OpenShiftConnection existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Подключение с ID " + id + " не найдено"));
        
        existing.setMasterUrl(updatedConnection.getMasterUrl());
        existing.setToken(updatedConnection.getToken());
        existing.setDefaultNamespace(updatedConnection.getDefaultNamespace());
        existing.setName(updatedConnection.getName());
        
        // Если это подключение должно быть активным, деактивируем все остальные
        if (updatedConnection.getActive() != null && updatedConnection.getActive()) {
            deactivateAllConnections();
            existing.setActive(true);
        } else if (updatedConnection.getActive() != null && !updatedConnection.getActive()) {
            existing.setActive(false);
        }
        
        OpenShiftConnection saved = repository.save(existing);
        log.info("Обновлена конфигурация подключения: {} (ID: {})", saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Активировать подключение (деактивирует все остальные)
     */
    @Transactional
    public void activateConnection(Long id) {
        deactivateAllConnections();
        OpenShiftConnection connection = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Подключение с ID " + id + " не найдено"));
        connection.setActive(true);
        repository.save(connection);
        log.info("Активировано подключение: {} (ID: {})", connection.getName(), id);
    }

    /**
     * Деактивировать все подключения
     */
    @Transactional
    public void deactivateAllConnections() {
        List<OpenShiftConnection> activeConnections = repository.findAll().stream()
                .filter(OpenShiftConnection::getActive)
                .toList();
        
        for (OpenShiftConnection conn : activeConnections) {
            conn.setActive(false);
            repository.save(conn);
        }
    }

    /**
     * Получить все подключения
     */
    public List<OpenShiftConnection> getAllConnections() {
        return repository.findAll();
    }

    /**
     * Удалить подключение
     */
    @Transactional
    public void deleteConnection(Long id) {
        repository.deleteById(id);
        log.info("Удалено подключение с ID: {}", id);
    }
}

