package com.antennes.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AuthManager {
    private static final SecretKey SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // 24 hours
    
    // Store users with their roles and passwords
    private static final Map<String, User> USERS = new HashMap<>();
    
    static class User {
        String password;
        String role;
        
        User(String password, String role) {
            this.password = password;
            this.role = role;
        }
    }
    
    static {
        // Default users with different roles
        USERS.put("admin", new User("admin123", "ADMIN"));
        USERS.put("operator", new User("operator123", "OPERATOR"));
        USERS.put("viewer", new User("viewer123", "VIEWER"));
    }
    
    public static String authenticate(String username, String password) {
        User user = USERS.get(username);
        if (user != null && user.password.equals(password)) {
            return generateToken(username, user.role);
        }
        return null;
    }
    
    private static String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }
    
    public static boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
    
    public static String getUsernameFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException e) {
            return null;
        }
    }
    
    public static String getRoleFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .get("role", String.class);
        } catch (JwtException e) {
            return null;
        }
    }
    
    // Add new user (admin only)
    public static boolean addUser(String username, String password, String role) {
        if (!USERS.containsKey(username)) {
            USERS.put(username, new User(password, role));
            return true;
        }
        return false;
    }
}