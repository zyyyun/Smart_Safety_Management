package com.example.smart_safety_management

import android.util.Log
import androidx.annotation.DrawableRes
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
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat

@Immutable
data class KakaoMapPin(
    val id: String,
    val lat: Double,
    val lon: Double,
    @DrawableRes val iconRes: Int,
    val alpha: Int = 255,
)
fun resizeBitmap(
    context: Context,
    @DrawableRes resId: Int,
    width: Int,
    height: Int
): Bitmap {
    val bitmap = BitmapFactory.decodeResource(context.resources, resId)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}

fun resizeBitmapWithAlpha(
    context: Context,
    @DrawableRes resId: Int,
    width: Int,
    height: Int,
    alpha: Float
): Bitmap {
    val drawable = ContextCompat.getDrawable(context, resId)!!
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    drawable.setBounds(0, 0, width, height)
    drawable.alpha = (alpha * 255).toInt()
    drawable.draw(canvas)

    return bmp
}



@Composable
fun KakaoMapView(
    lat: Double = 37.4563,
    lon: Double = 126.7052,
    modifier: Modifier = Modifier,
    targetLatLng: LatLng? = null,
    pins: List<KakaoMapPin> = emptyList(),
    selectedId: String? = null,
    centerOnSelectedPin: Boolean = false,
    dimUnselectedPins: Boolean = true,
    onPinClick: (pinId: String) -> Unit = {},
    onMapReady: (KakaoMap) -> Unit = {},
    onCenterChanged: (centerLat: Double, centerLon: Double) -> Unit = { _, _ -> },
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapViewState = remember { mutableStateOf<MapView?>(null) }
    val kakaoMapState = remember { mutableStateOf<KakaoMap?>(null) }
    val labelLayerState = remember { mutableStateOf<LabelLayer?>(null) }

    val didInit = remember { mutableStateOf(false) }
    val lastRenderKey = remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                mapViewState.value = this

                start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {}
                        override fun onMapError(exception: Exception) {
                            Log.e("KakaoMapView", "onMapError", exception)
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(kakaoMap: KakaoMap) {
                            kakaoMapState.value = kakaoMap

                            val labelManager = kakaoMap.getLabelManager()
                            Log.d("KakaoPin", "labelManager=$labelManager")

                            val layer = labelManager?.getLayer()
                            Log.d("KakaoPin", "layer=$layer")
                            labelLayerState.value = layer

                            layer?.setClickable(true)
                            layer?.setZOrder(5000)

                            kakaoMap.setOnLabelClickListener(object : KakaoMap.OnLabelClickListener {
                                override fun onLabelClicked(
                                    kakaoMap: KakaoMap,
                                    layer: LabelLayer,
                                    label: Label
                                ): Boolean {
                                    val id = label.tag?.toString()
                                    if (!id.isNullOrBlank()) {
                                        onPinClick(id)
                                        return true
                                    }
                                    return false
                                }
                            })

                            if (!didInit.value) {
                                didInit.value = true
                                val initial = LatLng.from(lat, lon)
                                kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(initial))
                            }

                            onMapReady(kakaoMap)
                        }
                    }
                )
            }
        }
    )

    // lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewState.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mv.resume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mv.pause() }
                Lifecycle.Event.ON_DESTROY -> runCatching { mv.finish() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapViewState.value?.pause() }
            runCatching { mapViewState.value?.finish() }
            mapViewState.value = null
            kakaoMapState.value = null
            labelLayerState.value = null
        }
    }

    LaunchedEffect(targetLatLng?.latitude, targetLatLng?.longitude, centerOnSelectedPin) {
        if (centerOnSelectedPin) return@LaunchedEffect  // ✅ AIEventDetail은 핀 기준으로만 이동
        val km = kakaoMapState.value ?: return@LaunchedEffect
        val t = targetLatLng ?: return@LaunchedEffect
        km.moveCamera(CameraUpdateFactory.newCenterPosition(t))
    }


    // ✅ 핀 그리기
    LaunchedEffect(pins, selectedId, labelLayerState.value) {
        Log.d("KakaoPin", "render pins size=${pins.size}, layer=${labelLayerState.value}")

        val layer = labelLayerState.value ?: return@LaunchedEffect

        val key = buildString {
            append(pins.size)
            append('|')
            append(selectedId ?: "")
            pins.forEach { p ->
                append('|')
                append(p.id)
                append('@')
                append(p.lat)
                append(',')
                append(p.lon)
                append('#')
                append(p.iconRes)
            }
        }
        if (lastRenderKey.value == key) return@LaunchedEffect
        lastRenderKey.value = key

        // ✅ 여기서 딱 1번만 지우기
        layer.removeAll()

        // ✅ (확인용) 카메라 중심 위치 로그
        val centerPos = try { kakaoMapState.value?.cameraPosition?.position } catch (_: Exception) { null }
        Log.d("KakaoPin", "camera center=$centerPos")

        pins.forEach { p ->
            val pos = LatLng.from(p.lat, p.lon)

            val isSelected = p.id == selectedId
            val scale = if (isSelected) 1.3f else 1.0f
            val alpha = when {
                !dimUnselectedPins -> 1.0f
                selectedId == null -> 1.0f
                isSelected -> 1.0f
                else -> 0.4f
            }


            val width = (72 * scale).toInt()
            val height = (96 * scale).toInt()

            val bitmap = resizeBitmapWithAlpha(
                mapViewState.value!!.context,
                p.iconRes,
                width,
                height,
                alpha
            )

            val style = LabelStyle.from(bitmap)
                .setAnchorPoint(0.5f, 1.0f)   // ✅ 핀 아래 꼭지가 좌표에 찍힘

            val styles = LabelStyles.from(style)


            val opt = LabelOptions.from(pos).apply {
                setTag(p.id)
                setClickable(true)
                setStyles(styles)
            }

            layer.addLabel(opt)
        }

    }
// ✅ (옵션) 선택 핀을 항상 화면 정중앙으로 (AIEventDetail에서만 켜기)
    LaunchedEffect(pins, selectedId, centerOnSelectedPin, kakaoMapState.value) {
        if (!centerOnSelectedPin) return@LaunchedEffect
        val km = kakaoMapState.value ?: return@LaunchedEffect

        val centerPin = pins.firstOrNull { it.id == selectedId } ?: pins.firstOrNull()
        if (centerPin != null) {
            val ll = LatLng.from(centerPin.lat, centerPin.lon)
            km.moveCamera(CameraUpdateFactory.newCenterPosition(ll))
        }
    }


    // center polling
    LaunchedEffect(kakaoMapState.value) {
        val km = kakaoMapState.value ?: return@LaunchedEffect

        var lastLat = Double.NaN
        var lastLon = Double.NaN

        while (isActive) {
            val cam = km.cameraPosition
            if (cam == null) {
                delay(400)
                continue
            }

            val pos = try { cam.position } catch (_: Exception) { null }
            if (pos == null) {
                delay(400)
                continue
            }

            val clat = pos.latitude
            val clon = pos.longitude

            val changed =
                lastLat.isNaN() || lastLon.isNaN() ||
                        abs(clat - lastLat) > 0.00003 ||
                        abs(clon - lastLon) > 0.00003

            if (changed) {
                lastLat = clat
                lastLon = clon
                onCenterChanged(clat, clon)
            }

            delay(400)
        }
    }
}

private fun tryInvokeSetText(options: Any, text: String) {
    try {
        val m = options.javaClass.methods.firstOrNull { it.name == "setText" && it.parameterTypes.size == 1 }
        m?.invoke(options, text)
    } catch (_: Exception) {}
}

