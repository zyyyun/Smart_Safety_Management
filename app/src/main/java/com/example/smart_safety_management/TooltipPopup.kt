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
    var anchor: View? = null // anchor에 접근할 수 있도록 공개
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

            if (UserSession.userRole == UserRole.MANAGER) {
                tooltipIcon.visibility = View.VISIBLE
                tooltipText.text = "누르면 근로자에게 알림이 가요"
                val params = tooltipArrow.layoutParams as LinearLayout.LayoutParams
                params.marginStart = dp(168)
                tooltipArrow.layoutParams = params
            } else {
                tooltipIcon.visibility = View.GONE
                tooltipText.text = "눌러서 일일안전점검 리스트를 작성할 수 있어요"
                val params = tooltipArrow.layoutParams as LinearLayout.LayoutParams
                params.marginStart = dp(249)
                tooltipArrow.layoutParams = params
            }

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

        // 윈도우에서의 절대 좌표 계산
        val loc = IntArray(2)
        anchorView.getLocationInWindow(loc)
        val anchorX = loc[0]
        val anchorY = loc[1]
        val anchorW = anchorView.width
        val anchorH = anchorView.height

        val anchorCenterX = anchorX + (anchorW / 2)
        
        val arrowStartMarginDp = if (UserSession.userRole == UserRole.MANAGER) 168 else 249
        val arrowCenterXInTooltip = dp(arrowStartMarginDp) + dp(6)

        var x = anchorCenterX - arrowCenterXInTooltip
        var y = anchorY - cachedHeight - marginPx

        val windowRect = Rect()
        anchorView.getWindowVisibleDisplayFrame(windowRect)

        // 화면 밖으로 나가지 않게 보정
        if (x < windowRect.left) x = windowRect.left
        if (x + cachedWidth > windowRect.right) x = windowRect.right - cachedWidth

        // 툴팁이 화면 상단 밖으로 나가면 아래쪽에 표시
        if (y < windowRect.top) {
            y = anchorY + anchorH + marginPx
        }

        // 부모 뷰(RecyclerView나 ScrollView)의 가시 영역 체크
        val scrollBounds = Rect()
        anchorView.getGlobalVisibleRect(scrollBounds)
        
        // anchorView가 실제로 화면에 보이고 있고, 부모 스크롤 가시 영역 안에 있는지 확인
        val isVisibleInParent = anchorView.isShown && 
                               scrollBounds.bottom > (anchorY + (anchorH / 2)) && 
                               scrollBounds.top < (anchorY + (anchorH / 2))

        try {
            if (isVisibleInParent) {
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