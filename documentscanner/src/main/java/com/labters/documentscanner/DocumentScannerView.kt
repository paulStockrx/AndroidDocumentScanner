package com.labters.documentscanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnAttach
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.labters.documentscanner.libraries.NativeClass
import com.labters.documentscanner.libraries.PerspectiveTransformation
import com.labters.documentscanner.libraries.PolygonView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc


class DocumentScannerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var holder: FrameLayout
    private lateinit var image: ImageView
    private lateinit var polygonView: PolygonView

    private lateinit var selectedImage: Bitmap
    private var isInitialized = false

    private var onLoad: OnLoadListener? = {
        Log.i(javaClass.simpleName, "loading = $it")
    }

    private val lifecycle: LifecycleOwner
        get() = findViewTreeLifecycleOwner()!!


    private val scope: LifecycleCoroutineScope
        get() = lifecycle.lifecycleScope

    private val nativeClass = NativeClass()

    init {
        inflate(context, R.layout.document_scanner, this).run {
            doOnAttach {
                holder = findViewById(R.id.holder)
                image = findViewById(R.id.image)
                polygonView = findViewById(R.id.polygon_view)
                isInitialized = true
            }
        }
    }

    private fun initView() {
        scope.launch {
            onLoad?.invoke(true)
            setImageRotation()
            initializeCropping()
            onLoad?.invoke(false)
        }
    }

    fun setImage(image: Bitmap) {
        selectedImage = image
        doWhenInitialised { initView() }
    }

    private suspend fun setImageRotation() {
        var tempBitmap = selectedImage.copy(selectedImage.config, true)
        for (i in 1..4) {
            val point2f = nativeClass.getPoint(tempBitmap)
            if (point2f == null) {
                tempBitmap = rotateBitmap(tempBitmap, (90 * i).toFloat()).first()
            } else {
                selectedImage = tempBitmap.copy(selectedImage.config, true)
                break
            }
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Flow<Bitmap> = flow<Bitmap> {
        val matrix = Matrix()
        matrix.postRotate(angle)
        emit(Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true))
    }

    private fun scaledBitmap(bitmap: Bitmap, width: Int, height: Int) = flow<Bitmap> {
        val m = Matrix()
        m.setRectToRect(
            RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), RectF(
                0f, 0f,
                width.toFloat(),
                height.toFloat()
            ), Matrix.ScaleToFit.CENTER
        )
        emit(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true))
    }

//    original
    private suspend fun initializeCropping() {
        val scaledBitmap: Bitmap = scaledBitmap(
            selectedImage,
            holder.width,
            holder.height
        ).first()
        image.setImageBitmap(scaledBitmap)
        val tempBitmap = (image.drawable as BitmapDrawable).bitmap

        val pointFs = getEdgePoints(tempBitmap)
        polygonView.points = pointFs
        polygonView.visibility = VISIBLE
        val padding = resources.getDimension(R.dimen.scanPadding).toInt() * 2
        val layoutParams =
            LayoutParams(tempBitmap.width + padding, tempBitmap.height + padding)
        layoutParams.gravity = Gravity.CENTER
        polygonView.layoutParams = layoutParams
        polygonView.setPointColor(ContextCompat.getColor(context, R.color.blue))
    }

    private fun getEdgePoints(tempBitmap: Bitmap): Map<Int, PointF>? {
        val pointFs: List<PointF> = getContourEdgePoints(tempBitmap)
        return orderedValidEdgePoints(tempBitmap, pointFs)
    }

    private fun getContourEdgePoints(tempBitmap: Bitmap): List<PointF> {
        var point2f = nativeClass.getPoint(tempBitmap)
        if (point2f == null) point2f = MatOfPoint2f()
        val points = listOf(*point2f.toArray())
        val result: MutableList<PointF> = ArrayList()
        for (i in points.indices) {
            result.add(PointF(points[i].x.toFloat(), points[i].y.toFloat()))
        }
        return result
    }

    private fun getOutlinePoints(tempBitmap: Bitmap): Map<Int, PointF> {
        val outlinePoints: MutableMap<Int, PointF> = HashMap()
        outlinePoints[0] = PointF(0f, 0f)
        outlinePoints[1] = PointF(tempBitmap.width.toFloat(), 0f)
        outlinePoints[2] = PointF(0f, tempBitmap.height.toFloat())
        outlinePoints[3] = PointF(tempBitmap.width.toFloat(), tempBitmap.height.toFloat())
        return outlinePoints
    }

    private fun orderedValidEdgePoints(
        tempBitmap: Bitmap,
        pointFs: List<PointF>
    ): Map<Int, PointF>? {
        var orderedPoints: Map<Int, PointF>? = polygonView.getOrderedPoints(pointFs)
        if (!polygonView.isValidShape(orderedPoints)) {
            orderedPoints = getOutlinePoints(tempBitmap)
        }
        return orderedPoints
    }
    @Throws
    fun getCroppedImage(): Bitmap {
        val perspectiveTransformation = PerspectiveTransformation()

        val points: Map<Int, PointF> = polygonView.points
        val xRatio: Float = selectedImage.width.toFloat() / image.width
        val yRatio: Float = selectedImage.height.toFloat() / image.height
        val x1 = points[0]!!.x * xRatio
        val x2 = points[1]!!.x * xRatio
        val x3 = points[2]!!.x * xRatio
        val x4 = points[3]!!.x * xRatio
        val y1 = points[0]!!.y * yRatio
        val y2 = points[1]!!.y * yRatio
        val y3 = points[2]!!.y * yRatio
        val y4 = points[3]!!.y * yRatio

        val point1 = Point(x1.toDouble(), y1.toDouble())
        val point2 = Point(x2.toDouble(), y2.toDouble())
        val point3 = Point(x3.toDouble(), y3.toDouble())
        val point4 = Point(x4.toDouble(), y4.toDouble())
        val cornerPoints = MatOfPoint2f(point1, point2, point3, point4)

        val selectedImageMat = Mat(selectedImage.width, selectedImage.height, CvType.CV_8UC1)
        Utils.bitmapToMat(selectedImage, selectedImageMat)

        val resultMat = perspectiveTransformation.transform(selectedImageMat, cornerPoints)

        val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resultMat, resultBitmap)

        val grayMat = Mat()
        Imgproc.cvtColor(resultMat, grayMat, Imgproc.COLOR_RGB2GRAY)

        Imgproc.adaptiveThreshold(grayMat, grayMat, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY, 31, 7.0)

        val finalBitmap = Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayMat, finalBitmap)

        val finalMat = Mat()
        Utils.bitmapToMat(finalBitmap, finalMat)
        nativeClass.saveImage(finalMat, "/final_norm.jpg")
    nativeClass.saveImage(resultMat, "/final.jpg")

        resultMat.release()
        grayMat.release()
        selectedImageMat.release()

        return resultBitmap
    }

    private fun doWhenInitialised(function: () -> Unit) {
        scope.launch {
            while (isInitialized.not()) {
                delay(500L)
            }
            function()
        }
    }

    fun setOnLoadListener(listener: OnLoadListener?) {
        onLoad = listener
    }
}
