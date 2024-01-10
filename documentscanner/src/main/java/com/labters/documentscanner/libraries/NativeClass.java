/*
 * *
 *  * Created by Ali YÜCE on 3/2/20 11:18 PM
 *  * https://github.com/mayuce/
 *  * Copyright (c) 2020 . All rights reserved.
 *  * Last modified 3/2/20 11:10 PM
 *
 */

package com.labters.documentscanner.libraries;

import android.graphics.Bitmap;

import com.labters.documentscanner.helpers.ImageUtils;
import com.labters.documentscanner.helpers.MathUtils;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class NativeClass {

    static {
        System.loadLibrary("opencv_java4");
    }

    private static final int THRESHOLD_LEVEL = 2;
    private static final double AREA_LOWER_THRESHOLD = 0.2;
    private static final double AREA_UPPER_THRESHOLD = 0.98;
    private static final double DOWNSCALE_IMAGE_SIZE = 600f;

    public Bitmap getScannedBitmap(Bitmap bitmap, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        PerspectiveTransformation perspective = new PerspectiveTransformation();
        MatOfPoint2f rectangle = new MatOfPoint2f();
        rectangle.fromArray(new Point(x1, y1), new Point(x2, y2), new Point(x3, y3), new Point(x4, y4));
        Mat dstMat = perspective.transform(ImageUtils.bitmapToMat(bitmap), rectangle);
        return ImageUtils.matToBitmap(dstMat);
    }

    private static Comparator<MatOfPoint2f> AreaDescendingComparator = new Comparator<MatOfPoint2f>() {
        public int compare(MatOfPoint2f m1, MatOfPoint2f m2) {
            double area1 = Imgproc.contourArea(m1);
            double area2 = Imgproc.contourArea(m2);
            return (int) Math.ceil(area2 - area1);
        }
    };


    public MatOfPoint2f getPoint(Bitmap bitmap) {

        Mat src = ImageUtils.bitmapToMat(bitmap);

        // Downscale image for better performance.
        double ratio = DOWNSCALE_IMAGE_SIZE / Math.max(src.width(), src.height());
        Size downscaledSize = new Size(src.width() * ratio, src.height() * ratio);
        Mat downscaled = new Mat(downscaledSize, src.type());
        Imgproc.resize(src, downscaled, downscaledSize);

        List<MatOfPoint2f> rectangles = getPoints(downscaled);
        if (rectangles.size() == 0) {
            return null;
        }
        Collections.sort(rectangles, AreaDescendingComparator);
        MatOfPoint2f largestRectangle = rectangles.get(0);
        MatOfPoint2f result = MathUtils.scaleRectangle(largestRectangle, 1f / ratio);
        return result;
    }

    public Mat increaseContrast(Mat originalImage) {
        // Create a Mat object to store the result
        Mat result = new Mat(originalImage.size(), originalImage.type());

        // The alpha value defines the contrast.
        // The higher the alpha value, the higher the contrast.
        // You can adjust this value according to your needs.
        double alpha = 1.5; // Try different values; 1.0 means original image.

        // The beta value allows you to adjust the brightness,
        // but for contrast enhancement, it's typically kept at 0.
        double beta = -1.0;

        // Apply the contrast adjustment
        originalImage.convertTo(result, -1, alpha, beta);

        return result;
    }

//    original
//    public List<MatOfPoint2f> getPoints(Mat src) {
//
//        if (src.empty()) {
//            throw new IllegalArgumentException("Input Mat 'src' is empty.");
//        }
//
//        // Blur the image to filter out the noise.
//        Mat blurred = new Mat();
//        Mat imgGray = new Mat();
//
//        Mat contrast = increaseContrast(src);
//        Imgproc.cvtColor(contrast, imgGray, Imgproc.COLOR_BGR2GRAY);
//        Imgproc.medianBlur(imgGray, blurred, 5);
//
//        // Set up images to use.
////        Mat gray0 = new Mat(blurred.size(), blurred.depth());
//        Mat gray0 = new Mat(blurred.size(), CvType.CV_8U);
//        Mat gray = new Mat();
//
//        // For Core.mixChannels.
//        List<MatOfPoint> contours = new ArrayList<>();
//        List<MatOfPoint2f> rectangles = new ArrayList<>();
//
//        List<Mat> sources = new ArrayList<>();
//        sources.add(blurred);
//        List<Mat> destinations = new ArrayList<>();
//        destinations.add(gray0);
//
//        // To filter rectangles by their areas.
//        int srcArea = src.rows() * src.cols();
//
//        // Find squares in every color plane of the image.
//        for (int c = 0; c < 3; c++) {
//            int[] ch = {c, 0};
//            MatOfInt fromTo = new MatOfInt(ch);
//
//            Core.mixChannels(sources, destinations, fromTo);
//
//            // Try several threshold levels.
//            for (int l = 0; l < THRESHOLD_LEVEL; l++) {
//                if (l == 0) {
//                    // HACK: Use Canny instead of zero threshold level.
//                    // Canny helps to catch squares with gradient shading.
//                    // NOTE: No kernel size parameters on Java API.
//                    Imgproc.Canny(gray0, gray, 10, 20);
//
//                    // Dilate Canny output to remove potential holes between edge segments.
//                    Imgproc.dilate(gray, gray, Mat.ones(new Size(3, 3), 0));
//                } else {
//                    int threshold = (l + 1) * 255 / THRESHOLD_LEVEL;
//                    Imgproc.threshold(gray0, gray, threshold, 255, Imgproc.THRESH_BINARY);
//                }
//
//                // Find contours and store them all as a list.
//                Imgproc.findContours(gray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
//
//                for (MatOfPoint contour : contours) {
//                    MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
//                    double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;
//
//                    // Approximate polygonal curves.
//                    MatOfPoint2f approx = new MatOfPoint2f();
//                    Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);
//
//                    if (isRectangle(approx, srcArea)) {
//                        rectangles.add(approx);
//                    }
//                }
//            }
//        }
//
//        return rectangles;
//
//    }

////    contrast canny
//    public List<MatOfPoint2f> getPoints(Mat src) {
//        if (src.empty()) {
//            throw new IllegalArgumentException("Input Mat 'src' is empty.");
//        }
//
//        // Apply contrast enhancement and convert to grayscale
//        Mat contrastEnhanced = increaseContrast(src);
//        Mat imgGray = new Mat();
//        Imgproc.cvtColor(contrastEnhanced, imgGray, Imgproc.COLOR_BGR2GRAY);
//
//        // Apply median blur to the grayscale image
//        Mat blurred = new Mat();
//        Imgproc.medianBlur(imgGray, blurred, 5);
//
//        // Prepare for contour detection
//        Mat grayCanny = new Mat();
//        Imgproc.Canny(blurred, grayCanny, 50, 80);
//        Imgproc.dilate(grayCanny, grayCanny, Mat.ones(new Size(3, 3), 0));
//
//        // Find contours
//        List<MatOfPoint> contours = new ArrayList<>();
//        Imgproc.findContours(grayCanny, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
//
//        // Process contours to find rectangles
//        List<MatOfPoint2f> rectangles = new ArrayList<>();
//        int srcArea = src.rows() * src.cols();
//        for (MatOfPoint contour : contours) {
//            MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
//            double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;
//            MatOfPoint2f approx = new MatOfPoint2f();
//            Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);
//
//            if (isRectangle(approx, srcArea)) {
//                rectangles.add(approx);
//            }
//        }
//
//        return rectangles;
//    }

//    adaptiveThreshold
//    public List<MatOfPoint2f> getPoints(Mat src) {
//        if (src.empty()) {
//            throw new IllegalArgumentException("Input Mat 'src' is empty.");
//        }
//
//        // Apply contrast enhancement
//        Mat contrastEnhanced = increaseContrast(src);
//
//        // Convert to grayscale
//        Mat imgGray = new Mat();
//        Imgproc.cvtColor(contrastEnhanced, imgGray, Imgproc.COLOR_BGR2GRAY);
//
//        // Apply Gaussian blur
//        Imgproc.GaussianBlur(imgGray, imgGray, new Size(5.0, 5.0), 0.0);
//
//        // Apply adaptive threshold
//        Imgproc.adaptiveThreshold(imgGray, imgGray, 255.0,
//                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
//                Imgproc.THRESH_BINARY, 11, 2.0);
//
//        // Find contours
//        List<MatOfPoint> contours = new ArrayList<>();
//        Imgproc.findContours(imgGray, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
//
//        // Process contours to find rectangles
//        List<MatOfPoint2f> rectangles = new ArrayList<>();
//        int srcArea = src.rows() * src.cols();
//        for (MatOfPoint contour : contours) {
//            MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
//            double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;
//            MatOfPoint2f approx = new MatOfPoint2f();
//            Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);
//
//            if (isRectangle(approx, srcArea)) {
//                rectangles.add(approx);
//            }
//        }
//
//        return rectangles;
//    }

    //    HSV
//    public List<MatOfPoint2f> getPoints(Mat src) {
//        if (src.empty()) {
//            throw new IllegalArgumentException("Input Mat 'src' is empty.");
//        }
//
//        Mat hsvImage = new Mat();
//        Imgproc.cvtColor(src, hsvImage, Imgproc.COLOR_BGR2HSV);
//
//        // Split into individual channels (Hue, Saturation, Value)
//        List<Mat> hsvChannels = new ArrayList<>();
//        Core.split(hsvImage, hsvChannels);
//        Mat hue = hsvChannels.get(0);
//        Mat saturation = hsvChannels.get(1);
//        Mat value = hsvChannels.get(2);
//
//        int delta_hue = 10;
//        int delta_saturation = -20;
//        int delta_value = 30;
//
//        Core.add(hue, Scalar.all(delta_hue), hue);
//        Core.add(saturation, Scalar.all(delta_saturation), saturation);
////        Core.add(value, Scalar.all(delta_value), value);
//        Core.merge(hsvChannels, hsvImage);
//
//        Mat processedImage = new Mat();
//        Imgproc.cvtColor(hsvImage, processedImage, Imgproc.COLOR_BGR2GRAY);
//
////        Imgproc.equalizeHist(processedImage, processedImage);
////
//
//        // Find contours
//        List<MatOfPoint> contours = new ArrayList<>();
//        Imgproc.findContours(processedImage, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
//
//        // Process contours to find rectangles
//        List<MatOfPoint2f> rectangles = new ArrayList<>();
//        int srcArea = src.rows() * src.cols();
//        for (MatOfPoint contour : contours) {
//            MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
//            double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;
//            MatOfPoint2f approx = new MatOfPoint2f();
//            Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);
//
//            if (isRectangle(approx, srcArea)) {
//                rectangles.add(approx);
//            }
//        }
//
//        return rectangles;
//    }

    // best
    public List<MatOfPoint2f> getPoints(Mat src) {
        if (src.empty()) {
            throw new IllegalArgumentException("Input Mat 'src' is empty.");
        }

        Mat modifiedImage = getModifiedImage(src);

        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(modifiedImage, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        // Process contours to find rectangles
        List<MatOfPoint2f> rectangles = new ArrayList<>();
        int srcArea = src.rows() * src.cols();
        for (MatOfPoint contour : contours) {
            MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
            double arcLen = Imgproc.arcLength(contourFloat, true) * 0.02;
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

            if (isRectangle(approx, srcArea)) {
                rectangles.add(approx);
            }
        }

        return rectangles;
    }

    private Mat getModifiedImage(Mat src) {
        Mat hsvImage = new Mat();
        Imgproc.cvtColor(src, hsvImage, Imgproc.COLOR_BGR2HSV);

        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsvImage, hsvChannels);
        Mat hue = hsvChannels.get(0);
        Mat saturation = hsvChannels.get(1);
        Mat value = hsvChannels.get(2);

        int delta_hue = 10;
        int delta_saturation = 80;
        int delta_value = 40;

        Mat modifiedHue = new Mat();
        Mat modifiedSaturation = new Mat();
        Mat modifiedValue = new Mat();

        Core.add(hue, Scalar.all(delta_hue), modifiedHue);
        Core.add(saturation, Scalar.all(delta_saturation), modifiedSaturation);
        Core.add(value, Scalar.all(delta_value), modifiedValue);

        hsvChannels.set(0, modifiedHue);
        hsvChannels.set(1, modifiedSaturation);
        hsvChannels.set(2, modifiedValue);
        Core.merge(hsvChannels, hsvImage);

        Mat processedImage = new Mat();
        Imgproc.cvtColor(hsvImage, processedImage, Imgproc.COLOR_HSV2BGR);
        Imgproc.cvtColor(processedImage, processedImage, Imgproc.COLOR_BGR2GRAY);

        Imgproc.adaptiveThreshold(processedImage, processedImage, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 35, 4.0);

        return processedImage;
    }





    private boolean isRectangle(MatOfPoint2f polygon, int srcArea) {
        MatOfPoint polygonInt = MathUtils.toMatOfPointInt(polygon);

        if (polygon.rows() != 4) {
            return false;
        }

        double area = Math.abs(Imgproc.contourArea(polygon));
        if (area < srcArea * AREA_LOWER_THRESHOLD || area > srcArea * AREA_UPPER_THRESHOLD) {
            return false;
        }

        if (!Imgproc.isContourConvex(polygonInt)) {
            return false;
        }

        // Check if the all angles are more than 72.54 degrees (cos 0.3).
        double maxCosine = 0;
        Point[] approxPoints = polygon.toArray();

        for (int i = 2; i < 5; i++) {
            double cosine = Math.abs(MathUtils.angle(approxPoints[i % 4], approxPoints[i - 2], approxPoints[i - 1]));
            maxCosine = Math.max(cosine, maxCosine);
        }

        return !(maxCosine >= 0.3);
    }

}
