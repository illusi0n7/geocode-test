package earth.asia.kr.sample

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import earth.asia.kr.sample.ui.theme.GeocodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeocodeTheme {
                Scaffold { innerPadding ->
                    GeoCodingApp(innerPadding)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Preview
@Composable
fun GeoCodingApp(innerPadding: PaddingValues = PaddingValues()) {
    val orientation = LocalConfiguration.current.orientation
    val context = LocalContext.current

    var geoCodeResults by remember { mutableStateOf(GeocodeResultState.None as GeocodeResultState) }
    var selectedResult by remember { mutableStateOf(null as ResultData?) }

    val state = rememberMarkerState(
        position = LatLng(37.2586646, 127.0563327)
    )
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.position, 15f)
    }

    LaunchedEffect(state.position) {
        onDeviceAddress(
            context, state.position.latitude, state.position.longitude, 100
        ).collectLatest { it ->
            geoCodeResults = it
        }
    }

    Box(modifier = Modifier.padding(innerPadding)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            onMapClick = { latLng ->
                selectedResult = null
                state.position = latLng
            },
            cameraPositionState = cameraState,
        ) {
            Marker(state = state)

            when (geoCodeResults) {
                is GeocodeResultState.Success -> (geoCodeResults as GeocodeResultState.Success).results.forEach { result ->
                    Marker(state = rememberMarkerState(
                        key = result.latLng.toString(), position = result.latLng
                    ), alpha = if (selectedResult == result) 0.7f else 0.2f, onClick = {
                        selectedResult = result
                        false
                    })
                }

                else -> Unit
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f))
                .padding(10.dp)
        ) {
            Text(
                text = "lat: ${state.position.latitude}    long: ${state.position.longitude}",
                fontWeight = FontWeight(1000)
            )
        }

        val scope = rememberCoroutineScope()
        ResultBox(
            modifier = Modifier
                .then(
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .fillMaxWidth() else Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .wrapContentWidth()
                )
                .clip(RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp))
                .background(Color.White.copy(alpha = 0.9f))
                .padding(16.dp),
            geoCodeResults = geoCodeResults,
            onClickLocation = { result: ResultData ->
                selectedResult = result
                scope.launch {
                    cameraState.animate(CameraUpdateFactory.newLatLng(result.latLng))
                }
            },
            selectedResult = selectedResult,
        )
    }
}

@Composable
fun ResultBox(
    geoCodeResults: GeocodeResultState,
    modifier: Modifier = Modifier,
    onClickLocation: (ResultData) -> Unit,
    selectedResult: ResultData? = null,
) {
    var detailView by remember { mutableStateOf(null as String?) }

    val orientation = LocalConfiguration.current.orientation
    val containerModifier = Modifier
        .animateContentSize()
        .then(
            if (detailView != null) modifier.fillMaxSize()
            else when (orientation) {
                Configuration.ORIENTATION_PORTRAIT -> modifier.height(200.dp)
                else -> modifier.width(200.dp)
            }
        )

    Box(modifier = containerModifier) {
        when (geoCodeResults) {
            is GeocodeResultState.Error -> Text(text = geoCodeResults.message)
            GeocodeResultState.Loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(50.dp)
            )

            is GeocodeResultState.Success -> {
                if (detailView != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = detailView!!,
                        )
                    }
                    BackHandler(enabled = detailView != null) {
                        detailView = null
                    }
                } else {

                    val state = rememberLazyListState()

                    LaunchedEffect(selectedResult) {
                        val  target = geoCodeResults.results.indexOfFirst { it == selectedResult }
                        if (target != -1) state.animateScrollToItem(target)
                    }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp), state = state) {
                        items(geoCodeResults.results) { result ->
                            ResultItem(result,
                                viewDetails = { detailView = result.detail },
                                onIconClick = { onClickLocation(result) },
                                selected = selectedResult == result
                            )
                        }
                    }
                }
            }

            GeocodeResultState.None -> Unit
        }
    }
}

