package com.openshift.controller.controller;

import com.openshift.controller.dto.DeploymentInfo;
import com.openshift.controller.service.ConnectionService;
import com.openshift.controller.service.DeploymentService;
import com.openshift.controller.service.NamespaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Web контроллер для управления Deployments
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DeploymentWebController {

    private final DeploymentService deploymentService;
    private final NamespaceService namespaceService;
    private final ConnectionService connectionService;

    /**
     * Главная страница - список всех Namespaces или форма настройки подключения
     */
    @GetMapping("/")
    public String index(Model model) {
        // Проверяем, настроено ли подключение
        if (!connectionService.hasActiveConnection()) {
            // Если подключение не настроено, перенаправляем на страницу настройки
            return "redirect:/connection/setup";
        }
        
        try {
            List<String> namespaces = namespaceService.getAllNamespaces();
            model.addAttribute("namespaces", namespaces);
        } catch (Exception e) {
            log.error("Ошибка при получении списка Namespaces", e);
            model.addAttribute("error", "Ошибка при получении списка проектов: " + e.getMessage());
            model.addAttribute("namespaces", List.of());
        }
        return "namespaces";
    }

    /**
     * Страница управления Deployments в namespace
     */
    @GetMapping("/deployments/{namespace}")
    public String deployments(@PathVariable String namespace, Model model) {
        try {
            List<DeploymentInfo> deployments = deploymentService.getAllDeployments(namespace);
            
            // Сохраняем состояние при первом открытии (если еще не сохранено)
            if (!deployments.isEmpty() && !deploymentService.hasState(namespace)) {
                deploymentService.saveCurrentState(namespace);
            }
            
            model.addAttribute("deployments", deployments);
            model.addAttribute("namespace", namespace);
        } catch (RuntimeException e) {
            log.error("Ошибка при получении списка Deployments в namespace '{}'", namespace, e);
            // Проверяем, содержит ли сообщение об ошибке информацию о доступе
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("Нет доступа")) {
                model.addAttribute("error", errorMessage);
            } else {
                model.addAttribute("error", "Ошибка при получении списка Deployments: " + errorMessage);
            }
            model.addAttribute("deployments", List.of());
            model.addAttribute("namespace", namespace);
        } catch (Exception e) {
            log.error("Неожиданная ошибка при получении списка Deployments", e);
            model.addAttribute("error", "Неожиданная ошибка: " + e.getMessage());
            model.addAttribute("deployments", List.of());
            model.addAttribute("namespace", namespace);
        }
        return "deployments";
    }

    /**
     * Масштабирование Deployment
     */
    @PostMapping("/deployments/{namespace}/{name}/scale")
    public String scaleDeployment(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestParam int replicas,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.scaleDeployment(namespace, name, replicas);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Deployment " + name + " масштабирован до " + replicas + " реплик");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось масштабировать Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при масштабировании Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при масштабировании: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Перезапустить все Deployments
     */
    @PostMapping("/deployments/{namespace}/restart-all")
    public String restartAll(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.restartAllDeployments(namespace);
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Перезапущено " + successCount + " из " + results.size() + " Deployments");
        } catch (Exception e) {
            log.error("Ошибка при перезапуске всех Deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Остановить все Deployments (установить в 0)
     */
    @PostMapping("/deployments/{namespace}/shutdown-all")
    public String shutdownAll(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.shutdownAllDeployments(namespace);
            long successCount = results.values().stream().filter(b -> b).count();
            redirectAttributes.addFlashAttribute("success", 
                "Остановлено " + successCount + " из " + results.size() + " Deployments");
        } catch (Exception e) {
            log.error("Ошибка при остановке всех Deployments", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при остановке: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Восстановить изначальное состояние
     */
    @PostMapping("/deployments/{namespace}/restore")
    public String restoreAll(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            Map<String, Boolean> results = deploymentService.restoreAllDeployments(namespace);
            if (results.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "Нет сохраненного состояния для восстановления");
            } else {
                long successCount = results.values().stream().filter(b -> b).count();
                redirectAttributes.addFlashAttribute("success", 
                    "Восстановлено " + successCount + " из " + results.size() + " Deployments");
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении состояния", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Перезапустить все поды для конкретного Deployment
     */
    @PostMapping("/deployments/{namespace}/{name}/pods/restart")
    public String restartPodsForDeployment(
            @PathVariable String namespace,
            @PathVariable String name,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.restartAllPodsForDeployment(namespace, name);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Поды для Deployment " + name + " перезапущены");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось перезапустить поды для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при перезапуске подов Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске подов: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Удалить все поды для конкретного Deployment
     */
    @PostMapping("/deployments/{namespace}/{name}/pods/delete")
    public String deletePodsForDeployment(
            @PathVariable String namespace,
            @PathVariable String name,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.deleteAllPodsForDeployment(namespace, name);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Все поды для Deployment " + name + " удалены");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось удалить поды для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при удалении подов Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при удалении подов: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Восстановить поды для конкретного Deployment
     */
    @PostMapping("/deployments/{namespace}/{name}/pods/restore")
    public String restorePodsForDeployment(
            @PathVariable String namespace,
            @PathVariable String name,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = deploymentService.restorePodsForDeployment(namespace, name);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Поды для Deployment " + name + " восстановлены");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось восстановить поды для Deployment " + name);
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении подов Deployment", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при восстановлении подов: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }

    /**
     * Обновить стартовые значения для всех Deployments в namespace
     */
    @PostMapping("/deployments/{namespace}/update-original-state")
    public String updateOriginalState(
            @PathVariable String namespace,
            RedirectAttributes redirectAttributes) {
        try {
            deploymentService.saveCurrentState(namespace);
            redirectAttributes.addFlashAttribute("success", 
                "Стартовые значения успешно обновлены для всех Deployments в namespace " + namespace);
        } catch (Exception e) {
            log.error("Ошибка при обновлении стартовых значений", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при обновлении стартовых значений: " + e.getMessage());
        }
        return "redirect:/deployments/" + namespace;
    }
}

