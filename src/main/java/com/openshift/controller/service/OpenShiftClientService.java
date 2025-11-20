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

import java.util.Optional;

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

    /**
     * Получить OpenShift клиент на основе активного подключения из БД
     */
    public Optional<OpenShiftClient> getClient() {
        Optional<OpenShiftConnection> connection = connectionService.getActiveConnection();
        
        if (connection.isEmpty()) {
            log.warn("Активное подключение к OpenShift не найдено в БД");
            return Optional.empty();
        }

        OpenShiftConnection conn = connection.get();
        
        // Если клиент уже создан для этого подключения, возвращаем его
        if (cachedClient != null && cachedConnection != null && 
            cachedConnection.getId().equals(conn.getId())) {
            return Optional.of(cachedClient);
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
                log.info("✅ Успешно подключено к OpenShift кластеру: {}", conn.getMasterUrl());
            } catch (Exception e) {
                log.warn("Предупреждение: не удалось получить версию кластера, но подключение установлено", e);
            }
            
            cachedClient = openShiftClient;
            cachedConnection = conn;
            
            return Optional.of(openShiftClient);
        } catch (Exception e) {
            log.error("Ошибка при создании OpenShift клиента", e);
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
        log.info("Кэш OpenShift клиента сброшен");
    }

    /**
     * Получить активное подключение
     */
    public Optional<OpenShiftConnection> getActiveConnection() {
        return connectionService.getActiveConnection();
    }
}

