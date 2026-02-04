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

    // ✅ 초기 moveCamera를 딱 1번만 하게 만드는 플래그
    val didInit = remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                mapViewState.value = this

                start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {
                            Log.d("KakaoMapView", "onMapDestroy")
                        }

                        override fun onMapError(exception: Exception) {
                            Log.e("KakaoMapView", "onMapError", exception)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(kakaoMap: KakaoMap) {
                            kakaoMapState.value = kakaoMap

                            // ✅ 초기 카메라는 '한 번만' 실행
                            if (!didInit.value) {
                                didInit.value = true
                                val initial = LatLng.from(lat, lon)
                                kakaoMap.moveCamera(
                                    CameraUpdateFactory.newCenterPosition(initial)
                                )
                                // 라벨(마커) 시도 — 실패해도 앱 안 죽게
                                tryAddLabelByReflection(kakaoMap, initial)
                            }

                            onMapReady(kakaoMap)
                        }
                    }
                )
            }
        },

        // ✅ 핵심: update에서는 moveCamera 하지 마라 (초기 위치로 튐 원인)
        update = {
            // 아무것도 하지 않음
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

    /** ✅ 외부 targetLatLng가 바뀌면 카메라 이동 (여기서만 이동!) */
    LaunchedEffect(targetLatLng?.latitude, targetLatLng?.longitude) {
        val km = kakaoMapState.value ?: return@LaunchedEffect
        val t = targetLatLng ?: return@LaunchedEffect

        km.moveCamera(CameraUpdateFactory.newCenterPosition(t))
        // 라벨이 계속 쌓이면 아래 줄은 주석처리해도 됨
        tryAddLabelByReflection(km, t)
    }

    /**
     * ✅ 중심 변화 감지 (폴링)
     * - isActive로 안전하게 종료
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
        val m = this.javaClass.methods.firstOrNull { it.name == "resume" && it.parameterTypes.isEmpty() }
        if (m != null) {
            m.invoke(this); return
        }
        safeCall("onResume")
    } catch (_: Exception) {}
}

private fun MapView.safePause() {
    try {
        val m = this.javaClass.methods.firstOrNull { it.name == "pause" && it.parameterTypes.isEmpty() }
        if (m != null) {
            m.invoke(this); return
        }
        safeCall("onPause")
    } catch (_: Exception) {}
}

private fun MapView.safeFinish() {
    try {
        val m = this.javaClass.methods.firstOrNull { it.name == "finish" && it.parameterTypes.isEmpty() }
        if (m != null) {
            m.invoke(this); return
        }
        safeCall("onDestroy")
    } catch (_: Exception) {}
}

private fun Any.safeCall(methodName: String) {
    try {
        val m = this.javaClass.methods.firstOrNull { it.name == methodName && it.parameterTypes.isEmpty() }
        m?.invoke(this)
    } catch (_: Exception) {}
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
        val fromMethod = labelOptionsCls.methods
            .firstOrNull { it.name == "from" && it.parameterTypes.size == 1 } ?: return

        val options = fromMethod.invoke(null, pos) ?: return

        val addLabel = layer.javaClass.methods
            .firstOrNull { it.name == "addLabel" && it.parameterTypes.size == 1 } ?: return

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
