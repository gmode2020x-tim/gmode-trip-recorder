package ca.gmode.triprecorder.export

import ca.gmode.triprecorder.data.PointEntity
import ca.gmode.triprecorder.data.TripEntity
import ca.gmode.triprecorder.settings.AutoRecordingConfig
import ca.gmode.triprecorder.tracking.StationaryTripTrimmer

enum class TripExportFormat(
    val id: String,
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    GPX("gpx", "GPX — navigation apps", "gpx", "application/gpx+xml"),
    KML("kml", "KML — Google Earth", "kml", "application/vnd.google-earth.kml+xml"),
    GEOJSON("geojson", "GeoJSON — maps and GIS", "geojson", "application/geo+json"),
    CSV("csv", "CSV — spreadsheet telemetry", "csv", "text/csv"),
    ;

    companion object {
        fun fromId(id: String?): TripExportFormat? = entries.firstOrNull { it.id == id }
    }
}

object TripFileExporter {
    fun suggestedFileName(trip: TripEntity, format: TripExportFormat): String {
        val safeTitle = trip.title
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .take(48)
            .ifBlank { "trip" }
        val date = trip.startAt.take(10).replace(Regex("[^0-9-]"), "").ifBlank { "undated" }
        return "GMODE_${date}_$safeTitle.${format.extension}"
    }

    fun render(
        trip: TripEntity,
        points: List<PointEntity>,
        format: TripExportFormat,
        trimConfig: AutoRecordingConfig? = null,
    ): String {
        val ordered = points.sortedBy { it.sequence }
        val trim = trimConfig?.let { StationaryTripTrimmer.trim(ordered, it) }
        val segments = trim?.segments ?: listOf(ordered)
        return when (format) {
            TripExportFormat.GPX -> renderGpx(trip, segments)
            TripExportFormat.KML -> renderKml(trip, segments)
            TripExportFormat.GEOJSON -> renderGeoJson(trip, segments, trim?.distanceMeters ?: trip.distanceMeters)
            TripExportFormat.CSV -> renderCsv(trip, segments)
        }
    }

