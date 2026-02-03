package com.example.smart_safety_management

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Header
data class PlaceSuggestion(
    val place_id: String,
    val title: String,
    val subtitle: String? = null,
    val address: String? = null,
    val road_address: String? = null,
    val zipcode: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
)
data class KakaoKeywordResponse(
    val documents: List<KakaoPlaceDoc> = emptyList()
)

data class KakaoPlaceDoc(
    val place_name: String?,
    val address_name: String?,
    val road_address_name: String?,
    val x: String?, // 경도(lon)
    val y: String?  // 위도(lat)
)

data class AutocompleteResponse(
    val items: List<PlaceSuggestion>
)

interface PlaceApi {
    @GET("v2/local/search/keyword.json")
    suspend fun keywordSearch(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("size") size: Int = 10
    ): Response<KakaoKeywordResponse>
}
