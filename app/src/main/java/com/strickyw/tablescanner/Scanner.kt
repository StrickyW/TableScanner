package com.strickyw.tablescanner

import android.graphics.*
import android.util.Log
import androidx.annotation.ColorInt
import kotlin.math.absoluteValue
import com.strickyw.tablescanner.Scanner.Direction.*
import com.strickyw.tablescanner.Scanner.Status.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Base class to search for table in bitmap, using [scan] method.
 * Constructor accepts [colorTolerance] which defines how much difference should be between colors to consider them as different.
 * It is used to find table lines on background.
 *
 * Created by Vladimir Baldin on 14.10.2020.
 */
class Scanner(val colorTolerance: Int) {

    enum class Direction {
        LEFT_RIGHT,
        RIGHT_LEFT,
        TOP_BOTTOM,
        BOTTOM_TOP
    }

    companion object {
        private val TAG = Scanner.javaClass.simpleName
    }

    // what the scanner currently sees
    private enum class Status {
        LINE, // a number of the scanner pixels is colored (to the right or left of the middle)
        BACKGROUND, // background only (no colored pixels)
        POINT // a few pixels in the middle are colored (up to line width)
    }

    private fun Int.equals(@ColorInt color: Int, tolerance: Int): Boolean {
        return (Color.red(this) - Color.red(color)).absoluteValue < tolerance &&
                (Color.green(this) - Color.green(color)).absoluteValue < tolerance &&
                (Color.blue(this) - Color.blue(color)).absoluteValue < tolerance
    }

    /**
     * Finds table cells in bitmap.
     *
     * @param [bitmap] bitmap containing table.
     * @param [start] initial point to start scanning.
     * @param [direction] initial direction to search for table.
     * @param [drawCells] display cells found.
     * @param [drawVertices] display vertices found.
     *
     * @return list of cells found.
     */
    fun scan(bitmap: Bitmap, start: Point, direction: Direction, drawCells: Boolean = false, drawVertices: Boolean = false): List<Cell> {
        val bgColor: Int = bitmap.getPixel(start.x, start.y) // obtain background color
        var scanWidth = 20 // set initial scanner array size, will be changed depending on line width
        Log.v(TAG, "bg: (${Color.red(bgColor)}, ${Color.green(bgColor)}, ${Color.blue(bgColor)})")
        var canvas = Canvas(bitmap)
        canvas.drawCircle(start.x.toFloat(), start.y.toFloat(), 5F, Paint().apply {
            color = Color.CYAN
            strokeWidth = 1F
        })
        val line = findLine(bitmap, bgColor, start, direction, scanWidth) // try to find line
        if (line != null) {
            Log.v(TAG, "Found line, width: ${line.width}")
//            canvas.drawCircle(line.point.x.toFloat(), line.point.y.toFloat(), 5F, Paint().apply {
//                color = Color.RED
//                strokeWidth = 1F
//            })
            val lineWidth = line.width
            scanWidth = 5 * lineWidth // change scanner width depending on line width
            val d = when (direction) { // initialize direction to investigate table
                LEFT_RIGHT, RIGHT_LEFT -> BOTTOM_TOP
                TOP_BOTTOM, BOTTOM_TOP -> LEFT_RIGHT
            }
            val foundVertices = mutableListOf<Vertex>() // collection to save discovered vertices
            val processedVertices = mutableListOf<Vertex>() // vertices with investigated neighbors
            val firstVertex = findVertex(bitmap, bgColor, lineWidth, line.point, d, scanWidth)
            if (firstVertex != null) {
                foundVertices.add(firstVertex)
                while (foundVertices.isNotEmpty()) {
                    val vertex = foundVertices.removeAt(0)
                    findVertex(bitmap, bgColor, lineWidth, Point(vertex.x, vertex.y + 2 * lineWidth), TOP_BOTTOM, scanWidth)?.let {
                        handleNewVertex(it, vertex, foundVertices, processedVertices)
                    }
                    findVertex(bitmap, bgColor, lineWidth, Point(vertex.x, vertex.y - 2 * lineWidth), BOTTOM_TOP, scanWidth)?.let {
                        handleNewVertex(it, vertex, foundVertices, processedVertices)
                    }
                    findVertex(bitmap, bgColor, lineWidth, Point(vertex.x + 2 * lineWidth, vertex.y), LEFT_RIGHT, scanWidth)?.let {
                        handleNewVertex(it, vertex, foundVertices, processedVertices)
                    }
                    findVertex(bitmap, bgColor, lineWidth, Point(vertex.x - 2 * lineWidth, vertex.y), RIGHT_LEFT, scanWidth)?.let {
                        handleNewVertex(it, vertex, foundVertices, processedVertices)
                    }
                    processedVertices.add(vertex)
                }
            }
            Log.v(TAG, "Found vertices: ${processedVertices.size}")
            val cells = findCells(processedVertices)
            Log.v(TAG, "Found cells: ${cells.size}")
            if (drawVertices)
                drawVertices(canvas, processedVertices)
            if (drawCells)
                drawCells(canvas, cells)
            return cells
        } else {
            return listOf()
        }
    }

