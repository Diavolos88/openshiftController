package com.openshift.controller.controller;

import com.openshift.controller.dto.PodInfo;
import com.openshift.controller.service.PodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Web контроллер для веб-интерфейса
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebController {

    private final PodService podService;

    @Value("${openshift.namespace:default}")
    private String defaultNamespace;

    /**
     * Главная страница - список всех подов
     */
    @GetMapping("/pods")
    public String index(@RequestParam(required = false) String namespace, Model model) {
        String ns = namespace != null ? namespace : defaultNamespace;
        
        try {
            List<PodInfo> pods = podService.getAllPods(ns);
            model.addAttribute("pods", pods);
            model.addAttribute("namespace", ns);
            model.addAttribute("defaultNamespace", defaultNamespace);
            model.addAttribute("podCount", pods.size());
        } catch (Exception e) {
            log.error("Ошибка при получении списка подов", e);
            model.addAttribute("error", "Ошибка при получении списка подов: " + e.getMessage());
            model.addAttribute("pods", List.of());
            model.addAttribute("namespace", ns);
        }
        
        return "index";
    }

    /**
     * Страница с деталями пода
     */
    @GetMapping("/pod/{namespace}/{podName}")
    public String podDetails(
            @PathVariable String namespace,
            @PathVariable String podName,
            Model model) {
        try {
            PodInfo pod = podService.getPod(namespace, podName);
            if (pod == null) {
                model.addAttribute("error", "Под не найден");
                return "error";
            }
            model.addAttribute("pod", pod);
            return "pod-details";
        } catch (Exception e) {
            log.error("Ошибка при получении информации о поде", e);
            model.addAttribute("error", "Ошибка: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Перезапуск пода
     */
    @PostMapping("/pod/{namespace}/{podName}/restart")
    public String restartPod(
            @PathVariable String namespace,
            @PathVariable String podName,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = podService.restartPod(namespace, podName);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Под " + podName + " успешно перезапущен");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось перезапустить под " + podName);
            }
        } catch (Exception e) {
            log.error("Ошибка при перезапуске пода", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        return "redirect:/pods?namespace=" + namespace;
    }

    /**
     * Удаление пода
     */
    @PostMapping("/pod/{namespace}/{podName}/delete")
    public String deletePod(
            @PathVariable String namespace,
            @PathVariable String podName,
            RedirectAttributes redirectAttributes) {
        try {
            boolean success = podService.deletePod(namespace, podName);
            if (success) {
                redirectAttributes.addFlashAttribute("success", 
                    "Под " + podName + " успешно удален");
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Не удалось удалить под " + podName);
            }
        } catch (Exception e) {
            log.error("Ошибка при удалении пода", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при удалении: " + e.getMessage());
        }
        return "redirect:/pods?namespace=" + namespace;
    }

    /**
     * Поиск подов по label selector
     */
    @GetMapping("/search")
    public String searchPods(
            @RequestParam String namespace,
            @RequestParam String labelSelector,
            Model model) {
        try {
            List<PodInfo> pods = podService.getPodsByLabel(namespace, labelSelector);
            model.addAttribute("pods", pods);
            model.addAttribute("namespace", namespace);
            model.addAttribute("labelSelector", labelSelector);
            model.addAttribute("podCount", pods.size());
            return "index";
        } catch (Exception e) {
            log.error("Ошибка при поиске подов", e);
            model.addAttribute("error", "Ошибка при поиске: " + e.getMessage());
            model.addAttribute("pods", List.of());
            return "index";
        }
    }

    /**
     * Массовое удаление выбранных подов
     */
    @PostMapping("/pods/batch/delete")
    public String deleteSelectedPods(
            @RequestParam String namespace,
            @RequestParam(required = false) List<String> podNames,
            RedirectAttributes redirectAttributes) {
        try {
            if (podNames == null || podNames.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны поды для удаления");
                return "redirect:/pods?namespace=" + namespace;
            }
            
            java.util.Map<String, Boolean> results = podService.deletePods(namespace, podNames);
            long successCount = results.values().stream().filter(b -> b).count();
            
            if (successCount == podNames.size()) {
                redirectAttributes.addFlashAttribute("success", 
                    "Успешно удалено " + successCount + " из " + podNames.size() + " подов");
            } else {
                redirectAttributes.addFlashAttribute("warning", 
                    "Удалено " + successCount + " из " + podNames.size() + " подов");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом удалении подов", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при удалении: " + e.getMessage());
        }
        return "redirect:/pods?namespace=" + namespace;
    }

    /**
     * Массовый перезапуск выбранных подов
     */
    @PostMapping("/pods/batch/restart")
    public String restartSelectedPods(
            @RequestParam String namespace,
            @RequestParam(required = false) List<String> podNames,
            RedirectAttributes redirectAttributes) {
        try {
            if (podNames == null || podNames.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Не выбраны поды для перезапуска");
                return "redirect:/pods?namespace=" + namespace;
            }
            
            java.util.Map<String, Boolean> results = podService.restartPods(namespace, podNames);
            long successCount = results.values().stream().filter(b -> b).count();
            
            if (successCount == podNames.size()) {
                redirectAttributes.addFlashAttribute("success", 
                    "Успешно перезапущено " + successCount + " из " + podNames.size() + " подов");
            } else {
                redirectAttributes.addFlashAttribute("warning", 
                    "Перезапущено " + successCount + " из " + podNames.size() + " подов");
            }
        } catch (Exception e) {
            log.error("Ошибка при массовом перезапуске подов", e);
            redirectAttributes.addFlashAttribute("error", 
                "Ошибка при перезапуске: " + e.getMessage());
        }
        return "redirect:/pods?namespace=" + namespace;
    }
}

