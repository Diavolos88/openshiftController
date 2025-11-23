package com.openshift.controller.service;

import com.openshift.controller.entity.OpenShiftConnection;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис для работы с Namespaces
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NamespaceService {

    private final OpenShiftClientService openShiftClientService;
    private final ConnectionService connectionService;
    private final MockDataService mockDataService;
    
    /**
     * Получить OpenShift клиент для конкретного подключения
     */
    private OpenShiftClient getClient(Long connectionId) {
        return openShiftClientService.getClientForConnection(connectionId)
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
    }

    /**
     * Получить OpenShift клиент (для обратной совместимости - использует активное подключение)
     */
    private OpenShiftClient getClient() {
        return openShiftClientService.getClient()
                .orElseThrow(() -> new RuntimeException("Подключение к OpenShift не настроено. Пожалуйста, настройте подключение на главной странице."));
    }
    
    /**
     * Получить namespace из подключения
     */
    private String getNamespace(Long connectionId) {
        return connectionService.getConnectionById(connectionId)
                .map(conn -> conn.getNamespace() != null ? conn.getNamespace() : "default")
                .orElse("default");
    }

    /**
     * Получить список всех Namespaces для конкретного подключения
     * 
     * Если у пользователя нет прав на получение списка всех namespace на уровне кластера (403 Forbidden),
     * возвращает список с одним доступным namespace из конфигурации.
     */
    public List<String> getAllNamespaces(Long connectionId) {
        log.info("Получение списка всех Namespaces для подключения ID: {}", connectionId);
        
        // Проверяем mock-подключение
        Optional<OpenShiftConnection> connectionOpt = connectionService.getConnectionById(connectionId);
        if (connectionOpt.isPresent() && connectionOpt.get().getIsMock() != null && connectionOpt.get().getIsMock()) {
            log.info("Использование mock-данных для namespaces");
            return mockDataService.getMockNamespaces();
        }
        
        OpenShiftClient openShiftClient = getClient(connectionId);
        String defaultNamespace = getNamespace(connectionId);
        
        try {
            NamespaceList namespaceList = openShiftClient.namespaces().list();
            
            List<String> namespaces = namespaceList.getItems().stream()
                    .map(ns -> ns.getMetadata().getName())
                    .filter(name -> !name.startsWith("kube-") && !name.startsWith("openshift-"))
                    .sorted()
                    .collect(Collectors.toList());
            
            log.info("Успешно получено {} namespace(ов)", namespaces.size());
            return namespaces;
            
        } catch (KubernetesClientException e) {
            // Обработка ошибки 403 Forbidden - у пользователя нет прав на список всех namespace
            if (e.getCode() == 403) {
                log.warn("Нет прав на получение списка всех namespace на уровне кластера (403 Forbidden). " +
                        "Используется namespace из конфигурации: {}", defaultNamespace);
                
                // Проверяем доступность namespace из конфигурации
                try {
                    Namespace namespace = openShiftClient.namespaces().withName(defaultNamespace).get();
                    if (namespace != null) {
                        log.info("Namespace '{}' доступен", defaultNamespace);
                        return Collections.singletonList(defaultNamespace);
                    } else {
                        log.error("Namespace '{}' не найден", defaultNamespace);
                        return Collections.emptyList();
                    }
                } catch (Exception ex) {
                    log.error("Ошибка при проверке доступности namespace '{}': {}", defaultNamespace, ex.getMessage());
                    // Все равно возвращаем namespace из конфигурации, так как он может быть доступен для других операций
                    return Collections.singletonList(defaultNamespace);
                }
            } else {
                // Для других ошибок пробрасываем исключение дальше
                log.error("Ошибка при получении списка namespace: {}", e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Получить список всех Namespaces (для обратной совместимости - использует активное подключение)
     */
    public List<String> getAllNamespaces() {
        return openShiftClientService.getActiveConnection()
                .map(conn -> getAllNamespaces(conn.getId()))
                .orElseThrow(() -> new RuntimeException("Активное подключение не найдено"));
    }
}

