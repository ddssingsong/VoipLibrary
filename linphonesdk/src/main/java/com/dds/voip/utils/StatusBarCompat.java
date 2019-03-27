package com.dds.voip.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Created by dds on 2017/1/11.
 * 联信摩贝
 */

public class StatusBarCompat {
    private static final int INVALID_VAL = -1;
    private static final int COLOR_DEFAULT = Color.parseColor("#1b82d2");

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void compat(Activity activity, int statusColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (statusColor != INVALID_VAL) {
                activity.getWindow().setStatusBarColor(statusColor);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            int color = COLOR_DEFAULT;
            ViewGroup contentView = (ViewGroup) activity.findViewById(android.R.id.content);
            if (statusColor != INVALID_VAL) {
                color = statusColor;
            }
            View statusBarView = new View(activity);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    getStatusBarHeight(activity));
            statusBarView.setBackgroundColor(color);
            contentView.addView(statusBarView, lp);
            contentView.setFitsSystemWindows(true);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        }
    }

    public static void compat(Activity activity) {
        compat(activity, INVALID_VAL);
    }

    public static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    // 状态栏处理：解决全屏切换非全屏页面被压缩问题
    private static void initStatusBar(Activity context, int barColor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (barColor != INVALID_VAL) {
                context.getWindow().setStatusBarColor(barColor);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                context.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                int statusBarHeight = getStatusBarHeight(context);
                View rectView = new View(context);
                // 绘制一个和状态栏一样高的矩形，并添加到视图中
                LinearLayout.LayoutParams params
                        = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBarHeight);
                rectView.setLayoutParams(params);
                //设置状态栏颜色
                int color = COLOR_DEFAULT;
                if (barColor != INVALID_VAL) {
                    color = barColor;
                }
                rectView.setBackgroundColor(color);
                // 添加矩形View到布局中
                ViewGroup decorView = (ViewGroup) context.getWindow().getDecorView();
                decorView.addView(rectView);
                ViewGroup rootView = (ViewGroup) ((ViewGroup) context.findViewById(android.R.id.content)).getChildAt(0);
                rootView.setFitsSystemWindows(true);
                rootView.setClipToPadding(true);
            } catch (Exception e) {
                // 锤子手机设置失败问题
                e.printStackTrace();
            }

        }
    }

}
