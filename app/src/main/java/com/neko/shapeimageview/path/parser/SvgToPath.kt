package com.neko.shapeimageview.path.parser

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.ArrayDeque
import java.util.Deque
import java.util.HashMap
import kotlin.math.round
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SvgToPath private constructor(private val atts: XmlPullParser) {

    companion object {
        private val TAG = SvgToPath::class.java.simpleName
        private const val DPI = 72.0f
        private val IDENTITY_MATRIX = Matrix()

        @JvmStatic
        fun getSVGFromInputStream(inputStream: InputStream): PathInfo {
            return parse(inputStream, true, DPI)
        }

        private fun parse(input: InputStream, ignoreDefs: Boolean, dpi: Float): PathInfo {
            try {
                val xr: XmlPullParser = Xml.newPullParser()
                val svgHandler = SvgToPath(xr)
                svgHandler.dpi = dpi

                if (ignoreDefs) {
                    xr.setInput(InputStreamReader(input))
                    svgHandler.processSvg()
                } else {
                    val cin = CopyInputStream(input)

                    val ids: XmlPullParser = Xml.newPullParser()
                    ids.setInput(InputStreamReader(cin.copy))
                    val idHandler = IdHandler(ids)
                    idHandler.processIds()
                    svgHandler.idXml = idHandler.idXml

                    xr.setInput(InputStreamReader(cin.copy))
                    svgHandler.processSvg()
                }

                return svgHandler.pathInfo ?: throw RuntimeException("PathInfo is null after parsing")
            } catch (e: Exception) {
                Log.w(TAG, "Parse error: $e")
                throw RuntimeException(e)
            }
        }
    }

    private var idXml = HashMap<String, String>()
    private val rect = RectF()
    private var dpi = DPI
    private var hidden = false
    private var hiddenLevel = 0
    private var inDefsElement = false

    private val pathStack: Deque<Path> = ArrayDeque()
    private val matrixStack: Deque<Matrix> = ArrayDeque()

    private var width = 0f
    private var height = 0f
    
    private lateinit var path: Path 
    private var pathInfo: PathInfo? = null

    @Throws(XmlPullParserException::class, IOException::class)
    fun processSvg() {
        var eventType = atts.eventType
        do {
            when (eventType) {
                XmlPullParser.START_DOCUMENT, XmlPullParser.END_DOCUMENT, XmlPullParser.TEXT -> Unit
                XmlPullParser.START_TAG -> startElement()
                XmlPullParser.END_TAG -> endElement()
            }
            eventType = atts.next()
        } while (eventType != XmlPullParser.END_DOCUMENT)
    }

    private fun pushTransform(atts: XmlPullParser) {
        val transform = ParseUtil.getStringAttr("transform", atts)
        val matrix = if (transform == null) IDENTITY_MATRIX else TransformParser.parseTransform(transform)
        matrixStack.push(matrix)
    }

    private fun pushTransform(pMatrix: Matrix?) {
        val matrix = pMatrix ?: IDENTITY_MATRIX
        matrixStack.push(matrix)
    }

    private fun popTransform(): Matrix {
        return matrixStack.pop()
    }

    private fun pushPath() {
        val newPath = Path()
        this.path = newPath
        pathStack.push(newPath)
    }

    private fun popPath(): Path {
        val poppedPath = pathStack.pop()
        if (pathStack.isNotEmpty()) {
            this.path = pathStack.peek()
        }
        return poppedPath
    }

    private fun startElement() {
        val localName = atts.name

        if (inDefsElement) return

        when (localName) {
            "svg" -> {
                width = round(getFloatAttr("width", atts, 0f) ?: 0f)
                height = round(getFloatAttr("height", atts, 0f) ?: 0f)

                val viewbox = NumberParse.getNumberParseAttr("viewBox", atts)

                pushPath()
                val matrix = Matrix(IDENTITY_MATRIX)

                if (viewbox?.numbers != null && viewbox.numbers.size == 4) {
                    if (width < 0.1f || height < 0.1f) {
                        width = viewbox.numbers[2] - viewbox.numbers[0]
                        height = viewbox.numbers[3] - viewbox.numbers[1]
                    } else {
                        val sx = width / (viewbox.numbers[2] - viewbox.numbers[0])
                        val sy = height / (viewbox.numbers[3] - viewbox.numbers[1])
                        matrix.setScale(sx, sy)
                    }
                }
                pushTransform(matrix)
            }
            "defs" -> {
                inDefsElement = true
            }
            "use" -> {
                val href = ParseUtil.getStringAttr("xlink:href", atts)
                val attTransform = ParseUtil.getStringAttr("transform", atts)
                val attX = ParseUtil.getStringAttr("x", atts)
                val attY = ParseUtil.getStringAttr("y", atts)

                val sb = buildString {
                    append("<g")
                    append(" xmlns='http://www.w3.org/2000/svg' ")
                    append(" xmlns:xlink='http://www.w3.org/1999/xlink' ")
                    append(" xmlns:sodipodi='http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd' ")
                    append(" xmlns:inkscape='http://www.inkscape.org/namespaces/inkscape' version='1.1'")
                    if (attTransform != null || attX != null || attY != null) {
                        append(" transform='")
                        if (attTransform != null) append(ParseUtil.escape(attTransform))
                        if (attX != null || attY != null) {
                            append("translate(")
                            append(attX?.let(ParseUtil::escape) ?: "0")
                            append(",")
                            append(attY?.let(ParseUtil::escape) ?: "0")
                            append(")")
                        }
                        append("'")
                    }

                    for (i in 0 until atts.attributeCount) {
                        val attrQName = atts.getAttributeName(i)
                        if ("x" != attrQName && "y" != attrQName &&
                            "width" != attrQName && "height" != attrQName &&
                            "xlink:href" != attrQName && "transform" != attrQName
                        ) {
                            append(" ")
                            append(attrQName)
                            append("='")
                            append(ParseUtil.escape(atts.getAttributeValue(i)))
                            append("'")
                        }
                    }

                    append(">")
                    if (href != null && href.length > 1) {
                        append(idXml[href.substring(1)])
                    }
                    append("</g>")
                }
            }
            "g" -> {
                if (hidden) hiddenLevel++
                if ("none" == ParseUtil.getStringAttr("display", atts)) {
                    if (!hidden) {
                        hidden = true
                        hiddenLevel = 1
                    }
                }
                pushTransform(atts)
                pushPath()
            }
            "rect" -> {
                if (!hidden) {
                    val x = getFloatAttr("x", atts, 0f) ?: 0f
                    val y = getFloatAttr("y", atts, 0f) ?: 0f
                    val w = getFloatAttr("width", atts) ?: 0f
                    val h = getFloatAttr("height", atts) ?: 0f
                    val rx = getFloatAttr("rx", atts, 0f) ?: 0f
                    val ry = getFloatAttr("ry", atts, 0f) ?: 0f
                    
                    val p = Path()
                    if (rx <= 0f && ry <= 0f) {
                        p.addRect(x, y, x + w, y + h, Path.Direction.CW)
                    } else {
                        rect.set(x, y, x + w, y + h)
                        p.addRoundRect(rect, rx, ry, Path.Direction.CW)
                    }
                    pushTransform(atts)
                    val matrix = popTransform()
                    p.transform(matrix)
                    path.addPath(p)
                }
            }
            "line" -> {
                if (!hidden) {
                    val x1 = getFloatAttr("x1", atts) ?: 0f
                    val x2 = getFloatAttr("x2", atts) ?: 0f
                    val y1 = getFloatAttr("y1", atts) ?: 0f
                    val y2 = getFloatAttr("y2", atts) ?: 0f
                    val p = Path()
                    p.moveTo(x1, y1)
                    p.lineTo(x2, y2)
                    pushTransform(atts)
                    val matrix = popTransform()
                    p.transform(matrix)
                    path.addPath(p)
                }
            }
            "circle" -> {
                if (!hidden) {
                    val centerX = getFloatAttr("cx", atts)
                    val centerY = getFloatAttr("cy", atts)
                    val radius = getFloatAttr("r", atts)
                    if (centerX != null && centerY != null && radius != null) {
                        val p = Path()
                        p.addCircle(centerX, centerY, radius, Path.Direction.CW)
                        pushTransform(atts)
                        val matrix = popTransform()
                        p.transform(matrix)
                        path.addPath(p)
                    }
                }
            }
            "ellipse" -> {
                if (!hidden) {
                    val centerX = getFloatAttr("cx", atts)
                    val centerY = getFloatAttr("cy", atts)
                    val radiusX = getFloatAttr("rx", atts)
                    val radiusY = getFloatAttr("ry", atts)
                    if (centerX != null && centerY != null && radiusX != null && radiusY != null) {
                        rect.set(centerX - radiusX, centerY - radiusY, centerX + radiusX, centerY + radiusY)
                        val p = Path()
                        p.addOval(rect, Path.Direction.CW)
                        pushTransform(atts)
                        val matrix = popTransform()
                        p.transform(matrix)
                        path.addPath(p)
                    }
                }
            }
            "polygon", "polyline" -> {
                if (!hidden) {
                    val numbers = NumberParse.getNumberParseAttr("points", atts)
                    if (numbers != null) {
                        val p = Path()
                        val points = numbers.numbers
                        if (points.size > 1) {
                            p.moveTo(points[0], points[1])
                            var i = 2
                            while (i < points.size) {
                                val x = points[i]
                                val y = points[i + 1]
                                p.lineTo(x, y)
                                i += 2
                            }
                            if (localName == "polygon") p.close()
                            pushTransform(atts)
                            val matrix = popTransform()
                            p.transform(matrix)
                            path.addPath(p)
                        }
                    }
                }
            }
            "path" -> {
                if (!hidden) {
                    val d = ParseUtil.getStringAttr("d", atts)
                    if (d != null) {
                        val p = PathParser.doPath(d)
                        pushTransform(atts)
                        val matrix = popTransform()
                        p.transform(matrix)
                        path.addPath(p)
                    }
                }
            }
            "metadata" -> Unit
            else -> {
                if (!hidden) {
                    Log.d(TAG, "Unrecognized tag: $localName (${showAttributes(atts)})")
                }
            }
        }
    }

    private fun showAttributes(a: XmlPullParser): String {
        val result = StringBuilder()
        for (i in 0 until a.attributeCount) {
            result.append(" ")
            result.append(a.getAttributeName(i))
            result.append("='")
            result.append(a.getAttributeValue(i))
            result.append("'")
        }
        return result.toString()
    }

    private fun endElement() {
        val localName = atts.name
        if (inDefsElement) {
            if (localName == "defs") inDefsElement = false
            return
        }

        if (localName == "svg") {
            val p = popPath()
            val matrix = popTransform()
            p.transform(matrix)
            pathInfo = PathInfo(p, width, height)
        } else if (localName == "g") {
            if (hidden) {
                hiddenLevel--
                if (hiddenLevel == 0) hidden = false
            }
            val p = popPath()
            val matrix = popTransform()
            p.transform(matrix)
            path.addPath(p)
        }
    }

    private fun getFloatAttr(name: String, attributes: XmlPullParser, defaultValue: Float? = null): Float? {
        val result = ParseUtil.convertUnits(name, attributes, dpi, width, height)
        return result ?: defaultValue
    }
}