@Composable
fun ResultItem(
    result: ResultData, viewDetails: () -> Unit, onIconClick: () -> Unit = {}, selected: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(Color.Red.copy(alpha = 0.3f), RoundedCornerShape(15.dp)) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fontWeight = FontWeight(500), text = result.title, modifier = Modifier.weight(1f).clickable(onClick = viewDetails))
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clickable(onClick = onIconClick)
        )
    }
}

fun onDeviceAddress(
    context: Context, latitude: Double, longitude: Double, maxResult: Int
) = callbackFlow {
    trySend(GeocodeResultState.Loading)
    with(Geocoder(context, Locale.getDefault())) {
        getFromLocation(latitude, longitude, maxResult) {
            val result = it.map { data ->
                ResultData(
                    data.getAddressLine(0),
                    formatFullAddress(data),
                    LatLng(data.latitude, data.longitude)
                )
            }
            trySend(GeocodeResultState.Success(result))
            close()
        }
    }
    awaitClose { }
}

sealed class GeocodeResultState {
    data object None : GeocodeResultState()
    data object Loading : GeocodeResultState()
    data class Success(val results: List<ResultData>) : GeocodeResultState()
    data class Error(val message: String) : GeocodeResultState()
}

fun formatFullAddress(address: Address?): String {
    // Address가 null일 경우 처리
    if (address == null) return "주소 정보가 없습니다."

    // 각 주소 구성 요소를 배열로 수집
    val addressComponents = mutableListOf<String>()

    // 주소 구성 요소를 추가
    address.getAddressLine(0)?.let { addressComponents.add("addressLine: $it") } // 전체 주소 라인
    addressComponents.add("lat ${address.latitude} long ${address.longitude}")
    address.featureName?.let { addressComponents.add("featureName: $it") }
    address.thoroughfare?.let { addressComponents.add("thoroughfare: $it") }
    address.premises?.let { addressComponents.add("premises: $it") }
    address.phone?.let { addressComponents.add("phone: $it") }
    address.url?.let { addressComponents.add("url: $it") }
    address.locality?.let { addressComponents.add("locality: $it") } // 도시
    address.subAdminArea?.let { addressComponents.add("subAdminArea: $it") } // 구역/시
    address.adminArea?.let { addressComponents.add("adminArea: $it") } // 주/도
    address.countryName?.let { addressComponents.add("countryName: $it") } // 국가
    address.postalCode?.let { addressComponents.add("postalCode: $it") } // 우편번호

    // 모든 구성 요소를 줄 바꿈으로 구분하여 결합
    return addressComponents.joinToString("\n\n")
}

suspend fun fetchGeocodeInfo(
    latitude: String, longitude: String, onResult: (GeocodeResultState) -> Unit
) {
    val latlng = "$latitude,$longitude"
    onResult(GeocodeResultState.Loading)
    withContext(Dispatchers.IO) {
        try {
            val response = RetrofitInstance.api.getGeocodeInfo(latlng)
            val jsonString = response.string()
            val gson = Gson().fromJson(jsonString, GeocodeResponse::class.java)
            if (gson.status == "OK") {
                withContext(Dispatchers.Main) {
                    val result = gson.results.map {
                        ResultData(
                            it.formatted_address,
                            GsonBuilder().setPrettyPrinting().create().toJson(it),
                            LatLng(it.geometry.location.lat, it.geometry.location.lng)
                        )
                    }
                    onResult(GeocodeResultState.Success(result))
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(GeocodeResultState.Error("No results found"))
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(GeocodeResultState.Error("Error: ${e.message}"))
            }
        }
    }
}

data class ResultData(val title: String, val detail: String, val latLng: LatLng)

interface GeocodeApiService {
    @GET("geocode/json")
    suspend fun getGeocodeInfo(
        @Query("latlng") latlng: String,
        @Query("language") language: String = "ko",
        @Query("key") apiKey: String = "API_CODE"
    ): ResponseBody
}

object RetrofitInstance {
    private const val BASE_URL = "https://maps.googleapis.com/maps/api/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()

    val api: GeocodeApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient).build().create(GeocodeApiService::class.java)
    }
}
