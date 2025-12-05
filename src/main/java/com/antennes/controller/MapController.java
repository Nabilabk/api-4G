package com.antennes.controller;

import com.antennes.api.ApiServer;
import com.antennes.database.AntenneDao;
import com.antennes.ml.PredictionService;
import com.antennes.model.Antenne;
import com.antennes.service.CoverageService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class MapController {

    @FXML private WebView webView;
    @FXML private Label antennaCountLabel;
    @FXML private Button toggleButton;
    @FXML private ComboBox<String> operatorComboBox;
    @FXML private ComboBox<String> riskComboBox;
    @FXML private Button testApiButton;
    @FXML private Button apiInfoButton;
    @FXML private Button exportButton;
    @FXML private Button logoutButton;
    @FXML private HBox operatorFilterBox;
    @FXML private HBox riskFilterBox;
    @FXML private HBox apiControlsBox;
    @FXML private Label roleLabel;

    private WebEngine engine;
    private List<Antenne> allData;
    private List<Antenne> filteredData;
    private boolean mapLoaded = false;
    private final CoverageService coverageService = new CoverageService();
    private boolean showingHeatmap = false;
    private String selectedOperator = "ALL";
    private String selectedRiskLevel = "ALL";
    private String authToken;
    private String userRole;
    private String username;

    private final AntenneDao dao = new AntenneDao();

    @FXML
    public void initialize() {
        System.out.println("=== INITIALISATION MAPCONTROLLER (Integrated Features) ===");

        // Center the window on startup
        Platform.runLater(() -> {
            Stage stage = (Stage) webView.getScene().getWindow();
            stage.centerOnScreen();
        });

        webView.setContextMenuEnabled(true);
        engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        
        // Activer la communication Java-JavaScript
        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));
        
        // Gérer les erreurs JavaScript
        engine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
            if (newEx != null) {
                System.err.println("Erreur JavaScript: " + newEx.getMessage());
            }
        });

        dao.createTableIfNotExists();
        dao.importCsvIfEmpty();

        allData = dao.findAll();

        // ================== PRÉDICTION IA ==================
        System.out.println("Lancement de l'IA pour prédire les pannes...");
        var predictor = new PredictionService();
        allData.forEach(a -> a.setFailureRisk(predictor.predictRisk(a)));
        System.out.println("Prédiction IA terminée !");

        filteredData = new ArrayList<>(allData);

        System.out.println("Loaded " + allData.size() + " antennas from SQLite + IA ready");

        initializeOperatorFilter();
        initializeRiskFilter();

        if (antennaCountLabel != null) {
            antennaCountLabel.setText("IA & API Active – " + allData.size() + " antennes");
        }

        // Setup API buttons
        if (testApiButton != null) {
            testApiButton.setOnAction(e -> testApiConnection());
        }
        
        if (apiInfoButton != null) {
            apiInfoButton.setOnAction(e -> showApiInfo());
        }

        // Setup export button
        if (exportButton != null) {
            exportButton.setOnAction(e -> exportData());
        }
        
        // Setup logout button
        if (logoutButton != null) {
            logoutButton.setOnAction(e -> handleLogout());
        }

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("Map loaded successfully");
                mapLoaded = true;
                updateToggleButton();
                Platform.runLater(() -> {
                    try { 
                        Thread.sleep(500); 
                    } catch (InterruptedException ignored) {}
                    invalidateMapSize();
                    showAntennasWithAutoCircles();
                });
            }
        });

        webView.widthProperty().addListener((obs, old, newVal) -> invalidateMapSize());
        webView.heightProperty().addListener((obs, old, newVal) -> invalidateMapSize());

        loadMap();
        
        // Hide/show elements based on role (will be updated when role is set)
        updateUIForRole();
    }
    
    @FXML
    private void handleLogout() {
        // Confirm logout
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Logout");
        confirmAlert.setHeaderText("Confirm Logout");
        confirmAlert.setContentText("Are you sure you want to logout?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Clear data
            authToken = null;
            userRole = null;
            username = null;
            
            // Stop API server
            ApiServer.stop();
            
            // Return to login screen
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-view.fxml"));
                Parent root = loader.load();
                
                Stage stage = (Stage) webView.getScene().getWindow();
                Scene scene = new Scene(root, 500, 500);
                stage.setScene(scene);
                stage.setTitle("Network Coverage - Login");
                stage.centerOnScreen();
                stage.show();
                
                // Clear login controller static data
                LoginController.logout();
                
                System.out.println("✅ User logged out successfully");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Logout Error", "Failed to return to login screen: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        System.out.println("🔐 Authentication token received: " + (token != null ? token.substring(0, Math.min(20, token.length())) : "null") + "...");
        
        // Display API status
        if (antennaCountLabel != null && authToken != null) {
            Platform.runLater(() -> {
                String tokenPreview = authToken.substring(0, Math.min(10, authToken.length()));
                antennaCountLabel.setText("API Active | Token: " + tokenPreview + "...");
            });
        }
    }
    
    public void setUserRole(String role) {
        this.userRole = role;
        updateUIForRole();
        updateRoleLabel();
    }
    
    public void setUsername(String username) {
        this.username = username;
        updateRoleLabel();
    }

    private void updateUIForRole() {
        if (userRole == null) return;
        
        Platform.runLater(() -> {
            switch (userRole.toUpperCase()) {
                case "ADMIN":
                    // Admin can do everything
                    if (exportButton != null) exportButton.setVisible(true);
                    if (operatorFilterBox != null) operatorFilterBox.setVisible(true);
                    if (riskFilterBox != null) riskFilterBox.setVisible(true);
                    if (apiControlsBox != null) apiControlsBox.setVisible(true);
                    if (toggleButton != null) toggleButton.setVisible(true);
                    break;
                    
                case "OPERATOR":
                    // Operator can filter and test API, but not export
                    if (exportButton != null) exportButton.setVisible(false);
                    if (operatorFilterBox != null) operatorFilterBox.setVisible(true);
                    if (riskFilterBox != null) riskFilterBox.setVisible(true);
                    if (apiControlsBox != null) apiControlsBox.setVisible(true);
                    if (toggleButton != null) toggleButton.setVisible(true);
                    break;
                    
                case "VIEWER":
                    // Viewer can only see map and toggle view
                    if (exportButton != null) exportButton.setVisible(false);
                    if (operatorFilterBox != null) operatorFilterBox.setVisible(false);
                    if (riskFilterBox != null) riskFilterBox.setVisible(false);
                    if (apiControlsBox != null) apiControlsBox.setVisible(false);
                    if (toggleButton != null) toggleButton.setVisible(true);
                    
                    // Set default view for viewer
                    if (antennaCountLabel != null) {
                        antennaCountLabel.setText("Viewer Mode - " + allData.size() + " antennas");
                    }
                    break;
            }
        });
    }

    private void updateRoleLabel() {
        if (roleLabel != null && userRole != null && username != null) {
            Platform.runLater(() -> {
                roleLabel.setText(username + " [" + userRole + "]");
                // Color code based on role
                switch (userRole.toUpperCase()) {
                    case "ADMIN":
                        roleLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        break;
                    case "OPERATOR":
                        roleLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
                        break;
                    case "VIEWER":
                        roleLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                        break;
                }
            });
        }
    }

    @FXML
    private void testApiConnection() {
        // Check if user has permission
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied", 
                     "VIEWER role cannot test API connections. Your role: " + userRole, 
                     Alert.AlertType.WARNING);
            return;
        }
        
        if (authToken == null) {
            showAlert("API Error", "Not authenticated. Please login first.", Alert.AlertType.ERROR);
            return;
        }
        
        new Thread(() -> {
            try {
                URL url = new URL("http://localhost:4567/api/stats");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                
                BufferedReader in;
                if (responseCode >= 200 && responseCode < 300) {
                    in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }
                
                String inputLine;
                StringBuilder response = new StringBuilder();
                
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                
                if (responseCode == 200) {
                    // Parse JSON for better display
                    String formattedResponse = formatJsonResponse(response.toString());
                    
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("API Test Successful");
                        alert.setHeaderText("✅ API Connection Established - HTTP " + responseCode);
                        
                        TextArea textArea = new TextArea(formattedResponse);
                        textArea.setEditable(false);
                        textArea.setWrapText(true);
                        textArea.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 11px;");
                        
                        alert.getDialogPane().setContent(textArea);
                        alert.getDialogPane().setPrefSize(600, 400);
                        alert.showAndWait();
                    });
                } else {
                    Platform.runLater(() -> {
                        showAlert("API Error", 
                                "HTTP " + responseCode + "\n\nResponse: " + response.toString(), 
                                Alert.AlertType.ERROR);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("API Connection Failed", 
                             "Cannot connect to API server: " + e.getMessage() + 
                             "\n\nMake sure the API server is running on port 4567.", 
                             Alert.AlertType.ERROR);
                });
            }
        }).start();
    }

    @FXML
    private void showApiInfo() {
        // Check if user has permission
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied", 
                     "VIEWER role cannot access API information. Your role: " + userRole, 
                     Alert.AlertType.WARNING);
            return;
        }
        
        String apiInfo = """
            🌐 NETWORK COVERAGE API - Documentation
            ======================================
            
            Base URL: http://localhost:4567
            
            🔐 AUTHENTICATION REQUIRED FOR ALL /api/* ENDPOINTS
            
            📋 AVAILABLE ENDPOINTS:
            
            1. GET /health
               - Health check (no auth required)
               Example: curl http://localhost:4567/health
            
            2. GET /api/antennas
               - Get all antennas with risk predictions
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/antennas
            
            3. GET /api/antennas/operator/:operator
               - Filter by operator (IAM, Orange, Inwi)
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/antennas/operator/IAM
            
            4. GET /api/stats
               - Get statistics and analytics
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/stats
            
            5. GET /api/antennas/nearby?lat=X&lon=Y&radius=Z
               - Radius search (radius in km, default: 10)
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" "http://localhost:4567/api/antennas/nearby?lat=33.5731&lon=-7.5898&radius=5"
            
            6. GET /api/export/:format
               - Export data (csv or json format) - ADMIN ONLY
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/export/csv -o antennas.csv
            
            7. POST /api/auth/login
               - Login with username/password
               Example: curl -X POST http://localhost:4567/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
            
            8. GET /api/auth/verify
               - Verify authentication token
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/auth/verify
            
            9. GET /api/admin/users
               - List all users - ADMIN ONLY
               Example: curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/admin/users
            
            10. POST /api/admin/users
                - Create new user - ADMIN ONLY
                Example: curl -X POST -H "Authorization: Bearer YOUR_TOKEN" -H "Content-Type: application/json" -d '{"username":"newuser","password":"password","role":"OPERATOR"}' http://localhost:4567/api/admin/users
            
            11. POST /api/auth/logout
                - Logout (invalidate token)
                Example: curl -X POST -H "Authorization: Bearer YOUR_TOKEN" http://localhost:4567/api/auth/logout
            
            🔧 YOUR API CREDENTIALS:
            
            Available Users:
            - ADMIN:     admin / admin123
            - OPERATOR:  operator / operator123
            - VIEWER:    viewer / viewer123
            
            📊 YOUR CURRENT ROLE: %s
            🔑 YOUR TOKEN: %s
            ⏱️  Token valid for 24 hours
            
            📡 API STATUS: %s
            ======================================
            API Server: Running on port 4567
            """.formatted(
                userRole != null ? userRole : "Unknown",
                authToken != null ? authToken.substring(0, Math.min(30, authToken.length())) + "..." : "Not available",
                isApiServerRunning() ? "✅ RUNNING" : "❌ STOPPED"
            );
        
        TextArea textArea = new TextArea(apiInfo);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Monospace'; -fx-font-size: 11px;");
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("API Documentation");
        alert.setHeaderText("REST API Endpoints - Network Coverage System");
        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setPrefSize(700, 600);
        alert.showAndWait();
    }

    private boolean isApiServerRunning() {
        try {
            URL url = new URL("http://localhost:4567/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String formatJsonResponse(String json) {
        // Simple JSON formatting
        StringBuilder formatted = new StringBuilder();
        int indent = 0;
        
        for (char c : json.toCharArray()) {
            if (c == '{' || c == '[') {
                formatted.append(c).append("\n").append("  ".repeat(++indent));
            } else if (c == '}' || c == ']') {
                formatted.append("\n").append("  ".repeat(--indent)).append(c);
            } else if (c == ',') {
                formatted.append(c).append("\n").append("  ".repeat(indent));
            } else {
                formatted.append(c);
            }
        }
        
        return formatted.toString();
    }

    private void initializeOperatorFilter() {
        if (operatorComboBox != null) {
            Set<String> operators = allData.stream()
                .map(Antenne::getNetwork)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            List<String> operatorList = new ArrayList<>(operators);
            Collections.sort(operatorList);
            operatorList.add(0, "TOUS");

            ObservableList<String> observableList = FXCollections.observableArrayList(operatorList);
            operatorComboBox.setItems(observableList);
            operatorComboBox.setValue("TOUS");
        }
    }

    private void initializeRiskFilter() {
        if (riskComboBox != null) {
            ObservableList<String> riskLevels = FXCollections.observableArrayList(
                "TOUS", "FAIBLE", "MODÉRÉ", "ÉLEVÉ", "CRITIQUE"
            );
            riskComboBox.setItems(riskLevels);
            riskComboBox.setValue("TOUS");
        }
    }

    @FXML
    private void filterByOperator() {
        // Check if user has permission
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied", 
                     "VIEWER role cannot filter data. Your role: " + userRole, 
                     Alert.AlertType.WARNING);
            return;
        }
        
        if (operatorComboBox == null) return;
        String selected = operatorComboBox.getValue();

        if (selected == null || "TOUS".equals(selected)) {
            selectedOperator = "ALL";
        } else {
            selectedOperator = selected;
        }
        
        applyCombinedFilters();
    }

    @FXML
    private void filterByRiskLevel() {
        // Check if user has permission
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied", 
                     "VIEWER role cannot filter data. Your role: " + userRole, 
                     Alert.AlertType.WARNING);
            return;
        }
        
        if (riskComboBox == null) return;
        String selected = riskComboBox.getValue();
        
        if (selected == null || "TOUS".equals(selected)) {
            selectedRiskLevel = "ALL";
        } else {
            selectedRiskLevel = selected;
        }
        
        applyCombinedFilters();
    }

    private void applyCombinedFilters() {
        List<Antenne> result = allData;
        
        // Apply operator filter
        if (selectedOperator != null && !"ALL".equals(selectedOperator)) {
            result = result.stream()
                .filter(a -> selectedOperator.equals(a.getNetwork()))
                .collect(Collectors.toList());
        }
        
        // Apply risk filter
        if (selectedRiskLevel != null && !"ALL".equals(selectedRiskLevel)) {
            result = result.stream()
                .filter(a -> matchesRiskLevel(a, selectedRiskLevel))
                .collect(Collectors.toList());
        }
        
        filteredData = result;
        
        if (showingHeatmap) showCoverageHeatmap();
        else showAntennasWithAutoCircles();

        updateStatusLabel();
    }

    private boolean matchesRiskLevel(Antenne antenna, String riskLevel) {
        double risk = antenna.getFailureRisk();
        switch (riskLevel) {
            case "FAIBLE": return risk < 0.3;
            case "MODÉRÉ": return risk >= 0.3 && risk < 0.6;
            case "ÉLEVÉ": return risk >= 0.6 && risk < 0.8;
            case "CRITIQUE": return risk >= 0.8;
            default: return true;
        }
    }

    private void updateStatusLabel() {
        if (antennaCountLabel != null) {
            String operatorText = "ALL".equals(selectedOperator) ? "TOUS" : selectedOperator;
            String riskText = "ALL".equals(selectedRiskLevel) ? "" : " • " + selectedRiskLevel;
            antennaCountLabel.setText(filteredData.size() + " antennes (" + operatorText + riskText + ")");
        }
    }

    private void updateToggleButton() {
        if (toggleButton != null) {
            toggleButton.setText(showingHeatmap ? "Voir Antennes" : "Voir Heatmap");
            toggleButton.setStyle(showingHeatmap 
                ? "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    private void invalidateMapSize() {
        if (mapLoaded) {
            Platform.runLater(() -> {
                try { 
                    engine.executeScript("if (typeof resizeCanvas === 'function') resizeCanvas();"); 
                } catch (Exception e) {
                    System.err.println("Erreur resize canvas: " + e.getMessage());
                }
            });
        }
    }

    // NEW: Show antennas with auto circles (from friend's feature)
    @FXML 
    private void showAntennasWithAutoCircles() {
        if (!mapLoaded) {
            System.out.println("Carte non encore chargée, attente...");
            return;
        }
        
        Platform.runLater(() -> {
            try {
                if (showingHeatmap) {
                    engine.executeScript("if (window.heatmapLayer) { map.removeLayer(window.heatmapLayer); window.heatmapLayer = null; }");
                    showingHeatmap = false;
                    updateToggleButton();
                }
                
                // Convertir les données en GeoJSON
                String geojson = toAntennaGeoJsonWithAutoCircles(filteredData);
                
                // Envoyer les données à JavaScript
                String script = "showAntennasWithAutoCircles(" + geojson + ")";
                System.out.println("Exécution script pour " + filteredData.size() + " antennes (cercles auto)");
                engine.executeScript(script);
                
            } catch (Exception e) { 
                System.err.println("Erreur affichage antennes: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // NEW: Show antennas (original method - kept for compatibility)
    @FXML 
    private void showAntennas() {
        showAntennasWithAutoCircles(); // Use the new method
    }

    @FXML 
    private void showCoverageHeatmap() {
        if (!mapLoaded) return;
        Platform.runLater(() -> {
            try {
                String data = coverageService.generateHeatmapData(filteredData);
                engine.executeScript("if (window.heatmapLayer) map.removeLayer(window.heatmapLayer);");
                engine.executeScript("addHeatmap(" + data + ")");
                showingHeatmap = true;
                updateToggleButton();
            } catch (Exception e) { 
                System.err.println("Erreur affichage heatmap: " + e.getMessage());
            }
        });
    }

    @FXML 
    private void toggleView() {
        if (showingHeatmap) showAntennasWithAutoCircles();
        else showCoverageHeatmap();
    }

    @FXML
    private void exportData() {
        // Check if user has permission
        if (!"ADMIN".equals(userRole)) {
            showAlert("Permission Denied", 
                     "Only ADMIN users can export data. Your role: " + userRole, 
                     Alert.AlertType.WARNING);
            return;
        }
        
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Antenna Data");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
            );
            
            File file = fileChooser.showSaveDialog(webView.getScene().getWindow());
            if (file != null) {
                if (file.getName().toLowerCase().endsWith(".csv")) {
                    exportToCsv(file, filteredData);
                } else if (file.getName().toLowerCase().endsWith(".json")) {
                    exportToJson(file, filteredData);
                } else {
                    exportToCsv(new File(file.getAbsolutePath() + ".csv"), filteredData);
                }
                
                showAlert("Export Successful", 
                         "Data exported successfully to:\n" + file.getAbsolutePath(), 
                         Alert.AlertType.INFORMATION);
            }
        } catch (Exception e) {
            showAlert("Export Error", 
                     "Failed to export data: " + e.getMessage(), 
                     Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void exportToCsv(File file, List<Antenne> antennas) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Latitude,Longitude,Signal(dBm),Technology,Operator,Range(m),RiskLevel,RiskScore");
            
            for (Antenne antenna : antennas) {
                writer.printf(Locale.US, "%.6f,%.6f,%.1f,%s,%s,%.0f,%s,%.3f%n",
                    antenna.getLat(), antenna.getLon(), antenna.getAverageSignal(),
                    antenna.getTechnology(), antenna.getNetwork(), antenna.getRange(),
                    antenna.getRiskLevel(), antenna.getFailureRisk());
            }
        }
    }

    private void exportToJson(File file, List<Antenne> antennas) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("[");
            for (int i = 0; i < antennas.size(); i++) {
                Antenne antenna = antennas.get(i);
                writer.printf(Locale.US,
                    "  {\"latitude\": %.6f, \"longitude\": %.6f, \"signal\": %.1f, " +
                    "\"technology\": \"%s\", \"operator\": \"%s\", \"range\": %.0f, " +
                    "\"riskLevel\": \"%s\", \"riskScore\": %.3f}",
                    antenna.getLat(), antenna.getLon(), antenna.getAverageSignal(),
                    antenna.getTechnology(), antenna.getNetwork(), antenna.getRange(),
                    antenna.getRiskLevel(), antenna.getFailureRisk());
                
                if (i < antennas.size() - 1) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }
            writer.println("]");
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // NEW: Conversion en GeoJSON pour antennes avec cercles automatiques (friend's feature)
    private String toAntennaGeoJsonWithAutoCircles(List<Antenne> antennes) {
        if (antennes.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
        
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        
        // Limiter pour performance
        int limit = Math.min(antennes.size(), 2000);
        
        for (int i = 0; i < limit; i++) {
            Antenne a = antennes.get(i);
            
            // Couleur selon le risque
            String riskColor = getEnhancedRiskColor(a.getFailureRisk());
            String riskLevel = a.getRiskLevel();
            
            sb.append(String.format(Locale.US,
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{" +
                "\"signal\":%.1f,\"tech\":\"%s\",\"operator\":\"%s\"," +
                "\"risk\":%.3f,\"riskColor\":\"%s\",\"riskLevel\":\"%s\"," +
                "\"radius_meters\":%.0f,\"lat\":%.6f,\"lng\":%.6f}}",
                a.getLon(), a.getLat(), 
                a.getAverageSignal(), 
                a.getTechnology(), 
                a.getNetwork(),
                a.getFailureRisk(), 
                riskColor, 
                riskLevel,
                a.getRange(),
                a.getLat(),
                a.getLon()
            ));
            
            if (i < limit - 1) sb.append(",");
        }
        sb.append("]}");
        
        System.out.println("Généré GeoJSON avec " + limit + " antennes (cercles auto)");
        return sb.toString();
    }

    // Original method kept for compatibility
    private String toEnhancedGeoJsonWithRisk(List<Antenne> antennes) {
        return toAntennaGeoJsonWithAutoCircles(antennes); // Use the new method
    }

    private String getEnhancedRiskColor(double risk) {
        if (risk < 0.2) return "#27ae60";     // Bright green - very low risk
        if (risk < 0.4) return "#2ecc71";     // Green - low risk
        if (risk < 0.6) return "#f39c12";     // Orange - medium risk
        if (risk < 0.8) return "#e67e22";     // Dark orange - high risk
        return "#e74c3c";                     // Red - critical risk
    }

    private int getRiskSize(double risk) {
        if (risk < 0.3) return 6;
        if (risk < 0.6) return 10;
        return 15;
    }

    private String getRiskPattern(double risk) {
        if (risk > 0.7) return "pulse";
        if (risk > 0.5) return "glow";
        return "solid";
    }

    private void loadMap() {
        try {
            // Charger le fichier HTML depuis les ressources
            java.net.URL url = getClass().getResource("/html/map.html");
            if (url != null) {
                System.out.println("Chargement map.html depuis: " + url.toString());
                engine.load(url.toExternalForm());
            } else {
                System.err.println("Fichier map.html non trouvé dans les ressources");
                // Créer une page HTML de secours
                engine.loadContent("<html><body><h1>Carte des Antennes</h1><p>Chargement en cours...</p></body></html>");
            }
        } catch (Exception e) {
            System.err.println("Erreur chargement map.html: " + e.getMessage());
            e.printStackTrace();
        }
    }
}