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
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

public class MapController {

    @FXML private WebView webView;
    @FXML private Label antennaCountLabel;
    @FXML private Button toggleButton;
    @FXML private ComboBox<String> operatorComboBox;
    @FXML private ComboBox<String> riskComboBox;
    @FXML private ComboBox<String> cityComboBox;
    @FXML private CheckBox riskFilterCheckBox;
    @FXML private Button testApiButton;
    @FXML private Button apiInfoButton;
    @FXML private Button exportButton;
    @FXML private Button logoutButton;
    @FXML private HBox operatorFilterBox;
    @FXML private HBox riskFilterBox;
    @FXML private HBox cityFilterBox;
    @FXML private HBox apiControlsBox;
    @FXML private Label roleLabel;
    @FXML private VBox riskCheckBoxContainer;
    @FXML private VBox exportButtonContainer;

    private WebEngine engine;
    private List<Antenne> allData;
    private List<Antenne> filteredData;
    private boolean mapLoaded = false;
    private final CoverageService coverageService = new CoverageService();
    private boolean showingHeatmap = false;
    private String selectedOperator = "ALL";
    private boolean riskFilterActive = false;
    private String selectedRiskLevel = "ALL";
    private String selectedCity = "ALL";
    private double[] cityCoords;
    private String authToken;
    private String userRole;
    private String username;
    private final AntenneDao dao = new AntenneDao();
    private final Gson gson = new Gson();
    private static final String GOOGLE_API_KEY = "YOUR_GOOGLE_PLACES_API_KEY_HERE";

