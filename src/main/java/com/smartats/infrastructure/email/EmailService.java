package com.smartats.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Email sending service
 * <p>
 * Capabilities:
 * 1. Send verification code emails
 * 2. Supports plain text emails
 * 3. Can be extended to HTML emails later
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.verification-code.expire-time:300}")
    private Integer expireTimeSeconds;

    /**
     * Send a verification code email
     *
     * @param to   the recipient's email address
     * @param code the verification code
     * @return whether the email was sent successfully
     */
    public boolean sendVerificationCode(String to, String code) {
        try {
            log.info("Sending the verification code email: to={}, code={}", to, code);

            // Create the email message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // Set the sender
            helper.setFrom(fromEmail);

            // Set the recipient
            helper.setTo(to);

            // Set the subject
            helper.setSubject("[SmartATS] Email verification code");

            // Set the email body
            String content = buildEmailContent(code);
            helper.setText(content, true);  // true = HTML format

            // Send the email
            mailSender.send(message);

            log.info("Verification code email sent successfully: to={}", to);
            return true;

        } catch (MessagingException e) {
            log.error("Failed to send the verification code email: to={}, error={}", to, e.getMessage());
            return false;
        }
    }

    /**
     * Build the email body (Minimalism Tech style, English version)
     * Design system:
     * - Style: Minimalism + Modern Tech
     * - Fonts: Source Han Sans / Noto Sans SC (CJK friendly) + system default fonts
     * - Colours: dark blue (#0F172A) + tech blue (#3B82F6) + success green (#22C55E)
     * - Traits: generous white space, clear hierarchy, high contrast, no superfluous decoration
     *
     * @param code the verification code
     * @return the email body in HTML format
     */
    private String buildEmailContent(String code) {
        int expireMinutes = expireTimeSeconds / 60;

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Email verification - SmartATS</title>
                <style>
                    /* ========== Reset & Base ========== */
                    *, *::before, *::after {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }

                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Helvetica Neue", Helvetica, Arial, sans-serif;
                        line-height: 1.6;
                        color: #1E293B;
                        background: #F8FAFC;
                        padding: 24px;
                        min-height: 100vh;
                        -webkit-font-smoothing: antialiased;
                        -moz-osx-font-smoothing: grayscale;
                    }

                    /* ========== Main Container ========== */
                    .email-wrapper {
                        max-width: 600px;
                        margin: 0 auto;
                    }

                    .email-container {
                        background: #FFFFFF;
                        border-radius: 16px;
                        border: 1px solid #E2E8F0;
                        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
                        overflow: hidden;
                    }

                    /* ========== Header Section ========== */
                    .header {
                        padding: 48px 40px 32px;
                        text-align: center;
                        border-bottom: 1px solid #F1F5F9;
                    }

                    .logo {
                        font-size: 32px;
                        font-weight: 700;
                        color: #0F172A;
                        letter-spacing: -0.03em;
                        margin-bottom: 8px;
                    }

                    .tagline {
                        font-size: 14px;
                        font-weight: 500;
                        color: #64748B;
                        letter-spacing: 0.02em;
                    }

                    /* ========== Content Section ========== */
                    .content {
                        padding: 40px;
                    }

                    .greeting {
                        font-size: 22px;
                        font-weight: 600;
                        color: #0F172A;
                        margin-bottom: 16px;
                    }

                    .message {
                        font-size: 15px;
                        color: #475569;
                        line-height: 1.7;
                        margin-bottom: 32px;
                    }

                    /* ========== Verification Code Card ========== */
                    .code-card {
                        background: #F8FAFC;
                        border: 1px solid #E2E8F0;
                        border-radius: 12px;
                        padding: 32px;
                        margin: 24px 0;
                        text-align: center;
                    }

                    .code-label {
                        font-size: 12px;
                        font-weight: 700;
                        color: #64748B;
                        letter-spacing: 0.1em;
                        margin-bottom: 16px;
                    }

                    .verification-code {
                        font-size: 40px;
                        font-weight: 700;
                        color: #0F172A;
                        letter-spacing: 0.1em;
                        margin: 16px 0;
                        user-select: all;
                    }

                    .code-expire {
                        font-size: 13px;
                        color: #64748B;
                        margin-top: 16px;
                    }

                    .code-expire strong {
                        color: #0F172A;
                        font-weight: 700;
                    }

                    /* ========== Info Box ========== */
                    .info-box {
                        background: #EFF6FF;
                        border-left: 3px solid #3B82F6;
                        padding: 16px 20px;
                        margin: 24px 0;
                        border-radius: 0 8px 8px 0;
                    }

                    .info-box-title {
                        font-weight: 700;
                        color: #1E40AF;
                        font-size: 13px;
                        margin-bottom: 4px;
                    }

                    .info-box-content {
                        font-size: 14px;
                        color: #1E40AF;
                        line-height: 1.6;
                    }

                    /* ========== Security Notice ========== */
                    .security-notice {
                        font-size: 13px;
                        color: #94A3B8;
                        text-align: center;
                        margin: 32px 0;
                        padding: 16px;
                    }

                    /* ========== Divider ========== */
                    .divider {
                        height: 1px;
                        background: #F1F5F9;
                        margin: 0;
                    }

                    /* ========== Footer ========== */
                    .footer {
                        padding: 32px 40px;
                        text-align: center;
                    }

                    .footer-text {
                        font-size: 13px;
                        color: #64748B;
                        line-height: 1.6;
                        margin: 8px 0;
                    }

                    .footer-links {
                        margin: 16px 0;
                    }

                    .footer-link {
                        color: #3B82F6;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 500;
                        margin: 0 8px;
                    }

                    .footer-link:hover {
                        text-decoration: underline;
                    }

                    .copyright {
                        font-size: 12px;
                        color: #94A3B8;
                        margin-top: 16px;
                    }

                    /* ========== Responsive Design ========== */
                    @media only screen and (max-width: 640px) {
                        body {
                            padding: 16px;
                        }

                        .header, .content, .footer {
                            padding: 32px 24px;
                        }

                        .logo {
                            font-size: 28px;
                        }

                        .greeting {
                            font-size: 20px;
                        }

                        .verification-code {
                            font-size: 32px;
                            letter-spacing: 0.05em;
                        }

                        .code-card {
                            padding: 24px 20px;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="email-wrapper">
                    <div class="email-container">
                        <!-- Header -->
                        <div class="header">
                            <div class="logo">SmartATS</div>
                            <div class="tagline">Intelligent recruitment management platform</div>
                        </div>

                        <!-- Content -->
                        <div class="content">
                            <div class="greeting">Hello!</div>

                            <p class="message">
                                Thank you for registering with SmartATS. Use the verification code below to complete your registration and activate your account.
                            </p>

                            <!-- Verification Code Card -->
                            <div class="code-card">
                                <div class="code-label">EMAIL VERIFICATION CODE</div>
                                <div class="verification-code">%s</div>
                                <div class="code-expire">
                                    This code is valid for <strong>%d minutes</strong>
                                </div>
                            </div>

                            <!-- Info Box -->
                            <div class="info-box">
                                <div class="info-box-title">Next step</div>
                                <div class="info-box-content">
                                    Enter this code on the registration page to activate your account and start using the SmartATS intelligent recruitment service.
                                </div>
                            </div>

                            <!-- Security Notice -->
                            <div class="security-notice">
                                If you did not request this code, please ignore this email.
                            </div>
                        </div>

                        <!-- Divider -->
                        <div class="divider"></div>

                        <!-- Footer -->
                        <div class="footer">
                            <p class="footer-text">
                                This email was sent automatically by the SmartATS system; please do not reply directly.<br>
                                If you need help, please contact our support team.
                            </p>

                            <div class="footer-links">
                                <a href="https://smartats.com/privacy" class="footer-link">Privacy Policy</a>
                                <span style="color: #94A3B8">·</span>
                                <a href="https://smartats.com/terms" class="footer-link">Terms of Service</a>
                                <span style="color: #94A3B8">·</span>
                                <a href="https://smartats.com/support" class="footer-link">Contact Support</a>
                            </div>

                            <p class="copyright">
                                &copy; 2026 SmartATS. All rights reserved.<br>
                                Building the future of intelligent recruitment
                            </p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code, expireMinutes);
    }
}
