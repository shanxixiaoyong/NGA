package sp.phone.linuxdo;

import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import sp.phone.util.ActivityUtils;

/** Small on-demand composers for the three supported LINUX DO mutations. */
public final class LinuxDoActionDialogs {

    public static void showReply(
            Context context,
            int topicId,
            Integer replyToPostNumber,
            Runnable onSuccess) {
        showComposer(context, "回复帖子", "输入回复内容", raw ->
                LinuxDoRepository.getInstance().createReply(
                        topicId, replyToPostNumber, raw, callback("回复成功", onSuccess)));
    }

    public static void showBoost(
            Context context,
            int topicId,
            int postId,
            Runnable onSuccess) {
        showComposer(context, "Boost 回复", "输入简短回复", raw ->
                LinuxDoRepository.getInstance().createBoost(
                        topicId, postId, raw, callback("Boost 已发送", onSuccess)));
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
            String title,
            String hint,
            Submit submit) {
        EditText input = new EditText(context);
        int horizontal = Math.round(20f * context.getResources().getDisplayMetrics().density);
        input.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setPadding(horizontal, input.getPaddingTop(),
                horizontal, input.getPaddingBottom());
        input.setHint(hint);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("发送", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String raw = input.getText().toString().trim();
                    if (raw.isEmpty()) {
                        input.setError("内容不能为空");
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    submit.send(raw);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private interface Submit {
        void send(String raw);
    }

    private LinuxDoActionDialogs() {
    }
}
