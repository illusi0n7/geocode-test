package earth.asia.kr.sample

data class GeocodeResponse(
    val results: List<GeocodeResult>,
    val status: String,
    val plus_code: PlusCode?
)

data class GeocodeResult(
    val address_components: List<AddressComponent>,
    val formatted_address: String,
    val geometry: Geometry,
    val place_id: String,
    val plus_code: PlusCode?,
    val types: List<String>
)

data class AddressComponent(
    val long_name: String,
    val short_name: String,
    val types: List<String>
)

data class Geometry(
    val location: Location,
    val location_type: String,
    val viewport: Viewport,
    val bounds: Viewport?
)

data class Location(
    val lat: Double,
    val lng: Double
)

data class Viewport(
    val northeast: Location,
    val southwest: Location
)

data class PlusCode(
    val compound_code: String,
    val global_code: String
)