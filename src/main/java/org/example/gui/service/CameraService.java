package org.example.gui.service;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_videoio.VideoCapture;

import java.util.Optional;

import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_HEIGHT;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_FRAME_WIDTH;

/**
 * Encapsulates the lifecycle of the webcam and exposes the latest frame in a thread-safe manner.
 */
public class CameraService {

    private final Object frameLock = new Object();

    private VideoCapture capture;
    private Mat latestFrame;
    private Thread captureThread;
    private volatile boolean running;

    /**
     * Starts the webcam capture using the specified device index and resolution.
     *
     * @param deviceIndex the camera device index
     * @param width       desired frame width
     * @param height      desired frame height
     * @return {@code true} if the camera was started successfully, otherwise {@code false}
     */
    public synchronized boolean start(int deviceIndex, int width, int height) {
        stop();

        capture = new VideoCapture(deviceIndex);
        if (!capture.isOpened()) {
            stop();
            return false;
        }

        capture.set(CAP_PROP_FRAME_WIDTH, width);
        capture.set(CAP_PROP_FRAME_HEIGHT, height);

        latestFrame = new Mat();
        running = true;

        captureThread = new Thread(this::captureLoop, "CameraCaptureThread");
        captureThread.setDaemon(true);
        captureThread.start();
        return true;
    }

    private void captureLoop() {
        Mat frame = new Mat();
        try {
            while (running && capture != null && capture.isOpened()) {
                if (capture.read(frame) && !frame.empty()) {
                    synchronized (frameLock) {
                        frame.copyTo(latestFrame);
                    }
                }
            }
        } finally {
            frame.close();
        }
    }

    /**
     * Stops the webcam capture and releases native resources.
     */
    public synchronized void stop() {
        running = false;

        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        if (capture != null) {
            if (capture.isOpened()) {
                capture.release();
            }
            capture.close();
            capture = null;
        }

        if (latestFrame != null) {
            latestFrame.close();
            latestFrame = null;
        }
    }

    /**
     * Returns a defensive copy of the latest frame if available.
     *
     * @return optional containing a cloned frame
     */
    public Optional<Mat> getLatestFrame() {
        if (!running || latestFrame == null || latestFrame.empty()) {
            return Optional.empty();
        }

        Mat copy = new Mat();
        synchronized (frameLock) {
            latestFrame.copyTo(copy);
        }

        return copy.empty() ? Optional.empty() : Optional.of(copy);
    }

    /**
     * Indicates whether the camera is currently capturing frames.
     *
     * @return {@code true} if capturing, otherwise {@code false}
     */
    public boolean isRunning() {
        return running;
    }
}