package com.smartats.module.analytics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Analytics Mapper
 * <p>
 * Focused on aggregate queries; does not extend BaseMapper to avoid exposing unnecessary CRUD methods.
 * Uses {@code <script>} dynamic SQL for optional condition filtering.
 */
@Mapper
public interface AnalyticsMapper {

    /**
     * Count applications grouped by status
     *
     * @param jobId     job ID (optional)
     * @param startDate start time (optional)
     * @param endDate   end time (optional)
     * @return List of {status: String, cnt: Long}
     */
    @Select({"<script>",
            "SELECT status, COUNT(*) AS cnt FROM job_applications",
            "<where>",
            "  <if test='jobId != null'>AND job_id = #{jobId}</if>",
            "  <if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "  <if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</where>",
            "GROUP BY status",
            "</script>"})
    List<Map<String, Object>> countGroupByStatus(@Param("jobId") Long jobId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Count application trend by month
     *
     * @return List of {month: "yyyy-MM", cnt: Long}
     */
    @Select({"<script>",
            "SELECT DATE_FORMAT(applied_at, '%Y-%m') AS month, COUNT(*) AS cnt",
            "FROM job_applications",
            "<where>",
            "  <if test='jobId != null'>AND job_id = #{jobId}</if>",
            "  <if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "  <if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</where>",
            "GROUP BY month ORDER BY month",
            "</script>"})
    List<Map<String, Object>> countByMonth(@Param("jobId") Long jobId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Compute the average AI match score
     */
    @Select({"<script>",
            "SELECT AVG(match_score) FROM job_applications",
            "WHERE match_score IS NOT NULL",
            "<if test='jobId != null'>AND job_id = #{jobId}</if>",
            "<if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "<if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</script>"})
    BigDecimal avgMatchScore(@Param("jobId") Long jobId,
                             @Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate);

    /**
     * Compute the average time-to-hire (days): average days from application to offer
     */
    @Select({"<script>",
            "SELECT AVG(DATEDIFF(updated_at, applied_at)) FROM job_applications",
            "WHERE status = 'OFFER'",
            "<if test='jobId != null'>AND job_id = #{jobId}</if>",
            "<if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "<if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</script>"})
    Double avgDaysToOffer(@Param("jobId") Long jobId,
                          @Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate);

    /**
     * Count the total number of scheduled interviews
     */
    @Select({"<script>",
            "SELECT COUNT(*) FROM interview_records",
            "WHERE status IN ('SCHEDULED', 'COMPLETED')",
            "<if test='startDate != null'>AND scheduled_at &gt;= #{startDate}</if>",
            "<if test='endDate != null'>AND scheduled_at &lt;= #{endDate}</if>",
            "</script>"})
    long countInterviews(@Param("startDate") LocalDateTime startDate,
                         @Param("endDate") LocalDateTime endDate);
}
