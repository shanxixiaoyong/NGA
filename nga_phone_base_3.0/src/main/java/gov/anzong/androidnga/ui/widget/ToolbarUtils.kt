package gov.anzong.androidnga.ui.widget

import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar

/**
 * Toolbar 的小工具。
 */
object ToolbarUtils {

    /**
     * 让 Toolbar 的标题文字可点击。
     *
     * Toolbar 只有在标题非空时才会创建标题 TextView，而 [sp.phone.ui.fragment.BaseFragment]
     * 要到 onResume 才把标题写进 Activity，所以先直接找一次，找不到就等下一帧再找一次。标题
     * TextView 一旦创建就一直复用，后续改标题文字不会换成新的实例，绑一次即可。
     *
     * 只绑标题本身，不绑整个 Toolbar：空白区域和导航、菜单按钮都不该触发这个动作。
     */
    @JvmStatic
    fun setOnTitleClickListener(toolbar: Toolbar?, listener: View.OnClickListener) {
        if (toolbar == null || bindTitleView(toolbar, listener)) {
            return
        }
        toolbar.post { bindTitleView(toolbar, listener) }
    }

    private fun bindTitleView(toolbar: Toolbar, listener: View.OnClickListener): Boolean {
        val titleView = findTitleView(toolbar) ?: return false
        titleView.setOnClickListener(listener)
        return true
    }

    /**
     * Toolbar 没有公开标题 TextView，只能按文字从子 View 里认出来；副标题的文字与标题不同，
     * 不会被误认。
     */
    private fun findTitleView(toolbar: Toolbar): TextView? {
        val title = toolbar.title
        if (TextUtils.isEmpty(title)) {
            return null
        }
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is TextView && TextUtils.equals(child.text, title)) {
                return child
            }
        }
        return null
    }
}
