package org.example.gui.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class WelcomeController {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Button closeButton;

    public void setUsername(String username) {
        welcomeLabel.setText("Xin chào, " + username + "!");
    }

    @FXML
    private void onClose(ActionEvent event) {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}