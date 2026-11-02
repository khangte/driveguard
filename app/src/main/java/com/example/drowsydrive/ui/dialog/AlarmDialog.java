package com.example.drowsydrive.ui.dialog;

import android.app.AlertDialog;
import android.content.Context;

/**
 * 경고 다이얼로그 UI. "알람 끄기"를 누르면 onStop을 호출한다.
 */
public final class AlarmDialog {
    private AlarmDialog() {
    }

    public static void show(Context context, String title, String message, Runnable onStop) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("알람 끄기", (dialog, which) -> onStop.run())
                .setCancelable(false)
                .show();
    }
}
