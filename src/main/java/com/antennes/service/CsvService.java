package com.antennes.service;

import com.antennes.model.Antenne;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CsvService {

    public static List<Antenne> loadFromResources() {
        List<Antenne> antennes = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                CsvService.class.getResourceAsStream("/Moroccan_towers.csv")))) {

            String[] headers = reader.readNext();
            if (headers == null)
                return antennes;

            String[] line;
            while ((line = reader.readNext()) != null) {
                try {
                    double lat = Double.parseDouble(line[0]);
                    double lon = Double.parseDouble(line[1]);
                    double signal = line[2] != null && !line[2].isEmpty() ? Double.parseDouble(line[2]) : 0;
                    String radio = line[3];
                    String network = line[4];
                    int mcc = Integer.parseInt(line[5]);
                    int mnc = Integer.parseInt(line[6]);
                    int tac = Integer.parseInt(line[7]);
                    int cid = Integer.parseInt(line[8]);
                    double range = line[9] != null && !line[9].isEmpty() ? Double.parseDouble(line[9]) : 1000;

                    antennes.add(new Antenne(lat, lon, signal, radio, network, mcc, mnc, tac, cid, range));
                } catch (Exception e) {
                    System.err.println("Erreur ligne CSV: " + String.join(",", line));
                }
            }
        } catch (IOException | CsvException e) {
            e.printStackTrace();
        }
        return antennes;
    }
}