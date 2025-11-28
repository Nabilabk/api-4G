package com.antennes.service;

import com.antennes.model.Antenne;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CsvService {

    private static final Random random = new Random();

    public static List<Antenne> loadFromResources() {
        long startTime = System.currentTimeMillis();
        List<Antenne> antennes = new ArrayList<>(31000);

        try (CSVReader reader = new CSVReader(new BufferedReader(
                new InputStreamReader(CsvService.class.getResourceAsStream("/antennes_4G.csv")), 8192))) {

            String[] headers = reader.readNext();
            if (headers == null) {
                return antennes;
            }

            String[] line;
            int errorCount = 0;
            int lineNumber = 1;

            while ((line = reader.readNext()) != null) {
                lineNumber++;
                try {
                    double lat = Double.parseDouble(line[0]);
                    double lon = Double.parseDouble(line[1]);
                    
                    // Générer un signal réaliste
                    double signal = generateRealisticSignal(lat, lon, line[4]);
                    
                    String radio = line[3];
                    String network = line[4];
                    int mcc = Integer.parseInt(line[5]);
                    int mnc = Integer.parseInt(line[6]);
                    int tac = Integer.parseInt(line[7]);
                    int cid = Integer.parseInt(line[8]);
                    double range = (line[9] != null && !line[9].isEmpty()) ? Double.parseDouble(line[9]) : 1000;

                    antennes.add(new Antenne(lat, lon, signal, radio, network, mcc, mnc, tac, cid, range));
                } catch (Exception e) {
                    errorCount++;
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Chargement CSV terminé en " + (endTime - startTime) + "ms");
            System.out.println("📊 " + antennes.size() + " antennes chargées avec signaux réalistes");
            if (errorCount > 0) {
                System.err.println("Erreurs ignorées: " + errorCount);
            }

        } catch (IOException | CsvException e) {
            System.err.println("Erreur chargement CSV: " + e.getMessage());
        }

        return antennes;
    }

    private static double generateRealisticSignal(double lat, double lon, String operator) {
        double baseSignal;
        switch (operator != null ? operator.toLowerCase() : "iam") {
            case "iam":
                baseSignal = -65;
                break;
            case "orange":
                baseSignal = -70;
                break;
            case "inwi":
                baseSignal = -75;
                break;
            default:
                baseSignal = -72;
        }
        
        double regionalVariation = 0;
        if (lat > 34.0) {
            regionalVariation = -2;
        } else if (lat > 33.0 && lon > -7.0) {
            regionalVariation = -5;
        } else if (lat > 32.0) {
            regionalVariation = -3;
        } else {
            regionalVariation = 2;
        }
        
        double randomVariation = (random.nextDouble() - 0.5) * 30;
        double finalSignal = baseSignal + regionalVariation + randomVariation;
        
        return Math.max(-95, Math.min(-50, finalSignal));
    }
}