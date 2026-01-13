package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
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

            cachedWidth = tooltipView.layoutParams.width
            cachedHeight = tooltipView.layoutParams.height

            popup = PopupWindow(
                tooltipView,
                cachedWidth,
                cachedHeight,
                false
            ).apply {
                // 사용자가 화면 어디든 탭하면 사라지게 하기 위해, 팝업 자체가 터치를 가로채지 않도록 설정
                isTouchable = false 
                isOutsideTouchable = false
                elevation = dp(4).toFloat()
                setBackgroundDrawable(null)
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
        // Y축 -5px ~ 5px 반복 애니메이션 (Floating Animation)
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

        var x = (anchorX + anchorW) - cachedWidth
        var y = anchorY - cachedHeight - marginPx

        val windowRect = Rect()
        anchorView.getWindowVisibleDisplayFrame(windowRect)

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
