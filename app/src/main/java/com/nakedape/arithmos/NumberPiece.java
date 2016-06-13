package com.nakedape.arithmos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Nathan on 4/6/2016.
 */
public class NumberPiece extends View {
    private String value;
    private String owner;
    private Paint textPaint;

    public NumberPiece(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public NumberPiece(Context context){
        super(context);
        textPaint = new Paint();
        textPaint.setColor(Color.RED);
        textPaint.setTextSize(24);
    }

    public void setValue(String input){
        String[] strings = input.split(":");
        value = strings[0];
        owner = strings[1];
    }

    @Override
    protected void onDraw(Canvas canvas){
        canvas.drawText(value, 0, 0, textPaint);
    }
}
