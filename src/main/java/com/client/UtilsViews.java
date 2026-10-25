package com.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Utilitats per gestionar les vistes de l'aplicació.
 *
 * Totes les vistes es carreguen dins d'un StackPane (parentContainer) i es
 * mostren/oculten amb una transició de foscat.
 */
public class UtilsViews {

    /** Contenidor on s'apilen totes les vistes. */
    public static StackPane parentContainer = new StackPane();

    /** Vistes carregades, per nom. */
    private static final Map<String, Pane> views = new HashMap<>();

    /** Controls de cada vista, per nom. */
    private static final Map<String, Object> controllers = new HashMap<>();

    /** Nom de la vista actualment visible. */
    private static String activeView = "";

    /** Carrega una vista FXML i l'afegeix al contenidor (oculta). */
    public static void addView(Class<?> clazz, String name, String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(clazz.getResource(fxmlPath));
        Pane view = loader.load();

        views.put(name, view);
        controllers.put(name, loader.getController());

        view.setOpacity(0);
        view.setVisible(false);
        parentContainer.getChildren().add(view);
    }

    /** Retorna el controlador de la vista amb el nom indicat. */
    public static Object getController(String name) {
        return controllers.get(name);
    }

    /** Retorna l'arrel (AnchorPane) de la vista amb el nom indicat. */
    public static Pane getView(String name) {
        return views.get(name);
    }

    /** Retorna el nom de la vista actualment visible ("" si cap). */
    public static String getActiveView() {
        return activeView;
    }

    /** Canvia a la vista indicada amb una transició de foscat. */
    public static void setViewAnimating(String name) {
        setViewAnimating(name, 500);
    }

    /** Canvia a la vista indicada amb una transició de foscat de millis ms. */
    public static void setViewAnimating(String name, int millis) {
        if (name.equals(activeView)) return;

        Pane newView = views.get(name);
        if (newView == null) return;

        if (!activeView.isEmpty()) {
            Pane oldView = views.get(activeView);
            FadeTransition out = new FadeTransition(Duration.millis(millis), oldView);
            out.setFromValue(1);
            out.setToValue(0);
            out.setOnFinished(e -> oldView.setVisible(false));
            out.play();
        }

        newView.setVisible(true);
        newView.toFront();

        FadeTransition in = new FadeTransition(Duration.millis(millis), newView);
        in.setFromValue(0);
        in.setToValue(1);
        in.play();

        activeView = name;
    }
}
