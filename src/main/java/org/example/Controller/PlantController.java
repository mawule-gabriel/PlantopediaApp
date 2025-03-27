package org.example.Controller;

import lombok.RequiredArgsConstructor;
import org.example.Service.PlantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class PlantController {

    private final PlantService plantService;

    @GetMapping
    public String showPlantGallery(Model model,
                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                   @RequestParam(value = "size", defaultValue = "3") int size) {
        // Force size to 3 to limit to 3 images per page
        size = 3;
        Map<String, Object> result = plantService.getPlants(page, size);

        model.addAttribute("images", result.get("plants"));
        model.addAttribute("totalPages", result.get("totalPages"));
        model.addAttribute("currentPage", result.get("currentPage"));
        model.addAttribute("hasNextPage", result.get("hasNextPage"));
        model.addAttribute("pageSize", size);

        return "gallery";
    }

    @GetMapping("/upload")
    public String showUploadPage() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("images") MultipartFile[] files,
                             @RequestParam("description") String description,
                             RedirectAttributes redirectAttributes) {
        try {
            if (files == null || files.length == 0) {
                redirectAttributes.addFlashAttribute("message", "Please select at least one file to upload");
                return "redirect:/upload";
            }

            boolean hasErrors = false;
            int successCount = 0;
            StringBuilder errorMessage = new StringBuilder();

            for (MultipartFile file : files) {
                String response = plantService.uploadImage(file, description);
                if (response.equals("success")) {
                    successCount++;
                } else if (response.equals("empty")) {
                    hasErrors = true;
                    errorMessage.append("One or more files were empty. ");
                }
            }

            if (successCount > 0) {
                redirectAttributes.addFlashAttribute("message", "Successfully uploaded " + successCount + " file(s).");
            }
            if (hasErrors) {
                redirectAttributes.addFlashAttribute("message", errorMessage.toString());
                return "redirect:/upload";
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "Failed to upload files: " + e.getMessage());
            return "redirect:/upload";
        }
        return "redirect:/";
    }

    @PostMapping("/delete")
    public String deleteImage(@RequestParam("imageKey") String imageKey, RedirectAttributes redirectAttributes) {
        String sanitizedKey = imageKey.split("\\?")[0];
        String decodedKey = URLDecoder.decode(sanitizedKey, StandardCharsets.UTF_8);

        boolean deleted = plantService.deleteImage(decodedKey);

        if (deleted) {
            redirectAttributes.addFlashAttribute("message", "Image deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete image.");
        }
        return "redirect:/";
    }
}