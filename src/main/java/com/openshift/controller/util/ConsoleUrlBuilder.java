package com.openshift.controller.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Утилитный класс для построения URL консоли OpenShift из API URL
 */
@Slf4j
public class ConsoleUrlBuilder {

    /**
     * Построить URL консоли OpenShift из API URL
     * 
     * @param masterUrl URL API сервера (например, https://api.xxx.openshiftapps.com:6443)
     * @param namespace namespace для прямого перехода в консоли
     * @return URL консоли или null, если не удалось построить
     */
    public static String buildConsoleUrl(String masterUrl, String namespace) {
        if (masterUrl == null || masterUrl.isEmpty()) {
            return null;
        }
        
        try {
            // Убираем порт и протокол
            String urlWithoutPort = masterUrl.replaceFirst(":6443$", "").replaceFirst("^https?://", "");
            
            // Пытаемся построить URL консоли в разных форматах
            String consoleUrl;
            
            // Формат для OpenShift Sandbox и подобных: api.rm1.0a51.p1.openshiftapps.com
            if (urlWithoutPort.startsWith("api.") && urlWithoutPort.contains(".openshiftapps.com")) {
                // Извлекаем часть после api.
                String clusterPart = urlWithoutPort.substring(4); // Убираем "api."
                // Строим: console-openshift-console.apps.{clusterPart}
                consoleUrl = "https://console-openshift-console.apps." + clusterPart;
            } 
            // Формат для других OpenShift кластеров: api.cluster.example.com
            else if (urlWithoutPort.startsWith("api.")) {
                // Извлекаем часть после api.
                String clusterPart = urlWithoutPort.substring(4); // Убираем "api."
                // Пробуем стандартный формат OpenShift 4.x
                consoleUrl = "https://console-openshift-console.apps." + clusterPart;
            } 
            // Если URL уже без api префикса
            else if (urlWithoutPort.contains(".")) {
                consoleUrl = "https://console-openshift-console.apps." + urlWithoutPort;
            } 
            else {
                return null;
            }
            
            // Добавляем путь к namespace, если указан
            if (namespace != null && !namespace.isEmpty()) {
                consoleUrl += "/k8s/cluster/projects/" + namespace;
            }
            
            return consoleUrl;
        } catch (Exception e) {
            log.warn("Не удалось построить URL консоли из masterUrl: {}", masterUrl, e);
            return null;
        }
    }
}

