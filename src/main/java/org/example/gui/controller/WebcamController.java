package org.example.gui.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.util.Duration;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bytedeco.opencv.opencv_core.Mat;
import org.example.gui.service.CameraService;
import org.example.gui.service.PythonProcessService;
import org.example.gui.service.PythonProcessService.PythonResult;
import org.example.gui.service.FaceLoginService;
import org.example.gui.service.FaceLoginService.LoginResult;
import org.example.gui.util.FrameUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class WebcamController {

    private static final int NUM_PICTURES = 5;
    private static final int CAMERA_DEVICE_INDEX = 0;
    private static final int CAMERA_WIDTH = 1280;
    private static final int CAMERA_HEIGHT = 720;
    private static final Duration FRAME_DURATION = Duration.millis(33);

    @FXML
    private ImageView imageView;
    @FXML
    private TextField usernameField;
    @FXML
    private Label statusLabel;
    @FXML
    private Button captureButton;
    @FXML
    private Button loginButton;
    @FXML
    private Button stopButton;
    @FXML
    private ProgressBar progressBar;

    private final CameraService cameraService = new CameraService();
    private final PythonProcessService pythonProcessService = new PythonProcessService();
    private final FaceLoginService faceLoginService = new FaceLoginService(pythonProcessService);
    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor(new CaptureThreadFactory());

    private Timeline displayTimeline;

    @FXML
    public void initialize() {
        progressBar.setProgress(0);
        stopButton.setDisable(true);
    }

    @FXML
    private void onCaptureFaces() {
        String username = usernameField.getText();
        if (username == null || username.isBlank()) {
            statusLabel.setText("⚠️ Please enter your username");
            return;
        }

        if (cameraService.isRunning()) {
            statusLabel.setText("Camera is already running");
            return;
        }

        boolean started = cameraService.start(CAMERA_DEVICE_INDEX, CAMERA_WIDTH, CAMERA_HEIGHT);
        if (!started) {
            statusLabel.setText("Cannot open camera");
            return;
        }

        statusLabel.setText("🎥 Starting camera...");
        captureButton.setDisable(true);
        loginButton.setDisable(true);
        stopButton.setDisable(false);
        progressBar.setProgress(0);

        startDisplayTimeline();
        captureExecutor.submit(() -> captureImages(username));
    }

    @FXML
    private void onLoginByFace() {
        captureButton.setDisable(true);
        loginButton.setDisable(true);
        stopButton.setDisable(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        statusLabel.setText("Recognizing face...");

        faceLoginService.loginByFace(result -> Platform.runLater(() -> handleFaceLoginResult(result)));
    }

    @FXML
    private void stopCamera() {
        cameraService.stop();
        stopDisplayTimeline();
        imageView.setImage(null);
        progressBar.setProgress(0);
        captureButton.setDisable(false);
        loginButton.setDisable(false);
        stopButton.setDisable(true);
        statusLabel.setText("Camera stopped");
    }

    private void captureImages(String username) {
        try {
            Path userDir = Path.of("dataset", username);
            Files.createDirectories(userDir);

            int savedCount = 0;
            while (cameraService.isRunning() && savedCount < NUM_PICTURES) {
                Optional<Mat> optionalFrame = cameraService.getLatestFrame();
                if (optionalFrame.isEmpty()) {
                    Thread.sleep(40);
                    continue;
                }

                try (Mat frame = optionalFrame.get()) {
                    savedCount++;
                    Path imagePath = userDir.resolve(String.format("img_%d.jpg", savedCount));
                    FrameUtils.saveFrame(frame, imagePath);
                }

                int progress = savedCount;
                Platform.runLater(() -> {
                    statusLabel.setText("Saved " + progress + "/" + NUM_PICTURES + " images");
                    progressBar.setProgress(progress / (double) NUM_PICTURES);
                });

                Thread.sleep(750);
            }

            boolean completed = savedCount >= NUM_PICTURES;
            Platform.runLater(() -> {
                stopCamera();
                if (completed) {
                    statusLabel.setText("Capturing done for " + username);
                    progressBar.setProgress(1.0);
                    captureButton.setDisable(true);
                    loginButton.setDisable(true);
                } else {
                    statusLabel.setText("Capture cancelled");
                }
            });

            if (completed) {
                pythonProcessService.executeAsync(
                        List.of("python3", "face_app.py", "enroll", username),
                        result -> Platform.runLater(() -> handlePythonCompletion(result, "Enrollment completed"))
                );
            }
        } catch (Exception e) {
            Platform.runLater(() -> {
                stopCamera();
                statusLabel.setText("Error saving images: " + e.getMessage());
            });
        }
    }

    private void startDisplayTimeline() {
        stopDisplayTimeline();
        displayTimeline = new Timeline(new KeyFrame(FRAME_DURATION, event -> {
            Optional<Mat> frame = cameraService.getLatestFrame();
            if (frame.isPresent()) {
                try (Mat mat = frame.get()) {
                    Image fxImage = FrameUtils.matToImage(mat);
                    imageView.setImage(fxImage);
                }
            }
        }));
        displayTimeline.setCycleCount(Timeline.INDEFINITE);
        displayTimeline.play();
    }

    private void stopDisplayTimeline() {
        if (displayTimeline != null) {
            displayTimeline.stop();
            displayTimeline = null;
        }
    }

    private void handlePythonCompletion(PythonResult result, String fallbackMessage) {
        if (result.exitCode() == 0) {
            String output = result.output() == null || result.output().isBlank()
                    ? fallbackMessage
                    : result.output();
            statusLabel.setText(output);
        } else {
            statusLabel.setText("Python exited (" + result.exitCode() + "): " + result.output());
        }

        captureButton.setDisable(false);
        loginButton.setDisable(false);
        stopButton.setDisable(true);
        progressBar.setProgress(0);
    }

    private void handleFaceLoginResult(LoginResult result) {
        captureButton.setDisable(false);
        loginButton.setDisable(false);
        stopButton.setDisable(true);
        progressBar.setProgress(0);

        if (result.success()) {
            statusLabel.setText("Welcome, " + result.username());
            showWelcomeScreen(result.username());
        } else {
            String message = result.message() == null || result.message().isBlank()
                    ? "Face not recognized or face has not been registered"
                    : result.message();
            statusLabel.setText(message);
            showWarning(message);
        }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(AlertType.WARNING);
        alert.setTitle("Face login warning");
        alert.setHeaderText(null);
        alert.setContentText(message);
        Stage owner = (Stage) imageView.getScene().getWindow();
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    private void showWelcomeScreen(String username) {
        try {
            FXMLLoader loader = new FXMLLoader(WebcamController.class.getResource("/org/example/gui/welcome-view.fxml"));
            Parent root = loader.load();
            WelcomeController controller = loader.getController();
            controller.setUsername(username);

            Stage stage = new Stage();
            stage.setTitle("Xin chào");
            stage.initModality(Modality.APPLICATION_MODAL);
            Stage owner = (Stage) imageView.getScene().getWindow();
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.setScene(new Scene(root));
            stage.setResizable(false);
            stage.showAndWait();
        } catch (IOException e) {
            statusLabel.setText("Unable to show welcome screen: " + e.getMessage());
        }
    }

    private static class CaptureThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "FaceCaptureThread");
            thread.setDaemon(true);
            return thread;
        }
    }
}