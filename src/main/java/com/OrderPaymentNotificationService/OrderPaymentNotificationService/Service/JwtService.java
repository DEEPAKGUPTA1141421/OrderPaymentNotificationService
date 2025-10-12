package com.OrderPaymentNotificationService.OrderPaymentNotificationService.Service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long jwtExpirationInMillis;

    public JwtService(
            @Value("${jwt.private.key.path}") String privateKeyPath,
            @Value("${jwt.public.key.path}") String publicKeyPath,
            @Value("${jwt.expiration.in.millis}") long jwtExpirationInMillis,
            ResourceLoader resourceLoader) throws Exception {

        this.privateKey = loadPrivateKey(privateKeyPath, resourceLoader);
        this.publicKey = loadPublicKey(publicKeyPath, resourceLoader);
        this.jwtExpirationInMillis = jwtExpirationInMillis;
    }

    // --- JWT Methods ---

    public String generateToken(String phone, String role, UUID id) {
        return Jwts.builder()
                .setSubject(phone)
                .claim("role", role)
                .claim("id", id.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationInMillis))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractPhone(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public UUID extractId(String token) {
        return UUID.fromString(parseClaims(token).get("id", String.class));
    }

    public boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (JwtException ex) {
            return false;
        }
    }

    // --- Helper Methods for loading keys from classpath ---
    private PrivateKey loadPrivateKey(String path, ResourceLoader resourceLoader) throws Exception {
        byte[] keyBytes = readPem(resourceLoader.getResource(path));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String path, ResourceLoader resourceLoader) throws Exception {
        byte[] keyBytes = readPem(resourceLoader.getResource(path));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private byte[] readPem(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            String pem = new String(bytes)
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s+", "");
            return Base64.getDecoder().decode(pem);
        }
    }
}
