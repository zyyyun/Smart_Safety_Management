package com.example.smart_safety_management

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun KakaoMapView(
    lat: Double = 37.4563,
    lon: Double = 126.7052,
    modifier: Modifier = Modifier,
    targetLatLng: LatLng? = null,
    onMapReady: (KakaoMap) -> Unit = {},
    onCenterChanged: (centerLat: Double, centerLon: Double) -> Unit = { _, _ -> }
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // MapView/KakaoMap은 recomposition에도 유지
    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val kakaoMapState = remember { mutableStateOf<KakaoMap?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                mapViewState.value = this

                start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {
                            // 필요하면 로그만
                            Log.d("KakaoMapView", "onMapDestroy")
                        }

                        override fun onMapError(exception: Exception) {
                            Log.e("KakaoMapView", "onMapError", exception)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(kakaoMap: KakaoMap) {
                            kakaoMapState.value = kakaoMap

                            // 초기 카메라
                            val initial = LatLng.from(lat, lon)
                            kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(initial))

                            onMapReady(kakaoMap)

                            // 라벨(마커) 시도 — 실패해도 앱 안 죽게
                            tryAddLabelByReflection(kakaoMap, initial)
                        }
                    }
                )
            }
        },
        // update: recomposition 때도 안전하게 좌표/타겟 반영
        update = {
            val km = kakaoMapState.value ?: return@AndroidView
            val t = targetLatLng ?: LatLng.from(lat, lon)
            km.moveCamera(CameraUpdateFactory.newCenterPosition(t))
            // 라벨은 옵션: 너무 많이 찍히면 주석 처리해도 됨
            tryAddLabelByReflection(km, t)
        }
    )

    /** ✅ 라이프사이클 연결 (resume/pause/finish 우선 호출, 없으면 fallback) */
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewState.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> mv.safeResume()
                Lifecycle.Event.ON_PAUSE -> mv.safePause()
                Lifecycle.Event.ON_DESTROY -> mv.safeFinish()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewState.value?.safePause()
            mapViewState.value?.safeFinish()
            mapViewState.value = null
            kakaoMapState.value = null
        }
    }

    /** ✅ 외부 targetLatLng가 바뀌면 카메라 이동(맵 준비된 경우만) */
    LaunchedEffect(targetLatLng) {
        val km = kakaoMapState.value ?: return@LaunchedEffect
        val t = targetLatLng ?: return@LaunchedEffect
        km.moveCamera(CameraUpdateFactory.newCenterPosition(t))
        tryAddLabelByReflection(km, t)
    }

    /**
     * ✅ 중심 변화 감지
     * - 리스너 API가 SDK 버전에 따라 다를 수 있어서 폴링 유지하되,
     * - isActive로 안전하게 종료되도록 수정
     */
    LaunchedEffect(kakaoMapState.value) {
        val km = kakaoMapState.value ?: return@LaunchedEffect

        var lastLat = Double.NaN
        var lastLon = Double.NaN

        while (isActive) {
            val center = readCenterByReflection(km)
            if (center != null) {
                val (clat, clon) = center

                val changed =
                    lastLat.isNaN() || lastLon.isNaN() ||
                            abs(clat - lastLat) > 0.00003 ||
                            abs(clon - lastLon) > 0.00003

                if (changed) {
                    lastLat = clat
                    lastLon = clon
                    onCenterChanged(clat, clon)
                }
            }
            delay(400)
        }
    }
}

/* ---------- MapView 라이프사이클 안전 호출 ---------- */

private fun MapView.safeResume() {
    try {
        // 최신 SDK
        val m = this.javaClass.methods.firstOrNull { it.name == "resume" && it.parameterTypes.isEmpty() }
        if (m != null) {
            m.invoke(this)
            return
        }
        // 구버전 fallback
        safeCall("onResume")
    } catch (_: Exception) {
    }
}

private fun MapView.safePause() {
    try {
        val m = this.javaClass.methods.firstOrNull { it.name == "pause" && it.parameterTypes.isEmpty() }
        if (m != null) {
            m.invoke(this)
            return
        }
        safeCall("onPause")
    } catch (_: Exception) {
    }
}

private fun MapView.safeFinish() {
    try {
        val m = this.javaClass.methods.firstOrNull { it.name == "finish" && it.parameterTypes.isEmpty() }
        if (m != null) {
            m.invoke(this)
            return
        }
        safeCall("onDestroy")
    } catch (_: Exception) {
    }
}

private fun Any.safeCall(methodName: String) {
    try {
        val m = this.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
        m?.invoke(this)
    } catch (_: Exception) {
    }
}

/* ---------- 리플렉션 유틸(라벨/중심) ---------- */

private fun tryAddLabelByReflection(kakaoMap: KakaoMap, pos: LatLng) {
    try {
        val labelManager = kakaoMap.javaClass.methods
            .firstOrNull { it.name == "getLabelManager" && it.parameterTypes.isEmpty() }
            ?.invoke(kakaoMap) ?: return

        val layer = labelManager.javaClass.methods
            .firstOrNull { it.name == "getLayer" && it.parameterTypes.isEmpty() }
            ?.invoke(labelManager)
            ?: labelManager.javaClass.fields.firstOrNull { it.name == "layer" }?.get(labelManager)
            ?: return

        val labelOptionsCls = Class.forName("com.kakao.vectormap.label.LabelOptions")
        val fromMethod = labelOptionsCls.methods.firstOrNull { it.name == "from" && it.parameterTypes.size == 1 }
            ?: return
        val options = fromMethod.invoke(null, pos) ?: return

        val addLabel = layer.javaClass.methods.firstOrNull { it.name == "addLabel" && it.parameterTypes.size == 1 }
            ?: return
        addLabel.invoke(layer, options)
    } catch (e: Exception) {
        Log.d("KakaoMapView", "label add skipped: ${e.message}")
    }
}

private fun readCenterByReflection(kakaoMap: KakaoMap): Pair<Double, Double>? {
    return try {
        val camPos = kakaoMap.javaClass.methods
            .firstOrNull { it.name == "getCameraPosition" && it.parameterTypes.isEmpty() }
            ?.invoke(kakaoMap) ?: return null

        val target = camPos.javaClass.methods.firstOrNull { it.name == "getTarget" }?.invoke(camPos)
            ?: camPos.javaClass.methods.firstOrNull { it.name == "getPosition" }?.invoke(camPos)
            ?: return null

        val lat = target.javaClass.methods.firstOrNull { it.name == "getLatitude" }?.invoke(target) as? Double
        val lon = target.javaClass.methods.firstOrNull { it.name == "getLongitude" }?.invoke(target) as? Double

        if (lat != null && lon != null) lat to lon else null
    } catch (_: Exception) {
        null
    }
}
