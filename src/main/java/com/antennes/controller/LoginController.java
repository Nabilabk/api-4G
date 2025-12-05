package com.antennes.controller;

import com.antennes.api.ApiServer;
import com.antennes.security.AuthManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.IOException;

public class LoginController {
    
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;
    
    private static String authToken;
    private static String userRole;
    private static String username;
    
    @FXML
    public void initialize() {
        errorLabel.setVisible(false);
        
        // Allow login with Enter key
        passwordField.setOnAction(e -> handleLogin());
        
        // Center the window
        Platform.runLater(() -> {
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.centerOnScreen();
        });
    }
    
    @FXML
    private void handleLogin() {
        username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password");
            return;
        }
        
        authToken = AuthManager.authenticate(username, password);
        
        if (authToken != null) {
            userRole = AuthManager.getRoleFromToken(authToken);
            
            // Start API server
            ApiServer.start();
            
            // Launch main application
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/map-view.fxml"));
                Parent root = loader.load();
                
                // Pass auth token and role to main controller
                MapController mapController = loader.getController();
                mapController.setAuthToken(authToken);
                mapController.setUserRole(userRole);
                mapController.setUsername(username);
                
                Stage stage = (Stage) loginButton.getScene().getWindow();
                Scene scene = new Scene(root, 1200, 750);
                stage.setScene(scene);
                stage.setTitle("Network Coverage Dashboard - " + username + " [" + userRole + "]");
                stage.centerOnScreen();
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
                showError("Error loading application");
            }
        } else {
            showError("Invalid username or password");
        }
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
    
    public static void logout() {
        authToken = null;
        userRole = null;
        username = null;
        
        // Stop API server
        ApiServer.stop();
    }
    
    public static String getAuthToken() {
        return authToken;
    }
    
    public static String getUserRole() {
        return userRole;
    }
    
    public static String getUsername() {
        return username;
    }
}