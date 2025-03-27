package org.example.Configurations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.sql.DataSource;

@Configuration
public class Config {
    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${rds.endpoint}")
    private String rdsEndpoint;

    @Value("${rds.port:5432}")
    private String rdsPort;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        try {
            SecretsManagerClient client = SecretsManagerClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId("PlantopediaDBCredentials8")  // Updated to match the new secret name
                    .build();

            GetSecretValueResponse getSecretValueResult = client.getSecretValue(getSecretValueRequest);
            String secretValue = getSecretValueResult.secretString();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode secretJson = mapper.readTree(secretValue);

            String username = secretJson.get("username").asText();
            String password = secretJson.get("password").asText();
            String dbname = secretJson.get("dbname").asText();

            String jdbcUrl = "jdbc:postgresql://" + rdsEndpoint + ":" + rdsPort + "/" + dbname;

            return DataSourceBuilder.create()
                    .url(jdbcUrl)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve database credentials from Secrets Manager", e);
        }
    }
}