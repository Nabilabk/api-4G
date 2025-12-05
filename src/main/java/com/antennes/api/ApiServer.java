package com.antennes.api;

import com.antennes.database.AntenneDao;
import com.antennes.model.Antenne;
import com.antennes.security.AuthManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class ApiServer {
    private static final AntenneDao dao = new AntenneDao();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static boolean isRunning = false;
    
    // In-memory token blacklist (in production, use Redis or database)
    private static final Map<String, Long> blacklistedTokens = new HashMap<>();
    
    public static void start() {
        if (isRunning) return;
        
        Spark.port(4567);
        
        // Enable CORS
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
            response.header("Access-Control-Allow-Credentials", "true");
        });
        
        options("/*", (request, response) -> {
            response.status(200);
            return "OK";
        });
        
        // Authentication middleware for all API endpoints except login and logout
        before("/api/*", (request, response) -> {
            String path = request.pathInfo();
            if ("/api/auth/login".equals(path) || "/api/auth/logout".equals(path)) {
                return; // Skip authentication for login and logout endpoints
            }
            
            String authHeader = request.headers("Authorization");
            
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                halt(401, "{\"error\": \"Authentication required\"}");
            }
            
            String token = authHeader.substring(7);
            
            // Check if token is blacklisted
            if (blacklistedTokens.containsKey(token)) {
                halt(401, "{\"error\": \"Token has been invalidated. Please login again.\"}");
            }
            
            if (!AuthManager.validateToken(token)) {
                halt(401, "{\"error\": \"Invalid or expired token\"}");
            }
        });
        
        // Role-based authorization middleware for admin endpoints
        before("/api/admin/*", (request, response) -> {
            String authHeader = request.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                halt(401, "{\"error\": \"Authentication required\"}");
            }
            
            String token = authHeader.substring(7);
            String role = AuthManager.getRoleFromToken(token);
            
            if (!"ADMIN".equals(role)) {
                halt(403, "{\"error\": \"Admin access required\"}");
            }
        });
        
        // Health check (no auth required)
        get("/health", (req, res) -> {
            res.type("application/json");
            return "{\"status\": \"running\", \"service\": \"antennas-api\", \"port\": 4567, \"timestamp\": " + System.currentTimeMillis() + "}";
        });
        
        // Get all antennas (requires authentication)
        get("/api/antennas", (req, res) -> {
            res.type("application/json");
            
            // Get user role for optional filtering
            String token = req.headers("Authorization").substring(7);
            String role = AuthManager.getRoleFromToken(token);
            
            List<Antenne> antennas = dao.findAll();
            
            // For VIEWER role, limit data or add watermark
            if ("VIEWER".equals(role)) {
                Map<String, Object> response = new HashMap<>();
                response.put("count", antennas.size());
                response.put("antennas", antennas);
                response.put("timestamp", System.currentTimeMillis());
                response.put("role", role);
                response.put("note", "Viewer access - limited functionality");
                return gson.toJson(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("count", antennas.size());
            response.put("antennas", antennas);
            response.put("timestamp", System.currentTimeMillis());
            response.put("role", role);
            
            return gson.toJson(response);
        });
        
        // Get antennas by operator (requires authentication)
        get("/api/antennas/operator/:operator", (req, res) -> {
            res.type("application/json");
            String operator = req.params(":operator");
            List<Antenne> antennas = dao.findByOperator(operator);
            
            Map<String, Object> response = new HashMap<>();
            response.put("operator", operator);
            response.put("count", antennas.size());
            response.put("antennas", antennas);
            response.put("role", AuthManager.getRoleFromToken(req.headers("Authorization").substring(7)));
            
            return gson.toJson(response);
        });
        
        // Get antenna statistics (requires authentication)
        get("/api/stats", (req, res) -> {
            res.type("application/json");
            List<Antenne> antennas = dao.findAll();
            
            Map<String, Long> operatorCount = new HashMap<>();
            Map<String, Long> techCount = new HashMap<>();
            double avgSignal = 0;
            double maxRisk = 0;
            double minRisk = 1;
            
            for (Antenne a : antennas) {
                operatorCount.merge(a.getNetwork(), 1L, Long::sum);
                techCount.merge(a.getTechnology(), 1L, Long::sum);
                avgSignal += a.getAverageSignal();
                
                double risk = a.getFailureRisk();
                if (risk > maxRisk) maxRisk = risk;
                if (risk < minRisk) minRisk = risk;
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalAntennas", antennas.size());
            stats.put("operators", operatorCount);
            stats.put("technologies", techCount);
            stats.put("averageSignal", antennas.isEmpty() ? 0 : avgSignal / antennas.size());
            stats.put("riskAnalysis", Map.of(
                "maxRisk", maxRisk,
                "minRisk", minRisk,
                "criticalCount", antennas.stream().filter(a -> a.getFailureRisk() > 0.8).count()
            ));
            stats.put("role", AuthManager.getRoleFromToken(req.headers("Authorization").substring(7)));
            
            return gson.toJson(stats);
        });
        
        // Search antennas by coordinates (radius search)
        get("/api/antennas/nearby", (req, res) -> {
            res.type("application/json");
            try {
                String latParam = req.queryParams("lat");
                String lonParam = req.queryParams("lon");
                
                if (latParam == null || lonParam == null) {
                    res.status(400);
                    return "{\"error\": \"Missing parameters. Required: lat, lon\"}";
                }
                
                double lat = Double.parseDouble(latParam);
                double lon = Double.parseDouble(lonParam);
                
                String radiusParam = req.queryParams("radius");
                double radius = radiusParam != null ? Double.parseDouble(radiusParam) : 10.0;
                
                List<Antenne> allAntennas = dao.findAll();
                List<Antenne> nearby = allAntennas.stream()
                    .filter(a -> calculateDistance(lat, lon, a.getLat(), a.getLon()) <= radius)
                    .toList();
                
                Map<String, Object> response = new HashMap<>();
                response.put("center", Map.of("lat", lat, "lon", lon));
                response.put("radius_km", radius);
                response.put("count", nearby.size());
                response.put("antennas", nearby);
                response.put("role", AuthManager.getRoleFromToken(req.headers("Authorization").substring(7)));
                
                return gson.toJson(response);
            } catch (NumberFormatException e) {
                res.status(400);
                return "{\"error\": \"Invalid number format\"}";
            } catch (Exception e) {
                res.status(500);
                return "{\"error\": \"Server error: " + e.getMessage() + "\"}";
            }
        });
        
        // Export data endpoint (ADMIN ONLY)
        get("/api/export/:format", (req, res) -> {
            String token = req.headers("Authorization").substring(7);
            String role = AuthManager.getRoleFromToken(token);
            
            if (!"ADMIN".equals(role)) {
                res.status(403);
                return "{\"error\": \"Admin access required for data export\"}";
            }
            
            String format = req.params(":format");
            List<Antenne> antennas = dao.findAll();
            
            if ("csv".equalsIgnoreCase(format)) {
                res.type("text/csv");
                res.header("Content-Disposition", "attachment; filename=antennas_export.csv");
                
                StringBuilder csv = new StringBuilder();
                csv.append("latitude,longitude,signal,technology,operator,range,failureRisk,riskLevel\n");
                
                for (Antenne a : antennas) {
                    csv.append(String.format("%.6f,%.6f,%.1f,%s,%s,%.0f,%.3f,%s\n",
                        a.getLat(), a.getLon(), a.getAverageSignal(),
                        a.getTechnology(), a.getNetwork(), a.getRange(),
                        a.getFailureRisk(), a.getRiskLevel()));
                }
                
                return csv.toString();
            } else if ("json".equalsIgnoreCase(format)) {
                res.type("application/json");
                res.header("Content-Disposition", "attachment; filename=antennas_export.json");
                return gson.toJson(antennas);
            } else {
                res.status(400);
                return "{\"error\": \"Unsupported format. Use 'csv' or 'json'\"}";
            }
        });
        
        // Authentication endpoint (no auth required)
        post("/api/auth/login", (req, res) -> {
            res.type("application/json");
            try {
                Map<?, ?> body = gson.fromJson(req.body(), Map.class);
                String username = (String) body.get("username");
                String password = (String) body.get("password");
                
                String token = AuthManager.authenticate(username, password);
                
                if (token != null) {
                    String role = AuthManager.getRoleFromToken(token);
                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    response.put("username", username);
                    response.put("role", role);
                    response.put("expires_in", 86400);
                    return gson.toJson(response);
                } else {
                    res.status(401);
                    return "{\"error\": \"Invalid credentials\"}";
                }
            } catch (Exception e) {
                res.status(400);
                return "{\"error\": \"Invalid request format: " + e.getMessage() + "\"}";
            }
        });
        
        // Logout endpoint (requires authentication)
        post("/api/auth/logout", (req, res) -> {
            res.type("application/json");
            
            String authHeader = req.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                res.status(401);
                return "{\"error\": \"Authentication required\"}";
            }
            
            String token = authHeader.substring(7);
            
            // Add token to blacklist
            blacklistedTokens.put(token, System.currentTimeMillis());
            
            // Clean up old blacklisted tokens (older than 24 hours)
            long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
            blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < twentyFourHoursAgo);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Successfully logged out");
            response.put("timestamp", System.currentTimeMillis());
            
            return gson.toJson(response);
        });
        
        // Verify token (no auth required)
        get("/api/auth/verify", (req, res) -> {
            res.type("application/json");
            String authHeader = req.headers("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                // Check if token is blacklisted
                if (blacklistedTokens.containsKey(token)) {
                    return "{\"valid\": false, \"reason\": \"Token has been invalidated\"}";
                }
                
                if (AuthManager.validateToken(token)) {
                    String username = AuthManager.getUsernameFromToken(token);
                    String role = AuthManager.getRoleFromToken(token);
                    return String.format("{\"valid\": true, \"username\": \"%s\", \"role\": \"%s\"}", username, role);
                }
            }
            
            res.status(401);
            return "{\"valid\": false}";
        });
        
        // ============= ADMIN ENDPOINTS =============
        
        // List all users (ADMIN ONLY)
        get("/api/admin/users", (req, res) -> {
            res.type("application/json");
            
            // Return user list (in real app, from database)
            Map<String, Object> users = new HashMap<>();
            users.put("admin", Map.of("role", "ADMIN", "description", "Full system access"));
            users.put("operator", Map.of("role", "OPERATOR", "description", "Can view and filter data"));
            users.put("viewer", Map.of("role", "VIEWER", "description", "Read-only access"));
            
            Map<String, Object> response = new HashMap<>();
            response.put("users", users);
            response.put("count", users.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return gson.toJson(response);
        });
        
        // Add new user (ADMIN ONLY)
        post("/api/admin/users", (req, res) -> {
            res.type("application/json");
            
            try {
                Map<?, ?> body = gson.fromJson(req.body(), Map.class);
                String username = (String) body.get("username");
                String password = (String) body.get("password");
                String userRole = (String) body.get("role");
                
                if (username == null || password == null || userRole == null) {
                    res.status(400);
                    return "{\"error\": \"Missing fields: username, password, role\"}";
                }
                
                // Validate role
                if (!"ADMIN".equals(userRole) && !"OPERATOR".equals(userRole) && !"VIEWER".equals(userRole)) {
                    res.status(400);
                    return "{\"error\": \"Invalid role. Use: ADMIN, OPERATOR, or VIEWER\"}";
                }
                
                if (AuthManager.addUser(username, password, userRole)) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "User created successfully");
                    response.put("username", username);
                    response.put("role", userRole);
                    return gson.toJson(response);
                } else {
                    res.status(409);
                    return "{\"error\": \"User already exists\"}";
                }
            } catch (Exception e) {
                res.status(400);
                return "{\"error\": \"Invalid request: " + e.getMessage() + "\"}";
            }
        });
        
        // Get system info (ADMIN ONLY)
        get("/api/admin/system", (req, res) -> {
            res.type("application/json");
            
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("javaVersion", System.getProperty("java.version"));
            systemInfo.put("os", System.getProperty("os.name"));
            systemInfo.put("memory", Map.of(
                "total", runtime.totalMemory() / 1024 / 1024 + " MB",
                "free", runtime.freeMemory() / 1024 / 1024 + " MB",
                "max", runtime.maxMemory() / 1024 / 1024 + " MB"
            ));
            systemInfo.put("uptime", System.currentTimeMillis() - startTime);
            systemInfo.put("database", "SQLite");
            systemInfo.put("antennasCount", dao.findAll().size());
            systemInfo.put("activeSessions", blacklistedTokens.size());
            
            return gson.toJson(systemInfo);
        });
        
        isRunning = true;
        startTime = System.currentTimeMillis();
        System.out.println("✅ API Server started on port 4567");
        System.out.println("📡 Available endpoints:");
        System.out.println("   - GET  /health");
        System.out.println("   - GET  /api/antennas");
        System.out.println("   - GET  /api/stats");
        System.out.println("   - GET  /api/antennas/operator/:operator");
        System.out.println("   - GET  /api/antennas/nearby?lat=X&lon=Y&radius=Z");
        System.out.println("   - GET  /api/export/:format (ADMIN ONLY)");
        System.out.println("   - POST /api/auth/login");
        System.out.println("   - POST /api/auth/logout");
        System.out.println("   - GET  /api/auth/verify");
        System.out.println("   - GET  /api/admin/users (ADMIN ONLY)");
        System.out.println("   - POST /api/admin/users (ADMIN ONLY)");
        System.out.println("   - GET  /api/admin/system (ADMIN ONLY)");
    }
    
    private static long startTime;
    
    public static void stop() {
        if (isRunning) {
            Spark.stop();
            isRunning = false;
            blacklistedTokens.clear();
            System.out.println("🛑 API Server stopped");
        }
    }
    
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}