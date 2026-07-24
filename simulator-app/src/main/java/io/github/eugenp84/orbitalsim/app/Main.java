
package io.github.eugenp84.orbitalsim.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;

    /**
     * Application entry point.
     * Launches the Orbital Mechanics Simulator JavaFX application.
     */
    public class Main extends Application
    {

        @Override
        public void start(Stage primaryStage) {
            // Temporary placeholder UI — confirms JavaFX is working
            Text text = new Text("Orbital Mechanics Simulator");
            StackPane root = new StackPane(text);
            Scene scene = new Scene(root, 800, 600);

            primaryStage.setTitle("Orbital Mechanics Simulator");
            primaryStage.setScene(scene);
            primaryStage.show();
        }

        public static void main(String[] args) {
            launch(args);
        }
    }
