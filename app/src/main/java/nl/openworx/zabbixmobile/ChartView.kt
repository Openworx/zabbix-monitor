/*
 * Openworx Mobile for Zabbix
 * Author: Openworx <info@openworx.nl>
 */
package nl.openworx.zabbixmobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Simple vector line chart in dark Zabbix style. */
class ChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<Pair<Long, Double>> = emptyList()
    var units: String = ""

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7499FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#337499FF")
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#22FFFFFF")
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9AAAB8")
        textSize = 26f
    }
    private val bgPaint = Paint().apply { color = Color.parseColor("#1F2A33") }

    fun setData(data: List<Pair<Long, Double>>, units: String) {
        this.points = data
        this.units = units
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, bgPaint)

        if (points.size < 2) {
            canvas.drawText(context.getString(R.string.no_data), w / 2 - 60, h / 2, textPaint)
            return
        }

        val padL = 110f
        val padR = 20f
        val padT = 24f
        val padB = 54f
        val plotW = w - padL - padR
        val plotH = h - padT - padB

        var min = points.minOf { it.second }
        var max = points.maxOf { it.second }
        if (min == max) { min -= 1.0; max += 1.0 }
        val range = max - min
        min -= range * 0.05
        max += range * 0.05

        val t0 = points.first().first
        val t1 = points.last().first
        val tSpan = (t1 - t0).coerceAtLeast(1)

        fun x(t: Long) = padL + (t - t0).toFloat() / tSpan * plotW
        fun y(v: Double) = padT + ((max - v) / (max - min)).toFloat() * plotH

        // Grid lines + y-axis labels
        for (i in 0..4) {
            val gy = padT + plotH * i / 4f
            canvas.drawLine(padL, gy, w - padR, gy, gridPaint)
            val value = max - (max - min) * i / 4.0
            canvas.drawText(shortVal(value), 8f, gy + 9f, textPaint)
        }

        // Line + fill
        val path = Path()
        val fill = Path()
        points.forEachIndexed { i, p ->
            val px = x(p.first)
            val py = y(p.second)
            if (i == 0) { path.moveTo(px, py); fill.moveTo(px, h - padB) ; fill.lineTo(px, py) }
            else { path.lineTo(px, py); fill.lineTo(px, py) }
        }
        fill.lineTo(x(t1), h - padB)
        fill.close()
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(path, linePaint)

        // Time labels
        val fmt = if (tSpan > 2 * 86400) SimpleDateFormat("d MMM", Locale.getDefault())
        else SimpleDateFormat("HH:mm", Locale.getDefault())
        canvas.drawText(fmt.format(Date(t0 * 1000)), padL, h - 16f, textPaint)
        val endLabel = fmt.format(Date(t1 * 1000))
        canvas.drawText(endLabel, w - padR - textPaint.measureText(endLabel), h - 16f, textPaint)
    }

    private fun shortVal(v: Double): String {
        val a = kotlin.math.abs(v)
        return when {
            a >= 1e12 -> String.format(Locale.US, "%.1fT", v / 1e12)
            a >= 1e9 -> String.format(Locale.US, "%.1fG", v / 1e9)
            a >= 1e6 -> String.format(Locale.US, "%.1fM", v / 1e6)
            a >= 1e3 -> String.format(Locale.US, "%.1fK", v / 1e3)
            a >= 100 -> String.format(Locale.US, "%.0f", v)
            else -> String.format(Locale.US, "%.2f", v)
        }
    }
}
