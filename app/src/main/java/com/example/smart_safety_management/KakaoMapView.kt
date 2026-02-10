package com.example.smart_safety_management

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.smart_safety_management.screens.location.MapCircle
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.*
import com.kakao.vectormap.shape.*
import com.kakao.vectormap.route.*
import java.util.ArrayList

@Composable
fun KakaoMapView(
    lat: Double,
    lon: Double,
    modifier: Modifier = Modifier,
    targetLatLng: LatLng? = null,
    pins: List<KakaoMapPin> = emptyList(),
    circles: List<MapCircle> = emptyList(), // ✅ 추가된 파라미터 (지오펜싱 원)
    selectedId: String? = null,
    onPinClick: (String) -> Unit
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var labelLayer by remember { mutableStateOf<LabelLayer?>(null) }
    var shapeLayer by remember { mutableStateOf<ShapeLayer?>(null) }
    var routeLineLayer by remember { mutableStateOf<RouteLineLayer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                start(object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {}
                    override fun onMapError(e: Exception?) {}
                }, object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        kakaoMap = map
                        labelLayer = map.labelManager?.layer
                        shapeLayer = map.shapeManager?.layer
                        routeLineLayer = map.routeLineManager?.layer
                        
                        // [수정] 초기 위치 설정 시 줌 레벨을 15로 설정 (50m 원이 잘 보이도록)
                        map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lon), 15))
                    }
                })
            }
        }
    )

    // 1. 핀(Label) 업데이트 로직
    LaunchedEffect(kakaoMap, pins) {
        val map = kakaoMap ?: return@LaunchedEffect
        val layer = labelLayer ?: return@LaunchedEffect
        
        layer.removeAll()
        
        pins.forEach { pin ->
            val styles = map.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from(bitmapFromVector(context, pin.iconRes, pin.alpha))
                )
            )
            
            layer.addLabel(
                LabelOptions.from(LatLng.from(pin.lat, pin.lon))
                    .setStyles(styles)
                    .setTag(pin.id)
            )
        }
    }

    // 2. 핀 클릭 리스너
    LaunchedEffect(kakaoMap) {
        val map = kakaoMap ?: return@LaunchedEffect
        map.setOnLabelClickListener { _, _, label ->
            val id = label.tag as? String
            if (id != null) {
                onPinClick(id)
            }
            true
        }
    }

    // 3. 카메라 이동 로직
    LaunchedEffect(targetLatLng) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (targetLatLng != null) {
            // [수정] 타겟 이동 시에도 줌 레벨 유지 또는 설정
            map.moveCamera(CameraUpdateFactory.newCenterPosition(targetLatLng, 15))
        }
    }

    // 4. ✅ 원(Circle) 그리기 로직 (지오펜싱 영역)
    LaunchedEffect(kakaoMap, circles) {
        val map = kakaoMap ?: return@LaunchedEffect
        // [수정] Polygon 에러(DotPoints) 회피를 위해 RouteLine(테두리) 사용
        val layer = routeLineLayer ?: return@LaunchedEffect
        
        layer.removeAll()

        circles.forEach { circle ->
            // 1. 원형 좌표 생성
            val points = createCirclePoints(circle.centerLat, circle.centerLng, circle.radius.toDouble()).toMutableList()
            
            // 테두리를 닫기 위해 시작점을 끝에 추가
            if (points.isNotEmpty()) {
                points.add(points.first())
            }

            // 2. 스타일 정의 (두께, 색상) - 순서 주의: (Float, Int)
            val style = RouteLineStyle.from(10f, circle.strokeColor.toArgb())

            // 3. RouteLine 그리기
            try {
                val segment = RouteLineSegment.from(points, style)
                val options = RouteLineOptions.from(segment)
                layer.addRouteLine(options)
            } catch (e: Exception) {
                android.util.Log.e("KakaoMap", "RouteLine Error: ${e.message}")
            }
        }
    }
}

// 벡터 드로어블 -> 비트맵 변환 함수
fun bitmapFromVector(context: Context, vectorResId: Int, alpha: Int = 255): Bitmap {
    val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    drawable.alpha = alpha
    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

// 원형 좌표 생성 함수
fun createCirclePoints(centerLat: Double, centerLng: Double, radiusMeters: Double): List<LatLng> {
    val points = ArrayList<LatLng>()
    val earthRadius = 6371000.0
    for (i in 0 until 360 step 10) {
        val theta = Math.toRadians(i.toDouble())
        val latRad = Math.toRadians(centerLat)
        val lngRad = Math.toRadians(centerLng)

        val newLat = Math.asin(Math.sin(latRad) * Math.cos(radiusMeters / earthRadius) +
                Math.cos(latRad) * Math.sin(radiusMeters / earthRadius) * Math.cos(theta))
        val newLng = lngRad + Math.atan2(Math.sin(theta) * Math.sin(radiusMeters / earthRadius) * Math.cos(latRad),
                Math.cos(radiusMeters / earthRadius) - Math.sin(latRad) * Math.sin(newLat))

        points.add(LatLng.from(Math.toDegrees(newLat), Math.toDegrees(newLng)))
    }
    return points
}