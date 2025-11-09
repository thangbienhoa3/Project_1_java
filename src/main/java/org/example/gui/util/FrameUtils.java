package org.example.gui.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;

import java.nio.file.Path;

import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGB;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2RGBA;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * Helper utilities for converting and persisting OpenCV frames.
 */
public final class FrameUtils {

    private FrameUtils() {
    }

    /**
     * Converts a {@link Mat} frame to a JavaFX {@link Image}.
     *
     * @param frame the OpenCV frame
     * @return the corresponding {@link Image}
     */
    public static Image matToImage(Mat frame) {
        Mat rgbaFrame = new Mat();
        cvtColor(frame, rgbaFrame, COLOR_BGR2RGBA);

        int width = rgbaFrame.cols();
        int height = rgbaFrame.rows();
        byte[] buffer = new byte[width * height * (int) rgbaFrame.elemSize()];
        rgbaFrame.data().get(buffer);

        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        writer.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), buffer, 0, width * 4);

        rgbaFrame.close();
        return image;
    }

    /**
     * Saves the provided {@link Mat} frame as a JPEG on disk.
     *
     * @param frame the frame to persist
     * @param path  the file path where the frame should be written
     */
    public static void saveFrame(Mat frame, Path path) {
        Mat rgbFrame = new Mat();
        cvtColor(frame, rgbFrame, COLOR_BGR2RGB);
        opencv_imgcodecs.imwrite(path.toString(), rgbFrame);
        rgbFrame.close();
    }
}