    private final Map<String, double[]> knownCities = Map.ofEntries(
        Map.entry("Casablanca", new double[]{33.5731, -7.5898}),
        Map.entry("Rabat", new double[]{34.0209, -6.8416}),
        Map.entry("Marrakech", new double[]{31.6295, -8.0083}),
        Map.entry("Fes", new double[]{34.0433, -5.0000}),
        Map.entry("Tangier", new double[]{35.7767, -5.8038}),
        Map.entry("Agadir", new double[]{30.4333, -9.6000}),
        Map.entry("Meknes", new double[]{33.8963, -5.5403}),
        Map.entry("Oujda", new double[]{34.6867, -1.9114}),
        Map.entry("Kenitra", new double[]{34.2614, -6.5803}),
        Map.entry("Tetouan", new double[]{35.5674, -5.4007}),
        Map.entry("Chefchaouen", new double[]{35.168430, -5.275784}),
        Map.entry("Ouarzazate", new double[]{30.933184, -6.939302}),
        Map.entry("Tiznit", new double[]{29.696901, -9.733198}),
        Map.entry("Tinghir", new double[]{31.520464, -5.530234}),
        Map.entry("Essaouira", new double[]{31.506327, -9.754354}),
        Map.entry("Berrechid", new double[]{33.351177, -7.577820}),
        Map.entry("Laayoune", new double[]{27.125286, -13.162500}),
        Map.entry("Berkane", new double[]{34.921410, -2.324295}),
        Map.entry("El Jadida", new double[]{33.233334, -8.500000}),
        Map.entry("Al Hoceima", new double[]{35.1333, -3.9667}),
        Map.entry("Er Rachidia", new double[]{31.9667, -4.3333}),
        Map.entry("Mohammedia", new double[]{33.7333, -7.35}),
        Map.entry("Khemisset", new double[]{33.8333, -6.0167}),
        Map.entry("Khouribga", new double[]{32.9667, -6.9167}),
        Map.entry("Ksar el Kebir", new double[]{35.0, -6.0}),
        Map.entry("Nador", new double[]{35.1667, -0.9667}),
        Map.entry("Ouezzane", new double[]{34.8333, -5.5833}),
        Map.entry("Safi", new double[]{32.3, -9.3333}),
        Map.entry("Settat", new double[]{33.0, -7.6667}),
        Map.entry("Tanger", new double[]{35.8333, -5.8167}),
        Map.entry("Sidi Kacem", new double[]{34.7333, -5.7}),
        Map.entry("Taourirt", new double[]{34.4, -2.5}),
        Map.entry("Taroudant", new double[]{30.4667, -8.8667}),
        Map.entry("Taza", new double[]{34.2167, -4.0}),
        Map.entry("Youssoufia", new double[]{32.2667, -8.05}),
        Map.entry("Kelaat Mgouna", new double[]{31.2167, -6.4333}),
        Map.entry("Ifrane", new double[]{33.5333, -5.1167}),
        Map.entry("Azrou", new double[]{33.4333, -5.2167}),
        Map.entry("Midelt", new double[]{32.6833, -4.75}),
        Map.entry("Khenifra", new double[]{32.9333, -5.6667}),
        Map.entry("Azilal", new double[]{31.9667, -6.5667}),
        Map.entry("Beni Mellal", new double[]{32.35, -6.35}),
        Map.entry("Temara", new double[]{33.9278, -6.9052})
    );

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
        
        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));
        
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
        initializeCityFilter();

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

        // Setup risk filter checkbox
        if (riskFilterCheckBox != null) {
            riskFilterCheckBox.setDisable(false);
            riskFilterCheckBox.setSelected(false);
            riskFilterCheckBox.setOnAction(e -> {
                if ("VIEWER".equals(userRole)) {
                    showAlert("Restricted Feature",
                             "VIEWER role cannot filter by risk level.",
                             Alert.AlertType.WARNING);
                    riskFilterCheckBox.setSelected(false);
                    return;
                }
                riskFilterActive = riskFilterCheckBox.isSelected();
                applyCombinedFilters();
            });
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
                    // Show initial view based on role
                    if ("VIEWER".equals(userRole)) {
                        showAntennasForViewer();
                    } else {
                        showAntennasWithAutoCircles();
                    }
                });
            }
        });

        webView.widthProperty().addListener((obs, old, newVal) -> invalidateMapSize());
        webView.heightProperty().addListener((obs, old, newVal) -> invalidateMapSize());

        loadMap();
        
        // Hide/show elements based on role (will be updated when role is set)
        updateUIForRole();
    }

    private void initializeCityFilter() {
        if (cityComboBox != null) {
            List<String> cityList = new ArrayList<>(knownCities.keySet());
            Collections.sort(cityList);
            cityList.add(0, "ALL");
            ObservableList<String> observableList = FXCollections.observableArrayList(cityList);
            cityComboBox.setItems(observableList);
            cityComboBox.setValue("ALL");
            cityComboBox.setEditable(true);
        }
    }

    @FXML
    private void filterByCity() {
        if (cityComboBox == null) return;
        String city = cityComboBox.getValue();
        
        if (city == null || "ALL".equals(city)) {
            selectedCity = "ALL";
            cityCoords = null;
        } else {
            Optional<double[]> coordsOpt = searchCityCoords(city);
            if (coordsOpt.isPresent()) {
                selectedCity = city;
                cityCoords = coordsOpt.get();
                System.out.println("City found: " + city + " at [" + cityCoords[0] + ", " + cityCoords[1] + "]");
            } else {
                selectedCity = "ALL";
                cityCoords = null;
                showAlert("City not found",
                         "Unable to locate '" + city + "'. Try a known Moroccan city.",
                         Alert.AlertType.WARNING);
                return;
            }
        }
        
        // Apply filters based on role
        if ("VIEWER".equals(userRole)) {
            applyCombinedFiltersForViewer();
        } else {
            applyCombinedFilters();
        }
    }

    private Optional<double[]> searchCityCoords(String query) {
        String queryLower = query.toLowerCase().trim();
        for (Map.Entry<String, double[]> entry : knownCities.entrySet()) {
            if (entry.getKey().toLowerCase().trim().equals(queryLower)) {
                return Optional.of(entry.getValue());
            }
        }
       
        if (GOOGLE_API_KEY.equals("YOUR_GOOGLE_PLACES_API_KEY_HERE")) {
            System.err.println("Clé Google Places non configurée. Utilisez seulement les villes connues.");
            return Optional.empty();
        }
       
        try {
            HttpClient client = HttpClient.newHttpClient();
            String encodedQuery = URLEncoder.encode(query + ", Morocco", StandardCharsets.UTF_8);
            URI uri = URI.create("https://maps.googleapis.com/maps/api/place/textsearch/json?query=" + encodedQuery + "&key=" + GOOGLE_API_KEY);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
           
            if (response.statusCode() == 200) {
                JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                JsonArray results = root.getAsJsonArray("results");
                if (results != null && !results.isEmpty()) {
                    JsonObject place = results.get(0).getAsJsonObject();
                    JsonObject geometry = place.getAsJsonObject("geometry");
                    JsonObject location = geometry.getAsJsonObject("location");
                    double lat = location.get("lat").getAsDouble();
                    double lng = location.get("lng").getAsDouble();
                    return Optional.of(new double[]{lat, lng});
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur recherche ville: " + e.getMessage());
        }
        return Optional.empty();
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dphi = Math.toRadians(lat2 - lat1);
        double dlambda = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dphi / 2) * Math.sin(dphi / 2) +
                   Math.cos(phi1) * Math.cos(phi2) *
                   Math.sin(dlambda / 2) * Math.sin(dlambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void applyCombinedFilters() {
        List<Antenne> result = new ArrayList<>(allData);
        
        // 1. Filtre opérateur : toujours actif
        if (selectedOperator != null && !"ALL".equals(selectedOperator)) {
            result = result.stream()
                .filter(a -> selectedOperator.equals(a.getNetwork()))
                .collect(Collectors.toList());
        }
        
        // 2. Filtre par niveau de risque : UNIQUEMENT si activé
        if (riskFilterActive && selectedRiskLevel != null && !"ALL".equals(selectedRiskLevel)) {
            result = result.stream()
                .filter(a -> matchesRiskLevel(a, selectedRiskLevel))
                .collect(Collectors.toList());
        }
        
        // 3. Filtre par ville (rayon 50km)
        if (selectedCity != null && !"ALL".equals(selectedCity) && cityCoords != null) {
            final double clat = cityCoords[0];
            final double clon = cityCoords[1];
            result = result.stream()
                .filter(a -> haversine(a.getLat(), a.getLon(), clat, clon) < 50000)
                .collect(Collectors.toList());
        }

        filteredData = new ArrayList<>(result);
        
        if (showingHeatmap) {
            showCoverageHeatmap();
        } else {
            if ("VIEWER".equals(userRole)) {
                showAntennasForViewer();
            } else {
                showAntennasWithAutoCircles();
            }
        }

        updateStatusLabel();
    }

    // Method specifically for VIEWER filtering
    private void applyCombinedFiltersForViewer() {
        List<Antenne> result = new ArrayList<>(allData);
        
        // VIEWER can only filter by city (radius 50km)
        if (selectedCity != null && !"ALL".equals(selectedCity) && cityCoords != null) {
            final double clat = cityCoords[0];
            final double clon = cityCoords[1];
            result = result.stream()
                .filter(a -> haversine(a.getLat(), a.getLon(), clat, clon) < 50000)
                .collect(Collectors.toList());
        }

        filteredData = new ArrayList<>(result);
        
        if (showingHeatmap) {
            showCoverageHeatmap();
        } else {
            showAntennasForViewer();
        }

        updateStatusLabelForViewer();
    }

    @FXML
    private void handleLogout() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Logout");
        confirmAlert.setHeaderText("Confirm Logout");
        confirmAlert.setContentText("Are you sure you want to logout?");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            authToken = null;
            userRole = null;
            username = null;
            
            ApiServer.stop();
            
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-view.fxml"));
                Parent root = loader.load();
                
                Stage stage = (Stage) webView.getScene().getWindow();
                Scene scene = new Scene(root, 400, 450);
                stage.setScene(scene);
                stage.setTitle("Network Coverage - Login");
                stage.centerOnScreen();
                stage.show();
                
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
            // Show/hide elements based on role
            switch (userRole.toUpperCase()) {
                case "ADMIN":
                    // ADMIN can see everything
                    if (exportButtonContainer != null) exportButtonContainer.setVisible(true);
                    if (operatorFilterBox != null) operatorFilterBox.setVisible(true);
                    if (riskFilterBox != null) riskFilterBox.setVisible(true);
                    if (cityFilterBox != null) cityFilterBox.setVisible(true);
                    if (apiControlsBox != null) apiControlsBox.setVisible(true);
                    if (riskCheckBoxContainer != null) riskCheckBoxContainer.setVisible(true);
                    if (toggleButton != null) toggleButton.setVisible(true);
                    
                    // Enable all controls
                    if (operatorComboBox != null) operatorComboBox.setDisable(false);
                    if (riskComboBox != null) riskComboBox.setDisable(false);
                    if (cityComboBox != null) cityComboBox.setDisable(false);
                    if (riskFilterCheckBox != null) riskFilterCheckBox.setDisable(false);
                    if (toggleButton != null) toggleButton.setDisable(false);
                    
                    // Reset filters for admin
                    selectedOperator = "ALL";
                    selectedRiskLevel = "ALL";
                    selectedCity = "ALL";
                    riskFilterActive = false;
                    
                    if (operatorComboBox != null) operatorComboBox.setValue("ALL");
                    if (riskComboBox != null) riskComboBox.setValue("ALL");
                    if (cityComboBox != null) cityComboBox.setValue("ALL");
                    if (riskFilterCheckBox != null) riskFilterCheckBox.setSelected(false);
                    
                    break;
                    
                case "OPERATOR":
                    // OPERATOR can see filters but not export
                    if (exportButtonContainer != null) exportButtonContainer.setVisible(false);
                    if (operatorFilterBox != null) operatorFilterBox.setVisible(true);
                    if (riskFilterBox != null) riskFilterBox.setVisible(true);
                    if (cityFilterBox != null) cityFilterBox.setVisible(true);
                    if (apiControlsBox != null) apiControlsBox.setVisible(true);
                    if (riskCheckBoxContainer != null) riskCheckBoxContainer.setVisible(true);
                    if (toggleButton != null) toggleButton.setVisible(true);
                    
                    // Enable filters for OPERATOR
                    if (operatorComboBox != null) operatorComboBox.setDisable(false);
                    if (riskComboBox != null) riskComboBox.setDisable(false);
                    if (cityComboBox != null) cityComboBox.setDisable(false);
                    if (riskFilterCheckBox != null) riskFilterCheckBox.setDisable(false);
                    if (toggleButton != null) toggleButton.setDisable(false);
                    
                    // Reset filters for operator
                    selectedOperator = "ALL";
                    selectedRiskLevel = "ALL";
                    selectedCity = "ALL";
                    riskFilterActive = false;
                    
                    if (operatorComboBox != null) operatorComboBox.setValue("ALL");
                    if (riskComboBox != null) riskComboBox.setValue("ALL");
                    if (cityComboBox != null) cityComboBox.setValue("ALL");
                    if (riskFilterCheckBox != null) riskFilterCheckBox.setSelected(false);
                    
                    break;
                    
                case "VIEWER":
                    // VIEWER can see city filter and toggle button (for heatmap/antennas)
                    // Hide all other controls
                    if (exportButtonContainer != null) exportButtonContainer.setVisible(false);
                    if (operatorFilterBox != null) operatorFilterBox.setVisible(false);
                    if (riskFilterBox != null) riskFilterBox.setVisible(false);
                    if (apiControlsBox != null) apiControlsBox.setVisible(false);
                    if (riskCheckBoxContainer != null) riskCheckBoxContainer.setVisible(false);
                    
                    // Show city filter and toggle button
                    if (cityFilterBox != null) {
                        cityFilterBox.setVisible(true);
                    }
                    
                    if (toggleButton != null) {
                        toggleButton.setVisible(true);
                        toggleButton.setDisable(false);
                    }
                    
                    // Disable/hide all other controls
                    if (operatorComboBox != null) operatorComboBox.setDisable(true);
                    if (riskComboBox != null) riskComboBox.setDisable(true);
                    if (cityComboBox != null) cityComboBox.setDisable(false); // Only city is enabled
                    if (riskFilterCheckBox != null) riskFilterCheckBox.setDisable(true);
                    
                    // Reset any active filters for VIEWER
                    selectedOperator = "ALL";
                    selectedRiskLevel = "ALL";
                    selectedCity = "ALL";
                    riskFilterActive = false;
                    
                    if (riskComboBox != null) riskComboBox.setValue("ALL");
                    if (operatorComboBox != null) operatorComboBox.setValue("ALL");
                    if (cityComboBox != null) cityComboBox.setValue("ALL");
                    if (riskFilterCheckBox != null) riskFilterCheckBox.setSelected(false);
                    
                    // Ensure we're showing antennas view by default
                    showingHeatmap = false;
                    updateToggleButton();
                    
                    // Update label
                    updateStatusLabelForViewer();
                    
                    // Show antennas for VIEWER initially
                    Platform.runLater(() -> {
                        if (mapLoaded) {
                            showAntennasForViewer();
                        }
                    });
                    break;
            }
            
            // Update the toggle button text based on current state
            updateToggleButton();
        });
    }

    private void updateRoleLabel() {
        if (roleLabel != null && userRole != null && username != null) {
            Platform.runLater(() -> {
                roleLabel.setText(username + " [" + userRole + "]");
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

    private void initializeOperatorFilter() {
        if (operatorComboBox != null) {
            Set<String> operators = allData.stream()
                .map(Antenne::getNetwork)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            List<String> operatorList = new ArrayList<>(operators);
            Collections.sort(operatorList);
            operatorList.add(0, "ALL");

            ObservableList<String> observableList = FXCollections.observableArrayList(operatorList);
            operatorComboBox.setItems(observableList);
            operatorComboBox.setValue("ALL");
        }
    }

    private void initializeRiskFilter() {
        if (riskComboBox != null) {
            ObservableList<String> riskLevels = FXCollections.observableArrayList(
                "ALL", "LOW", "MEDIUM", "HIGH", "CRITICAL"
            );
            riskComboBox.setItems(riskLevels);
            riskComboBox.setValue("ALL");
        }
    }

    @FXML
    private void filterByOperator() {
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied",
                     "VIEWER role cannot filter by operator.",
                     Alert.AlertType.WARNING);
            return;
        }
        
        if (operatorComboBox == null) return;
        String selected = operatorComboBox.getValue();

        if (selected == null || "ALL".equals(selected)) {
            selectedOperator = "ALL";
        } else {
            selectedOperator = selected;
        }
        
        applyCombinedFilters();
    }

    @FXML
    private void filterByRiskLevel() {
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied",
                     "VIEWER role cannot filter by risk level.",
                     Alert.AlertType.WARNING);
            return;
        }
        
        if (riskComboBox == null) return;
        String selected = riskComboBox.getValue();
        
        if (selected == null || "ALL".equals(selected)) {
            selectedRiskLevel = "ALL";
        } else {
            selectedRiskLevel = selected;
        }
        
        applyCombinedFilters();
    }

    private boolean matchesRiskLevel(Antenne antenna, String riskLevel) {
        double risk = antenna.getFailureRisk();
        switch (riskLevel) {
            case "LOW": return risk < 0.3;
            case "MEDIUM": return risk >= 0.3 && risk < 0.6;
            case "HIGH": return risk >= 0.6 && risk < 0.8;
            case "CRITICAL": return risk >= 0.8;
            default: return true;
        }
    }

    private void updateStatusLabel() {
        if (antennaCountLabel != null) {
            String operatorText = "ALL".equals(selectedOperator) ? "ALL" : selectedOperator;
            String riskText = "ALL".equals(selectedRiskLevel) ? "" : " • " + selectedRiskLevel;
            String cityText = "ALL".equals(selectedCity) ? "" : " • " + selectedCity;
            String modeText = showingHeatmap ? " • HEATMAP" : " • ANTENNAS";
            antennaCountLabel.setText(filteredData.size() + " antennas (" + operatorText + riskText + cityText + modeText + ")");
        }
    }
    
    // Update status label for VIEWER
    private void updateStatusLabelForViewer() {
        if (antennaCountLabel != null) {
            String cityText = "ALL".equals(selectedCity) ? "All Cities" : "City: " + selectedCity;
            String modeText = showingHeatmap ? " • HEATMAP" : " • ANTENNAS";
            antennaCountLabel.setText("VIEWER MODE • " + filteredData.size() + " antennas • " + cityText + modeText);
        }
    }

    private void updateToggleButton() {
        if (toggleButton != null) {
            toggleButton.setText(showingHeatmap ? "VIEW ANTENNAS" : "VIEW HEATMAP");
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
                
                String geojson = toAntennaGeoJsonWithAutoCircles(filteredData);
                String script = "showAntennasWithAutoCircles(" + geojson + ")";
                System.out.println("Exécution script pour " + filteredData.size() + " antennes (cercles auto)");
                engine.executeScript(script);
                
                if (cityCoords != null) {
                    String zoomScript = String.format(Locale.US, "zoomToCity(%.6f, %.6f, 12);", cityCoords[0], cityCoords[1]);
                    engine.executeScript(zoomScript);
                }
                
            } catch (Exception e) { 
                System.err.println("Erreur affichage antennes: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // Method to show antennas for VIEWER (without risk information)
    private void showAntennasForViewer() {
        if (!mapLoaded) {
            System.out.println("Map not loaded yet, waiting...");
            return;
        }
        
        Platform.runLater(() -> {
            try {
                if (showingHeatmap) {
                    engine.executeScript("if (window.heatmapLayer) { map.removeLayer(window.heatmapLayer); window.heatmapLayer = null; }");
                    showingHeatmap = false;
                    updateToggleButton();
                }
                
                // Generate GeoJSON without risk information
                String geojson = toAntennaGeoJsonForViewer(filteredData);
                String script = "showAntennasForViewer(" + geojson + ")";
                System.out.println("Executing script for " + filteredData.size() + " antennas (Viewer mode)");
                engine.executeScript(script);
                
                if (cityCoords != null) {
                    String zoomScript = String.format(Locale.US, "zoomToCity(%.6f, %.6f, 12);", cityCoords[0], cityCoords[1]);
                    engine.executeScript(zoomScript);
                }
                
            } catch (Exception e) { 
                System.err.println("Error displaying antennas for viewer: " + e.getMessage());
                e.printStackTrace();
            }
        });
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
                
                if (cityCoords != null) {
                    String zoomScript = String.format(Locale.US, "zoomToCity(%.6f, %.6f, 12);", cityCoords[0], cityCoords[1]);
                    engine.executeScript(zoomScript);
                }
            } catch (Exception e) { 
                System.err.println("Erreur affichage heatmap: " + e.getMessage());
            }
        });
    }

    @FXML 
    private void toggleView() {
        if (showingHeatmap) {
            if ("VIEWER".equals(userRole)) {
                showAntennasForViewer();
            } else {
                showAntennasWithAutoCircles();
            }
        } else {
            showCoverageHeatmap();
        }
    }

    @FXML
    private void exportData() {
        if (!"ADMIN".equals(userRole)) {
            showAlert("Permission Denied", 
                     "Only ADMIN users can export data.", 
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

    private String toAntennaGeoJsonWithAutoCircles(List<Antenne> antennes) {
        if (antennes.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
        
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        
        int limit = Math.min(antennes.size(), 5000);
        
        for (int i = 0; i < limit; i++) {
            Antenne a = antennes.get(i);
            
            String clusterColor = riskFilterActive 
                ? getEnhancedRiskColor(a.getFailureRisk())
                : "#3498db";
            
            String riskLevel = a.getRiskLevel();
            
            sb.append(String.format(Locale.US,
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{" +
                "\"signal\":%.1f,\"tech\":\"%s\",\"operator\":\"%s\"," +
                "\"risk\":%.3f,\"riskColor\":\"%s\",\"riskLevel\":\"%s\"," +
                "\"radius_meters\":%.0f,\"lat\":%.6f,\"lng\":%.6f,\"clusterColor\":\"%s\"}}",
                a.getLon(), a.getLat(), 
                a.getAverageSignal(), 
                a.getTechnology(), 
                a.getNetwork(),
                a.getFailureRisk(), 
                clusterColor, 
                riskLevel,
                a.getRange(),
                a.getLat(),
                a.getLon(),
                clusterColor
            ));
            
            if (i < limit - 1) sb.append(",");
        }
        sb.append("]}");
        
        System.out.println("Généré GeoJSON avec " + limit + " antennes (cercles auto)");
        return sb.toString();
    }

    // Generate GeoJSON without risk data for VIEWER
    private String toAntennaGeoJsonForViewer(List<Antenne> antennes) {
        if (antennes.isEmpty()) {
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
        
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        
        int limit = Math.min(antennes.size(), 2000);
        
        for (int i = 0; i < limit; i++) {
            Antenne a = antennes.get(i);
            
            // Use a neutral blue color for all antennas in viewer mode
            String clusterColor = "#3498db"; // Blue color for all
            
            sb.append(String.format(Locale.US,
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{" +
                "\"signal\":%.1f,\"tech\":\"%s\",\"operator\":\"%s\"," +
                "\"radius_meters\":%.0f,\"lat\":%.6f,\"lng\":%.6f,\"clusterColor\":\"%s\"}}",
                a.getLon(), a.getLat(), 
                a.getAverageSignal(), 
                a.getTechnology(), 
                a.getNetwork(),
                a.getRange(),
                a.getLat(),
                a.getLon(),
                clusterColor
            ));
            
            if (i < limit - 1) sb.append(",");
        }
        sb.append("]}");
        
        System.out.println("Generated GeoJSON for viewer with " + limit + " antennas");
        return sb.toString();
    }

    private String getEnhancedRiskColor(double risk) {
        if (risk < 0.3) return "#27ae60";
        if (risk < 0.5) return "#f39c12";
        if (risk < 0.7) return "#e67e22";
        return "#e74c3c";
    }

    private void loadMap() {
        try {
            java.net.URL url = getClass().getResource("/html/map.html");
            if (url != null) {
                System.out.println("Chargement map.html depuis: " + url.toString());
                engine.load(url.toExternalForm());
            } else {
                System.err.println("Fichier map.html non trouvé dans les ressources");
                engine.loadContent("<html><body><h1>Carte des Antennes</h1><p>Chargement en cours...</p></body></html>");
            }
        } catch (Exception e) {
            System.err.println("Erreur chargement map.html: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void testApiConnection() {
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied", 
                     "VIEWER role cannot test API connections.", 
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
        if ("VIEWER".equals(userRole)) {
            showAlert("Permission Denied", 
                     "VIEWER role cannot access API information.", 
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
            ⏱  Token valid for 24 hours
            
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

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}