private class CopyInputStream(original: InputStream) {
    val copy: InputStream

    init {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var read: Int
        var src = original
        while (true) {
            read = src.read(buffer)
            if (read <= 0) break
            baos.write(buffer, 0, read)
        }
        val bytes = baos.toByteArray()
        copy = ByteArrayInputStream(bytes)
    }
}

private class IdHandler(private val parser: XmlPullParser) {
    val idXml: HashMap<String, String> = HashMap()

    fun processIds() {
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    for (i in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(i)
                        if (name.equals("id", ignoreCase = true)) {
                            val id = parser.getAttributeValue(i)
                            idXml[id] = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
        }
    }
}

private class NumberParse(val numbers: FloatArray) {
    companion object {
        fun getNumberParseAttr(name: String, atts: XmlPullParser): NumberParse? {
            val raw = ParseUtil.getStringAttr(name, atts) ?: return null
            val nums = parseNumberList(raw)
            if (nums.isEmpty()) return null
            return NumberParse(nums.toFloatArray())
        }

        private fun parseNumberList(src: String): List<Float> {
            return src.trim()
                .replace(",", " ")
                .split("\\s+".toRegex())
                .mapNotNull { it.trim().toFloatOrNull() }
        }
    }
}

private object TransformParser {
    fun parseTransform(src: String): Matrix {
        val mat = Matrix()
        val cleaned = src.replace("\n", "").trim()
        if (cleaned.isEmpty()) return mat

        val tokens = cleaned.split(")")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (tok in tokens) {
            when {
                tok.startsWith("translate") -> {
                    val nums = extractNumbers(tok)
                    val tx = nums.getOrElse(0) { 0f }
                    val ty = nums.getOrElse(1) { 0f }
                    mat.postTranslate(tx, ty)
                }
                tok.startsWith("scale") -> {
                    val nums = extractNumbers(tok)
                    val sx = nums.getOrElse(0) { 1f }
                    val sy = nums.getOrElse(1) { sx }
                    mat.postScale(sx, sy)
                }
                tok.startsWith("rotate") -> {
                    val nums = extractNumbers(tok)
                    if (nums.size >= 3) {
                        mat.postRotate(nums[0], nums[1], nums[2])
                    } else if (nums.size == 1) {
                        mat.postRotate(nums[0])
                    }
                }
                tok.startsWith("skewX") -> {
                    val nums = extractNumbers(tok)
                    val angle = nums.getOrElse(0) { 0f }
                    mat.postSkew(Math.tan(Math.toRadians(angle.toDouble())).toFloat(), 0f)
                }
                tok.startsWith("skewY") -> {
                    val nums = extractNumbers(tok)
                    val angle = nums.getOrElse(0) { 0f }
                    mat.postSkew(0f, Math.tan(Math.toRadians(angle.toDouble())).toFloat())
                }
                tok.startsWith("matrix") -> {
                    val nums = extractNumbers(tok)
                    if (nums.size == 6) {
                        val values = floatArrayOf(
                            nums[0], nums[2], nums[4],
                            nums[1], nums[3], nums[5],
                            0f, 0f, 1f
                        )
                        val m2 = Matrix()
                        m2.setValues(values)
                        mat.postConcat(m2)
                    }
                }
            }
        }

        return mat
    }

