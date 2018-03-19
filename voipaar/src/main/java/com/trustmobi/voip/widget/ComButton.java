package com.trustmobi.voip.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.trustmobi.voip.voipaar.R;

/**
 * Created by dds on 2018/3/17 0017.
 */

public class ComButton extends RelativeLayout implements View.OnClickListener {
    private ImageView mImgView;
    private TextView mTextView;

    public ComButton(Context context) {
        this(context, null);
    }

    public ComButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.voip_widget_com_button, this, true);
        mImgView = findViewById(R.id.img);
        mTextView = findViewById(R.id.text);

        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ComButton);
        if (attributes != null) {
            int imageId = attributes.getResourceId(R.styleable.ComButton_src, -1);
            if (imageId != -1) {
                mImgView.setImageResource(imageId);
            }
            String text = attributes.getString(R.styleable.ComButton_text);
            mTextView.setText(text);
            boolean enable = attributes.getBoolean(R.styleable.ComButton_enable, true);
            mImgView.setEnabled(enable);
            attributes.recycle();
        }

        mImgView.setOnClickListener(this);

    }

    public void setImageResource(int resId) {
        mImgView.setImageResource(resId);
    }

    public void setText(String str) {
        mTextView.setText(str);
    }

    public void setEnable(boolean enable) {
        mImgView.setEnabled(enable);
    }


    @Override
    public void onClick(View v) {
        onComClick.onClick(this);
    }

    private onComClick onComClick = null;

    public void setComClickListener(onComClick onComClick) {
        this.onComClick = onComClick;
    }

    public interface onComClick {
        void onClick(View v);
    }
}
