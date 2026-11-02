package com.example.drowsydrive.ui.main;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.example.drowsydrive.BuildConfig;
import com.example.drowsydrive.R;

/**
 * MainActivity의 메뉴/안내/종료 다이얼로그 표시만 담당하고, 사용자 선택은 Listener로 전달한다.
 * 위치 조회와 화면 이동은 Activity가 처리한다.
 */
public class MainDialogs {
    public static final String KEYWORD_REST_AREA = "휴게소";
    public static final String KEYWORD_REST_STOP = "졸음쉼터";

    public interface Listener {
        /** 메뉴에서 지도를 선택했다. 처리했으면 true를 반환해 메뉴를 닫는다. */
        boolean onMapMenuSelected();
        /** 메뉴에서 CO2 센서 선택을 요청했다. */
        void onBluetoothMenuSelected();
        /** 안내창에서 휴게소/졸음쉼터 검색을 선택했다. */
        void onGuideKeywordSelected(String keyword);
        /** 종료 다이얼로그에서 "예"를 선택했다. */
        void onExitConfirmed();
    }

    private final Activity activity;
    private final Listener listener;
    private Dialog menuDialog;

    public MainDialogs(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    /** 앱 종료 여부 확인 다이얼로그 */
    public void showExitDialog() {
        new AlertDialog.Builder(activity)
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("예", (dialog, which) -> listener.onExitConfirmed())
                .setNegativeButton("아니요", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    /** 안내 요청 다이얼로그: 휴게소/졸음쉼터 검색 선택지 제공 */
    public void showGuideDialog() {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.notification_dialog);
        dialog.setTitle("안내 요청");

        Button btnNo = dialog.findViewById(R.id.btn_no);
        Button btnRestArea = dialog.findViewById(R.id.btn_rest_area);
        Button btnRestStop = dialog.findViewById(R.id.btn_rest_stop);

        btnNo.setOnClickListener(view -> dialog.dismiss());
        btnRestArea.setOnClickListener(view -> {
            listener.onGuideKeywordSelected(KEYWORD_REST_AREA);
            dialog.dismiss();
        });
        btnRestStop.setOnClickListener(view -> {
            listener.onGuideKeywordSelected(KEYWORD_REST_STOP);
            dialog.dismiss();
        });

        dialog.show();
    }

    /** 메뉴 다이얼로그 토글 */
    public void toggleMenuDialog(View anchorView) {
        if (menuDialog != null && menuDialog.isShowing()) {
            menuDialog.dismiss();
            menuDialog = null;
        } else {
            showMenuDialog(anchorView);
        }
    }

    private void showMenuDialog(View anchorView) {
        menuDialog = new Dialog(activity);
        menuDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        menuDialog.setContentView(R.layout.menu_dialog);
        menuDialog.setCanceledOnTouchOutside(true);

        Window window = menuDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            int[] location = new int[2];
            anchorView.getLocationOnScreen(location); // FAB 버튼의 위치 가져오기
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = location[0];
            params.y = location[1] - anchorView.getHeight() - 300; // FAB 위쪽으로 다이얼로그 위치 조정
            window.setAttributes(params);
        }

        Button mapButton = menuDialog.findViewById(R.id.map_button);
        mapButton.setOnClickListener(view -> {
            if (listener.onMapMenuSelected() && menuDialog != null) {
                menuDialog.dismiss();
            }
        });

        View bluetoothButton = menuDialog.findViewById(R.id.bluetooth_button);
        bluetoothButton.setVisibility(BuildConfig.CO2_SENSOR_ENABLED ? View.VISIBLE : View.GONE);
        bluetoothButton.setOnClickListener(view -> {
            menuDialog.dismiss();
            listener.onBluetoothMenuSelected();
        });

        menuDialog.show();
    }
}
