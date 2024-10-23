import kotlin.math.max

data class Trip(
    val tripId: String,
    val routeId: String,
    val shapeId: String,
    val headsign: String
)

class TripManager {
    private val trips: List<Trip>
    private val skipIds: MutableList<String> = mutableListOf()
    private val storedTrips: MutableMap<String, String> = mutableMapOf()

    constructor(tripFile: String = "trips.txt") {
        trips = readAllText(tripFile).split("\n").mapIndexedNotNull { index, line ->
            if(index == 0) return@mapIndexedNotNull null
            line.split(",").takeIf { it.size >= 8 }?.let { cols ->
                Trip(
                    tripId = cols[2],
                    routeId = cols[0],
                    shapeId = cols[7],
                    headsign = cols[3]
                )
            }
        }
    }

    fun findShapeId(lineNumber: String, startStation: String, endStation: String): String? {
        fun String.normalize(): String = this
            .lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace(Regex("\\W+"), "")
            .trim()

        fun levenshteinDistance(a: String, b: String): Int {
            var n = a.length
            var m = b.length

            // Ensure n <= m to use O(min(n,m)) space
            var (smallerStr, largerStr) = if (n > m) b to a else a to b
            n = smallerStr.length
            m = largerStr.length

            var currentRow = IntArray(n + 1) { it }

            for (i in 1..m) {
                val previousRow = currentRow
                currentRow = IntArray(n + 1)
                currentRow[0] = i

                for (j in 1..n) {
                    val insertCost = previousRow[j] + 1
                    val deleteCost = currentRow[j - 1] + 1
                    val replaceCost = previousRow[j - 1] + if (smallerStr[j - 1] != largerStr[i - 1]) 1 else 0
                    currentRow[j] = minOf(insertCost, deleteCost, replaceCost)
                }
            }

            return currentRow[n]
        }

        fun similarity(a: String, b: String): Double {
            val maxLen = max(a.length, b.length)
            if(maxLen == 0) return 1.0
            val dist = levenshteinDistance(a, b)
            return 1.0 - dist / maxLen
        }

        fun String.getSecondNumericPart(): Int? = this
            .split("_")
            .getOrNull(1)
            ?.toIntOrNull()


        val tid = "${lineNumber}-${startStation}-${endStation}"

        val normStartStation = startStation.normalize()
        val normEndStation = endStation.normalize()

        val matchingTrips = trips.filter { it.routeId == lineNumber }.also {
            if(it.isEmpty()) {
                skipIds.add(tid)
                return null
            }
        }

        var bestScoreStart = -1.0
        val bestMatchesStart: MutableList<Trip> = mutableListOf()
        var bestScoreEnd = -1.0
        val bestMatchesEnd: MutableList<Trip> = mutableListOf()
        matchingTrips.forEach {
            val startScore = similarity(normStartStation, it.headsign.normalize())
            if(startScore > bestScoreStart) {
                bestMatchesStart.clear()
                bestMatchesStart.add(it)
                bestScoreStart = startScore
            } else if(startScore == bestScoreStart) {
                bestMatchesStart.add(it)
            }

            val endScore = similarity(normEndStation, it.headsign.normalize())
            if(endScore > bestScoreEnd) {
                bestMatchesEnd.clear()
                bestMatchesEnd.add(it)
                bestScoreEnd = endScore
            } else if(endScore == bestScoreEnd) {
                bestMatchesEnd.add(it)
            }
        }

        if(bestMatchesStart.isEmpty() && bestMatchesEnd.isEmpty()) {
            skipIds.add(tid)
            return null
        }

        if(bestMatchesEnd.size == 1)
            return bestMatchesEnd.first().shapeId.also {
                storedTrips[tid] = it
            }

        bestMatchesEnd.forEach { endCandidate ->
            endCandidate.shapeId.getSecondNumericPart()?.let { endSP ->
                bestMatchesStart.firstOrNull { start ->
                    start.shapeId.getSecondNumericPart() == endSP
                }?.let { result ->
                    storedTrips[tid] = result.shapeId
                    return result.shapeId
                }
            }
        }

        if(bestMatchesEnd.isNotEmpty()) {
            //Fallback
            return bestMatchesEnd.first().shapeId.also {
                storedTrips[tid] = it
            }
        }

        skipIds.add(tid)
        return null




    }
}