package com.antennes.service;

import com.antennes.model.Antenne;
import java.util.*;
import java.util.stream.Collectors;

public class CoverageService {
    
    private static final Random random = new Random();
    
    public String generateHeatmapData(List<Antenne> antennas) {
        System.out.println("=== GÉNÉRATION HEATMAP COMPLÈTE ===");
        
        StringBuilder heatmapData = new StringBuilder();
        heatmapData.append("[");
        
        int pointCount = 0;
        
        // 1. POINTS AUTOUR DES GRANDES VILLES (toujours visibles)
        double[][] cityPoints = {
            // Casablanca - différentes intensités
            {33.5731, -7.5898, 0.9}, {33.5781, -7.5948, 0.8}, {33.5681, -7.5848, 0.7},
            {33.5831, -7.5998, 0.6}, {33.5631, -7.5798, 0.5}, {33.5881, -7.6048, 0.4},
            {33.5581, -7.5748, 0.3}, {33.5931, -7.6098, 0.2},
            
            // Rabat
            {34.0209, -6.8416, 0.8}, {34.0259, -6.8466, 0.7}, {34.0159, -6.8366, 0.6},
            {34.0109, -6.8316, 0.5}, {34.0309, -6.8516, 0.4},
            
            // Marrakech
            {31.6295, -8.0089, 0.7}, {31.6345, -8.0139, 0.6}, {31.6245, -8.0039, 0.5},
            {31.6195, -7.9989, 0.4}, {31.6395, -8.0189, 0.3},
            
            // Fès
            {33.8869, -5.5536, 0.6}, {33.8919, -5.5586, 0.5}, {33.8819, -5.5486, 0.4},
            
            // Tanger
            {35.7595, -5.8340, 0.7}, {35.7645, -5.8390, 0.6}, {35.7545, -5.8290, 0.5},
            
            // Agadir
            {30.4270, -9.5981, 0.5}, {30.4320, -9.6031, 0.4}, {30.4220, -9.5931, 0.3},
            
            // Safi
            {32.2995, -9.2372, 0.4}, {32.3045, -9.2422, 0.3}, {32.2945, -9.2322, 0.2},
            
            // Autres villes
            {34.2500, -6.6000, 0.5}, {33.0000, -7.6200, 0.4}, {32.0000, -6.0000, 0.3}
        };
        
        for (double[] point : cityPoints) {
            if (pointCount > 0) heatmapData.append(",");
            heatmapData.append(String.format(Locale.US, "[%.6f,%.6f,%.2f]", 
                point[0], point[1], point[2]));
            pointCount++;
        }
        
        // 2. POINTS BASÉS SUR LES ANTENNES RÉELLES
        if (!antennas.isEmpty()) {
            List<Antenne> sampleAntennas = new ArrayList<>(antennas);
            Collections.shuffle(sampleAntennas);
            
            int sampleSize = Math.min(sampleAntennas.size(), 200);
            
            for (int i = 0; i < sampleSize; i++) {
                Antenne antenna = sampleAntennas.get(i);
                if (antenna.getLat() == 0 && antenna.getLon() == 0) continue;
                
                // Générer plusieurs points autour de chaque antenne
                for (int j = 0; j < 5; j++) {
                    if (pointCount > 0) heatmapData.append(",");
                    
                    double radius = 0.015; // 1.5km
                    double lat = antenna.getLat() + (random.nextDouble() - 0.5) * radius;
                    double lon = antenna.getLon() + (random.nextDouble() - 0.5) * radius;
                    
                    // Intensité basée sur le signal réel
                    double intensity = convertSignalToIntensity(antenna.getAverageSignal());
                    intensity = Math.min(1.0, Math.max(0.1, intensity * (0.7 + random.nextDouble() * 0.6)));
                    
                    heatmapData.append(String.format(Locale.US, "[%.6f,%.6f,%.2f]", 
                        lat, lon, intensity));
                    pointCount++;
                    
                    if (pointCount >= 800) break;
                }
                if (pointCount >= 800) break;
            }
        }
        
        heatmapData.append("]");
        
        System.out.println("✅ " + pointCount + " points heatmap générés");
        return heatmapData.toString();
    }
    // In CoverageService.java - add this method if you want operator-specific heatmaps
public String generateHeatmapData(List<Antenne> antennas, String operator) {
    if (operator != null && !operator.equals("ALL")) {
        antennas = antennas.stream()
            .filter(a -> operator.equals(a.getNetwork()))
            .collect(Collectors.toList());
    }
    return generateHeatmapData(antennas);
}
    private double convertSignalToIntensity(double signal) {
        if (signal == 0) return 0.5;
        
        // Conversion dBm -> intensité
        if (signal >= -70) return 0.9;
        if (signal >= -80) return 0.7;
        if (signal >= -90) return 0.4;
        return 0.2;
    }
}