    private fun renderGpx(trip: TripEntity, segments: List<List<PointEntity>>) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"GMODE Trip Recorder\" ")
        append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        append("xmlns:gmode=\"https://gmode.ca/trip-recorder/1\" ")
        append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
        append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        append("  <metadata><name>${xml(trip.title)}</name><time>${xml(trip.startAt)}</time></metadata>\n")
        append("  <trk><name>${xml(trip.title)}</name><type>${xml(trip.tripType)}</type>\n")
        segments.forEach { points ->
            append("  <trkseg>\n")
            points.forEach { point ->
                append("    <trkpt lat=\"${number(point.latitude)}\" lon=\"${number(point.longitude)}\">\n")
                point.altitudeMeters?.let { append("      <ele>${number(it)}</ele>\n") }
                append("      <time>${xml(point.recordedAt)}</time>\n")
                val extensions = listOfNotNull(
                    point.accuracyMeters?.let { "<gmode:accuracyMeters>${number(it)}</gmode:accuracyMeters>" },
                    point.speedMps?.let { "<gmode:speedMps>${number(it)}</gmode:speedMps>" },
                    point.bearingDegrees?.let { "<gmode:bearingDegrees>${number(it)}</gmode:bearingDegrees>" },
                    point.satelliteCount?.let { "<gmode:satellites>$it</gmode:satellites>" },
                )
                if (extensions.isNotEmpty()) append("      <extensions>${extensions.joinToString("")}</extensions>\n")
                append("    </trkpt>\n")
            }
            append("  </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    private fun renderKml(trip: TripEntity, segments: List<List<PointEntity>>) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\n")
        append("  <Document><name>${xml(trip.title)}</name><Placemark>\n")
        append("    <name>${xml(trip.title)}</name>\n")
        append("    <description>${xml("${trip.tripType} • ${segments.sumOf { it.size }} exported points")}</description>\n")
        append("    <gx:MultiTrack>\n")
        segments.forEach { points ->
            append("    <gx:Track>\n")
            points.forEach { append("      <when>${xml(it.recordedAt)}</when>\n") }
            points.forEach {
                append("      <gx:coord>${number(it.longitude)} ${number(it.latitude)} ${number(it.altitudeMeters ?: 0.0)}</gx:coord>\n")
            }
            append("    </gx:Track>\n")
        }
        append("    </gx:MultiTrack>\n  </Placemark></Document>\n</kml>\n")
    }

    private fun renderGeoJson(
        trip: TripEntity,
        segments: List<List<PointEntity>>,
        distanceMeters: Double,
    ) = buildString {
        val points = segments.flatten()
        append("{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n    {\n")
        append("      \"type\": \"Feature\",\n      \"properties\": {\n")
        append("        \"id\": ${json(trip.id)},\n")
        append("        \"name\": ${json(trip.title)},\n")
        append("        \"tripType\": ${json(trip.tripType)},\n")
        append("        \"status\": ${json(trip.status)},\n")
        append("        \"startAt\": ${json(trip.startAt)},\n")
        append("        \"endAt\": ${nullableJson(trip.endAt)},\n")
        append("        \"distanceMeters\": ${number(distanceMeters)},\n")
        append("        \"pointCount\": ${points.size},\n")
        append("        \"segmentCount\": ${segments.size},\n")
        append("        \"coordTimes\": [${points.joinToString(",") { json(it.recordedAt) }}],\n")
        append("        \"speedMps\": [${points.joinToString(",") { nullableNumber(it.speedMps) }}],\n")
        append("        \"accuracyMeters\": [${points.joinToString(",") { nullableNumber(it.accuracyMeters) }}]\n")
        append("      },\n      \"geometry\": {\n        \"type\": \"MultiLineString\",\n        \"coordinates\": [")
        append(segments.joinToString(",") { segment ->
            "[${segment.joinToString(",") { "[${number(it.longitude)},${number(it.latitude)},${number(it.altitudeMeters ?: 0.0)}]" }}]"
        })
        append("]\n      }\n    }\n  ]\n}\n")
    }

    private fun renderCsv(trip: TripEntity, segments: List<List<PointEntity>>) = buildString {
        append(
            "trip_id,trip_title,trip_type,segment,sequence,recorded_at,latitude,longitude,accuracy_meters," +
                "altitude_meters,vertical_accuracy_meters,speed_mps,bearing_degrees,pressure_hpa," +
                "acceleration_rms_ms2,acceleration_peak_ms2,acceleration_peak_x_ms2," +
                "acceleration_peak_y_ms2,acceleration_peak_z_ms2,gyroscope_peak_rad_s,battery_percent," +
                "is_charging,network_type,satellite_count,synced\n",
        )
        segments.forEachIndexed { segmentIndex, points ->
            points.forEach { point ->
                append(
                    listOf(
                    csv(trip.id), csv(trip.title), csv(trip.tripType), segmentIndex.toString(), point.sequence.toString(), csv(point.recordedAt),
                    number(point.latitude), number(point.longitude), optional(point.accuracyMeters),
                    optional(point.altitudeMeters), optional(point.verticalAccuracyMeters), optional(point.speedMps),
                    optional(point.bearingDegrees), optional(point.pressureHpa), optional(point.accelerationRmsMs2),
                    optional(point.accelerationPeakMs2), optional(point.accelerationPeakXMs2),
                    optional(point.accelerationPeakYMs2), optional(point.accelerationPeakZMs2),
                    optional(point.gyroscopePeakRadS), optional(point.batteryPercent),
                    point.isCharging.toString(), csv(point.networkType), point.satelliteCount?.toString().orEmpty(),
                    point.synced.toString(),
                    ).joinToString(","),
                )
                append('\n')
            }
        }
    }

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun nullableJson(value: String?): String = value?.let(::json) ?: "null"

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun number(value: Double): String = if (value.isFinite()) value.toString() else "0"

    private fun nullableNumber(value: Double?): String = value?.takeIf { it.isFinite() }?.toString() ?: "null"

    private fun optional(value: Double?): String = value?.takeIf { it.isFinite() }?.toString().orEmpty()
}
