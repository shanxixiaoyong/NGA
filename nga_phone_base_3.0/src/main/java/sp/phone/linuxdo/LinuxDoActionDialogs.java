package sp.phone.linuxdo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

import gov.anzong.androidnga.R;

import sp.phone.theme.ThemeManager;
import sp.phone.util.ActivityUtils;
import sp.phone.util.ImageUtils;

/** Small on-demand composers for the three supported LINUX DO mutations. */
public final class LinuxDoActionDialogs {

    public interface ReactionSelection {
        void onSelected(String reactionId);
    }

    /** Compact anchored reaction palette: visual emoji only, arranged left-to-right. */
    public static void showReactionPicker(
            Context context,
            View anchor,
            List<String> reactionIds,
            String selectedReaction,
            ReactionSelection selection) {
        if (context == null || anchor == null || selection == null) {
            return;
        }
        // Do not trust topic projections here: Discourse commonly returns only the eight
        // standard emoji and omits both site reactions. The picker is an exact, fixed product
        // surface, backed by the same ten embedded PNGs used in post summaries.
        List<String> palette = LinuxDoReactionAssets.ids();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 6), dp(context, 5), dp(context, 6), dp(context, 5));
        card.setBackground(paletteBackground(context));

        GridLayout grid = new GridLayout(context);
        // Ten fixed site reactions, arranged as two compact rows so narrow phones never clip the
        // final custom pair. The artwork is 20dp, matching the reaction emoji beside a post.
        grid.setColumnCount(5);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        grid.setUseDefaultMargins(false);
        card.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow popup = createPopup(card);
        int accent = ThemeManager.getInstance().getAccentColor(context);
        for (String reactionId : palette) {
            if (reactionId == null || reactionId.trim().isEmpty()) continue;
            ImageView emoji = new ImageView(context);
            emoji.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            emoji.setPadding(dp(context, 6), dp(context, 6),
                    dp(context, 6), dp(context, 6));
            ImageUtils.loadLinuxDoReaction(emoji, reactionId);
            emoji.setContentDescription("表情 " + reactionId);
            emoji.setClickable(true);
            emoji.setFocusable(true);
            emoji.setBackground(reactionBackground(
                    context, accent, reactionId.equalsIgnoreCase(selectedReaction)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(context, 32);
            params.height = dp(context, 32);
            grid.addView(emoji, params);
            emoji.setOnClickListener(view -> {
                popup.dismiss();
                selection.onSelected(reactionId);
            });
        }

        showAnchoredPopup(context, anchor, popup, card);
    }

    private static PopupWindow createPopup(View content) {
        PopupWindow popup = new PopupWindow(
                content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(content.getContext(), 8));
        popup.setClippingEnabled(true);
        return popup;
    }

    private static void showAnchoredPopup(
            Context context, View anchor, PopupWindow popup, View content) {
        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] anchorPosition = new int[2];
        anchor.getLocationOnScreen(anchorPosition);
        int screenWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        int x = Math.max(dp(context, 8), Math.min(
                anchorPosition[0], screenWidth - content.getMeasuredWidth() - dp(context, 8)));
        int y = Math.max(dp(context, 8),
                anchorPosition[1] - content.getMeasuredHeight() - dp(context, 8));
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    private static android.graphics.drawable.Drawable paletteBackground(Context context) {
        TypedValue value = new TypedValue();
        int color = Color.WHITE;
        if (context.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true)) {
            color = value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor((color & 0x00ffffff) | 0xff000000);
        drawable.setStroke(dp(context, 1), 0x30000000);
        drawable.setCornerRadius(dp(context, 16));
        return drawable;
    }

    private static android.graphics.drawable.Drawable reactionBackground(
            Context context, int accent, boolean selected) {
        if (!selected) {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, value, true)) {
                return context.getDrawable(value.resourceId);
            }
            return null;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor((accent & 0x00ffffff) | 0x18000000);
        drawable.setStroke(dp(context, 1), accent);
        drawable.setCornerRadius(dp(context, 12));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.max(1, Math.round(
                value * context.getResources().getDisplayMetrics().density));
    }

    public static void showReply(
            Context context,
            View anchor,
            int topicId,
            Integer replyToPostNumber,
            Runnable onSuccess) {
        showComposer(context, anchor,
                replyToPostNumber == null ? "回复主题" : "回复本楼",
                "输入正式回复，至少20字",
                20,
                raw ->
                LinuxDoRepository.getInstance().createReply(
                        topicId, replyToPostNumber, raw, callback("回复成功", onSuccess)));
    }

    public static void showBoost(
            Context context,
            View anchor,
            int topicId,
            int postId,
            Runnable onSuccess) {
        if (context == null || anchor == null || topicId <= 0 || postId <= 0) return;
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 10), dp(context, 5), dp(context, 6), dp(context, 5));
        card.setBackground(paletteBackground(context));

        LinearLayout inputRow = new LinearLayout(context);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setHint("Boost，最多16字");
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        inputRow.addView(input, new LinearLayout.LayoutParams(dp(context, 210), dp(context, 42)));

        TextView send = new TextView(context);
        send.setText("发送");
        send.setTextColor(ThemeManager.getInstance().getAccentColor(context));
        send.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        send.setGravity(Gravity.CENTER);
        send.setBackground(reactionBackground(context,
                ThemeManager.getInstance().getAccentColor(context), false));
        inputRow.addView(send, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 40)));
        addComposerEmojiStrip(context, card, input);

        PopupWindow popup = createPopup(card);
        send.setOnClickListener(view -> {
            String raw = input.getText() == null ? "" : input.getText().toString().trim();
            if (raw.isEmpty()) {
                input.setError("请输入 Boost 内容");
                return;
            }
            send.setEnabled(false);
            popup.dismiss();
            LinuxDoRepository.getInstance().createBoost(
                    topicId, postId, raw, callback("Boost 已发送", onSuccess));
        });
        showAnchoredPopup(context, anchor, popup, card);
        input.requestFocus();
        input.post(() -> {
            InputMethodManager manager = (InputMethodManager) context.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private static LinuxDoRepository.MutationCallback callback(
            String successMessage,
            Runnable onSuccess) {
        return new LinuxDoRepository.MutationCallback() {
            @Override
            public void onSuccess() {
                ActivityUtils.showToast(successMessage);
                if (onSuccess != null) onSuccess.run();
            }

            @Override
            public void onError(String message) {
                ActivityUtils.showToast(message);
            }
        };
    }

    private static void showComposer(
            Context context,
            View anchor,
            String title,
            String hint,
            int minimumLength,
            Submit submit) {
        if (context == null || anchor == null) return;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 12), dp(context, 8),
                dp(context, 10), dp(context, 7));
        content.setBackground(paletteBackground(context));
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTextColor(ThemeManager.getInstance().getAccentColor(context));
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, dp(context, 30), 1f));
        TextView counter = new TextView(context);
        counter.setText("0 / 至少" + minimumLength + "字");
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        counter.setAlpha(0.64f);
        header.addView(counter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 30)));
        content.addView(header, new LinearLayout.LayoutParams(
                dp(context, 294), dp(context, 30)));
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        content.addView(input, new LinearLayout.LayoutParams(dp(context, 294), dp(context, 108)));
        addComposerEmojiStrip(context, content, input);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s == null ? 0 : Character.codePointCount(s, 0, s.length());
                counter.setText(length + " / 至少" + minimumLength + "字");
                counter.setAlpha(length >= minimumLength ? 1f : 0.64f);
                counter.setTextColor(length >= minimumLength
                        ? ThemeManager.getInstance().getAccentColor(context)
                        : titleView.getCurrentTextColor());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        TextView send = new TextView(context);
        send.setText("发送");
        send.setTextColor(ThemeManager.getInstance().getAccentColor(context));
        send.setGravity(Gravity.CENTER);
        send.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(
                dp(context, 60), dp(context, 38));
        sendParams.gravity = Gravity.END;
        content.addView(send, sendParams);
        PopupWindow popup = createPopup(content);
        send.setOnClickListener(view -> {
            String raw = input.getText() == null ? "" : input.getText().toString().trim();
            int length = raw.codePointCount(0, raw.length());
            if (length < minimumLength) {
                input.setError("正式回复至少需要" + minimumLength + "字，当前" + length + "字");
                return;
            }
            send.setEnabled(false);
            popup.dismiss();
            submit.send(raw);
        });
        showAnchoredPopup(context, anchor, popup, content);
        input.requestFocus();
        input.post(() -> {
            InputMethodManager manager = (InputMethodManager) context.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private interface Submit {
        void send(String raw);
    }

    private static void addComposerEmojiStrip(
            Context context, LinearLayout parent, EditText input) {
        // Keep reply and Boost composers on the same fixed, offline ten-reaction palette as the
        // floor picker.  The old single 320dp row was clipped by the 294dp reply card, which made
        // the final two site reactions disappear and encouraged Web/CDN fallbacks with mismatched
        // artwork.  A compact 5 x 2 grid is fully visible even on narrow phones.
        List<String> ids = LinuxDoReactionAssets.ids();
        GridLayout strip = new GridLayout(context);
        strip.setColumnCount(5);
        strip.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        strip.setUseDefaultMargins(false);
        strip.setPadding(0, dp(context, 2), 0, dp(context, 2));
        for (String id : ids) {
            if (id == null || id.trim().isEmpty()) continue;
            ImageView emoji = new ImageView(context);
            emoji.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            emoji.setPadding(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
            ImageUtils.loadLinuxDoReaction(emoji, id);
            GridLayout.LayoutParams emojiParams = new GridLayout.LayoutParams();
            emojiParams.width = dp(context, 30);
            emojiParams.height = dp(context, 30);
            strip.addView(emoji, emojiParams);
            emoji.setOnClickListener(view -> {
                // Discourse owns these custom mappings. Sending Unicode substitutes changes
                // tieba_087/bili_057/distorted_face into entirely different reactions.
                String insertion = ":" + id + ":";
                int start = Math.max(0, input.getSelectionStart());
                input.getText().insert(start, insertion);
            });
        }
        parent.addView(strip, new LinearLayout.LayoutParams(
                dp(context, 150), dp(context, 64)));
    }

    /** Short, non-blocking feedback matching the web reaction's “emoji +1” interaction. */
    public static void showReactionFeedback(
            Context context, View anchor, String reactionId, boolean removing) {
        if (context == null || anchor == null || reactionId == null) return;
        LinearLayout chip = new LinearLayout(context);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5));
        chip.setBackground(paletteBackground(context));
        ImageView emoji = new ImageView(context);
        emoji.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ImageUtils.loadLinuxDoReaction(emoji, reactionId);
        chip.addView(emoji, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
        TextView delta = new TextView(context);
        delta.setText(removing ? " −1" : " +1");
        delta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        delta.setTextColor(ThemeManager.getInstance().getAccentColor(context));
        chip.addView(delta);
        PopupWindow popup = new PopupWindow(chip, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(false);
        popup.setClippingEnabled(true);
        showAnchoredPopup(context, anchor, popup, chip);
        chip.setAlpha(0f);
        chip.setTranslationY(dp(context, 8));
        chip.animate().alpha(1f).translationY(0f).setDuration(120).withEndAction(() ->
                chip.animate().alpha(0f).translationY(-dp(context, 18))
                        .setStartDelay(360).setDuration(220)
                        .withEndAction(popup::dismiss).start()).start();
    }

    private LinuxDoActionDialogs() {
    }
}
