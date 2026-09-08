package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.alibaba.android.arouter.launcher.ARouter;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import butterknife.BindView;
import butterknife.OnClick;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.LauncherSubActivity;
import gov.anzong.androidnga.arouter.ARouterConstants;
import gov.anzong.androidnga.base.util.ToastUtils;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.ParamKey;
import sp.phone.param.ContentSource;
import sp.phone.util.ActivityUtils;
import sp.phone.linuxdo.LinuxDoRepository;
import gov.anzong.androidnga.http.OnHttpCallBack;

/**
 * Created by Justwen on 2017/11/19.
 */

public class TopicListFragment extends TopicSearchFragment {

    private Menu mOptionMenu;
    private boolean mNotificationCheckInFlight;
    private int mLinuxDoUnreadCount;
    private View mLinuxDoNotificationDot;
    private View mLinuxDoQuickMenuView;

    @BindView(R.id.fab_post)
    public FloatingActionButton mFab;

    @BindView(R.id.appbar)
    public AppBarLayout mAppBarLayout;

    @BindView(R.id.toolbar)
    public Toolbar mToolbar;

    @Override
    protected void setTitle() {
        if (mRequestParam.title != null) {
            setTitle(mRequestParam.title);
        }
    }

    @Override
    public void setData(TopicListInfo result) {
        super.setData(result);
        if (mRequestParam.title == null && result.getName() != null && getActivity() != null) {
            mRequestParam.title = result.getName();
            getActivity().setTitle(mRequestParam.title);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutId = R.layout.fragment_topic_list_board;
        return inflater.inflate(layoutId, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mRequestParam.source == ContentSource.LINUX_DO) {
            mFab.setVisibility(View.GONE);
        }
    }

    @Override
    public void hideLoadingView() {
        AppBarLayout.LayoutParams lp = (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        lp.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        super.hideLoadingView();
    }

    @Override
    public void scrollTo(int position) {
        if (position == 0) {
            mAppBarLayout.setExpanded(true, true);
        }
        super.scrollTo(position);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mRequestParam != null && mRequestParam.source == ContentSource.LINUX_DO) {
            refreshLinuxDoNotifications();
        }
    }

    @OnClick(R.id.fab_post)
    public void startPostActivity() {
        ARouter.getInstance()
                .build(ARouterConstants.ACTIVITY_POST)
                .withInt(ParamKey.KEY_FID, mRequestParam.fid)
                .withString(ParamKey.KEY_STID, mRequestParam.stid != 0 ? String.valueOf(mRequestParam.stid) : null)
                .withString(ParamKey.KEY_ACTION, "new")
                .navigation();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (mRequestParam.source == ContentSource.LINUX_DO) {
            super.onPrepareOptionsMenu(menu);
            menu.findItem(R.id.menu_add_bookmark).setVisible(false);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(false);
            menu.findItem(R.id.menu_search).setVisible(true);
            menu.findItem(R.id.menu_search).setTitle("搜索");
            menu.findItem(R.id.menu_linuxdo_quick_menu).setVisible(true);
            // LinuxDo options are flat actions in this same overflow menu. The old
            // aggregate settings entry stays hidden so users do not have to open a
            // second “LINUX DO 设置” screen just to reach login/filters/DNS.
            menu.findItem(R.id.menu_linuxdo_settings).setVisible(false);
            menu.findItem(R.id.menu_linuxdo_login).setVisible(false);
            menu.findItem(R.id.menu_linuxdo_profile).setVisible(false);
            menu.findItem(R.id.menu_linuxdo_notifications).setVisible(false);
            // Keep one category entry only.  The old dedicated category id is kept
            // in the menu for upgrades/deep-links, but exposing it beside
            // menu_sub_board produced two visually identical "子板块" actions.
            menu.findItem(R.id.menu_linuxdo_categories).setVisible(false);
            menu.findItem(R.id.menu_linuxdo_filters).setVisible(false);
            menu.findItem(R.id.menu_linuxdo_doh).setVisible(false);
            menu.findItem(R.id.menu_twenty_four).setVisible(false);
            menu.findItem(R.id.menu_board_head).setVisible(false);
            menu.findItem(R.id.menu_recommend).setVisible(false);
            menu.findItem(R.id.menu_sub_board).setVisible(false);
            menu.findItem(R.id.menu_sub_board).setTitle("子板块");
            menu.findItem(R.id.menu_history).setVisible(false);
            ensureLinuxDoQuickMenu(menu.findItem(R.id.menu_linuxdo_quick_menu));
            updateNotificationMenuTitle();
            menu.findItem(R.id.menu_favorite).setVisible(false);
            return;
        }
        menu.findItem(R.id.menu_linuxdo_settings).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_login).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_profile).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_notifications).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_categories).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_filters).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_doh).setVisible(false);
        menu.findItem(R.id.menu_linuxdo_quick_menu).setVisible(false);
        menu.findItem(R.id.menu_search).setTitle(R.string.menu_search);
        if (mPresenter.isBookmarkBoard(mRequestParam.fid, mRequestParam.stid)) {
            menu.findItem(R.id.menu_add_bookmark).setVisible(false);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(true);
        } else {
            menu.findItem(R.id.menu_add_bookmark).setVisible(true);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(false);
        }

        if (mTopicListInfo != null) {
            menu.findItem(R.id.menu_sub_board).setVisible(!mTopicListInfo.getSubBoardList().isEmpty());
        } else {
            menu.findItem(R.id.menu_sub_board).setVisible(false);
        }

        if (mRequestParam.fid == 0 && mRequestParam.stid == 0) {
            menu.findItem(R.id.menu_add_bookmark).setVisible(false);
            menu.findItem(R.id.menu_remove_bookmark).setVisible(false);
        }

        menu.findItem(R.id.menu_board_head).setVisible(mRequestParam.boardHead != null);

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.topic_list_menu, menu);
        mOptionMenu = menu;
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void refreshLinuxDoNotifications() {
        if (mNotificationCheckInFlight) return;
        mNotificationCheckInFlight = true;
        LinuxDoRepository.getInstance().loadUnreadNotificationTotal(
                new OnHttpCallBack<Integer>() {
                    @Override public void onSuccess(Integer total) {
                        mNotificationCheckInFlight = false;
                        if (!isAdded()) return;
                        mLinuxDoUnreadCount = total == null ? 0 : Math.max(0, total);
                        updateNotificationMenuTitle();
                    }

                    @Override public void onError(String ignored) {
                        mNotificationCheckInFlight = false;
                    }
                });
    }

    private void updateNotificationMenuTitle() {
        if (mLinuxDoNotificationDot != null) {
            mLinuxDoNotificationDot.setVisibility(
                    mLinuxDoUnreadCount > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void ensureLinuxDoQuickMenu(MenuItem item) {
        if (item == null || mLinuxDoQuickMenuView != null) return;
        FrameLayout action = new FrameLayout(requireContext());
        action.setContentDescription("LINUX DO 菜单");
        action.setPadding(dp(12), dp(12), dp(12), dp(12));
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_more_ver_24dp);
        icon.setColorFilter(Color.WHITE);
        action.addView(icon, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        View dot = new View(requireContext());
        GradientDrawable dotBackground = new GradientDrawable();
        dotBackground.setShape(GradientDrawable.OVAL);
        dotBackground.setColor(0xffff5252);
        dot.setBackground(dotBackground);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(8), dp(8),
                Gravity.TOP | Gravity.END);
        dotParams.setMargins(0, dp(7), dp(7), 0);
        action.addView(dot, dotParams);
        mLinuxDoNotificationDot = dot;
        mLinuxDoQuickMenuView = action;
        item.setActionView(action);
        action.setOnClickListener(this::showLinuxDoCompactMenu);
    }

    private void showLinuxDoCompactMenu(View anchor) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        TypedValue backgroundValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.colorBackground,
                backgroundValue, true);
        int backgroundColor = backgroundValue.resourceId != 0
                ? requireContext().getColor(backgroundValue.resourceId) : backgroundValue.data;
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setStroke(dp(1), 0x26000000);
        background.setCornerRadius(dp(10));
        card.setBackground(background);
        PopupWindow popup = new PopupWindow(card, dp(156),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(6));
        addCompactMenuRow(card, "子板块", () -> {
            if (mRequestParam.linuxDoCategoryId > 0) {
                sp.phone.linuxdo.LinuxDoNavigation.openCategoryDirectory(requireContext(),
                        mRequestParam.linuxDoCategoryId, mRequestParam.title,
                        mRequestParam.linuxDoCategorySlug);
            } else sp.phone.linuxdo.LinuxDoNavigation.openCategoryDirectory(requireContext());
        }, popup);
        String notification = mLinuxDoUnreadCount > 0
                ? "通知  ·  " + mLinuxDoUnreadCount : "通知";
        addCompactMenuRow(card, notification,
                () -> sp.phone.linuxdo.LinuxDoNavigation.openNotifications(requireContext()), popup);
        addCompactMenuRow(card, "我的资料",
                () -> sp.phone.linuxdo.LinuxDoNavigation.openProfile(requireContext(), null), popup);
        addCompactMenuRow(card, "信任等级",
                () -> sp.phone.linuxdo.LinuxDoNavigation.openTrustLevels(requireContext()), popup);
        addCompactMenuRow(card, "登录",
                () -> sp.phone.linuxdo.LinuxDoNavigation.openLogin(requireContext()), popup);
        addCompactMenuRow(card, "网络验证",
                () -> sp.phone.linuxdo.LinuxDoNavigation.openVerification(requireContext()), popup);
        addCompactMenuRow(card, "屏蔽",
                () -> sp.phone.linuxdo.LinuxDoNavigation.openFilterSettings(requireContext()), popup);
        addCompactMenuRow(card, "DNS",
                () -> sp.phone.linuxdo.LinuxDoNavigation.editDoh(requireContext()), popup);
        addCompactMenuRow(card, "浏览历史",
                () -> ActivityUtils.startHistoryTopicActivity(requireContext()), popup);
        popup.showAsDropDown(anchor, -dp(108), -dp(4));
    }

    private void addCompactMenuRow(LinearLayout card, String label, Runnable action,
                                   PopupWindow popup) {
        TextView row = new TextView(requireContext());
        row.setText(label);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(10), 0);
        TypedValue selectable = new TypedValue();
        requireContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, selectable, true);
        if (selectable.resourceId != 0) row.setBackgroundResource(selectable.resourceId);
        card.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));
        row.setOnClickListener(view -> {
            popup.dismiss();
            action.run();
        });
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value
                * requireContext().getResources().getDisplayMetrics().density));
    }

    @Override
    public void onDestroyView() {
        mLinuxDoNotificationDot = null;
        mLinuxDoQuickMenuView = null;
        super.onDestroyView();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_bookmark:
                mPresenter.addBookmarkBoard();
                item.setVisible(false);
                mOptionMenu.findItem(R.id.menu_remove_bookmark).setVisible(true);
                ToastUtils.showToast(R.string.toast_add_bookmark_board);
                break;
            case R.id.menu_remove_bookmark:
                mPresenter.removeBookmarkBoard(mRequestParam.fid, mRequestParam.stid);
                item.setVisible(false);
                mOptionMenu.findItem(R.id.menu_add_bookmark).setVisible(true);
                ToastUtils.showToast(R.string.toast_remove_bookmark_board);
                break;
            case R.id.menu_sub_board:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    if (mRequestParam.linuxDoCategoryId > 0) {
                        sp.phone.linuxdo.LinuxDoNavigation.openCategoryDirectory(
                                requireContext(), mRequestParam.linuxDoCategoryId,
                                mRequestParam.title, mRequestParam.linuxDoCategorySlug);
                    } else {
                        sp.phone.linuxdo.LinuxDoNavigation.openCategoryDirectory(requireContext());
                    }
                } else {
                    showSubBoardList();
                }
                break;
            case R.id.menu_search:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openSearch(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_settings:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openSettings(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_login:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openLogin(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_profile:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openProfile(requireContext(), null);
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_notifications:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openNotifications(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_categories:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openCategoryDirectory(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_filters:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.openFilterSettings(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_linuxdo_doh:
                if (mRequestParam.source == ContentSource.LINUX_DO) {
                    sp.phone.linuxdo.LinuxDoNavigation.editDoh(requireContext());
                    break;
                }
                return super.onOptionsItemSelected(item);
            case R.id.menu_board_head:
                mPresenter.startArticleActivity(mRequestParam.boardHead, mRequestParam.title + " - 版头");
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showSubBoardList() {
        Intent intent = new Intent(getContext(), LauncherSubActivity.class);
        intent.putExtra("fragment", BoardSubListFragment.class.getName());
        intent.putExtra(ParamKey.KEY_TITLE, mRequestParam.title);
        intent.putExtra(ParamKey.KEY_FID, mRequestParam.fid);

        intent.putParcelableArrayListExtra("subBoard", mTopicListInfo.getSubBoardList());
        startActivityForResult(intent, ActivityUtils.REQUEST_CODE_SUB_BOARD);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ActivityUtils.REQUEST_CODE_SUB_BOARD && resultCode == Activity.RESULT_OK) {
            mPresenter.loadPage(1, mRequestParam);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
