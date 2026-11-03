package com.smartats;

import com.smartats.infrastructure.storage.FileStorageService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MinIO integration test
 * <p>
 * Requires a MinIO container running in a Docker environment.
 * In CI the test is skipped automatically when the environment variable CI=true is set.
 * It can also be excluded via Maven: -Dtest='!MinioFileStorageServiceTest'
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@DisabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "The MinIO integration test requires a Docker environment")
class MinioFileStorageServiceTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void testEnsureBucketExists() {
        // should not throw an exception
        assertDoesNotThrow(() -> fileStorageService.ensureBucketExists());
    }

    @Test
    void testUploadAndDeleteFile() throws Exception {
        // prepare the test file
        String content = "Hello, MinIO!";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                content.getBytes(StandardCharsets.UTF_8)
        );

        String objectName = "test/" + System.currentTimeMillis() + "/test.txt";

        // test the upload
        String url = fileStorageService.uploadFile(file, objectName);
        assertNotNull(url);
        assertTrue(url.contains(objectName));

        // test that the file exists
        assertTrue(fileStorageService.fileExists(objectName));

        // test retrieving the URL
        String fileUrl = fileStorageService.getFileUrl(objectName);
        assertNotNull(fileUrl);

        // test the deletion
        assertDoesNotThrow(() -> fileStorageService.deleteFile(objectName));

        // test that the file no longer exists
        assertFalse(fileStorageService.fileExists(objectName));
    }

    @Test
    void testGetPresignedUrl() {
        String objectName = "test/presigned.txt";

        String url = fileStorageService.getPresignedUrl(objectName, 3600);
        assertNotNull(url);
        assertTrue(url.contains("X-Amz-Expires"));
    }
}