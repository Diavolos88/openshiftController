package com.openshift.controller.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация для подключения к OpenShift/Kubernetes кластеру
 * 
 * Подключение теперь настраивается через веб-интерфейс и сохраняется в БД.
 * Клиент создается динамически через OpenShiftClientService на основе данных из БД.
 */
@Slf4j
@Configuration
public class OpenShiftConfig {
    // Конфигурация перенесена в OpenShiftClientService
    // Клиент создается динамически на основе данных из БД
}

