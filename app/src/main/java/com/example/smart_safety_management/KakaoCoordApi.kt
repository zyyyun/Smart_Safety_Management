package com.example.smart_safety_management


import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface KakaoCoordApi {

    // 카카오 좌표 → 주소 (역지오코딩)
    @GET("v2/local/geo/coord2address.json")
    suspend fun coord2Address(
        @Query("x") x: Double, // 경도 (lon)
        @Query("y") y: Double  // 위도 (lat)
    ): Response<Coord2AddressResponse>
}

/* ---------- Response Models ---------- */

data class Coord2AddressResponse(
    val documents: List<Coord2AddressDocument>
)

data class Coord2AddressDocument(
    val road_address: KakaoRoadAddress?,
    val address: KakaoAddress?
)

data class KakaoRoadAddress(
    val address_name: String?, // 예: "인천 연수구 해송로30번길 20"
    val zone_no: String?       // ✅ 우편번호 (22000)
)

data class KakaoAddress(
    val address_name: String?
)
