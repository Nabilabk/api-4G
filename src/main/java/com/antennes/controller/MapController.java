package com.antennes.controller;

import com.antennes.model.Antenne;
import com.antennes.service.CsvService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.List;
import java.util.Locale;

public class MapController {

    @FXML private WebView webView;
    @FXML private Label antennaCountLabel;

    private WebEngine engine;
    private List<Antenne> allData;
    private boolean mapLoaded = false;

    @FXML
    public void initialize() {
        // Enable context menu and interactions on WebView
        webView.setContextMenuEnabled(true);

        engine = webView.getEngine();

        // Enable JavaScript (should be enabled by default, but let's be explicit)
        engine.setJavaScriptEnabled(true);

        // Enable console logging from JavaScript
        engine.setOnAlert(event -> System.out.println("JS Alert: " + event.getData()));

        // Log JavaScript errors
        engine.setOnError(event -> {
            System.err.println("WebView Error: " + event.getMessage());
        });

        // Set user agent to avoid potential blocking
        engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        allData = CsvService.loadFromResources();
        System.out.println("ANTENNES CHARGÉES: " + allData.size());

        // Update antenna count label
        if (antennaCountLabel != null) {
            antennaCountLabel.setText(allData.size() + " antennes 4G");
        }

        // Single listener for load worker state
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            System.out.println("WebView state changed: " + oldState + " -> " + newState);

            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("Carte chargée avec succès !");

                // Inject console.log interceptor to capture JavaScript logs
                try {
                    engine.executeScript(
                        "var originalLog = console.log;" +
                        "var originalError = console.error;" +
                        "console.log = function(message) { " +
                        "    originalLog(message); " +
                        "};" +
                        "console.error = function(message) { " +
                        "    originalError(message); " +
                        "};"
                    );
                    System.out.println("Console interceptor injected");
                } catch (Exception e) {
                    System.err.println("Error injecting console: " + e.getMessage());
                }

                mapLoaded = true;

                // Wait a bit for the map to fully initialize before loading data
                Platform.runLater(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    invalidateMapSize();
                    loadData();
                });
            } else if (newState == Worker.State.FAILED) {
                System.err.println("Failed to load map!");
                Throwable exception = engine.getLoadWorker().getException();
                if (exception != null) {
                    exception.printStackTrace();
                }
            }
        });

        webView.widthProperty().addListener((obs, old, newVal) -> invalidateMapSize());
        webView.heightProperty().addListener((obs, old, newVal) -> invalidateMapSize());

        // Load map last
        loadMap();
    }

    private void invalidateMapSize() {
        if (mapLoaded) {
            Platform.runLater(() -> {
                try {
                    // For Canvas-based map, we need to trigger resize
                    engine.executeScript("if (typeof resizeCanvas === 'function') { resizeCanvas(); console.log('Map resized'); }");
                } catch (Exception e) {
                    System.err.println("Error invalidating map size: " + e.getMessage());
                }
            });
        }
    }

    @FXML
    private void loadData() {
        if (!mapLoaded) return;

        Platform.runLater(() -> {
            // All data is 4G, no filtering needed
            System.out.println("Préparation de " + allData.size() + " antennes 4G pour chargement dynamique");

            String geojson = toGeoJson(allData);
            try {
                // Send data to JavaScript for dynamic loading based on viewport
                engine.executeScript("setAllAntennas(" + geojson + ")");
                System.out.println("Données envoyées ! (" + allData.size() + " antennes 4G disponibles)");
            } catch (Exception e) {
                System.err.println("Erreur GeoJSON: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void loadMap() {
        try {
            // Load HTML from resources - this works better with external resources in JavaFX WebView
            java.net.URL resourceUrl = getClass().getResource("/html/map.html");
            if (resourceUrl == null) {
                System.err.println("ERROR: Cannot find /html/map.html in resources!");
                System.err.println("Trying to list resources...");
                java.net.URL htmlDir = getClass().getResource("/html/");
                if (htmlDir != null) {
                    System.out.println("Found /html/ directory: " + htmlDir);
                } else {
                    System.err.println("/html/ directory not found!");
                }
                return;
            }

            String mapUrl = resourceUrl.toExternalForm();
            System.out.println("Loading map from: " + mapUrl);
            engine.load(mapUrl);
            System.out.println("Map load initiated");
        } catch (Exception e) {
            System.err.println("Error loading map HTML: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String toGeoJson(List<Antenne> antennes) {
        if (antennes.isEmpty()) return "{\"type\":\"FeatureCollection\",\"features\":[]}";

        // Pre-allocate StringBuilder with estimated size for better performance
        int estimatedSize = antennes.size() * 150; // Approximate size per feature
        StringBuilder sb = new StringBuilder(estimatedSize);
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");

        for (int i = 0; i < antennes.size(); i++) {
            Antenne a = antennes.get(i);
            double radiusKm = a.getRange() / 1000.0;
            int signal = (int) a.getAverageSignal();
            if (signal == 0) signal = generateSignal(a.getTechnology());

            // Optimized: Use Locale.US to ensure decimal points (not commas) in JSON
            sb.append(String.format(Locale.US,
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]},\"properties\":{\"signal\":%d,\"tech\":\"%s\",\"radius\":%.1f,\"couverture\":\"%s\"}}",
                a.getLon(), a.getLat(), signal, a.getTechnology(), radiusKm, a.getCouverture()
            ));
            if (i < antennes.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    private int generateSignal(String tech) {
        return switch (tech) {
            case "2G" -> -100 + (int)(Math.random() * 45);
            case "3G" -> -95 + (int)(Math.random() * 40);
            case "4G" -> -90 + (int)(Math.random() * 45);
            case "5G" -> -85 + (int)(Math.random() * 50);
            default -> -85 + (int)(Math.random() * 40);
        };
    }
}