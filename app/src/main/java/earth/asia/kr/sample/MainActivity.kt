package earth.asia.kr.sample

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import earth.asia.kr.sample.ui.theme.GeocodeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

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
    var geoCodeResults by remember { mutableStateOf(GeocodeResultState.None as GeocodeResultState) }
    val state = rememberMarkerState(
        position = LatLng(37.2586646, 127.0563327)
    )

    LaunchedEffect(state.position) {
        fetchGeocodeInfo(
            state.position.latitude.toString(), state.position.longitude.toString()
        ) { results ->
            geoCodeResults = results
        }
    }

    Box(modifier = Modifier.padding(innerPadding)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            onMapClick = { latLng ->
                state.position = latLng
            },
            cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(state.position, 15f)
            },
        ) {
            Marker(state = state)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f)).padding(10.dp)
        ) {
            Text(text = "lat: ${state.position.latitude}    long: ${state.position.longitude}", fontWeight = FontWeight(1000))
            when (geoCodeResults) {
                is GeocodeResultState.Success -> Text((geoCodeResults as GeocodeResultState.Success).result.plus_code.toString())
                else -> Unit
            }
        }

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
            geoCodeResults = geoCodeResults
        )
    }
}

@Composable
fun ResultBox(geoCodeResults: GeocodeResultState, modifier: Modifier = Modifier) {
    var detailView by remember { mutableStateOf(null as GeocodeResult?) }

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
                            text = GsonBuilder().setPrettyPrinting().create().toJson(detailView),
                        )
                    }
                    BackHandler(enabled = detailView != null) {
                        detailView = null
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        items(geoCodeResults.result.results) { result ->
                            Text(
                                text = result.formatted_address,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        onClick = { detailView = result },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(color = Color.Black.copy(alpha = 0.4f))
                                    )
                            )
                        }
                    }
                }
            }

            GeocodeResultState.None -> Unit
        }
    }
}


sealed class GeocodeResultState {
    data object None : GeocodeResultState()
    data object Loading : GeocodeResultState()
    data class Success(val result: GeocodeResponse) : GeocodeResultState()
    data class Error(val message: String) : GeocodeResultState()
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
                    onResult(GeocodeResultState.Success(gson))
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
