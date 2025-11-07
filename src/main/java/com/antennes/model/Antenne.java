package com.antennes.model;

public class Antenne {
    private final double lat;
    private final double lon;
    private final double averageSignal;
    private final String radio;
    private final String network;
    private final int mcc;
    private final int mnc;
    private final int tac;
    private final int cid;
    private final double range;

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

    public String getCouverture() {
        if (range >= 1000) return "Forte";
        else if (range >= 500) return "Moyenne";
        else return "Faible";
    }

    public String getTechnology() {
        return switch (radio) {
            case "GSM" -> "2G";
            case "UMTS" -> "3G";
            case "LTE" -> "4G";
            case "NR" -> "5G";
            default -> radio != null ? radio : "4G";
        };
    }

    @Override
    public String toString() {
        return String.format("Antenne{lat=%.6f, lon=%.6f, range=%.0fm, couverture='%s', op='%s'}",
                lat, lon, range, getCouverture(), network);
    }
}