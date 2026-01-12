package com.example.smart_safety_management

import android.content.Context
import android.graphics.Rect
import android.view.*
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.view.doOnPreDraw

class TooltipPopup(private val context: Context) {

    private var popup: PopupWindow? = null
    private var anchor: View? = null
    private var marginDp: Int = 8
    
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0

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
                isTouchable = false
                isOutsideTouchable = false
                elevation = dp(4).toFloat()
                setBackgroundDrawable(null)
                isClippingEnabled = false
            }
        }

        if (anchorView.isAttachedToWindow) {
            updatePosition()
        } else {
            anchorView.doOnPreDraw {
                updatePosition()
            }
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

        // [수정] 앵커의 오른쪽 끝을 기준으로 툴팁을 배치합니다.
        // 앵커의 오른쪽 끝(anchorX + anchorW)에서 툴팁 너비(cachedWidth)만큼 왼쪽으로 이동하고, 
        // 추가로 8dp 정도 더 왼쪽으로 밀어줍니다.
        var x = (anchorX + anchorW) - cachedWidth
        var y = anchorY - cachedHeight - marginPx

        val windowRect = Rect()
        anchorView.getWindowVisibleDisplayFrame(windowRect)

        // 화면 왼쪽 끝을 넘어가면 최소한 왼쪽 끝에 맞춤
        if (x < windowRect.left) x = windowRect.left
        
        // 화면 오른쪽 끝을 넘어가면 오른쪽 끝에 맞춤
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
        popup?.dismiss()
        popup = null
        anchor = null
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
