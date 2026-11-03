package com.smartats.infrastructure.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * File storage service interface
 * Design principle: program against an interface so the implementation can later switch to Aliyun OSS
 */
public interface FileStorageService {

    /**
     * Upload a file
     *
     * @param file       the file
     * @param objectName the object name (file path)
     * @return the file access path
     */
    String uploadFile(MultipartFile file, String objectName);

    /**
     * Upload a file stream
     *
     * @param inputStream the file stream
     * @param objectName  the object name
     * @param size        the file size
     * @param contentType the file type
     * @return the file access path
     */
    String uploadFile(InputStream inputStream, String objectName, long size, String contentType);

    /**
     * Delete a file
     *
     * @param objectName the object name
     */
    void deleteFile(String objectName);

    /**
     * Get the file access URL
     *
     * @param objectName the object name
     * @return the access URL
     */
    String getFileUrl(String objectName);

    /**
     * Generate a presigned URL (a temporary access link)
     *
     * @param objectName the object name
     * @param expires    the expiry time (seconds)
     * @return the presigned URL
     */
    String getPresignedUrl(String objectName, int expires);

    /**
     * Check whether the file exists
     *
     * @param objectName the object name
     * @return whether it exists
     */
    boolean fileExists(String objectName);

    /**
     * Ensure the bucket exists
     */
    void ensureBucketExists();
}