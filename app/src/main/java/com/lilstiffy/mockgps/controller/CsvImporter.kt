package com.lilstiffy.mockgps.controller

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.android.gms.maps.model.LatLng
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object CsvImporter {

    private val TIMESTAMP_FORMATTERS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
    )

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
    )

    private val TIME_FORMATTERS = listOf(
        DateTimeFormatter.ISO_LOCAL_TIME,
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("HH:mm"),
    )

    @Throws(IOException::class)
    fun parse(context: Context, uri: Uri): List<RoutePoint> {
        val resolver: ContentResolver = context.contentResolver
        return resolver.openInputStream(uri)?.use { inputStream ->
            parse(inputStream)
        } ?: emptyList()
    }

    @Throws(IOException::class)
    fun parse(inputStream: InputStream): List<RoutePoint> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val rows = reader.readLines().filter { it.isNotBlank() }
        if (rows.isEmpty()) return emptyList()

        var dataStartIndex = 0
        val firstRowValues = splitRow(rows.first())
        val headers = if (looksLikeHeader(firstRowValues)) {
            dataStartIndex = 1
            firstRowValues.map { it.lowercase() }
        } else {
            null
        }

        val latIdx = headerIndex(headers, listOf("lat", "latitude")) ?: 0
        val lonIdx = headerIndex(headers, listOf("lon", "lng", "longitude")) ?: 1
        val labelIdx = headerIndex(headers, listOf("label", "name", "title"))
        val timestampIdx = headerIndex(headers, listOf("timestamp", "datetime", "date_time"))
        val dateIdx = headerIndex(headers, listOf("date"))
        val timeIdx = headerIndex(headers, listOf("time", "hour"))

        val points = mutableListOf<RoutePoint>()

        for (index in dataStartIndex until rows.size) {
            val values = splitRow(rows[index])
            if (values.size <= maxOf(latIdx, lonIdx)) continue

            val lat = values.getOrNull(latIdx)?.toDoubleOrNull() ?: continue
            val lon = values.getOrNull(lonIdx)?.toDoubleOrNull() ?: continue
            val label = labelIdx?.let { values.getOrNull(it) }?.takeIf { it.isNotBlank() }

            val timestamp = parseTimestamp(
                full = timestampIdx?.let { values.getOrNull(it) },
                date = dateIdx?.let { values.getOrNull(it) },
                time = timeIdx?.let { values.getOrNull(it) }
            )

            points += RoutePoint(
                position = LatLng(lat, lon),
                label = label,
                timestamp = timestamp
            )
        }

        return points
    }

    private fun parseTimestamp(full: String?, date: String?, time: String?): LocalDateTime? {
        full?.let { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                TIMESTAMP_FORMATTERS.forEach { formatter ->
                    try {
                        return LocalDateTime.parse(trimmed, formatter)
                    } catch (_: DateTimeParseException) {
                        // try next formatter
                    }
                }
            }
        }

        val parsedDate = date?.trim()?.takeIf { it.isNotEmpty() }?.let { rawDate ->
            DATE_FORMATTERS.firstNotNullOfOrNull { formatter ->
                try {
                    LocalDate.parse(rawDate, formatter)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }

        val parsedTime = time?.trim()?.takeIf { it.isNotEmpty() }?.let { rawTime ->
            TIME_FORMATTERS.firstNotNullOfOrNull { formatter ->
                try {
                    LocalTime.parse(rawTime, formatter)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }

        return when {
            parsedDate != null && parsedTime != null -> LocalDateTime.of(parsedDate, parsedTime)
            parsedDate != null -> LocalDateTime.of(parsedDate, LocalTime.MIDNIGHT)
            else -> null
        }
    }

    private fun splitRow(row: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (char in row) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                }

                char == ',' && !inQuotes -> {
                    result += sb.toString().trim()
                    sb.clear()
                }

                else -> sb.append(char)
            }
        }
        result += sb.toString().trim()
        return result
    }

    private fun looksLikeHeader(values: List<String>): Boolean {
        return values.any { value ->
            val lower = value.lowercase()
            lower.contains("lat") || lower.contains("lon") || lower.contains("label")
        }
    }

    private fun headerIndex(headers: List<String>?, aliases: List<String>): Int? {
        headers ?: return null
        aliases.forEach { alias ->
            val idx = headers.indexOfFirst { it == alias }
            if (idx >= 0) return idx
        }
        return null
    }
}
