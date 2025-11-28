package com.antennes.controller;

import com.antennes.database.AntenneDao;
import com.antennes.ml.PredictionService;
import com.antennes.model.Antenne;
import com.antennes.service.CoverageService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class MapController {

    @FXML private WebView webView;
    @FXML private Label antennaCountLabel;
    @FXML private Button toggleButton;
    @FXML private ComboBox<String> operatorComboBox;
    @FXML private ComboBox<String> riskComboBox;

    private WebEngine engine;
    private List<Antenne> allData;
    private List<Antenne> filteredData;
    private boolean mapLoaded = false;
    private final CoverageService coverageService = new CoverageService();
    private boolean showingHeatmap = false;
    private String selectedOperator = "ALL";
    private String selectedRiskLevel = "ALL";

    private final AntenneDao dao = new AntenneDao();

    @FXML
    public void initialize() {
        System.out.println("=== INITIALISATION MAPCONTROLLER (SQLite + IA Prédiction) ===");

        webView.setContextMenuEnabled(true);
        engine = webView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setOnAlert(event -> System.out.println("JS ALERT: " + event.getData()));

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
            antennaCountLabel.setText("IA Active – " + allData.size() + " antennes");
        }

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("Map loaded successfully");
                mapLoaded = true;
                updateToggleButton();
                Platform.runLater(() -> {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    invalidateMapSize();
                    showAntennas();
                });
            }
        });

        webView.widthProperty().addListener((obs, old, newVal) -> invalidateMapSize());
        webView.heightProperty().addListener((obs, old, newVal) -> invalidateMapSize());

        loadMap();
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
        else showAntennas();

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
            toggleButton.setText(showingHeatmap ? "See Antennas" : "See Heatmap");
            toggleButton.setStyle(showingHeatmap 
                ? "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;"
                : "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    private void invalidateMapSize() {
        if (mapLoaded) {
            Platform.runLater(() -> {
                try { engine.executeScript("if (typeof resizeCanvas === 'function') resizeCanvas();"); }
                catch (Exception ignored) {}
            });
        }
    }

    @FXML 
    private void showAntennas() {
        if (!mapLoaded) return;
        Platform.runLater(() -> {
            try {
                if (showingHeatmap) {
                    engine.executeScript("if (window.heatmapLayer) { map.removeLayer(window.heatmapLayer); window.heatmapLayer = null; }");
                    showingHeatmap = false;
                }
                String geojson = toEnhancedGeoJsonWithRisk(filteredData);
                engine.executeScript("setAllAntennas(" + geojson + ")");
                updateToggleButton();
            } catch (Exception e) { e.printStackTrace(); }
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
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @FXML 
    private void toggleView() {
        if (showingHeatmap) showAntennas();
        else showCoverageHeatmap();
    }

    @FXML
    private void exportData() {
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

    private String toEnhancedGeoJsonWithRisk(List<Antenne> antennes) {
        if (antennes.isEmpty()) return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        int limit = Math.min(antennes.size(), 5000);

        for (int i = 0; i < limit; i++) {
            Antenne a = antennes.get(i);
            double radiusKm = a.getRange() / 1000.0;
            
            String riskColor = getEnhancedRiskColor(a.getFailureRisk());
            int riskSize = getRiskSize(a.getFailureRisk());
            String riskPattern = getRiskPattern(a.getFailureRisk());
            
            sb.append(String.format(Locale.US,
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{" +
                "\"signal\":%.1f,\"tech\":\"%s\",\"radius\":%.2f,\"operator\":\"%s\"," +
                "\"risk\":%.3f,\"riskColor\":\"%s\",\"riskSize\":%d,\"riskPattern\":\"%s\"," +
                "\"riskLevel\":\"%s\"}}",
                a.getLon(), a.getLat(), a.getAverageSignal(), a.getTechnology(), radiusKm, a.getNetwork(),
                a.getFailureRisk(), riskColor, riskSize, riskPattern, a.getRiskLevel()
            ));
            if (i < limit - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
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
            java.net.URL url = getClass().getResource("/html/map.html");
            if (url != null) engine.load(url.toExternalForm());
        } catch (Exception e) {
            System.err.println("Erreur chargement map.html: " + e.getMessage());
        }
    }
}