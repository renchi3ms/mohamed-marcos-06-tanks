package com.client;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controlador de la vista de resultat (guanyador, perdedor i sortida al lobby).
 */
public class CtrlResult {

    @FXML public Label txtTitle;
    @FXML public Label txtWinner;
    @FXML public Label txtLoser;

    @FXML
    private void onBackClick() {
        // Desconnectar del servidor i tornar a la vista de configuracio.
        // createFresh() (a connectToServer) creara una connexio nova al proper connect.
        if (Main.wsClient != null) {
            Main.wsClient.disconnect();
            Main.wsClient = null;
        }

        Main.clientName = "";
        Main.clients = null;
        Main.tanks = null;
        Main.bullets = null;
        Main.obstacles = null;
        Main.ctrlPlay.resetEffects();

        UtilsViews.setViewAnimating("ViewConfig");
    }
}
