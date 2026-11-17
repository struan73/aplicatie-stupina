package ro.stupina.offline.export

import org.json.JSONArray
import org.json.JSONObject
import ro.stupina.offline.data.db.*
import ro.stupina.offline.data.repository.ApiaryBackupPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupAndReportManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ro", "RO"))

    fun generateHivesCsv(hives: List<HiveEntity>, queens: List<QueenEntity>): String {
        val sb = StringBuilder("\uFEFF") // UTF-8 BOM pentru diacritice românești în Excel
        sb.appendLine("NrStup,TipFamilie,TipStup,Stare,RamePuiet,TotalRame,Zona,MatcaAn,MatcaOrigine,Arhivat")
        hives.forEach { hive ->
            val q = queens.firstOrNull { it.hiveId == hive.id && it.isActive }
            sb.appendLine(
                listOf(
                    hive.hiveNumber,
                    csvEscape(hive.familyType),
                    csvEscape(hive.hiveType),
                    csvEscape(hive.status),
                    hive.broodFrames,
                    hive.totalFrames,
                    csvEscape(hive.locationZone),
                    q?.hatchYear?.toString() ?: "",
                    csvEscape(q?.origin ?: ""),
                    if (hive.isArchived) "Da" else "Nu"
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun generateObservationsCsv(observations: List<ObservationEntity>): String {
        val sb = StringBuilder("\uFEFF")
        sb.appendLine("DataOra,Stup,Categorie,Observatie,DictareVocala,TemperaturaC,MiereKg")
        observations.forEach { obs ->
            val target = obs.hiveNumberSnapshot?.let { "Stup $it" } ?: "Întreaga stupină"
            sb.appendLine(
                listOf(
                    csvEscape(dateFormat.format(Date(obs.timestamp))),
                    csvEscape(target),
                    csvEscape(obs.category),
                    csvEscape(obs.text),
                    if (obs.isVoiceRecorded) "Da" else "Nu",
                    obs.temperatureCelsius?.toString() ?: "",
                    obs.honeyKg?.toString() ?: ""
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun generateExcelSpreadsheetXml(
        hives: List<HiveEntity>,
        observations: List<ObservationEntity>,
        activities: List<ActivityEntity>
    ): String {
        val rowsHives = hives.joinToString("\n") { h ->
            "<Row>" +
                cell(h.hiveNumber.toString(), "Number") +
                cell(h.familyType) +
                cell(h.hiveType) +
                cell(h.status) +
                cell(h.broodFrames.toString(), "Number") +
                cell(h.locationZone) +
                "</Row>"
        }
        val rowsObs = observations.joinToString("\n") { o ->
            val scope = o.hiveNumberSnapshot?.let { "Stup $it" } ?: "Generală stupină"
            "<Row>" +
                cell(dateFormat.format(Date(o.timestamp))) +
                cell(scope) +
                cell(o.category) +
                cell(o.text) +
                "</Row>"
        }
        val rowsAct = activities.joinToString("\n") { a ->
            val scope = a.hiveNumberSnapshot?.let { "Stup $it" } ?: "Stupină"
            "<Row>" +
                cell(dateFormat.format(Date(a.performedAt))) +
                cell(scope) +
                cell(a.activityType) +
                cell(a.description) +
                cell(a.quantity) +
                "</Row>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <?mso-application progid="Excel.Sheet"?>
            <Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
                      xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">
              <Worksheet ss:Name="Stupi">
                <Table>
                  <Row>${cell("NrStup")}${cell("TipFamilie")}${cell("TipStup")}${cell("Stare")}${cell("RamePuiet")}${cell("Zona")}</Row>
                  $rowsHives
                </Table>
              </Worksheet>
              <Worksheet ss:Name="Observatii">
                <Table>
                  <Row>${cell("DataOra")}${cell("Tintă")}${cell("Categorie")}${cell("Text")}</Row>
                  $rowsObs
                </Table>
              </Worksheet>
              <Worksheet ss:Name="Interventii">
                <Table>
                  <Row>${cell("DataOra")}${cell("Tintă")}${cell("Tip")}${cell("Descriere")}${cell("Cantitate")}</Row>
                  $rowsAct
                </Table>
              </Worksheet>
            </Workbook>
        """.trimIndent()
    }

    fun serializeBackupToJson(payload: ApiaryBackupPayload): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("apiaries", JSONArray().apply {
            payload.apiaries.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("name", a.name)
                    put("location", a.location)
                    put("ownerName", a.ownerName)
                    put("notes", a.notes)
                    put("createdAt", a.createdAt)
                })
            }
        })

        root.put("hives", JSONArray().apply {
            payload.hives.forEach { h ->
                put(JSONObject().apply {
                    put("id", h.id)
                    put("apiaryId", h.apiaryId)
                    put("hiveNumber", h.hiveNumber)
                    put("establishedDate", h.establishedDate)
                    put("familyType", h.familyType)
                    put("hiveType", h.hiveType)
                    put("status", h.status)
                    put("broodFrames", h.broodFrames)
                    put("totalFrames", h.totalFrames)
                    put("locationZone", h.locationZone)
                    put("isArchived", h.isArchived)
                    put("archivedAt", h.archivedAt)
                    put("updatedAt", h.updatedAt)
                })
            }
        })

        root.put("observations", JSONArray().apply {
            payload.observations.forEach { o ->
                put(JSONObject().apply {
                    put("id", o.id)
                    put("apiaryId", o.apiaryId)
                    put("hiveId", o.hiveId)
                    put("hiveNumberSnapshot", o.hiveNumberSnapshot)
                    put("timestamp", o.timestamp)
                    put("category", o.category)
                    put("text", o.text)
                    put("voiceTranscript", o.voiceTranscript)
                    put("isVoiceRecorded", o.isVoiceRecorded)
                    put("temperatureCelsius", o.temperatureCelsius)
                    put("honeyKg", o.honeyKg)
                })
            }
        })

        root.put("queens", JSONArray().apply {
            payload.queens.forEach { q ->
                put(JSONObject().apply {
                    put("id", q.id)
                    put("hiveId", q.hiveId)
                    put("hatchYear", q.hatchYear)
                    put("origin", q.origin)
                    put("breed", q.breed)
                    put("markedColor", q.markedColor)
                    put("notes", q.notes)
                    put("installedAt", q.installedAt)
                    put("isActive", q.isActive)
                })
            }
        })

        root.put("activities", JSONArray().apply {
            payload.activities.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("apiaryId", a.apiaryId)
                    put("hiveId", a.hiveId)
                    put("hiveNumberSnapshot", a.hiveNumberSnapshot)
                    put("activityType", a.activityType)
                    put("description", a.description)
                    put("substanceOrMaterial", a.substanceOrMaterial)
                    put("quantity", a.quantity)
                    put("performedAt", a.performedAt)
                })
            }
        })

        root.put("reminders", JSONArray().apply {
            payload.reminders.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("apiaryId", r.apiaryId)
                    put("hiveId", r.hiveId)
                    put("hiveNumberSnapshot", r.hiveNumberSnapshot)
                    put("title", r.title)
                    put("category", r.category)
                    put("notes", r.notes)
                    put("dueDate", r.dueDate)
                    put("isCompleted", r.isCompleted)
                    put("completedAt", r.completedAt)
                })
            }
        })

        root.put("events", JSONArray().apply {
            payload.events.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("apiaryId", e.apiaryId)
                    put("primaryHiveId", e.primaryHiveId)
                    put("secondaryHiveNumber", e.secondaryHiveNumber)
                    put("eventType", e.eventType)
                    put("description", e.description)
                    put("fromLocation", e.fromLocation)
                    put("toLocation", e.toLocation)
                    put("eventDate", e.eventDate)
                })
            }
        })

        return root.toString(2)
    }

    fun deserializeBackupFromJson(jsonStr: String): ApiaryBackupPayload {
        val root = JSONObject(jsonStr)
        val apiaries = mutableListOf<ApiaryEntity>()
        val apiariesArr = root.getJSONArray("apiaries")
        for (i in 0 until apiariesArr.length()) {
            val o = apiariesArr.getJSONObject(i)
            apiaries.add(
                ApiaryEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    location = o.getString("location"),
                    ownerName = o.getString("ownerName"),
                    notes = o.optString("notes", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val hives = mutableListOf<HiveEntity>()
        val hivesArr = root.getJSONArray("hives")
        for (i in 0 until hivesArr.length()) {
            val o = hivesArr.getJSONObject(i)
            hives.add(
                HiveEntity(
                    id = o.getLong("id"),
                    apiaryId = o.optLong("apiaryId", 1L),
                    hiveNumber = o.getInt("hiveNumber"),
                    establishedDate = o.getLong("establishedDate"),
                    familyType = o.getString("familyType"),
                    hiveType = o.getString("hiveType"),
                    status = o.getString("status"),
                    broodFrames = o.optInt("broodFrames", 0),
                    totalFrames = o.optInt("totalFrames", 10),
                    locationZone = o.optString("locationZone", "Vatră principală"),
                    isArchived = o.optBoolean("isArchived", false),
                    archivedAt = if (o.isNull("archivedAt")) null else o.getLong("archivedAt"),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }

        val observations = mutableListOf<ObservationEntity>()
        val obsArr = root.getJSONArray("observations")
        for (i in 0 until obsArr.length()) {
            val o = obsArr.getJSONObject(i)
            observations.add(
                ObservationEntity(
                    id = o.getLong("id"),
                    apiaryId = o.optLong("apiaryId", 1L),
                    hiveId = if (o.isNull("hiveId")) null else o.getLong("hiveId"),
                    hiveNumberSnapshot = if (o.isNull("hiveNumberSnapshot")) null else o.getInt("hiveNumberSnapshot"),
                    timestamp = o.getLong("timestamp"),
                    category = o.getString("category"),
                    text = o.getString("text"),
                    voiceTranscript = if (o.isNull("voiceTranscript")) null else o.optString("voiceTranscript"),
                    isVoiceRecorded = o.optBoolean("isVoiceRecorded", false),
                    temperatureCelsius = if (o.isNull("temperatureCelsius")) null else o.optDouble("temperatureCelsius"),
                    honeyKg = if (o.isNull("honeyKg")) null else o.optDouble("honeyKg")
                )
            )
        }

        val queens = mutableListOf<QueenEntity>()
        val qArr = root.getJSONArray("queens")
        for (i in 0 until qArr.length()) {
            val o = qArr.getJSONObject(i)
            queens.add(
                QueenEntity(
                    id = o.getLong("id"),
                    hiveId = o.getLong("hiveId"),
                    hatchYear = o.getInt("hatchYear"),
                    origin = o.getString("origin"),
                    breed = o.optString("breed", "Carpatină"),
                    markedColor = o.optString("markedColor", "Alb"),
                    notes = o.optString("notes", ""),
                    installedAt = o.optLong("installedAt", System.currentTimeMillis()),
                    isActive = o.optBoolean("isActive", true)
                )
            )
        }

        val activities = mutableListOf<ActivityEntity>()
        val aArr = root.getJSONArray("activities")
        for (i in 0 until aArr.length()) {
            val o = aArr.getJSONObject(i)
            activities.add(
                ActivityEntity(
                    id = o.getLong("id"),
                    apiaryId = o.optLong("apiaryId", 1L),
                    hiveId = if (o.isNull("hiveId")) null else o.getLong("hiveId"),
                    hiveNumberSnapshot = if (o.isNull("hiveNumberSnapshot")) null else o.getInt("hiveNumberSnapshot"),
                    activityType = o.getString("activityType"),
                    description = o.getString("description"),
                    substanceOrMaterial = o.optString("substanceOrMaterial", ""),
                    quantity = o.optString("quantity", ""),
                    performedAt = o.getLong("performedAt")
                )
            )
        }

        val reminders = mutableListOf<ReminderEntity>()
        val rArr = root.getJSONArray("reminders")
        for (i in 0 until rArr.length()) {
            val o = rArr.getJSONObject(i)
            reminders.add(
                ReminderEntity(
                    id = o.getLong("id"),
                    apiaryId = o.optLong("apiaryId", 1L),
                    hiveId = if (o.isNull("hiveId")) null else o.getLong("hiveId"),
                    hiveNumberSnapshot = if (o.isNull("hiveNumberSnapshot")) null else o.getInt("hiveNumberSnapshot"),
                    title = o.getString("title"),
                    category = o.getString("category"),
                    notes = o.optString("notes", ""),
                    dueDate = o.getLong("dueDate"),
                    isCompleted = o.optBoolean("isCompleted", false),
                    completedAt = if (o.isNull("completedAt")) null else o.getLong("completedAt")
                )
            )
        }

        val events = mutableListOf<EventEntity>()
        val eArr = root.getJSONArray("events")
        for (i in 0 until eArr.length()) {
            val o = eArr.getJSONObject(i)
            events.add(
                EventEntity(
                    id = o.getLong("id"),
                    apiaryId = o.optLong("apiaryId", 1L),
                    primaryHiveId = if (o.isNull("primaryHiveId")) null else o.getLong("primaryHiveId"),
                    secondaryHiveNumber = if (o.isNull("secondaryHiveNumber")) null else o.getInt("secondaryHiveNumber"),
                    eventType = o.getString("eventType"),
                    description = o.getString("description"),
                    fromLocation = if (o.isNull("fromLocation")) null else o.optString("fromLocation"),
                    toLocation = if (o.isNull("toLocation")) null else o.optString("toLocation"),
                    eventDate = o.getLong("eventDate")
                )
            )
        }

        return ApiaryBackupPayload(apiaries, hives, observations, queens, activities, reminders, events)
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun cell(value: String, type: String = "String"): String {
        val safe = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        return "<Cell><Data ss:Type=\"$type\">$safe</Data></Cell>"
    }
}
