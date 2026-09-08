package com.ronitgandhi.motionfuel.share

import com.ronitgandhi.motionfuel.domain.model.GeoPoint
import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException

object GpxCodec {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_POINTS = 30_000
    fun read(input: InputStream): List<GeoPoint> {
        val bytes = input.readBytesBounded()
        val xml = bytes.toString(Charsets.UTF_8)
        require(!xml.contains('\u0000') && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "DTD and entity declarations are not supported." }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        builder.setErrorHandler(object : ErrorHandler {
            override fun warning(e: SAXParseException) = Unit
            override fun error(e: SAXParseException) { throw e }
            override fun fatalError(e: SAXParseException) { throw e }
        })
        val document = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xml)))
        require(document.documentElement.localName == "gpx") { "Choose a GPX route file." }
        var nodes = document.getElementsByTagNameNS("*", "trkpt")
        if (nodes.length == 0) nodes = document.getElementsByTagNameNS("*", "rtept")
        require(nodes.length in 2..MAX_POINTS) { "Route must contain 2–30,000 points." }
        return (0 until nodes.length).map { index ->
            val node = nodes.item(index) as org.w3c.dom.Element
            val lat = node.getAttribute("lat").toDouble()
            val lon = node.getAttribute("lon").toDouble()
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "Invalid route coordinates." }
            fun child(name: String) = node.getElementsByTagNameNS("*", name).item(0)?.textContent
            val altitude = child("ele")?.toDouble()?.also { require(it.isFinite() && it in -12000.0..100000.0) }
            GeoPoint(lat, lon, altitude, timestampMillis = child("time")?.let { Instant.parse(it).toEpochMilli() } ?: index * 1000L)
        }
    }
    fun write(name: String, points: List<GeoPoint>): String {
        require(points.size in 2..MAX_POINTS)
        fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"MotionFuel\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>${escape(name)}</name><trkseg>")
            points.forEach { p ->
                require(p.latitude.isFinite() && p.latitude in -90.0..90.0 && p.longitude.isFinite() && p.longitude in -180.0..180.0)
                append("<trkpt lat=\"${p.latitude}\" lon=\"${p.longitude}\">")
                p.altitudeMeters?.takeIf(Double::isFinite)?.let { append("<ele>$it</ele>") }
                append("<time>${Instant.ofEpochMilli(p.timestampMillis)}</time></trkpt>")
            }
            append("</trkseg></trk></gpx>")
        }
    }
    private fun InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            require(output.size() + n <= MAX_BYTES) { "GPX file exceeds 4 MB." }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
}
