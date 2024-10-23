import kotlin.math.*

data class Shape(
    val id: String,
    val lat: Double,
    val lng: Double,
    val seq: Int,
    val shapeDist: Double? = null,
)

class ShapeManager {
    private val stopDists: MutableMap<String, Double> = mutableMapOf()
    private val shapeData: Map<String, MutableList<Shape>>

    constructor(shapeFile: String = "shapes.txt") {
        shapeData = readAllText(shapeFile).split("\n").mapIndexedNotNull { index: Int, line: String ->
            if(index == 0) return@mapIndexedNotNull null
            line.split(",").takeIf { it.size >= 4 }?.let { cols ->
                Shape(
                    id = cols[0],
                    lat = cols[1].toDouble(),
                    lng = cols[2].toDouble(),
                    seq = cols[3].toInt(),
                    shapeDist = cols.getOrNull(4)?.toDoubleOrNull()
                )
            }
        }.groupBy { it.id }.map {
            it.key to it.value.toMutableList()
        }.toMap()
    }

    private fun toRadians(deg: Double): Double = deg / 180.0 * PI

    /**
     * Haversine formula. Giving great-circle distances between two points on a sphere from their longitudes and latitudes.
     * It is a special case of a more general formula in spherical trigonometry, the law of haversines, relating the
     * sides and angles of spherical "triangles".
     *
     * https://rosettacode.org/wiki/Haversine_formula#Java
     *
     * @return Distance in kilometers
     */
    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = toRadians(lat2 - lat1);
        val dLon = toRadians(lng2 - lng1);
        val originLat = toRadians(lat1);
        val destinationLat = toRadians(lat2);

        val a = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(originLat) * cos(destinationLat);
        val c = 2 * asin(sqrt(a));
        return 6371.0 * c;
    }

    fun Shape.calculateDistance(wholeShape: List<Shape>): Double {
        return wholeShape.filter {
            it.seq <= this.seq
        }.mapIndexed { index: Int, shape: Shape ->
            val prevShape = wholeShape[index.minus(1).takeIf { it >= 0 } ?: return@mapIndexed 0.0]
            return@mapIndexed haversine(
                prevShape.lat, prevShape.lng,
                shape.lat, shape.lng
            )
        }.sum()
    }

    fun getPercentageDistance(shapeId: String, stopId: String, coord: Pair<Double, Double>): Double {
        val sid = "${shapeId}-${stopId}"
        return stopDists.getOrPut(sid) {
            calculatePercentageDistance(shapeId, coord)
        }
    }

    fun calculatePercentageDistance(shapeId: String, coord: Pair<Double, Double>): Double {
        shapeData[shapeId]?.let { shapeCoords ->
            val stopPoint = shapeCoords.minBy {
                haversine(it.lat, it.lng, coord.first, coord.second)
            }.let { stopPnt ->
                if(stopPnt.shapeDist == null)
                    stopPnt.copy(shapeDist = stopPnt.calculateDistance(shapeCoords)).also {
                        shapeCoords[shapeCoords.indexOf(stopPnt)] = it
                    }
                else stopPnt
            }

            val lastPoint = shapeCoords.last().let { lastPnt ->
                if(lastPnt.shapeDist == null)
                    lastPnt.copy(shapeDist = lastPnt.calculateDistance(shapeCoords)).also {
                        shapeCoords[shapeCoords.indexOf(lastPnt)] = it
                    }
                else lastPnt
            }

            return stopPoint.shapeDist!! / lastPoint.shapeDist!!
        }

        return Double.NaN
    }


}