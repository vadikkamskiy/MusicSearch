package musicsearch;

import java.util.Objects;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Toolkit;
import java.awt.PopupMenu;

import musicsearch.widgets.MainWindow;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

public class App extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        try {
            stage.getIcons().add(new Image(
                Objects.requireNonNull(
                    getClass().getResourceAsStream("/icon.ico")
                )
            ));
        } catch (Exception e) {
            System.err.println("Не удалось загрузить иконку: " + e.getMessage());
        }

        MainWindow mainWindow = new MainWindow();

        stage.setTitle("Audio Search");
        stage.setScene(mainWindow.getScene());
        stage.setMinWidth(1080);
        stage.setMinHeight(480);

        stage.setOnCloseRequest(e -> {
            e.consume();
            stage.hide();
        });

        stage.show();

        setupTray();
        setupHotkey();
    }

    public static void main(String[] args) {
        launch(args);
    }

    // ===================== TRAY =====================

    private void setupTray() {
        if (!SystemTray.isSupported()) {
            System.err.println("Tray not supported");
            return;
        }

        Platform.setImplicitExit(false);

        try {
            SystemTray tray = SystemTray.getSystemTray();

            java.awt.Image image = Toolkit.getDefaultToolkit()
                    .getImage(getClass().getResource("/icon.ico"));

            PopupMenu menu = new PopupMenu();

            java.awt.MenuItem openItem = new java.awt.MenuItem("Open");
            java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");

            openItem.addActionListener(e ->
                Platform.runLater(() -> {
                    primaryStage.show();
                    primaryStage.toFront();
                })
            );

            exitItem.addActionListener(e -> {
                tray.remove(tray.getTrayIcons()[0]);
                Platform.exit();
                System.exit(0);
            });

            menu.add(openItem);
            menu.add(exitItem);

            TrayIcon trayIcon = new TrayIcon(image, "TrayApp", menu);
            trayIcon.setImageAutoSize(true);

            trayIcon.addActionListener(e ->
                Platform.runLater(() -> {
                    primaryStage.show();
                    primaryStage.toFront();
                })
            );

            tray.add(trayIcon);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== HOTKEY =====================

    private void setupHotkey() {
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            e.printStackTrace();
            return;
        }

        GlobalScreen.addNativeKeyListener(new NativeKeyListener() {

            private boolean ctrl;
            private boolean alt;

            @Override
            public void nativeKeyPressed(NativeKeyEvent e) {
                if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) ctrl = true;
                if (e.getKeyCode() == NativeKeyEvent.VC_ALT) alt = true;

                if (ctrl && alt && e.getKeyCode() == NativeKeyEvent.VC_M) {
                    Platform.runLater(() -> toggleStage());
                }
            }

            @Override
            public void nativeKeyReleased(NativeKeyEvent e) {
                if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) ctrl = false;
                if (e.getKeyCode() == NativeKeyEvent.VC_ALT) alt = false;
            }
        });
    }

    private void toggleStage() {
        if (primaryStage.isShowing()) {
            primaryStage.hide();
        } else {
            primaryStage.show();
            primaryStage.toFront();
        }
    }
}
