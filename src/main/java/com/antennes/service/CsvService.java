package com.antennes.service;

import com.antennes.model.Antenne;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CsvService {

    public static List<Antenne> loadFromResources() {
        long startTime = System.currentTimeMillis();
        // Pre-allocate with estimated capacity for better performance
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
                    // Parse only necessary fields for better performance
                    double lat = Double.parseDouble(line[0]);
                    double lon = Double.parseDouble(line[1]);
                    double signal = (line[2] != null && !line[2].isEmpty()) ? Double.parseDouble(line[2]) : 0;
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
                    if (errorCount <= 5) { // Only log first 5 errors
                        System.err.println("Erreur ligne " + lineNumber + ": " + e.getMessage());
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Chargement CSV terminé en " + (endTime - startTime) + "ms");
            if (errorCount > 0) {
                System.err.println("Total erreurs ignorées: " + errorCount);
            }

        } catch (IOException | CsvException e) {
            System.err.println("Erreur critique lors du chargement du CSV: " + e.getMessage());
            e.printStackTrace();
        }

        return antennes;
    }
}