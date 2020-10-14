package com.strickyw.tablescanner

import android.util.Log
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Created by Vladimir Baldin on 14.10.2020.
 */
class Vertex(val x: Int, val y: Int) {

    private val neighbors: MutableList<Vertex> = mutableListOf()

    override fun equals(other: Any?): Boolean {
        if (other == null)
            return this == null
        return (other as? Vertex)?.let { abs(it.x - x) < 10 && abs(it.y - y) < 10 } ?: false
    }

    override fun hashCode(): Int {
        return ((x / 10f).roundToInt() * 10).shl(10) + ((y / 10f).roundToInt() * 10)
    }

    fun addNeighbor(vertex: Vertex) {
        if (!neighbors.contains(vertex))
            neighbors.add(vertex)
    }

    fun getNeighbors() = neighbors

    override fun toString(): String {
        return "$x : $y"
    }
}