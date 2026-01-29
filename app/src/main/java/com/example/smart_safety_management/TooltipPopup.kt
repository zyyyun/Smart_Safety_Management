package com.example.smart_safety_management

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
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
            val bgRoundBox = tooltipView.findViewById<View>(R.id.bg_round_box)

            // 그림자가 잘리지 않도록 여백 추가
            tooltipView.setPadding(dp(10), dp(10), dp(10), dp(20))

            // 박스와 화살표의 그림자 높이를 2dp로 통일 (XML의 translationZ 무시)
            val unifiedElevation = dp(2).toFloat()
            bgRoundBox.elevation = unifiedElevation
            bgRoundBox.translationZ = 0f

            tooltipArrow.elevation = unifiedElevation
            tooltipArrow.translationZ = 0f

            // 화살표 그림자를 위한 역삼각형 Outline 설정
            tooltipArrow.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val w = view.width.toFloat()
                    val h = view.height.toFloat()
                    if (w > 0 && h > 0) {
                        val path = Path()
                        path.moveTo(0f, 0f)
                        path.lineTo(w, 0f)
                        path.lineTo(w / 2f, h)
                        path.close()

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            outline.setPath(path)
                        } else {
                            @Suppress("DEPRECATION")
                            outline.setConvexPath(path)
                        }
                    }
                }
            }
            tooltipArrow.clipToOutline = false

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
                elevation = 0f
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
        val padding = dp(10)

        val loc = IntArray(2)
        anchorView.getLocationInWindow(loc)
        val anchorX = loc[0]
        val anchorY = loc[1]
        val anchorW = anchorView.width
        val anchorH = anchorView.height

        val anchorCenterX = anchorX + (anchorW / 2)

        val arrowStartMarginDp = if (UserSession.userRole == UserRole.MANAGER) 168 else 249
        val arrowCenterXInTooltip = padding + dp(arrowStartMarginDp) + dp(10)

        var x = anchorCenterX - arrowCenterXInTooltip
        var y = anchorY - cachedHeight - marginPx + dp(20)

        val windowRect = Rect()
        anchorView.getWindowVisibleDisplayFrame(windowRect)

        if (x < windowRect.left) x = windowRect.left
        if (x + cachedWidth > windowRect.right) x = windowRect.right - cachedWidth

        if (y < windowRect.top) {
            y = anchorY + anchorH + marginPx - padding
        }

        val scrollBounds = Rect()
        anchorView.getGlobalVisibleRect(scrollBounds)
        
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