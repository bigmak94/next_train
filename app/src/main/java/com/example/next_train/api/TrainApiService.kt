package com.example.next_train.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class IdfmResponse(
    val Siri: SiriResponse
)

data class SiriResponse(
    val ServiceDelivery: ServiceDelivery
)

data class ServiceDelivery(
    val StopMonitoringDelivery: List<StopMonitoringDelivery>
)

data class StopMonitoringDelivery(
    val MonitoredStopVisit: List<MonitoredStopVisit>
)

data class MonitoredStopVisit(
    val MonitoredVehicleJourney: MonitoredVehicleJourney
)

data class MonitoredVehicleJourney(
    val MonitoredCall: MonitoredCall
)

data class MonitoredCall(
    val ExpectedDepartureTime: String?,
    val AimedDepartureTime: String?
)

interface TrainApi {
    @Headers(
        "Accept: application/json",
        "apiKey: UKxA4CN4Kbsr3zveOkErsJHaXSZJePaY"
    )
    @GET("stop-monitoring?MonitoringRef=STIF%3AStopArea%3ASP%3A43135%3A&LineRef=STIF%3ALine%3A%3AC01742%3A")
    suspend fun getNextTrains(): Response<IdfmResponse>
}

object TrainApiService {
    private const val BASE_URL = "https://prim.iledefrance-mobilites.fr/marketplace/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: TrainApi = retrofit.create(TrainApi::class.java)

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun getWaitingMinutes(isoTime: String?): Int {
        if (isoTime == null) return -1
        return try {
            val date = isoDateFormat.parse(isoTime)
            val now = Date()
            ((date.time - now.time) / 60000).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun formatTime(minutes: Int): String {
        return when {
            minutes <= 0 -> "À l'approche"
            minutes == 1 -> "Dans 1 minute"
            else -> "Dans $minutes minutes"
        }
    }

    suspend fun getNextTrains(): kotlin.Result<List<TrainInfo>> {
        return try {
            val response = api.getNextTrains()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val trains = body.Siri.ServiceDelivery.StopMonitoringDelivery
                    .firstOrNull()?.MonitoredStopVisit
                    ?.map { visit ->
                        val journey = visit.MonitoredVehicleJourney
                        val waitingMinutes = getWaitingMinutes(
                            journey.MonitoredCall.ExpectedDepartureTime 
                            ?: journey.MonitoredCall.AimedDepartureTime
                        )
                        TrainInfo(
                            waitingMinutes = waitingMinutes,
                            displayTime = formatTime(waitingMinutes)
                        )
                    } ?: emptyList()
                kotlin.Result.success(trains)
            } else {
                kotlin.Result.failure(Exception("Erreur lors de la récupération des horaires: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }
}

data class TrainInfo(
    val waitingMinutes: Int,
    val displayTime: String
) 