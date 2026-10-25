package com.client;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controlador de la vista de configuracio (connexio al servidor).
 */
public class CtrlConfig {

    @FXML public ComboBox<String> txtProtocol;
    @FXML public TextField txtHost;
    @FXML public TextField txtPort;
    @FXML public Button btnConnect;
    @FXML public Label txtMessage;
    @FXML public ComboBox<String> cmbScenario;

    @FXML
    private void initialize() {
        txtProtocol.getItems().addAll("ws", "wss");
        txtProtocol.getSelectionModel().select("ws");
        txtHost.setText("localhost");
        txtPort.setText("3000");
        cmbScenario.getItems().addAll("Arena", "Corridor", "Maze");
        cmbScenario.getSelectionModel().select("Arena");
    }

    /** Escenari triat, en minuscules ("arena", "corridor" o "maze"). */
    public String getScenario() {
        return cmbScenario.getValue().toLowerCase();
    }

    @FXML
    private void onConnectClick() {
        btnConnect.setDisable(true);
        Main.pauseDuring(100, () -> btnConnect.setDisable(false));
        Main.connectToServer();
    }
}
