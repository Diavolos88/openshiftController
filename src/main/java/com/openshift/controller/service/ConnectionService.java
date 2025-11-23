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
     * Получить первое активное подключение (для обратной совместимости)
     * Теперь может быть несколько активных подключений одновременно
     */
    public Optional<OpenShiftConnection> getActiveConnection() {
        return repository.findFirstByActiveTrueOrderByIdAsc();
    }

    /**
     * Проверить, настроено ли подключение
     */
    public boolean hasActiveConnection() {
        return repository.existsByActiveTrue();
    }

    /**
     * Сохранить новое подключение
     * Все подключения теперь активны одновременно, поле active используется только для отображения в UI
     */
    @Transactional
    public OpenShiftConnection saveConnection(OpenShiftConnection connection) {
        // Все подключения по умолчанию активны (не деактивируем другие)
        if (connection.getActive() == null) {
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
        existing.setNamespace(updatedConnection.getNamespace());
        existing.setName(updatedConnection.getName());
        existing.setIsMock(updatedConnection.getIsMock() != null ? updatedConnection.getIsMock() : false);
        existing.setGroup(updatedConnection.getGroup());
        
        // Поле active используется только для отображения в UI, не влияет на работу подключения
        if (updatedConnection.getActive() != null) {
            existing.setActive(updatedConnection.getActive());
        }
        
        OpenShiftConnection saved = repository.save(existing);
        log.info("Обновлена конфигурация подключения: {} (ID: {})", saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Активировать/деактивировать подключение (только для UI, не влияет на работу)
     */
    @Transactional
    public void activateConnection(Long id) {
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
     * Получить подключение по ID
     */
    public Optional<OpenShiftConnection> getConnectionById(Long id) {
        return repository.findById(id);
    }

    /**
     * Удалить подключение
     */
    @Transactional
    public void deleteConnection(Long id) {
        repository.deleteById(id);
        log.info("Удалено подключение с ID: {}", id);
    }

    /**
     * Сохранить список подключений (для обновления связей с группами)
     */
    @Transactional
    public List<OpenShiftConnection> saveAll(List<OpenShiftConnection> connections) {
        List<OpenShiftConnection> saved = repository.saveAll(connections);
        log.info("Сохранено {} подключений", saved.size());
        return saved;
    }
}

