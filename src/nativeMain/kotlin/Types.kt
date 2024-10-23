import kotlinx.serialization.Serializable

@Serializable
data class Stop(
    val id: String,
    val arrival: String?,
    val departure: String?,
    val perc: Double
)

@Serializable
data class PublicTransport(
    val name: String,
    val stops: List<Stop>
)