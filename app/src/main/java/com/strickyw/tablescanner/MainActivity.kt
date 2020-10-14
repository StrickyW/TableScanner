package com.strickyw.tablescanner

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.strickyw.tablescanner.R
import com.shockwave.pdfium.PdfiumCore
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.max

/**
 * Created by Vladimir Baldin on 14.10.2020.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = MainActivity::class.simpleName

    private var cells: List<Cell> = listOf()
    private var scale = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        openPdf()
        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                cells.forEach {
                    val rect = Rect(it.topLeft.x, it.topLeft.y, it.bottomRight.x, it.bottomRight.y)
                    if (rect.contains((event.x / scale).toInt(), (event.y / scale).toInt())) {
                        Toast.makeText(this, "Clicked: ${it.id}", Toast.LENGTH_SHORT).show()
                        return@forEach
                    }
                }
            }
            true
        }
    }

    fun openPdf() {
        try {
            val inputStream = assets.open("invoice.pdf")
            val bytes = ByteArray(inputStream.available())
            inputStream.read(bytes)

            val pageNum = 0
            val pdfiumCore = PdfiumCore(this)
            val pdfDocument = pdfiumCore.newDocument(bytes)
            pdfiumCore.openPage(pdfDocument, pageNum)

            val width = pdfiumCore.getPageWidthPoint(pdfDocument, pageNum)
            val height = pdfiumCore.getPageHeightPoint(pdfDocument, pageNum)

            // ARGB_8888 - best quality, high memory usage, higher possibility of OutOfMemoryError
            // RGB_565 - little worse quality, twice less memory usage
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            pdfiumCore.renderPageBitmap(pdfDocument, bitmap, pageNum, 0, 0, width, height)

            cells = Scanner(50).scan(
                    bitmap,
                    Point((0.5 * bitmap.width).toInt(), (0.5 * bitmap.height).toInt()),
                    Scanner.Direction.TOP_BOTTOM,
                    true
            )
            lifecycleScope.launch {
                delay(500)
                scale = max(imageView.width.toFloat() / bitmap.width, imageView.height.toFloat() / bitmap.height)
            }
            imageView.setImageBitmap(bitmap)

            pdfiumCore.closeDocument(pdfDocument) // important!
        } catch (ex: IOException) {
            ex.printStackTrace()
        }

    }
}
