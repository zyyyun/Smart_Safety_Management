package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.view.doOnPreDraw

class TooltipPopup(private val context: Context) {

    private var popup: PopupWindow? = null
    private var anchor: View? = null
    private var marginDp: Int = 8

    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0

    private var animator: ObjectAnimator? = null

    fun showAlways(anchorView: View, marginDp: Int = 8) {
        this.anchor = anchorView
        this.marginDp = marginDp

        if (popup == null) {
            val tooltipView = LayoutInflater.from(context)
                .inflate(R.layout.view_tooltip, FrameLayout(context), false)

            // 뷰 크기 측정 (wrap_content 대응)
            tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            cachedWidth = tooltipView.measuredWidth
            cachedHeight = tooltipView.measuredHeight

            popup = PopupWindow(
                tooltipView,
                cachedWidth,
                cachedHeight,
                false
            ).apply {
                isTouchable = false
                isOutsideTouchable = false
                elevation = dp(12).toFloat()
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                isClippingEnabled = false
            }

            startFloatingAnimation(tooltipView)
        }

        if (anchorView.isAttachedToWindow) {
            updatePosition()
        } else {
            anchorView.doOnPreDraw {
                updatePosition()
            }
        }
    }

    private fun startFloatingAnimation(view: View) {
        animator?.cancel()
        animator = ObjectAnimator.ofFloat(view, "translationY", -5f, 5f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    fun updatePosition() {
        val pw = popup ?: return
        val anchorView = anchor ?: return

        if (!anchorView.isAttachedToWindow) return

        val marginPx = dp(marginDp)

        val loc = IntArray(2)
        anchorView.getLocationInWindow(loc)
        val anchorX = loc[0]
        val anchorY = loc[1]
        val anchorW = anchorView.width
        val anchorH = anchorView.height

        // 화살표의 중심을 버튼의 중심에 맞추는 계산
        // 1. 버튼의 중앙 X 좌표
        val anchorCenterX = anchorX + (anchorW / 2)

        // 2. 툴팁 내 화살표 중심 위치 (marginStart 168dp + 너비 12dp의 절반 6dp = 174dp)
        val arrowCenterXInTooltip = dp(174)

        // 3. 팝업의 시작 X 좌표 계산
        var x = anchorCenterX - arrowCenterXInTooltip
        var y = anchorY - cachedHeight - marginPx

        val windowRect = Rect()
        anchorView.getWindowVisibleDisplayFrame(windowRect)

        // 화면 밖으로 나가지 않도록 보정
        if (x < windowRect.left) x = windowRect.left
        if (x + cachedWidth > windowRect.right) x = windowRect.right - cachedWidth

        if (y < windowRect.top) {
            y = anchorY + anchorH + marginPx
        }

        val isVisible = anchorView.isShown &&
                anchorY + anchorH > windowRect.top &&
                anchorY < windowRect.bottom

        try {
            if (isVisible) {
                if (pw.isShowing) {
                    pw.update(x, y, -1, -1)
                } else {
                    pw.showAtLocation(anchorView.rootView, Gravity.NO_GRAVITY, x, y)
                }
            } else {
                if (pw.isShowing) pw.dismiss()
            }
        } catch (e: Exception) {}
    }

    fun dismiss() {
        animator?.cancel()
        animator = null
        popup?.dismiss()
        popup = null
        anchor = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}