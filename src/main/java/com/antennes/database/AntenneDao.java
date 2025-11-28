package com.antennes.database;

import com.antennes.model.Antenne;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AntenneDao {

    public void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS antennes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                average_signal REAL,
                radio TEXT,
                network TEXT,
                mcc INTEGER,
                mnc INTEGER,
                tac INTEGER,
                cid INTEGER,
                range_m REAL,
                technology TEXT GENERATED ALWAYS AS (
                    CASE radio
                        WHEN 'GSM' THEN '2G'
                        WHEN 'UMTS' THEN '3G'
                        WHEN 'LTE' THEN '4G'
                        WHEN 'NR' THEN '5G'
                        ELSE COALESCE(radio, '4G')
                    END
                ) VIRTUAL,
                couverture TEXT GENERATED ALWAYS AS (
                    CASE
                        WHEN range_m >= 1000 THEN 'Forte'
                        WHEN range_m >= 500 THEN 'Moyenne'
                        ELSE 'Faible'
                    END
                ) VIRTUAL
            );
            CREATE INDEX IF NOT EXISTS idx_network ON antennes(network);
            CREATE INDEX IF NOT EXISTS idx_signal ON antennes(average_signal);
            """;

        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Table antennes + indexes ready");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Run once – imports CSV only if DB is empty
    public void importCsvIfEmpty() {
        try (Connection conn = Database.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM antennes")) {

            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("Database already contains " + rs.getInt(1) + " antennas → skip import");
                return;
            }
        } catch (SQLException ignored) {}

        System.out.println("First start detected → importing CSV into SQLite...");
        long start = System.currentTimeMillis();

        String sql = """
            INSERT INTO antennes 
            (lat, lon, average_signal, radio, network, mcc, mnc, tac, cid, range_m)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            var list = com.antennes.service.CsvService.loadFromResources(); // reuse your existing parser

            conn.setAutoCommit(false);
            for (Antenne a : list) {
                ps.setDouble(1, a.getLat());
                ps.setDouble(2, a.getLon());
                ps.setDouble(3, a.getAverageSignal());
                ps.setString(4, a.getRadio());
                ps.setString(5, a.getNetwork());
                ps.setInt(6, a.getMcc());
                ps.setInt(7, a.getMnc());
                ps.setInt(8, a.getTac());
                ps.setInt(9, a.getCid());
                ps.setDouble(10, a.getRange());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();

            System.out.println("Import completed: " + list.size() + " antennas in " +
                    (System.currentTimeMillis() - start) + " ms");
        } catch (SQLException e) {
            throw new RuntimeException("Import failed", e);
        }
    }

    public List<Antenne> findAll() {
        return findByOperator(null);
    }

    public List<Antenne> findByOperator(String operator) {
        String sql = "SELECT lat, lon, average_signal, radio, network, mcc, mnc, tac, cid, range_m " +
                     "FROM antennes";
        if (operator != null && !operator.equals("TOUS") && !operator.isEmpty()) {
            sql += " WHERE network = ?";
        }

        List<Antenne> result = new ArrayList<>();

        try (Connection conn = Database.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (operator != null && !operator.equals("TOUS") && !operator.isEmpty()) {
                ps.setString(1, operator);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Antenne(
                        rs.getDouble("lat"),
                        rs.getDouble("lon"),
                        rs.getDouble("average_signal"),
                        rs.getString("radio"),
                        rs.getString("network"),
                        rs.getInt("mcc"),
                        rs.getInt("mnc"),
                        rs.getInt("tac"),
                        rs.getInt("cid"),
                        rs.getDouble("range_m")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}