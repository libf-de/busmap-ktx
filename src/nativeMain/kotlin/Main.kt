import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSStringMeta
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen

@Serializable
data class DmrResponse(
//    val locations: List<DmrLocation>,
    val stopEvents: List<DmrStopEvent>
) {
    @Serializable
    data class DmrStopEvent(
        val location: DmrLocation,
        val transportation: DmrTransportation,
        val departureTimePlanned: String,
        val departureTimeBaseTimetable: String? = null,
        val departureTimeEstimated: String? = null,
        val arrivalTimeEstimated: String? = null,
        val arrivalTimePlanned: String? = null,
        val previousLocations: List<DmrRelLocation> = emptyList(),
        val onwardLocations: List<DmrRelLocation> = emptyList()
    ) {
        @Serializable
        data class DmrLocation(
            val parent: DmrParent,
            val coord: List<Double>
        )

        @Serializable
        data class DmrTransportation(
            val number: String,
            val origin: DmrStopName,
            val destination: DmrStopName
        ) {
            @Serializable
            data class DmrStopName(val name: String)
        }

        @Serializable
        data class DmrRelLocation(
            val parent: DmrParent,
            val arrivalTimeEstimated: String? = null,
            val arrivalTimePlanned: String? = null,
            val departureTimeEstimated: String? = null,
            val departureTimePlanned: String? = null,
            val coord: List<Double>
        )

        @Serializable
        data class DmrParent(val id: String)
    }
}

val sm: ShapeManager = ShapeManager()
val tm = TripManager()

suspend fun updateData(): List<PublicTransport> {
    val rsp = client.get("https://westfalenfahrplan.de/nwl-efa/XML_DM_REQUEST?calcOneDirection=1&deleteAssignedStops_dm=1&depSequence=30&depType=stopEvents&doNotSearchForStops=1&genMaps=0&inclMOT_5=true&includeCompleteStopSeq=1&includedMeans=checkbox&itOptionsActive=1&itdDateTimeDepArr=dep&language=de&lineRestriction=400&locationServerActive=1&maxTimeLoop=1&mode=direct&name_dm=de%3A05515%3A41000&nwlDMMacro=1&outputFormat=rapidJSON&ptOptionsActive=1&routeType=LEASTTIME&serverInfo=1&sl3plusDMMacro=1&trITMOTvalue100=10&type_dm=any&useAllStops=1&useRealtime=1&coordOutputFormat=WGS84%5Bdd.ddddd%5D")
    val dmr: DmrResponse = json.decodeFromString(rsp.bodyAsText())

    return dmr.stopEvents.mapNotNull {
        println("Processing Line ${it.transportation.number}")
        val shapeId = tm.findShapeId(it.transportation.number, it.transportation.origin.name, it.transportation.destination.name)
            ?: return@mapNotNull null

        val prevStops = it.previousLocations.toStopList(shapeId)
        val nextStops = it.onwardLocations.toStopList(shapeId)
        val thisStop = listOf(
            Stop(
                id = it.location.parent.id.split(":")[2],
                arrival = it.arrivalTimeEstimated ?: it.arrivalTimePlanned,
                departure = it.departureTimeEstimated ?: it.departureTimePlanned,
                perc = sm.getPercentageDistance(shapeId, it.location.parent.id, Pair(it.location.coord[0], it.location.coord[1]))
            )
        )

        PublicTransport(
            name = it.transportation.number,
            stops = listOf(prevStops, nextStops, thisStop).flatten()
        )
    }
}

private fun List<DmrResponse.DmrStopEvent.DmrRelLocation>.toStopList(shapeId: String): List<Stop> {
    return this.map {
        val id = it.parent.id.split(":")[2]
        Stop(
            id = id,
            arrival = it.arrivalTimeEstimated ?: it.arrivalTimePlanned,
            departure = it.departureTimeEstimated ?: it.departureTimePlanned,
            perc = sm.getPercentageDistance(shapeId, id, Pair(it.coord[0], it.coord[1]))
        )
    }

}


private val json = Json {
    ignoreUnknownKeys = true
}

private val pJson = Json {
    prettyPrint = true
}

private val client = HttpClient(Curl)

fun main() {
    runBlocking {
        println(pJson.encodeToString(
            updateData()
        ))
    }
}
