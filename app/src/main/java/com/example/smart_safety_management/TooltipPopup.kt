package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
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

            val tooltipIcon = tooltipView.findViewById<ImageView>(R.id.tooltip_icon)
            val tooltipText = tooltipView.findViewById<TextView>(R.id.tooltip_text)
            val tooltipArrow = tooltipView.findViewById<View>(R.id.tooltip_arrow)

            // 역할에 따른 텍스트, 아이콘, 그리고 화살표 위치 설정
            if (UserSession.userRole == UserRole.MANAGER) {
                tooltipIcon.visibility = View.VISIBLE
                tooltipText.text = "누르면 근로자에게 알림이 가요"
                
                // 관리자 기본 화살표 위치
                val params = tooltipArrow.layoutParams as LinearLayout.LayoutParams
                params.marginStart = dp(168)
                tooltipArrow.layoutParams = params
            } else {
                tooltipIcon.visibility = View.GONE
                tooltipText.text = "눌러서 일일안전점검 리스트를 작성할 수 있어요"
                
                // 근로자용 화살표 위치
                val params = tooltipArrow.layoutParams as LinearLayout.LayoutParams
                params.marginStart = dp(249)
                tooltipArrow.layoutParams = params
            }

            // 뷰 크기 측정
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

        val anchorCenterX = anchorX + (anchorW / 2)
        
        // 화살표 위치에 따른 X 좌표 계산 (중심점 기준 보정)
        val arrowStartMarginDp = if (UserSession.userRole == UserRole.MANAGER) 168 else 249
        val arrowCenterXInTooltip = dp(arrowStartMarginDp) + dp(6) // marginStart + (화살표 너비 12dp / 2)

        var x = anchorCenterX - arrowCenterXInTooltip
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