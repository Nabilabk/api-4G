package com.antennes.ml;

import com.antennes.model.Antenne;
import java.util.Random;

public class PredictionService {
    private static final Random rnd = new Random(42);

    public double predictRisk(Antenne a) {
        double risk = 0.0;

        if (a.getAverageSignal() < -100) risk += 0.48;
        else if (a.getAverageSignal() < -90) risk += 0.35;
        else if (a.getAverageSignal() < -80) risk += 0.20;

        if (a.getRange() > 6000) risk += 0.40;
        else if (a.getRange() > 3000) risk += 0.25;

        if ("2G".equals(a.getTechnology())) risk += 0.35;
        if ("3G".equals(a.getTechnology())) risk += 0.28;

        double lat = a.getLat(), lon = a.getLon();
        if ((lat >= 33.5 && lat <= 33.7 && lon >= -7.8 && lon <= -7.4) ||
            (lat >= 33.9 && lat <= 34.1 && lon >= -6.9 && lon <= -6.7)) risk += 0.22;

        risk += rnd.nextDouble() * 0.18;
        return Math.min(1.0, Math.max(0.0, risk));
    }
}