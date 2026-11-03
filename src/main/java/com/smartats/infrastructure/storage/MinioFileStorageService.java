package com.smartats.infrastructure.storage;

import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String endpoint;

    @Override
    public String uploadFile(MultipartFile file, String objectName) {
        try {
            InputStream inputStream = file.getInputStream();
            long size = file.getSize();
            String contentType = file.getContentType();

            return uploadFile(inputStream, objectName, size, contentType);
        } catch (Exception e) {
            log.error("Failed to upload the file: objectName={}", objectName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "File upload failed");
        }
    }

    @Override
    public String uploadFile(InputStream inputStream, String objectName, long size, String contentType) {
        try {
            // Ensure the bucket exists
            ensureBucketExists();

            // Upload the file
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)  // -1 means part upload is not used
                    .contentType(contentType)
                    .build();

            minioClient.putObject(putObjectArgs);

            log.info("File uploaded successfully: bucket={}, object={}", bucketName, objectName);

            // Return the access path
            return getFileUrl(objectName);

        } catch (Exception e) {
            log.error("Failed to upload the file stream: objectName={}", objectName, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "File upload failed");
        }
    }

    @Override
    public void deleteFile(String objectName) {
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            minioClient.removeObject(removeObjectArgs);

            log.info("File deleted successfully: objectName={}", objectName);

        } catch (Exception e) {
            log.error("Failed to delete the file: objectName={}", objectName, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to delete the file");
        }
    }

    @Override
    public String getFileUrl(String objectName) {
        // Public access URL (the bucket must be set to Public)
        return String.format("%s/%s/%s", endpoint, bucketName, objectName);
    }

    @Override
    public String getPresignedUrl(String objectName, int expires) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectName)
                    .expiry(expires, TimeUnit.SECONDS)
                    .build();

            return minioClient.getPresignedObjectUrl(args);

        } catch (Exception e) {
            log.error("Failed to generate the presigned URL: objectName={}", objectName, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Failed to generate the presigned URL");
        }
    }

    @Override
    public boolean fileExists(String objectName) {
        try {
            StatObjectArgs args = StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            minioClient.statObject(args);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Set the bucket to public read (development environment only)
     * In production, presigned URLs should be used instead
     */
    private void setBucketPublic() {
        try {
            // Build the bucket policy (read only)
            String policy = String.format(
                    "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::%s/*\"]}]}",
                    bucketName
            );

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policy)
                            .build()
            );

            log.info("Bucket public read policy applied successfully: {}", bucketName);

        } catch (Exception e) {
            // If the policy already exists or cannot be applied, only log a warning; the application keeps running
            log.warn("Failed to set the bucket public policy (it may already be set): bucket={}, error={}", bucketName, e.getMessage());
        }
    }

    @Override
    public void ensureBucketExists() {
        try {
            // Check whether the bucket exists
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!found) {
                // Create the bucket
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("Bucket created successfully: {}", bucketName);
            }

            // Set the bucket to public read (development environment)
            // Note: this is attempted on every start and is skipped when it is already set
            setBucketPublic();

        } catch (Exception e) {
            log.error("Failed to check/create the bucket: bucket={}", bucketName, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Bucket initialisation failed");
        }
    }
}