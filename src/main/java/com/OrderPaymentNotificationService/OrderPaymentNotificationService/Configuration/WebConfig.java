package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service.JwtService;
import com.OrderPaymentNotificationService.OrderPaymentNotificationService.filter.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig {

        private final JwtService jwtService;

        // ✅ CORS Configuration (IMPORTANT)
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();

                // 🔥 Allow your frontend origins (update if needed)
                config.setAllowedOriginPatterns(List.of("*"));
                config.setAllowedMethods(List.of("*"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);

                // Allow all standard HTTP methods
                config.setAllowedMethods(List.of(
                                "GET", "POST", "PUT", "DELETE", "OPTIONS"));

                // Allow headers
                config.setAllowedHeaders(List.of(
                                "Authorization",
                                "Content-Type",
                                "X-Requested-With",
                                "Accept"));

                // If using JWT (Authorization header)
                config.setAllowCredentials(false);

                // Cache preflight response (optional)
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);

                return source;
        }

        // ✅ Security Configuration
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

                JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);

                http
                                .cors(cors -> {
                                }) // ✅ MUST ENABLE CORS HERE
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // ✅ Allow preflight requests
                                                .requestMatchers("/", "/api/v1/users/wallet/**",
                                                                "/api/v1/users/payment-methods/**",
                                                                "/api/v1/users/loyalty-points/**")
                                                .hasRole("USER")

                                                // ─── Buy Now: direct single-product purchase ─────────
                                                .requestMatchers(HttpMethod.POST, "/api/v1/buy-now")
                                                .hasRole("USER")

                                                // ─── Booking: checkout + order history ───────────────
                                                .requestMatchers(HttpMethod.GET, "/api/v1/booking/**")
                                                .hasRole("USER")

                                                // ─── Receipt download ──────────────────────────────
                                                .requestMatchers(HttpMethod.GET, "/api/v1/receipt/**")
                                                .hasRole("USER")

                                                // ─── COD: OTP generation (customer only) ───
                                                .requestMatchers("/api/v1/payment/cod/generate-otp")
                                                .hasRole("USER")

                                                // ─── COD: Payment confirmation (delivery partner only) ───
                                                .requestMatchers("/api/v1/payment/cod/confirm")
                                                .hasRole("DELIVERY")

                                                // ─── COD: QR generation + status poll (delivery partner only) ───
                                                .requestMatchers("/api/v1/payment/cod/generate-payment-qr")
                                                .hasRole("DELIVERY")
                                                .requestMatchers("/api/v1/payment/cod/qr-status/**")
                                                .hasRole("DELIVERY")

                                                                // ✅ SendBird webhooks — verified by HMAC signature, not JWT
                                                .requestMatchers("/webhooks/sendbird/**")
                                                .permitAll()

                                                // ✅ Public APIs
                                                .requestMatchers("/apisss/v1/*")
                                                .permitAll()

                                                // 🔒 Secure everything else
                                                .anyRequest().authenticated())

                                // ✅ JWT Filter
                                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}
// juoiiojnji jioji mmjio uiouoinjjjknjknjnjnjkjjnkjnk
// mlkklijkjiljijijijnnjjijihuhuhuhhuhuhugihhuihuikuhuigyjjijinjkj
// hujijhjjujijkjjijoiiouu8oi khuuk
