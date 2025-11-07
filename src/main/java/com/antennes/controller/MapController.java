package com.antennes.controller;

import com.antennes.model.Antenne;
import com.antennes.service.CsvService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.List;
import java.util.stream.Collectors;

public class MapController {

    @FXML private WebView webView;
    @FXML private ComboBox<String> techFilter;

    private WebEngine engine;
    private List<Antenne> allData;
    private boolean mapLoaded = false;

    @FXML
    public void initialize() {
        engine = webView.getEngine();
        loadMap();

        allData = CsvService.loadFromResources();
        System.out.println("ANTENNES CHARGÉES: " + allData.size());

        techFilter.setItems(FXCollections.observableArrayList("All", "2G", "3G", "4G", "5G"));
        techFilter.setValue("All");

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                System.out.println("Carte chargée avec succès !");
                mapLoaded = true;
                loadData();
            }
        });

        webView.widthProperty().addListener((obs, old, newVal) -> invalidateMapSize());
        webView.heightProperty().addListener((obs, old, newVal) -> invalidateMapSize());
    }

    private void invalidateMapSize() {
        if (mapLoaded) {
            Platform.runLater(() -> engine.executeScript("if (map) map.invalidateSize();"));
        }
    }

    @FXML
    private void loadData() {
        if (!mapLoaded) return;

        Platform.runLater(() -> {
            List<Antenne> data = allData;
            String filter = techFilter.getValue();
            if (filter != null && !filter.equals("All")) {
                data = data.stream()
                        .filter(a -> filter.equals(a.getTechnology()))
                        .collect(Collectors.toList());
            }

            String geojson = toGeoJson(data);
            try {
                engine.executeScript("addCoverage(" + geojson + ")");
                System.out.println("GeoJSON envoyé ! (" + data.size() + " antennes)");
            } catch (Exception e) {
                System.err.println("Erreur GeoJSON: " + e.getMessage());
            }
        });
    }

    private void loadMap() {
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    let map, layer;
                    document.addEventListener('DOMContentLoaded', () => {
                        map = L.map('map').setView([31.7917, -7.0926], 6);
                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                            attribution: '&copy; OpenStreetMap'
                        }).addTo(map);

                        window.addCoverage = function(geojson) {
                            if (layer) map.removeLayer(layer);
                            if (!geojson.features || geojson.features.length === 0) return;

                            layer = L.geoJSON(geojson, {
                                pointToLayer: (f, latlng) => {
                                    const s = f.properties.signal;
                                    const c = s > -80 ? '#00ff00' : s > -100 ? '#ffff00' : '#ff0000';
                                    return L.circleMarker(latlng, {
                                        radius: 6,
                                        fillColor: c,
                                        color: '#000',
                                        weight: 1,
                                        fillOpacity: 0.8
                                    }).bindPopup(
                                        `<b>${f.properties.tech}</b><br>` +
                                        `Signal: ${s} dBm<br>` +
                                        `Rayon: ${f.properties.radius} km<br>` +
                                        `<b>Couverture: ${f.properties.couverture}</b>`
                                    );
                                }
                            }).addTo(map);
                            map.fitBounds(layer.getBounds(), { padding: [30, 30] });
                        };
                    });
                </script>
            </body>
            </html>
            """;
        engine.loadContent(html);
    }

    private String toGeoJson(List<Antenne> antennes) {
        if (antennes.isEmpty()) return "{\"type\":\"FeatureCollection\",\"features\":[]}";

        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < antennes.size(); i++) {
            Antenne a = antennes.get(i);
            double radiusKm = a.getRange() / 1000.0;
            int signal = (int) a.getAverageSignal();
            if (signal == 0) signal = generateSignal(a.getTechnology());

            sb.append(String.format(
                "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[%f,%f]},\"properties\":{\"signal\":%d,\"tech\":\"%s\",\"radius\":%.1f,\"couverture\":\"%s\"}}",
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