package com.example.Contract_review.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class OnlyOfficeJwtService {

    @Value("${onlyoffice.jwt.enabled:false}")
    private boolean jwtEnabled;

    @Value("${onlyoffice.jwt.secret:}")
    private String jwtSecret;

    @Value("${onlyoffice.jwt.ttl-seconds:3600}")
    private long ttlSeconds;

    public boolean isEnabled() {
        return jwtEnabled && jwtSecret != null && !jwtSecret.isEmpty();
    }

    public String sign(Map<String, Object> payload) {
        if (!isEnabled()) {
            return null;
        }
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttlSeconds * 1000);
        return Jwts.builder()
                .setClaims(payload)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}