    private fun extractNumbers(token: String): List<Float> {
        val inside = token.substringAfter("(").trim()
        if (inside.isEmpty()) return emptyList()
        return inside.replace(",", " ").split("\\s+".toRegex())
            .mapNotNull { it.toFloatOrNull() }
    }
}

private object ParseUtil {
    private const val XLINK_NS = "http://www.w3.org/1999/xlink"

    fun getStringAttr(name: String, atts: XmlPullParser): String? {
        for (i in 0 until atts.attributeCount) {
            val nm = atts.getAttributeName(i)
            if (nm == name) return atts.getAttributeValue(i)
        }
        val local = if (name.contains(":")) name.substringAfter(":") else name
        for (i in 0 until atts.attributeCount) {
            if (atts.getAttributeName(i) == local) return atts.getAttributeValue(i)
        }
        if (name == "xlink:href") {
            val href = atts.getAttributeValue(XLINK_NS, "href")
            if (href != null) return href
        }
        return null
    }

    fun escape(src: String): String {
        return src.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
            .replace("\"", "&quot;")
    }

    fun convertUnits(name: String, attributes: XmlPullParser, dpi: Float, width: Float, height: Float): Float? {
        val raw = getStringAttr(name, attributes) ?: return null
        val s = raw.trim()
        if (s.endsWith("%")) {
            val v = s.dropLast(1).toFloatOrNull() ?: return null
            return if (name.equals("width", true) ||
                name.equals("x", true) ||
                name.equals("cx", true) ||
                name.equals("rx", true)
            ) {
                width * v / 100f
            } else {
                height * v / 100f
            }
        }
        return when {
            s.endsWith("px") -> s.dropLast(2).toFloatOrNull()
            s.endsWith("pt") -> s.dropLast(2).toFloatOrNull()?.let { it * dpi / 72f }
            s.endsWith("in") -> s.dropLast(2).toFloatOrNull()?.let { it * dpi }
            s.endsWith("cm") -> s.dropLast(2).toFloatOrNull()?.let { it * dpi / 2.54f }
            s.endsWith("mm") -> s.dropLast(2).toFloatOrNull()?.let { it * dpi / 25.4f }
            else -> s.toFloatOrNull()
        }
    }
}

private object PathParser {
    fun doPath(d: String): Path {
        return try {
            androidx.core.graphics.PathParser.createPathFromPathData(d) ?: Path()
        } catch (t: Throwable) {
            Path()
        }
    }
}
