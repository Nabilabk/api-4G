package com.antennes;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        try {
            // Show login screen first
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 500);
            stage.setTitle("Network Coverage - Login");
            stage.setScene(scene);
            stage.show();
            
            // Stop API server when app closes
            stage.setOnCloseRequest(e -> {
                try {
                    com.antennes.api.ApiServer.stop();
                } catch (Exception ex) {
                    // Ignore
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start application: " + e.getMessage());
            showErrorDialog(e);
        }
    }
    
    private void showErrorDialog(Exception e) {
        try {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Application Error");
            alert.setHeaderText("Failed to start application");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        } catch (Exception ex) {
            // If JavaFX fails completely, print to console
            System.err.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            System.setProperty("prism.order", "sw");
            System.setProperty("prism.verbose", "true");
            
            launch(args);
        } catch (Exception e) {
            System.err.println("Application failed to launch: " + e.getMessage());
            e.printStackTrace();
        }
    }
}