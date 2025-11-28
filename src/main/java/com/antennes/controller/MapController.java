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
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.*;
import java.util.stream.Collectors;

public class MapController {

    @FXML private WebView webView;
    @FXML private Label antennaCountLabel;
    @FXML private Button toggleButton;
    @FXML private ComboBox<String> operatorComboBox;

    private WebEngine engine;
    private List<Antenne> allData;
    private List<Antenne> filteredData;
    private boolean mapLoaded = false;
    private final CoverageService coverageService = new CoverageService();
    private boolean showingHeatmap = false;
    private String selectedOperator = "ALL";

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

    @FXML
    private void filterByOperator() {
        if (operatorComboBox == null) return;
        String selected = operatorComboBox.getValue();

        if (selected == null || "TOUS".equals(selected)) {
            filteredData = new ArrayList<>(allData);
            selectedOperator = "ALL";
        } else {
            filteredData = dao.findByOperator(selected);
            selectedOperator = selected;
        }

        if (showingHeatmap) showCoverageHeatmap();
        else showAntennas();

        if (antennaCountLabel != null) {
            antennaCountLabel.setText(filteredData.size() + " antennes (" + 
                (selectedOperator.equals("ALL") ? "TOUS" : selectedOperator) + ")");
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

    @FXML private void showAntennas() {
        if (!mapLoaded) return;
        Platform.runLater(() -> {
            try {
                if (showingHeatmap) {
                    engine.executeScript("if (window.heatmapLayer) { map.removeLayer(window.heatmapLayer); window.heatmapLayer = null; }");
                    showingHeatmap = false;
                }
                String geojson = toGeoJsonWithRisk(filteredData);
                engine.executeScript("setAllAntennas(" + geojson + ")");
                updateToggleButton();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @FXML private void showCoverageHeatmap() {
        if (!mapLoaded) return;
        Platform.runLater(() -> {
            try {
                String data = coverageService.generateHeatmapData(filteredData);
                engine.executeScript("if (window.heatmapLayer) map.removeLayer(window.heatmapLayer);");
                engine.executeScript("addHeatmap(" + data + ")");
                showingHeatmap = true;
                if (antennaCountLabel != null) antennaCountLabel.setText("Heatmap (" + selectedOperator + ")");
                updateToggleButton();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @FXML private void toggleView() {
        if (showingHeatmap) showAntennas();
        else showCoverageHeatmap();
    }

    private void loadMap() {
        try {
            java.net.URL url = getClass().getResource("/html/map.html");
            if (url != null) engine.load(url.toExternalForm());
        } catch (Exception e) {
            System.err.println("Erreur chargement map.html: " + e.getMessage());
        }
    }

    // VERSION AVEC RISQUE IA
    private String toGeoJsonWithRisk(List<Antenne> antennes) {
        if (antennes.isEmpty()) return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        int limit = Math.min(antennes.size(), 5000);

        for (int i = 0; i < limit; i++) {
            Antenne a = antennes.get(i);
            double radiusKm = a.getRange() / 1000.0;
            sb.append(String.format(Locale.US,
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{\"signal\":%.1f,\"tech\":\"%s\",\"radius\":%.2f,\"operator\":\"%s\",\"risk\":%.3f,\"riskColor\":\"%s\"}}",
                a.getLon(), a.getLat(), a.getAverageSignal(), a.getTechnology(), radiusKm, a.getNetwork(),
                a.getFailureRisk(), a.getRiskColor()
            ));
            if (i < limit - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }
}