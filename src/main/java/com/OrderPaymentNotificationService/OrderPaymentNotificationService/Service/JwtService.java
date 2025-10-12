package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.OrderPaymentNotificationService.OrderPaymentNotificationService.Configuration.KeyLoader;

import jakarta.annotation.PostConstruct;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final KeyLoader keyLoader;
    private final String privateKeyPath;
    private final String publicKeyPath;
    private final long jwtExpirationInMillis;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    public JwtService(
            KeyLoader keyLoader,
            @Value("${jwt.private.key.path}") String privateKeyPath,
            @Value("${jwt.public.key.path}") String publicKeyPath,
            @Value("${jwt.expiration.in.millis}") long jwtExpirationInMillis) {
        this.keyLoader = keyLoader;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
        this.jwtExpirationInMillis = jwtExpirationInMillis;
        System.out.println("[JwtService] Constructor initialized with paths: " + privateKeyPath + ", " + publicKeyPath);
    }

    /**
     * Load keys at startup and fail fast if keys are missing.
     * 
     * @throws Exception if keys are missing or invalid
     */
    @PostConstruct
    public void initKeys() throws Exception {
        System.out.println("[JwtService] Initializing JWT keys...");
        this.privateKey = keyLoader.loadPrivateKey(privateKeyPath);
        this.publicKey = keyLoader.loadPublicKey(publicKeyPath);
        System.out.println("[JwtService] JWT keys loaded successfully.");
    }

    // --- JWT Generation ---
    public String generateToken(String phone, String role, UUID id) {
        System.out.println("[JwtService] Generating token for phone: " + phone + ", role: " + role + ", id: " + id);
        String token = Jwts.builder()
                .setSubject(phone)
                .claim("role", role)
                .claim("id", id.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationInMillis))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
        System.out.println("[JwtService] Token generated successfully.");
        return token;
    }

    // --- JWT Parsing ---
    public Claims parseClaims(String token) throws JwtException {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            System.out.println("[JwtService] Token parsed successfully. Claims: " + claims);
            return claims;
        } catch (JwtException e) {
            System.err.println("[JwtService] ERROR parsing token: " + e.getMessage());
            throw e;
        }
    }

    public String extractPhone(String token) {
        String phone = parseClaims(token).getSubject();
        System.out.println("[JwtService] Extracted phone: " + phone);
        return phone;
    }

    public String extractRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        System.out.println("[JwtService] Extracted role: " + role);
        return role;
    }

    public UUID extractId(String token) {
        UUID id = UUID.fromString(parseClaims(token).get("id", String.class));
        System.out.println("[JwtService] Extracted ID: " + id);
        return id;
    }

    public boolean isTokenExpired(String token) {
        boolean expired = parseClaims(token).getExpiration().before(new Date());
        System.out.println("[JwtService] Token expired: " + expired);
        return expired;
    }

    public boolean validateToken(String token) {
        try {
            boolean valid = !isTokenExpired(token);
            System.out.println("[JwtService] Token valid: " + valid);
            return valid;
        } catch (JwtException ex) {
            System.err.println("[JwtService] Token validation failed: " + ex.getMessage());
            return false;
        }
    }
}
