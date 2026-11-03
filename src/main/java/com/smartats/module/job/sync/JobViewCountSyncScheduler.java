package com.smartats.module.job.sync;

import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.module.job.entity.Job;
import com.smartats.module.job.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled job for syncing job view counts
 * <p>
 * Batch-syncs the view-count counters in Redis to the MySQL database
 * <p>
 * Optimization strategy:
 * - Use Redis atomic counters (INCR) to accumulate view counts in real time
 * - Batch-sync to MySQL on a schedule (reduces database write pressure)
 * - Eventual consistency (minute-level delay is acceptable)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobViewCountSyncScheduler {

    private final StringRedisTemplate redisTemplate;
    private final JobMapper jobMapper;

    /**
     * Periodically sync the Redis view counters to the database
     * <p>
     * Execution frequency: once every 10 minutes
     * <p>
     * Execution logic:
     * 1. Scan all counter:job:view:* keys
     * 2. Atomically read and delete each counter (GETDEL avoids losing increments)
     * 3. Batch-update the corresponding jobs table records
     */
    @Scheduled(fixedRate = 600000)  // Runs once every 10 minutes
    public void syncViewCountToDatabase() {
        long startTime = System.currentTimeMillis();
        log.info("Start syncing job view counts: Redis → MySQL");

        try {
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 1: Scan all job view counters
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            String scanPattern = RedisKeyConstants.COUNTER_JOB_VIEW_PREFIX + "*";

            // Use the SCAN command to avoid blocking (cursor-based traversal)
            List<String> counterKeys = redisTemplate.execute((RedisCallback<List<String>>) connection -> {
                List<String> keys = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions()
                    .match(scanPattern)
                    .count(100)
                    .build();

                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                } catch (Exception e) {
                    log.error("SCAN failed", e);
                }
                return keys;
            });

            if (counterKeys == null || counterKeys.isEmpty()) {
                log.debug("No view counters need to be synced");
                return;
            }

            log.info("Found {} job view counters", counterKeys.size());

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 2: Batch-read the Redis counter values
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            List<JobUpdateBatch> updateBatches = new ArrayList<>();

            for (String counterKey : counterKeys) {
                // Extract jobId from the key (format: counter:job:view:{jobId})
                String jobIdStr = counterKey.substring(RedisKeyConstants.COUNTER_JOB_VIEW_PREFIX.length());

                try {
                    Long jobId = Long.parseLong(jobIdStr);
                    // Use GETDEL to atomically read and delete, avoiding increments lost between GET+DELETE
                    String countStr = redisTemplate.opsForValue().getAndDelete(counterKey);

                    if (countStr != null) {
                        int increment = Integer.parseInt(countStr);
                        updateBatches.add(new JobUpdateBatch(jobId, increment, counterKey));
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid jobId: key={}", counterKey);
                }
            }

            if (updateBatches.isEmpty()) {
                log.debug("No valid view-count data to sync");
                return;
            }

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Step 3: Batch-update the database
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            int successCount = 0;
            int failCount = 0;

            for (JobUpdateBatch batch : updateBatches) {
                try {
                    Job job = jobMapper.selectById(batch.jobId());
                    if (job != null) {
                        int currentCount = job.getViewCount() != null ? job.getViewCount() : 0;
                        job.setViewCount(currentCount + batch.increment());
                        jobMapper.updateById(job);
                        successCount++;
                    } else {
                        log.warn("Position not found, skipping sync: jobId={}", batch.jobId());
                    }
                } catch (Exception e) {
                    log.error("Sync failed: jobId={}, increment={}", batch.jobId(), batch.increment(), e);
                    failCount++;
                }
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("Job view count sync complete: {} succeeded, {} failed, took {} ms",
                successCount, failCount, costTime);

        } catch (Exception e) {
            log.error("Job view count sync error", e);
        }
    }

    /**
     * Batch update data structure
     *
     * @param jobId      job ID
     * @param increment  view-count increment
     * @param counterKey Redis counter Key
     */
    private record JobUpdateBatch(Long jobId, int increment, String counterKey) {}
}
