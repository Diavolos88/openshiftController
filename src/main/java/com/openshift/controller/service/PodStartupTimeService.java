package com.openshift.controller.service;

import com.openshift.controller.entity.OpenShiftConnection;
import com.openshift.controller.entity.PodStartupTime;
import com.openshift.controller.repository.PodStartupTimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Сервис для работы с временем старта подов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodStartupTimeService {

    private final PodStartupTimeRepository repository;

    /**
     * Сохранить или обновить время старта пода для deployment
     */
    @Transactional
    public void saveStartupTime(Long connectionId, String namespace, String deploymentName, Long startupTimeSeconds) {
        log.info("Сохранение времени старта для deployment {}/{}/{}: {} секунд", 
                connectionId, namespace, deploymentName, startupTimeSeconds);
        
        Optional<PodStartupTime> existing = repository.findByConnectionIdAndNamespaceAndDeploymentName(
                connectionId, namespace, deploymentName);
        
        if (existing.isPresent()) {
            PodStartupTime startupTime = existing.get();
            startupTime.setStartupTimeSeconds(startupTimeSeconds);
            repository.save(startupTime);
            log.info("Обновлено время старта для deployment {}/{}/{}", connectionId, namespace, deploymentName);
        } else {
            PodStartupTime startupTime = PodStartupTime.builder()
                    .connection(OpenShiftConnection.builder().id(connectionId).build())
                    .namespace(namespace)
                    .deploymentName(deploymentName)
                    .startupTimeSeconds(startupTimeSeconds)
                    .build();
            repository.save(startupTime);
            log.info("Сохранено время старта для deployment {}/{}/{}", connectionId, namespace, deploymentName);
        }
    }

    /**
     * Получить время старта для deployment
     */
    public Optional<Long> getStartupTime(Long connectionId, String namespace, String deploymentName) {
        return repository.findByConnectionIdAndNamespaceAndDeploymentName(connectionId, namespace, deploymentName)
                .map(PodStartupTime::getStartupTimeSeconds);
    }
}
