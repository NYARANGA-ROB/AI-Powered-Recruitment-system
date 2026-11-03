package com.smartats.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * Unit tests for UserService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper userMapper;
    @Mock
    private VerificationCodeService verificationCodeService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("zhangsan");
        testUser.setPassword("$2a$10$encodedPassword");
        testUser.setEmail("zhangsan@test.com");
        testUser.setRole("HR");
        testUser.setDailyAiQuota(100);
        testUser.setStatus(1);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Registration tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("Registration succeeds")
        void shouldRegisterSuccessfully() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setEmail("new@test.com");
            request.setVerificationCode("123456");

            given(verificationCodeService.verifyCode("new@test.com", "123456")).willReturn(true);
            given(userMapper.selectCount(any(LambdaQueryWrapper.class))).willReturn(0L);
            given(passwordEncoder.encode("password123")).willReturn("$2a$10$encoded");
            given(userMapper.insert(any(User.class))).willReturn(1);

            userService.register(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userMapper).should().insert(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("newuser");
            assertThat(saved.getPassword()).isEqualTo("$2a$10$encoded");
            assertThat(saved.getRole()).isEqualTo("HR");
        }

        @Test
        @DisplayName("An invalid verification code throws an exception")
        void shouldThrowWhenCodeInvalid() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setEmail("new@test.com");
            request.setVerificationCode("000000");

            given(verificationCodeService.verifyCode("new@test.com", "000000")).willReturn(false);

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("An existing username throws an exception")
        void shouldThrowWhenUsernameExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("existuser");
            request.setPassword("password123");
            request.setEmail("new@test.com");
            request.setVerificationCode("123456");

            given(verificationCodeService.verifyCode("new@test.com", "123456")).willReturn(true);
            given(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .willReturn(1L); // username exists

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("An existing email address throws an exception")
        void shouldThrowWhenEmailExists() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setEmail("exist@test.com");
            request.setVerificationCode("123456");

            given(verificationCodeService.verifyCode("exist@test.com", "123456")).willReturn(true);
            given(userMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .willReturn(0L)  // username check
                    .willReturn(1L); // email check

            assertThatThrownBy(() -> userService.register(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("The role defaults to HR")
        void shouldDefaultRoleToHR() {
            RegisterRequest request = new RegisterRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setEmail("new@test.com");
            request.setVerificationCode("123456");
            request.setRole(null); // no role specified

            given(verificationCodeService.verifyCode(anyString(), anyString())).willReturn(true);
            given(userMapper.selectCount(any(LambdaQueryWrapper.class))).willReturn(0L);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");
            given(userMapper.insert(any(User.class))).willReturn(1);

            userService.register(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            then(userMapper).should().insert(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo("HR");
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Login tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("Successful login returns the token and user information")
        void shouldLoginSuccessfully() {
            LoginRequest request = new LoginRequest();
            request.setUsername("zhangsan");
            request.setPassword("password123");

            given(userMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testUser);
            given(passwordEncoder.matches("password123", testUser.getPassword())).willReturn(true);
            given(jwtUtil.generateToken(1L, "zhangsan", "HR")).willReturn("access-token");
            given(jwtUtil.generateRefreshToken(1L, "zhangsan")).willReturn("refresh-token");
            given(jwtUtil.getExpiration()).willReturn(7200L);
            given(jwtUtil.getRefreshExpiration()).willReturn(604800L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            LoginResponse response = userService.login(request);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getExpiresIn()).isEqualTo(7200L);
            assertThat(response.getUserInfo().getUserId()).isEqualTo(1L);
            assertThat(response.getUserInfo().getUsername()).isEqualTo("zhangsan");
            assertThat(response.getUserInfo().getRole()).isEqualTo("HR");
        }

        @Test
        @DisplayName("A missing user throws an exception")
        void shouldThrowWhenUserNotFound() {
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("password123");

            given(userMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("A wrong password throws an exception")
        void shouldThrowWhenPasswordWrong() {
            LoginRequest request = new LoginRequest();
            request.setUsername("zhangsan");
            request.setPassword("wrongpassword");

            given(userMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testUser);
            given(passwordEncoder.matches("wrongpassword", testUser.getPassword())).willReturn(false);

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("A disabled account throws an exception")
        void shouldThrowWhenAccountDisabled() {
            LoginRequest request = new LoginRequest();
            request.setUsername("zhangsan");
            request.setPassword("password123");

            testUser.setStatus(0); // disabled
            given(userMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testUser);
            given(passwordEncoder.matches("password123", testUser.getPassword())).willReturn(true);

            assertThatThrownBy(() -> userService.login(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Login reads today's AI usage count")
        void shouldReadTodayAiUsedFromRedis() {
            LoginRequest request = new LoginRequest();
            request.setUsername("zhangsan");
            request.setPassword("password123");

            given(userMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(testUser);
            given(passwordEncoder.matches("password123", testUser.getPassword())).willReturn(true);
            given(jwtUtil.generateToken(anyLong(), anyString(), anyString())).willReturn("token");
            given(jwtUtil.generateRefreshToken(anyLong(), anyString())).willReturn("refresh");
            given(jwtUtil.getExpiration()).willReturn(7200L);
            given(jwtUtil.getRefreshExpiration()).willReturn(604800L);
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(contains("rate:ai:"))).willReturn("5");

            LoginResponse response = userService.login(request);

            assertThat(response.getUserInfo().getTodayAiUsed()).isEqualTo(5);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Token refresh tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("refreshToken")
    class RefreshTokenTests {

        @Test
        @DisplayName("A blank token throws an exception")
        void shouldThrowWhenTokenBlank() {
            assertThatThrownBy(() -> userService.refreshToken(""))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("A null token throws an exception")
        void shouldThrowWhenTokenNull() {
            assertThatThrownBy(() -> userService.refreshToken(null))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
