package com.currencyconverter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(
                MainApp.class.getResource("main-view.fxml")
        );

        Scene scene = new Scene(fxmlLoader.load(), 620, 520);

        // Load stylesheet
        scene.getStylesheets().add(
                Objects.requireNonNull(
                        MainApp.class.getResource("styles.css")
                ).toExternalForm()
        );

        stage.setTitle("Currency Converter");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