    private fun drawVertices(canvas: Canvas, vertices: List<Vertex>) {
        val vertexPaint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 2F
        }
        vertices.forEach {
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), 3F, vertexPaint)
        }
    }

    private fun drawCells(canvas: Canvas, cells: List<Cell>) {
        val cellPaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 2F
        }
        cells.forEach {
            canvas.drawLine(
                    it.topLeft.x.toFloat(),
                    it.topLeft.y.toFloat(),
                    it.topRight.x.toFloat(),
                    it.topRight.y.toFloat(),
                    cellPaint
            )
            canvas.drawLine(
                    it.topRight.x.toFloat(),
                    it.topRight.y.toFloat(),
                    it.bottomRight.x.toFloat(),
                    it.bottomRight.y.toFloat(),
                    cellPaint
            )
            canvas.drawLine(
                    it.bottomRight.x.toFloat(),
                    it.bottomRight.y.toFloat(),
                    it.bottomLeft.x.toFloat(),
                    it.bottomLeft.y.toFloat(),
                    cellPaint
            )
            canvas.drawLine(
                    it.bottomLeft.x.toFloat(),
                    it.bottomLeft.y.toFloat(),
                    it.topLeft.x.toFloat(),
                    it.topLeft.y.toFloat(),
                    cellPaint
            )
        }
    }

    /**
     * Handles new vertex found adding it to list, linking with previous one.
     *
     * @param [newVertex] new vertex
     * @param [startVertex] origin of discovering, i.e. one of neighbors.
     * @param [foundVertices] collection to save discovered vertices.
     * @param [processedVertices] vertices with investigated neighbors.
     */
    private fun handleNewVertex(newVertex: Vertex, startVertex: Vertex, foundVertices: MutableList<Vertex>, processedVertices: List<Vertex>) {
        var foundVertex: Vertex? = processedVertices.firstOrNull { it == newVertex }
        if (foundVertex == null)
            foundVertex = foundVertices.firstOrNull { it == newVertex }
        if (foundVertex == null) {
            foundVertex = newVertex
            foundVertices.add(foundVertex)
        }
        startVertex.addNeighbor(foundVertex)
        foundVertex.addNeighbor(startVertex)
    }

    /**
     * Finds line starting from [start] and moving in [direction].
     *
     * @param [bitmap] bitmap containing table.
     * @param [bgColor] background color.
     * @param [start] start point for search.
     * @param [direction] direction for search.
     * @param [scanWidth] number of points in scanner array.
     */
    private fun findLine(bitmap: Bitmap, @ColorInt bgColor: Int, start: Point, direction: Direction, scanWidth: Int): Line? {
        val points = Array(scanWidth) {
            var x = 0
            var y = 0
            when (direction) {
                LEFT_RIGHT, RIGHT_LEFT -> {
                    x = start.x
                    y = start.y - scanWidth / 2 + it
                }
                TOP_BOTTOM, BOTTOM_TOP -> {
                    x = start.x - scanWidth / 2 + it
                    y = start.y
                }
            }
            Point(x, y)
        }
        var startEdgePoint: Point? = null
        var endEdgePoint: Point? = null
        when (direction) {
            LEFT_RIGHT -> {
                while (points[0].x < bitmap.width) {
                    when (checkPixels(bitmap, bgColor, scanWidth / 5, scanWidth, points)) {
                        LINE -> {
                            if (startEdgePoint == null)
                                startEdgePoint = Point(points[scanWidth / 2])
                        }
                        else -> {
                            if (startEdgePoint != null) {
                                endEdgePoint = Point(points[scanWidth / 2])
                                val point = Point((startEdgePoint.x + endEdgePoint.x) / 2, (startEdgePoint.y + endEdgePoint.y) / 2)
                                val lineWidth = sqrt((endEdgePoint.x - startEdgePoint.x).toFloat().pow(2) + (endEdgePoint.y - startEdgePoint.y).toFloat().pow(2)).toInt()
                                return Line(point, lineWidth)
                            }
                        }
                    }
                    points.forEach {
                        it.x++
                    }
                }
            }
            RIGHT_LEFT -> {
                while (points[0].x > 0) {
                    when (checkPixels(bitmap, bgColor, scanWidth / 5, scanWidth, points)) {
                        LINE -> {
                            if (startEdgePoint == null)
                                startEdgePoint = Point(points[scanWidth / 2])
                        }
                        else -> {
                            if (startEdgePoint != null) {
                                endEdgePoint = Point(points[scanWidth / 2])
                                val point = Point((startEdgePoint.x + endEdgePoint.x) / 2, (startEdgePoint.y + endEdgePoint.y) / 2)
                                val lineWidth = sqrt((endEdgePoint.x - startEdgePoint.x).toFloat().pow(2) + (endEdgePoint.y - startEdgePoint.y).toFloat().pow(2)).toInt()
                                return Line(point, lineWidth)
                            }
                        }
                    }
                    points.forEach {
                        it.x--
                    }
                }
            }
            TOP_BOTTOM -> {
                while (points[0].y < bitmap.height) {
                    when (checkPixels(bitmap, bgColor, scanWidth / 5, scanWidth, points)) {
                        LINE -> {
                            if (startEdgePoint == null)
                                startEdgePoint = Point(points[scanWidth / 2])
                        }
                        else -> {
                            if (startEdgePoint != null) {
                                endEdgePoint = Point(points[scanWidth / 2])
                                val point = Point((startEdgePoint.x + endEdgePoint.x) / 2, (startEdgePoint.y + endEdgePoint.y) / 2)
                                val lineWidth = sqrt((endEdgePoint.x - startEdgePoint.x).toFloat().pow(2) + (endEdgePoint.y - startEdgePoint.y).toFloat().pow(2)).toInt()
                                return Line(point, lineWidth)
                            }
                        }
                    }
                    points.forEach {
                        it.y++
                    }
                }
            }
            BOTTOM_TOP -> {
                while (points[0].y > 0) {
                    when (checkPixels(bitmap, bgColor, scanWidth / 5, scanWidth, points)) {
                        LINE -> {
                            if (startEdgePoint == null)
                                startEdgePoint = Point(points[scanWidth / 2])
                        }
                        else -> {
                            if (startEdgePoint != null) {
                                endEdgePoint = Point(points[scanWidth / 2])
                                val point = Point((startEdgePoint.x + endEdgePoint.x) / 2, (startEdgePoint.y + endEdgePoint.y) / 2)
                                val lineWidth = sqrt((endEdgePoint.x - startEdgePoint.x).toFloat().pow(2) + (endEdgePoint.y - startEdgePoint.y).toFloat().pow(2)).toInt()
                                return Line(point, lineWidth)
                            }
                        }
                    }
                    points.forEach {
                        it.y--
                    }
                }
            }
        }
        return null
    }

    /**
     * Finds vertex starting from [start] and moving in [direction] along table line.
     *
     * @param [bitmap] bitmap containing table.
     * @param [bgColor] background color.
     * @param [lineWidth] table border width.
     * @param [start] start point for search.
     * @param [direction] direction for search.
     * @param [scanWidth] number of points in scanner array.
     *
     * @return vertex or null if nothing has been found.
     */
    private fun findVertex(bitmap: Bitmap, @ColorInt bgColor: Int, lineWidth: Int, start: Point, direction: Direction, scanWidth: Int): Vertex? {
        val points = Array(scanWidth) {
            var x = 0
            var y = 0
            when (direction) {
                LEFT_RIGHT, RIGHT_LEFT -> {
                    x = start.x
                    y = start.y - scanWidth / 2 + it
                }
                TOP_BOTTOM, BOTTOM_TOP -> {
                    x = start.x - scanWidth / 2 + it
                    y = start.y
                }
            }
            Point(x, y)
        }
        when (direction) {
            LEFT_RIGHT -> {
                while (points[0].x < bitmap.width) {
                    when (checkPixels(bitmap, bgColor, lineWidth, scanWidth, points)) {
                        BACKGROUND -> return null
                        LINE -> return Vertex(points[scanWidth / 2].x, points[scanWidth / 2].y)
                    }
                    points.forEach {
                        it.x++
                    }
                }
            }
            RIGHT_LEFT -> {
                while (points[0].x > 0) {
                    when (checkPixels(bitmap, bgColor, lineWidth, scanWidth, points)) {
                        BACKGROUND -> return null
                        LINE -> return Vertex(points[scanWidth / 2].x, points[scanWidth / 2].y)
                    }
                    points.forEach {
                        it.x--
                    }
                }
            }
            TOP_BOTTOM -> {
                while (points[0].y < bitmap.height) {
                    when (checkPixels(bitmap, bgColor, lineWidth, scanWidth, points)) {
                        BACKGROUND -> return null
                        LINE -> return Vertex(points[scanWidth / 2].x, points[scanWidth / 2].y)
                    }
                    points.forEach {
                        it.y++
                    }
                }
            }
            BOTTOM_TOP -> {
                while (points[0].y > 0) {
                    when (checkPixels(bitmap, bgColor, lineWidth, scanWidth, points)) {
                        BACKGROUND -> return null
                        LINE -> return Vertex(points[scanWidth / 2].x, points[scanWidth / 2].y)
                    }
                    points.forEach {
                        it.y--
                    }
                }
            }
        }
        return null
    }

    /**
     * Checks current scanner state, analyzing array of pixels.
     * @param [bitmap] bitmap containing table.
     * @param [bgColor] background color.
     * @param [lineWidth] table border width.
     * @param [scanWidth] number of points in scanner array.
     * @param [points] scanner points.
     *
     * @return current status, one of [Status].
     */
    private fun checkPixels(bitmap: Bitmap, @ColorInt bgColor: Int, lineWidth: Int, scanWidth: Int, points: Array<Point>): Status {
        val pixelStatuses = BooleanArray(scanWidth)
        for (i in 0 until scanWidth) {
            val color = bitmap.getPixel(points[i].x, points[i].y)
            pixelStatuses[i] = !color.equals(bgColor, colorTolerance)
        }
        if (pixelStatuses.none { it }) {
            return BACKGROUND
        } else {
            var found = true
            for (i in (scanWidth / 2 - 2 * lineWidth)..(scanWidth / 2)) {
                if (!pixelStatuses[i]) {
                    found = false
                    break
                }
            }
            if (!found) {
                found = true
                for (i in (scanWidth / 2)..(scanWidth / 2 + 2 * lineWidth)) {
                    if (!pixelStatuses[i]) {
                        found = false
                        break
                    }
                }
            }
            if (found) {
                return LINE
            } else {
                return POINT
            }
        }
    }

    /**
     * Finds cells based on list of vertices.
     *
     * @param [vertices] vertices to search for cells.
     *
     * @return list of cells built based on [vertices]
     */
    private fun findCells(vertices: List<Vertex>): List<Cell> {
        val cells = mutableListOf<Cell>()
        for (vertex in vertices) {
            vertex.getNeighbors().forEach { n1 ->
                vertex.getNeighbors().filter { it != n1 }.forEach { n2 ->
                    n1.getNeighbors().filter { it != vertex }.forEach { nn1 ->
                        n2.getNeighbors().filter { it != vertex }.forEach { nn2 ->
                            if (nn1 == nn2) {
                                val cell = Cell(cells.size + 1, vertex, n1, n2, nn1)
                                if (!cells.contains(cell))
                                    cells.add(cell)
                            }
                        }
                    }
                }
            }
        }
        return cells
    }
}