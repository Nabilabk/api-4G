package com.antennes.model;

public class Antenne {
    private final double lat;
    private final double lon;
    private final double averageSignal;
    private final String radio;
    private final String network;
    private final int mcc, mnc, tac, cid;
    private final double range;

    // Nouveaux champs pour l'IA
    private double failureRisk = 0.0;
    private String riskLevel = "Sain";

    public Antenne(double lat, double lon, double averageSignal, String radio, String network,
                   int mcc, int mnc, int tac, int cid, double range) {
        this.lat = lat;
        this.lon = lon;
        this.averageSignal = averageSignal;
        this.radio = radio;
        this.network = network;
        this.mcc = mcc;
        this.mnc = mnc;
        this.tac = tac;
        this.cid = cid;
        this.range = range;
    }

    // Getters existants
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getAverageSignal() { return averageSignal; }
    public String getRadio() { return radio; }
    public String getNetwork() { return network; }
    public int getMcc() { return mcc; }
    public int getMnc() { return mnc; }
    public int getTac() { return tac; }
    public int getCid() { return cid; }
    public double getRange() { return range; }

    public String getTechnology() {
        return switch (radio) {
            case "GSM" -> "2G";
            case "UMTS" -> "3G";
            case "LTE" -> "4G";
            case "NR" -> "5G";
            default -> radio != null ? radio : "4G";
        };
    }

    // Nouvelles méthodes IA
    public double getFailureRisk() { return failureRisk; }
    public void setFailureRisk(double risk) {
        this.failureRisk = risk;
        this.riskLevel = risk < 0.3 ? "Sain" :
                         risk < 0.6 ? "Risque modéré" : "Panne probable";
    }
    public String getRiskLevel() { return riskLevel; }
    public String getRiskColor() {
        if (failureRisk < 0.3) return "#2ecc71";     // vert
        if (failureRisk < 0.6) return "#f39c12";     // orange
        return "#e74c3c";                          // rouge
    }

    @Override
    public String toString() {
        return String.format("Antenne{%.6f,%.6f | %s | %.1f dBm | risk=%.1f%%}",
                lat, lon, network, averageSignal, failureRisk*100);
    }
}