package com.smartats.module.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User entity
 *
 * @TableName: the mapped database table, users
 * @Data: Lombok generates getter/setter/toString/equals
 */
@Data
@TableName("users")
public class User {

    /**
     * Primary key ID (auto-increment)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Username (unique)
     */
    private String username;

    /**
     * Password (stored BCrypt-encrypted)
     */
    private String password;

    /**
     * Email (unique)
     */
    private String email;

    /**
     * Role: ADMIN/HR/INTERVIEWER
     */
    private String role;

    /**
     * Daily AI call quota
     */
    private Integer dailyAiQuota;

    /**
     * Account status: 0-disabled, 1-enabled
     */
    private Integer status;

    /**
     * Created at
     */
    private LocalDateTime createdAt;

    /**
     * Updated at
     */
    private LocalDateTime updatedAt;
}