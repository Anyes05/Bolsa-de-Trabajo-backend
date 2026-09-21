package uy.ccisj.api.storage;

import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3StorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${app.storage.bucket:}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    public void putObject(String key, MultipartFile file) {
        ensureBucketConfigured();
        try {
            var putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer el archivo para cargarlo a S3", exception);
        }
    }

    public void deleteObject(String key) {
        ensureBucketConfigured();
        var request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(request);
    }

    public String presignedDownloadUrl(String key, Duration duration) {
        ensureBucketConfigured();
        var getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
    }

    private void ensureBucketConfigured() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("No se configuro app.storage.bucket para el backend");
        }
    }
}
