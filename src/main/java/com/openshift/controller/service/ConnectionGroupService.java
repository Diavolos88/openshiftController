package com.openshift.controller.service;

import com.openshift.controller.entity.ConnectionGroup;
import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.repository.ConnectionGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для управления группами подключений
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionGroupService {

    private final ConnectionGroupRepository repository;
    private final ConnectionService connectionService;

    /**
     * Получить все группы
     */
    public List<ConnectionGroup> getAllGroups() {
        return repository.findAllByOrderByNameAsc();
    }

    /**
     * Получить группу по ID
     */
    public Optional<ConnectionGroup> getGroupById(Long id) {
        return repository.findById(id);
    }

    /**
     * Создать новую группу
     */
    @Transactional
    public ConnectionGroup createGroup(String name, String description) {
        ConnectionGroup group = ConnectionGroup.builder()
                .name(name)
                .description(description)
                .build();
        
        ConnectionGroup saved = repository.save(group);
        log.info("Создана группа подключений: {} (ID: {})", saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Обновить группу
     */
    @Transactional
    public ConnectionGroup updateGroup(Long id, String name, String description) {
        ConnectionGroup group = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Группа с ID " + id + " не найдена"));
        
        group.setName(name);
        group.setDescription(description);
        
        ConnectionGroup saved = repository.save(group);
        log.info("Обновлена группа подключений: {} (ID: {})", saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Удалить группу
     * При удалении группы подключения не удаляются, они просто перестают принадлежать группе
     */
    @Transactional
    public void deleteGroup(Long id) {
        ConnectionGroup group = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Группа с ID " + id + " не найдена"));
        
        // Удаляем связь с подключениями (подключения не удаляются)
        List<OpenShiftConnection> connections = group.getConnections();
        connections.forEach(conn -> conn.setGroup(null));
        if (!connections.isEmpty()) {
            connectionService.saveAll(connections);
        }
        
        repository.delete(group);
        log.info("Удалена группа подключений с ID: {}", id);
    }

    /**
     * Добавить подключение в группу
     */
    @Transactional
    public void addConnectionToGroup(Long groupId, Long connectionId) {
        ConnectionGroup group = repository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Группа с ID " + groupId + " не найдена"));
        
        OpenShiftConnection connection = connectionService.getConnectionById(connectionId)
                .orElseThrow(() -> new RuntimeException("Подключение с ID " + connectionId + " не найдено"));
        
        connection.setGroup(group);
        connectionService.updateConnection(connectionId, connection);
        
        log.info("Подключение {} добавлено в группу {}", connection.getName(), group.getName());
    }

    /**
     * Удалить подключение из группы
     */
    @Transactional
    public void removeConnectionFromGroup(Long connectionId) {
        OpenShiftConnection connection = connectionService.getConnectionById(connectionId)
                .orElseThrow(() -> new RuntimeException("Подключение с ID " + connectionId + " не найдено"));
        
        if (connection.getGroup() != null) {
            String groupName = connection.getGroup().getName();
            connection.setGroup(null);
            connectionService.updateConnection(connectionId, connection);
            log.info("Подключение {} удалено из группы {}", connection.getName(), groupName);
        }
    }

    /**
     * Получить все подключения в группе
     */
    public List<OpenShiftConnection> getGroupConnections(Long groupId) {
        ConnectionGroup group = repository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Группа с ID " + groupId + " не найдена"));
        
        return group.getConnections();
    }
}

