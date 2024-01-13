/*
 * *
 *  * Created by Ali YÜCE on 3/2/20 11:18 PM
 *  * https://github.com/mayuce/
 *  * Copyright (c) 2020 . All rights reserved.
 *  * Last modified 3/2/20 11:10 PM
 *
 */

package com.labters.documentscanner.libraries;

import android.content.Intent;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.net.Uri;
import android.os.Environment;

public class NativeClass {

    static {
        System.loadLibrary("opencv_java4");
    }

    private static final int THRESHOLD_LEVEL = 2;
    private static final double AREA_LOWER_THRESHOLD = 0.4;
    private static final double AREA_UPPER_THRESHOLD = 0.98;
    private static final double DOWNSCALE_IMAGE_SIZE = 2000f;

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
        double areaThreshold = 500;
        double[] arcLengths = {0.05, 0.1};

        int srcArea = src.rows() * src.cols();
        for (double arcLenFactor : arcLengths) {
            for (MatOfPoint contour : contours) {
                double contourArea = Imgproc.contourArea(contour);
                if (contourArea > areaThreshold) {
                    MatOfPoint2f contourFloat = MathUtils.toMatOfPointFloat(contour);
                    double arcLen = Imgproc.arcLength(contourFloat, true) * arcLenFactor;
                    MatOfPoint2f approx = new MatOfPoint2f();
                    Imgproc.approxPolyDP(contourFloat, approx, arcLen, true);

                    if (isRectangle(approx, srcArea)) {
                        rectangles.add(approx);
                    }
                }
            }
        }

        return rectangles;
    }

    private Mat getModifiedImage(Mat src) {
        String fileName;

        Imgproc.cvtColor(src, src, Imgproc.COLOR_BGR2RGB);

        Mat blurred = new Mat();
//        Imgproc.medianBlur(src, blurred, 3);
        Imgproc.GaussianBlur(src, blurred, new Size(5, 5), 0);
        fileName = "/blurred.jpg";
        saveImage(blurred, fileName);

        Mat hsvImage = new Mat();
        Imgproc.cvtColor(blurred, hsvImage, Imgproc.COLOR_BGR2HSV);

        List<Mat> hsvChannels = new ArrayList<>();
        Core.split(hsvImage, hsvChannels);
        Mat hue = hsvChannels.get(0);
        Mat saturation = hsvChannels.get(1);
        Mat value = hsvChannels.get(2);

        double delta_value =  -0.05 * Core.mean(value).val[0];

        Mat modifiedHue = hue.clone();
        Mat modifiedSaturation = saturation.clone();
        Mat modifiedValue = new Mat();

        modifiedHue.setTo(Scalar.all(180));
        modifiedSaturation.setTo(Scalar.all(110));

        Core.add(value, Scalar.all(delta_value), modifiedValue);

        hsvChannels.set(0, modifiedHue);
        hsvChannels.set(1, modifiedSaturation);
        hsvChannels.set(2, modifiedValue);
        Core.merge(hsvChannels, hsvImage);

        Mat processedImage = new Mat();
        Imgproc.cvtColor(hsvImage, processedImage, Imgproc.COLOR_HSV2BGR);

        fileName = "/hsv.jpg";
        saveImage(processedImage, fileName);

        Mat cannyImage = new Mat();
        Imgproc.Canny(processedImage, cannyImage, 40, 80);

        fileName = "/canny.jpg";
        saveImage(cannyImage, fileName);

        Mat dilatedImage = new Mat();
        Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.dilate(cannyImage, dilatedImage, dilateElement);

        fileName = "/dilate.jpg";
        saveImage(dilatedImage, fileName);

//        Imgproc.cvtColor(processedImage, processedImage, Imgproc.COLOR_BGR2GRAY);
//        Mat adaptiveImage = new Mat();
//        Imgproc.adaptiveThreshold(processedImage, adaptiveImage, 255.0,
//                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
//                Imgproc.THRESH_BINARY, 45, 3.0);

//        fileName = "/adaptiveThreshold.jpg";
//        saveImage(adaptiveImage, fileName);

        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(dilatedImage, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

        Mat contoursImage = src.clone();

        double areaThreshold = 500;

        for (int i = 0; i < contours.size(); i++) {
            double contourArea = Imgproc.contourArea(contours.get(i));
            if (contourArea > areaThreshold) {
                Imgproc.drawContours(contoursImage, contours, i, new Scalar(0, 255, 0), 2);
            }
        }

        fileName = "/contours.jpg";
        saveImage(contoursImage, fileName);

        return dilatedImage;
    }


    public void saveImage(Mat image, String imageName) {
        // Define the directory path
        String directoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
        directoryPath += "/camScan";

        // Create the directory if it doesn't exist
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // Create the full file path
        String filePath = directoryPath + imageName;

        // Save the image
        Imgcodecs.imwrite(filePath, image);
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

        return !(maxCosine >= 0.1);
    }

}
