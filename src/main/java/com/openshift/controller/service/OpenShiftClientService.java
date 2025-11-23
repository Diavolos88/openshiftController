package com.openshift.controller.service;

import com.openshift.controller.entity.OpenShiftConnection;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для создания и управления OpenShift клиентом на основе данных из БД
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenShiftClientService {

    private final ConnectionService connectionService;
    private OpenShiftClient cachedClient;
    private OpenShiftConnection cachedConnection;
    // Кэш клиентов для каждого подключения по ID
    private final Map<Long, OpenShiftClient> clientsCache = new ConcurrentHashMap<>();
    private final Map<Long, OpenShiftConnection> connectionsCache = new ConcurrentHashMap<>();

    /**
     * Получить OpenShift клиент на основе активного подключения из БД (для обратной совместимости)
     */
    public Optional<OpenShiftClient> getClient() {
        Optional<OpenShiftConnection> connection = connectionService.getActiveConnection();
        
        if (connection.isEmpty()) {
            log.warn("Активное подключение к OpenShift не найдено в БД");
            return Optional.empty();
        }

        return getClientForConnection(connection.get().getId());
    }

    /**
     * Получить OpenShift клиент для конкретного подключения по ID
     */
    public Optional<OpenShiftClient> getClientForConnection(Long connectionId) {
        Optional<OpenShiftConnection> connectionOpt = connectionService.getConnectionById(connectionId);
        
        if (connectionOpt.isEmpty()) {
            log.warn("Подключение с ID {} не найдено в БД", connectionId);
            return Optional.empty();
        }

        OpenShiftConnection conn = connectionOpt.get();
        
        // Для mock-подключений возвращаем пустой Optional
        if (conn.getIsMock() != null && conn.getIsMock()) {
            return Optional.empty();
        }
        
        // Если клиент уже создан для этого подключения, возвращаем его из кэша
        if (clientsCache.containsKey(connectionId)) {
            OpenShiftClient cached = clientsCache.get(connectionId);
            // Проверяем, что подключение не изменилось
            OpenShiftConnection cachedConn = connectionsCache.get(connectionId);
            if (cachedConn != null && cachedConn.getId().equals(conn.getId())) {
                return Optional.of(cached);
            } else {
                // Подключение изменилось, удаляем старый клиент
                try {
                    cached.close();
                } catch (Exception e) {
                    log.warn("Ошибка при закрытии старого клиента", e);
                }
                clientsCache.remove(connectionId);
                connectionsCache.remove(connectionId);
            }
        }
        
        // Создаем новый клиент
        try {
            log.info("Создание OpenShift клиента для подключения: {} (ID: {})", 
                    conn.getName(), conn.getId());
            log.debug("URL: {}", conn.getMasterUrl());
            
            Config config = new ConfigBuilder()
                    .withMasterUrl(conn.getMasterUrl())
                    .withOauthToken(conn.getToken())
                    .withTrustCerts(true)
                    .build();

            KubernetesClient kubernetesClient = new KubernetesClientBuilder()
                    .withConfig(config)
                    .build();

            OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
            
            // Проверяем подключение
            try {
                openShiftClient.getVersion();
                log.info("✅ Успешно подключено к OpenShift кластеру: {} (ID: {})", conn.getMasterUrl(), connectionId);
            } catch (Exception e) {
                log.warn("Предупреждение: не удалось получить версию кластера, но подключение установлено", e);
            }
            
            // Сохраняем в кэш
            clientsCache.put(connectionId, openShiftClient);
            connectionsCache.put(connectionId, conn);
            
            // Также обновляем старый кэш для обратной совместимости
            cachedClient = openShiftClient;
            cachedConnection = conn;
            
            return Optional.of(openShiftClient);
        } catch (Exception e) {
            log.error("Ошибка при создании OpenShift клиента для подключения ID: {}", connectionId, e);
            return Optional.empty();
        }
    }

    /**
     * Сбросить кэш клиента (например, при изменении конфигурации)
     */
    public void resetCache() {
        if (cachedClient != null) {
            try {
                cachedClient.close();
            } catch (Exception e) {
                log.warn("Ошибка при закрытии клиента", e);
            }
        }
        cachedClient = null;
        cachedConnection = null;
        
        // Закрываем все клиенты из кэша
        for (Map.Entry<Long, OpenShiftClient> entry : clientsCache.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                log.warn("Ошибка при закрытии клиента для подключения ID: {}", entry.getKey(), e);
            }
        }
        clientsCache.clear();
        connectionsCache.clear();
        
        log.info("Кэш OpenShift клиента сброшен");
    }

    /**
     * Сбросить кэш для конкретного подключения
     */
    public void resetCacheForConnection(Long connectionId) {
        OpenShiftClient client = clientsCache.remove(connectionId);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Ошибка при закрытии клиента для подключения ID: {}", connectionId, e);
            }
        }
        connectionsCache.remove(connectionId);
        
        // Если это было активное подключение, сбрасываем и старый кэш
        if (cachedConnection != null && cachedConnection.getId().equals(connectionId)) {
            cachedClient = null;
            cachedConnection = null;
        }
        
        log.info("Кэш OpenShift клиента сброшен для подключения ID: {}", connectionId);
    }

    /**
     * Получить активное подключение
     */
    public Optional<OpenShiftConnection> getActiveConnection() {
        return connectionService.getActiveConnection();
    }
}

