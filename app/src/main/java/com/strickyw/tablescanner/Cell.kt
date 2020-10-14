package com.strickyw.tablescanner

import android.util.Log

/**
 * Created by Vladimir Baldin on 14.10.2020.
 */
class Cell(val id: Int, vararg vertices: Vertex) {

    var topLeft: Vertex
    var topRight: Vertex
    var bottomRight: Vertex
    var bottomLeft: Vertex

    init {
        vertices.sortWith(Comparator { v1, v2 ->
            v1.x - v2.x
        })
        vertices.sortWith(Comparator { v1, v2 ->
            v1.y - v2.y
        })
        topLeft = vertices[0]
        topRight = vertices[1]
        bottomLeft = vertices[2]
        bottomRight = vertices[3]
    }

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return this == null
        return (other as? Cell)?.let {
            topLeft == it.topLeft && topRight == it.topRight && bottomLeft == it.bottomLeft && bottomRight == it.bottomRight
        } ?: false
    }
}