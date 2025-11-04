package org.example.gui.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

public class WebcamController {

    private static final int NUM_PICTURES = 5;

    @FXML private ImageView imageView;
    @FXML private TextField textField;
    @FXML private Label statusLabel;
    @FXML private Button captureButton;
    @FXML private Button loginButton;

    private VideoCapture capture;
    private volatile boolean running;
    private volatile Mat latestFrame;
    private Timeline displayTimeline;
    private Thread captureThread;

    // =============== ENROLL (Đăng ký khuôn mặt) ==================
    @FXML
    private void onCaptureFaces() {
        String username = textField.getText();
        if (username == null || username.isEmpty()) {
            statusLabel.setText("⚠️ Please enter your username");
            return;
        }

        captureButton.setDisable(true);
        loginButton.setDisable(true);
        statusLabel.setText("🎥 Starting camera...");

        capture = new VideoCapture(0);
        capture.set(3, 1280);
        capture.set(4, 720);

        if (!capture.isOpened()) {
            statusLabel.setText("Cannot open camera");
            captureButton.setDisable(false);
            loginButton.setDisable(false);
            return;
        }

        running = true;
        latestFrame = new Mat();

        captureThread = new Thread(() -> {
            while (running && capture.isOpened()) {
                synchronized (this) {
                    if (latestFrame != null)
                        capture.read(latestFrame);
                }
            }
        });
        captureThread.setDaemon(true);
        captureThread.start();

        displayTimeline = new Timeline(new KeyFrame(Duration.millis(33), e -> {
            if (latestFrame != null && !latestFrame.empty()) {
                imageView.setImage(matToImage(latestFrame));
            }
        }));
        displayTimeline.setCycleCount(Timeline.INDEFINITE);
        displayTimeline.play();

        new Thread(() -> saveFiveImages(username)).start();
    }

    private void saveFiveImages(String username) {
        try {
            int savedCount = 0;
            java.io.File userDir = new java.io.File("dataset/" + username);
            java.nio.file.Files.createDirectories(userDir.toPath());

            while (running && savedCount < NUM_PICTURES) {
                if (latestFrame != null && !latestFrame.empty()) {
                    savedCount++;
                    String file = String.format("dataset/%s/img_%d.jpg", username, savedCount);
                    Mat rgbFrame = new Mat();
                    cvtColor(latestFrame, rgbFrame, COLOR_BGR2RGB);
                    opencv_imgcodecs.imwrite(file, rgbFrame);
                    int progress = savedCount;
                    Platform.runLater(() ->
                            statusLabel.setText("Saved " + progress + "/" + NUM_PICTURES + " images"));
                    Thread.sleep(1000);
                }
            }

            running = false;
            Platform.runLater(this::stopCamera);
            Thread.sleep(500);

            Platform.runLater(() ->
                    statusLabel.setText("Capturing done for " + username));

            // Sau khi lưu 5 ảnh, gọi face_app.py để encode
            new Thread(() -> runPythonProcess("face_app.py", "enroll", username)).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLoginByFace() {
        captureButton.setDisable(true);
        loginButton.setDisable(true);
        statusLabel.setText("Recognizing face...");
        new Thread(() -> runPythonProcess("face_app.py", "login")).start();
    }


    private void runPythonProcess(String... args) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("python3");
            cmd.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String result = output.toString().trim();

            Platform.runLater(() -> {
                if (exitCode == 0) {
                    if (result.isEmpty())
                        statusLabel.setText("Python finished successfully");
                    else
                        statusLabel.setText(result);
                } else {
                    statusLabel.setText("Python exited (" + exitCode + "): " + result);
                }
                captureButton.setDisable(false);
                if (loginButton != null) loginButton.setDisable(false);
            });

        } catch (Exception e) {
            Platform.runLater(() -> statusLabel.setText("Error running Python: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    @FXML
    private void stopCamera() {
        running = false;
        try {
            if (captureThread != null && captureThread.isAlive()) {
                captureThread.join(500);
            }
        } catch (InterruptedException ignored) {}

        if (displayTimeline != null) {
            displayTimeline.stop();
            displayTimeline = null;
        }

        if (capture != null && capture.isOpened()) {
            capture.release();
            capture = null;
        }

        imageView.setImage(null);
        captureButton.setDisable(false);
        if (loginButton != null) loginButton.setDisable(false);
    }

    // =============== CHUYỂN MAT → IMAGEVIEW ==================
    private Image matToImage(Mat mat) {
        int width = mat.cols(), height = mat.rows(), channels = mat.channels();
        byte[] source = new byte[width * height * channels];
        mat.data().get(source);
        BufferedImage bImage = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] target = ((DataBufferByte) bImage.getRaster().getDataBuffer()).getData();
        System.arraycopy(source, 0, target, 0, source.length);
        return SwingFXUtils.toFXImage(bImage, null);
    }
}
