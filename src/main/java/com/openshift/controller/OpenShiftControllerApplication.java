package com.openshift.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

/**
 * Главный класс Spring Boot приложения для управления подами в OpenShift
 * 
 * Сервис предоставляет REST API для:
 * - Перезапуска подов
 * - Удаления подов
 * - Получения информации о подах
 * - Масштабирования deployment'ов
 */
@SpringBootApplication
public class OpenShiftControllerApplication {

    public static void main(String[] args) {
        // Устанавливаем UTF-8 кодировку для корректного отображения русских символов
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("console.encoding", "UTF-8");
        System.setProperty("user.language", "ru");
        System.setProperty("user.country", "RU");
        
        // Устанавливаем локаль
        Locale.setDefault(new Locale("ru", "RU"));
        
        SpringApplication.run(OpenShiftControllerApplication.class, args);
    }
}

