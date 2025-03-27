package org.example.Repository;

import org.example.Entity.Plant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlantRepository extends JpaRepository<Plant, Long> {

    // Custom query to find a Plant by its S3 URL
    @Query("SELECT p FROM Plant p WHERE p.s3url LIKE %:imageKey")
    Optional<Plant> findByS3urlEndingWith(@Param("imageKey") String imageKey);
}
