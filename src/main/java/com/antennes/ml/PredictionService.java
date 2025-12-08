package com.antennes.ml;

import com.antennes.model.Antenne;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredictionService {

    // Global risk per quarter from data_for_app.csv (precomputed linear model)
    private static final Map<String, Double> globalRisk = new HashMap<>();

    // Meteo risk per city per quarter from meteo_risk_by_city_quarter.csv
    private static final Map<String, Map<String, Double>> meteoRisk = new HashMap<>();

    // Cities with lat/lon from cities_lat_lon.csv
    private static final List<Map<String, Object>> cities = new ArrayList<>();

    static {
        // Load data_for_app.csv and precompute global risk
        try (BufferedReader br = new BufferedReader(new InputStreamReader(PredictionService.class.getResourceAsStream("/data_for_app.csv")))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                String quarter = "T" + v[1] + "-" + v[0];
                double plaintes = Double.parseDouble(v[2]);
                double debit = Double.parseDouble(v[3]);
                double latence = Double.parseDouble(v[4]);
                double parc_mobile = Double.parseDouble(v[5]);
                double parc_internet = Double.parseDouble(v[6]);

                // Normalized linear model (weights from training on real data)
                double norm_plaintes = plaintes / 311.0;
                double norm_parc_mobile = (parc_mobile - 44000000) / (58751000 - 44000000);
                double norm_parc_internet = (parc_internet - 23000000) / (41184000 - 23000000);
                double norm_debit = 1 - (debit / 101.5);
                double norm_latence = latence / 139.0;

                double risk = 0.3 * norm_plaintes + 0.2 * norm_parc_mobile + 0.2 * norm_parc_internet + 0.15 * norm_debit + 0.15 * norm_latence;
                globalRisk.put(quarter, Math.min(1.0, Math.max(0.0, risk)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load meteo_risk_by_city_quarter.csv
        try (BufferedReader br = new BufferedReader(new InputStreamReader(PredictionService.class.getResourceAsStream("/meteo_risk_by_city_quarter.csv")))) {
            String[] headers = br.readLine().split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                String city = v[0];
                Map<String, Double> risks = new HashMap<>();
                for (int i = 1; i < v.length; i++) {
                    risks.put(headers[i], Double.parseDouble(v[i]));
                }
                meteoRisk.put(city, risks);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Load cities_lat_lon.csv
        try (BufferedReader br = new BufferedReader(new InputStreamReader(PredictionService.class.getResourceAsStream("/cities_lat_lon.csv")))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] v = line.split(",");
                Map<String, Object> city = new HashMap<>();
                city.put("city", v[0]);
                city.put("lat", Double.parseDouble(v[1]));
                city.put("lon", Double.parseDouble(v[2]));
                cities.add(city);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public double predictRisk(Antenne a) {
        String quarter = getCurrentQuarter();  // e.g. "T4-2025"

        // Global risk
        double risk = globalRisk.getOrDefault(quarter, 0.5);

        // Local meteo risk
        String city = getClosestCity(a.getLat(), a.getLon());
        double meteo = meteoRisk.getOrDefault(city, new HashMap<>()).getOrDefault(quarter, 0.0);
        risk += meteo;

        // Your original rules (signal, range, tech)
        if (a.getAverageSignal() < -100) risk += 0.2;
        else if (a.getAverageSignal() < -90) risk += 0.15;
        else if (a.getAverageSignal() < -80) risk += 0.1;
        if (a.getRange() > 6000) risk += 0.18;
        if (a.getRange() > 3000) risk += 0.1;
        if ("2G".equals(a.getRadio())) risk += 0.12;
        if ("3G".equals(a.getRadio())) risk += 0.08;

        // Geo bonus from original (hardcoded regions)
        double lat = a.getLat(), lon = a.getLon();
        if ((lat >= 33.5 && lat <= 33.7 && lon >= -7.8 && lon <= -7.4) ||
            (lat >= 33.9 && lat <= 34.1 && lon >= -6.9 && lon <= -6.7)) risk += 0.1;

        return Math.min(1.0, Math.max(0.0, risk));
    }

    private String getCurrentQuarter() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int q = (now.getMonthValue() - 1) / 3 + 1;
        return "T" + q + "-" + year;
    }

    private String getClosestCity(double lat, double lon) {
        double minDist = Double.MAX_VALUE;
        String closest = "Casablanca";  // fallback
        for (Map<String, Object> c : cities) {
            double clat = (double) c.get("lat");
            double clon = (double) c.get("lon");
            double d = haversine(lat, lon, clat, clon);
            if (d < minDist) {
                minDist = d;
                closest = (String) c.get("city");
            }
        }
        return closest;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}