package com.nakedape.arithmos;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

/**
 * Created by Nathan on 6/23/2016.
 */
public class GoalView extends View {

    private static String LOG_TAG = "GoalView";

    public GoalView(Context context, AttributeSet attrs){
        super(context, attrs);
        int textColor, baseColor, textSize;
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.GoalView,
                0, 0);

        try {
            orientation = a.getInteger(R.styleable.GoalView_orientation, 0);
            baseColor = a.getColor(R.styleable.GoalView_shapeColor, ResourcesCompat.getColor(getResources(), R.color.primary, null));
            textColor = a.getColor(R.styleable.GoalView_textColor, ResourcesCompat.getColor(getResources(), R.color.text_primary_light, null));
        } finally {
            a.recycle();
        }

        textPaint = new Paint();
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(textColor);

        unPlayedPaint = new Paint();
        unPlayedPaint.setColor(baseColor);

        p1Paint = new Paint();
        p1Paint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color1, null));

        p2Paint = new Paint();
        p2Paint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color1, null));

        checkMarkBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_green_check_mark);
    }

    // Class overrides
    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int reqWidth = MeasureSpec.getSize(widthMeasureSpec), reqHeight = MeasureSpec.getSize(heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec), heightMode = MeasureSpec.getMode(heightMeasureSpec);


        setMeasuredDimension(reqWidth, reqHeight);
    }
    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){
        if (list != null && list.size() > 0)
            calculateSizes(w, h);
    }
    @Override
    protected void onDraw(Canvas canvas){
        if (list != null)
        for (int i = 0; i < list.size(); i++){
            RectF r = shapeRects.get(i);
            if (game.getGoalsWon(game.PLAYER1).contains(list.get(i))) {
                canvas.drawCircle(r.centerX(), r.centerY(), shapeDiam / 2, p1Paint);
                canvas.drawBitmap(checkMarkBitmap, null, r, null);
            }
            else if (game.getGoalsWon(game.PLAYER2).contains(list.get(i))) {
                canvas.drawCircle(r.centerX(), r.centerY(), shapeDiam / 2, p2Paint);
                canvas.drawBitmap(checkMarkBitmap, null, r, null);
            }
            else
                canvas.drawCircle(r.centerX(), r.centerY(), shapeDiam / 2, unPlayedPaint);


            canvas.drawText(list.get(i), r.centerX(), r.centerY() + textPaint.getTextSize() / 3, textPaint);
        }
    }


    private static int VERTICAL = 0, HORIZONTAL = 1;
    private ArithmosGame game;
    private ArrayList<String> list;
    private ArrayList<RectF> shapeRects;
    private Paint textPaint, unPlayedPaint, p1Paint, p2Paint;
    private int orientation = 0;
    private float shapeDiam;
    private Bitmap checkMarkBitmap;

    public void setGame(ArithmosGame game){
        this.game = game;
        list = game.getGoalList();
        calculateSizes(getWidth(), getHeight());
    }

    private void calculateSizes(int w, int h) {
        shapeRects = new ArrayList<>(list.size());

        if (orientation == VERTICAL){
            float diam = Math.min((float)h / list.size(), w);
            float margin = diam * 0.05f;
            shapeDiam = diam - 2 * margin;
            for (int r = 0; r < list.size(); r++){
                shapeRects.add(new RectF(0, diam * r, diam, diam *(r + 1)));
            }
            textPaint.setTextSize(diam * 0.5f);
        }
        else {
            int cols = list.size(), rows = 1;
            float diam = Math.min(h, (float)w / cols);
            while (rows * diam < 0.8 * h && cols * diam < 0.8 * w){
                rows++;
                cols = (int)Math.ceil((double)list.size() / rows);
                diam = Math.min((float)h / rows, (float)w / cols);
            }
            for (int r = 0, i = 0; r < rows && i < list.size(); r++)
                for (int c = 0; c < cols; c++){
                    shapeRects.add(new RectF(c * diam, r * diam, (c + 1) * diam, (r + 1) * diam));
                    i++;
                }
            float margin = diam * 0.05f;
            shapeDiam = diam - 2 * margin;
            textPaint.setTextSize(diam * 0.5f);
        }
        invalidate();
    }
}
