package org.example.Service;

import lombok.RequiredArgsConstructor;
import org.example.Entity.Plant;
import org.example.Repository.PlantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlantService {

    private final PlantRepository repository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    private final String BUCKET_NAME = "memorybucket10";
    private final String REGION = "us-east-1";

    // Generate presigned URL for secure image access
    private String generatePresignedUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // URL valid for 10 minutes
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .build())
                .build();
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toString();
    }

    // List plant images with presigned URLs
    public Map<String, Object> getPlants(int page, int size) {
        Map<String, Object> result = new HashMap<>();

        Pageable pageable = PageRequest.of(page, size, Sort.by("uploadedAt").descending());
        Page<Plant> plantPage = repository.findAll(pageable);

        List<Map<String, String>> imageData = plantPage.getContent().stream()
                .map(plant -> {
                    Map<String, String> imageMap = new HashMap<>();
                    String key = plant.getS3url().split(".com/")[1]; // Extract key from URL
                    imageMap.put("key", generatePresignedUrl(key)); // Use presigned URL
                    imageMap.put("value", plant.getDescription() != null ? plant.getDescription() : "No description available");
                    return imageMap;
                })
                .collect(Collectors.toList());

        result.put("plants", imageData);
        result.put("totalPages", plantPage.getTotalPages());
        result.put("currentPage", plantPage.getNumber());
        result.put("hasNextPage", plantPage.hasNext());

        return result;
    }

    // Upload plant images with no size limit
    public String uploadImage(MultipartFile file, String description) throws IOException {
        if (file.isEmpty()) {
            return "empty";
        }

        // Removed size check: if (file.getSize() > 100000) { return "size"; }

        String key = "plant_" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        String s3Url = "https://" + BUCKET_NAME + ".s3.amazonaws.com/" + key;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

        Plant plant = Plant.builder()
                .name(file.getOriginalFilename())
                .description(description)
                .s3url(s3Url)
                .build();

        repository.save(plant);
        return "success";
    }

    // Delete a plant image
    public boolean deleteImage(String imageKey) {
        try {
            String key = imageKey.contains("?") ? imageKey.split("\\?")[0] : imageKey;
            if (key.contains(".com/")) {
                key = key.split(".com/")[1];
            }

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .build();

            Plant plant = repository.findByS3urlEndingWith(key)
                    .orElseThrow(() -> new RuntimeException("Image not found"));
            repository.delete(plant);
            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (S3Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}