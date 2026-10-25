package com.client;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import com.shared.BulletData;
import com.shared.TankData;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    public static UtilsWS wsClient;

    public static String clientName = "";
    public static List<String> clients;
    public static List<TankData> tanks;
    public static List<BulletData> bullets;
    public static List<double[]> obstacles;
    public static String scenario = "";

    /** Canvas de la vista de joc (per alinear-hi la capa d'explosions). */
    public static Canvas playCanvas;
    /** Capa d'efectes visuals, per sobre de totes les vistes. */
    public static ExplosionLayer explosionLayer;

    public static CtrlConfig ctrlConfig;
    public static CtrlWait ctrlWait;
    public static CtrlPlay ctrlPlay;
    public static CtrlResult ctrlResult;

    public static void main(String[] args) {

        // Iniciar app JavaFX   
        launch(args);
    }
    
    @Override
    public void start(Stage stage) throws Exception {

        final int windowWidth = 400;
        final int windowHeight = 300;

        UtilsViews.parentContainer.setStyle("-fx-font: 14 arial;");
        UtilsViews.addView(getClass(), "ViewConfig", "/assets/viewConfig.fxml"); 
        UtilsViews.addView(getClass(), "ViewWait", "/assets/viewWait.fxml");
        UtilsViews.addView(getClass(), "ViewPlay", "/assets/viewPlay.fxml");
        UtilsViews.addView(getClass(), "ViewResult", "/assets/viewResult.fxml");

        ctrlConfig = (CtrlConfig) UtilsViews.getController("ViewConfig");
        ctrlWait = (CtrlWait) UtilsViews.getController("ViewWait");
        ctrlPlay = (CtrlPlay) UtilsViews.getController("ViewPlay");
        ctrlResult = (CtrlResult) UtilsViews.getController("ViewResult");

        // Capa d'explosions: es col-loca a la pila, per sobre de totes les vistes,
        // i te la mateixa mida que la pila (les coordenades es converteixen a mà).
        Pane playView = UtilsViews.getView("ViewPlay");
        for (var node : playView.getChildrenUnmodifiable()) {
            if (node instanceof VBox vbox) {
                for (var child : vbox.getChildrenUnmodifiable()) {
                    if (child instanceof Canvas c) playCanvas = c;
                }
            }
        }
        explosionLayer = new ExplosionLayer();
        explosionLayer.setMouseTransparent(true);
        explosionLayer.setPickOnBounds(false);
        explosionLayer.prefWidthProperty().bind(UtilsViews.parentContainer.widthProperty());
        explosionLayer.prefHeightProperty().bind(UtilsViews.parentContainer.heightProperty());
        UtilsViews.parentContainer.getChildren().add(explosionLayer);

        UtilsViews.setViewAnimating("ViewConfig", 10);

        Scene scene = new Scene(UtilsViews.parentContainer);
        
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> stop()); // Call close method when closing window
        stage.setTitle("JavaFX");
        stage.setMinWidth(windowWidth);
        stage.setMinHeight(windowHeight);
        stage.show();

        // Add icon only if not Mac
        if (!System.getProperty("os.name").contains("Mac")) {
            Image icon = new Image(getClass().getResourceAsStream("/icons/icon.png"));
            stage.getIcons().add(icon);
        }

        // Hook de prova (smoke tests): connectar i jugar sense intervencio del ratoli.
        if (System.getProperty("tanks.autoconnect") != null) {
            pauseDuring(1000, () -> connectToServer());
        }
    }

    @Override
    public void stop() { 
        if (wsClient != null) {
            wsClient.forceExit();
        }
        System.exit(1); // Kill all executor services
    }

    public static void pauseDuring(long milliseconds, Runnable action) {
        PauseTransition pause = new PauseTransition(Duration.millis(milliseconds));
        pause.setOnFinished(event -> Platform.runLater(action));
        pause.play();
    }

    public static void connectToServer() {

        ctrlConfig.txtMessage.setTextFill(Color.BLACK);
        ctrlConfig.txtMessage.setText("Connecting ...");
    
        pauseDuring(1500, () -> { // Give time to show connecting message ...

            String protocol = ctrlConfig.txtProtocol.getValue();
            String host = ctrlConfig.txtHost.getText();
            String port = ctrlConfig.txtPort.getText();
            String scenario = ctrlConfig.getScenario();
            wsClient = UtilsWS.createFresh(protocol + "://" + host + ":" + port);

            // El primer client que es connecta (amfitrio) comunica l'escenari triat.
            wsClient.onOpen((msg) -> {
                wsClient.safeSend("{\"type\":\"setScenario\",\"value\":\"" + scenario + "\"}");
            });
    
            wsClient.onMessage((response) -> { Platform.runLater(() -> { wsMessage(response); }); });
            wsClient.onError((response) -> { Platform.runLater(() -> { wsError(response); }); });
        });
    }
   
    private static void wsMessage(String response) {
        
        // System.out.println(response);
        
        JSONObject msgObj = new JSONObject(response);
        switch (msgObj.getString("type")) {
            case "serverData":
                clientName = msgObj.getString("clientName");

                JSONArray arrClients = msgObj.getJSONArray("clientsList");
                List<String> newClients = new ArrayList<>();
                for (int i = 0; i < arrClients.length(); i++) {
                    newClients.add(arrClients.getString(i));
                }
                clients = newClients;

                JSONArray arrTanks = msgObj.getJSONArray("tanksList");
                List<TankData> newTanks = new ArrayList<>();
                for (int i = 0; i < arrTanks.length(); i++) {
                    newTanks.add(TankData.fromJSON(arrTanks.getJSONObject(i)));
                }
                tanks = newTanks;

                JSONArray arrBullets = msgObj.getJSONArray("bulletsList");
                List<BulletData> newBullets = new ArrayList<>();
                for (int i = 0; i < arrBullets.length(); i++) {
                    newBullets.add(BulletData.fromJSON(arrBullets.getJSONObject(i)));
                }
                bullets = newBullets;

                JSONArray arrObstacles = msgObj.getJSONArray("obstaclesList");
                List<double[]> newObstacles = new ArrayList<>();
                for (int i = 0; i < arrObstacles.length(); i++) {
                    JSONObject o = arrObstacles.getJSONObject(i);
                    newObstacles.add(new double[] {o.getDouble("x"), o.getDouble("y"),
                                                    o.getDouble("w"), o.getDouble("h")});
                }
                obstacles = newObstacles;

                if (msgObj.has("scenario")) {
                    scenario = msgObj.getString("scenario");
                }

                if (clients.size() == 1) {

                    ctrlWait.txtPlayer0.setText(clients.get(0));

                } else if (clients.size() > 1) {

                    ctrlWait.txtPlayer0.setText(clients.get(0));
                    ctrlWait.txtPlayer1.setText(clients.get(1));
                    ctrlPlay.title.setText(clients.get(0) + " vs " + clients.get(1));
                }
                
                if (UtilsViews.getActiveView().equals("ViewConfig")) {
                    UtilsViews.setViewAnimating("ViewWait");
                }

                break;
            
            case "countdown":
                int value = msgObj.getInt("value");
                String txt = String.valueOf(value);
                if (value == 0) {
                    UtilsViews.setViewAnimating("ViewPlay");
                    txt = "GO";
                    if (System.getProperty("tanks.autoconnect") != null) {
                        ctrlPlay.startAutoShoot();
                    }
                }
                ctrlWait.txtTitle.setText(txt);
                break;

            case "gameOver":
                String winner = msgObj.getString("winner");
                String loser = msgObj.getString("loser");
                ctrlResult.txtTitle.setText("GAME OVER");
                ctrlResult.txtWinner.setText("Winner: " + winner);
                ctrlResult.txtLoser.setText("Loser: " + loser);
                UtilsViews.setViewAnimating("ViewResult");
                break;
        }
    }

    private static void wsError(String response) {
        String connectionRefused = "Connection refused";
        if (response.indexOf(connectionRefused) != -1) {
            ctrlConfig.txtMessage.setTextFill(Color.RED);
            ctrlConfig.txtMessage.setText(connectionRefused);
            pauseDuring(1500, () -> {
                ctrlConfig.txtMessage.setText("");
            });
        }
    }
}
