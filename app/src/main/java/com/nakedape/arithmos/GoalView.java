package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

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
        p2Paint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color2, null));

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
            if (game.getGoalType() != ArithmosLevel.GOAL_SINGLE_NUM)
                calculateMultiSizes(getWidth(), getHeight());
            else calculateSingleSize(getWidth(), getHeight());
    }
    @Override
    protected void onDraw(Canvas canvas){
        if (list != null)
            switch (game.getGoalType()){
                case ArithmosLevel.GOAL_SINGLE_NUM:
                    onDrawSingle(canvas);
                    break;
                default:
                    onDrawMulti(canvas);

        }
    }


    private static int VERTICAL = 0, HORIZONTAL = 1;
    private ArithmosGame game;
    private ArrayList<String> list;
    private Paint textPaint, unPlayedPaint, p1Paint, p2Paint;
    private int orientation = 0;
    private float shapeDiam;
    private Bitmap checkMarkBitmap;

    public void setGame(ArithmosGame game){
        this.game = game;
        list = game.getGoalList();
        if (game.getGoalType() == ArithmosLevel.GOAL_SINGLE_NUM)
            calculateSingleSize(getWidth(), getHeight());
        else
            calculateMultiSizes(getWidth(), getHeight());
    }

    // Multi number goal mode
    private ArrayList<RectF> shapeRects;
    private void calculateMultiSizes(int w, int h) {
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
            float diam = Math.min(w, h);
            while (rows * diam < h && cols * diam > w){
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
    private void onDrawMulti(Canvas canvas){
        for (int i = 0; i < list.size(); i++) {
            RectF r = shapeRects.get(i);
            if (game.getGoalsWon(game.PLAYER1).contains(list.get(i))) {
                canvas.drawCircle(r.centerX(), r.centerY(), shapeDiam / 2, p1Paint);
                canvas.drawBitmap(checkMarkBitmap, null, r, null);
            } else if (game.getGoalsWon(game.PLAYER2).contains(list.get(i))) {
                canvas.drawCircle(r.centerX(), r.centerY(), shapeDiam / 2, p2Paint);
                canvas.drawBitmap(checkMarkBitmap, null, r, null);
            } else
                canvas.drawCircle(r.centerX(), r.centerY(), shapeDiam / 2, unPlayedPaint);


            canvas.drawText(list.get(i), r.centerX(), r.centerY() + textPaint.getTextSize() / 3, textPaint);
        }
    }


    // Animated single goal numbers
    RectF shapeRect;
    boolean stopAnimation = true;
    String currentGoal = "";

    private void calculateSingleSize(int w, int h){
        if (orientation == VERTICAL) {
            float diam = Math.min(getResources().getDimensionPixelSize(R.dimen.goal_text_size) * 2, w);
            float margin = diam * 0.05f;
            shapeDiam = diam - 2 * margin;
            textPaint.setTextSize(diam * 0.5f);
            shapeRect = new RectF(0, h, diam, h + diam);
        } else {
            float diam = Math.min(getResources().getDimensionPixelSize(R.dimen.goal_text_size) * 2, h);
            float margin = diam * 0.05f;
            shapeDiam = diam - 2 * margin;
            textPaint.setTextSize(diam * 0.5f);
            shapeRect = new RectF(diam, 0, 0, diam);
        }
        invalidate();
    }
    public void startGoalAnimation(){
        if (game == null || game.getGoalType() != ArithmosLevel.GOAL_SINGLE_NUM) return;

        if (stopAnimation) {
            stopAnimation = false;
            currentGoal = String.valueOf(game.getCurrentGoal());
            final int duration = 5000;
            ValueAnimator shootUp;
            if (orientation == VERTICAL) {
                shootUp = ValueAnimator.ofFloat(getHeight(), 0);
                shootUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float t = (float) animation.getAnimatedValue();
                        shapeRect.offsetTo(shapeRect.left, t);
                        invalidate();
                    }
                });
            } else {
                shootUp = ValueAnimator.ofFloat(0, getWidth());
                shootUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float t = (float) animation.getAnimatedValue();
                        shapeRect.offsetTo(t, shapeRect.top);
                        invalidate();
                    }
                });
            }
            shootUp.setDuration(duration / 2);
            shootUp.setInterpolator(new DecelerateInterpolator());
            shootUp.setRepeatCount(ValueAnimator.INFINITE);
            shootUp.setRepeatMode(ValueAnimator.REVERSE);
            shootUp.addListener(new Animator.AnimatorListener() {
                int count = 0;

                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {

                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                    count = ++count % 2;
                    if (count == 0) {
                        game.nextGoalIndex();
                        currentGoal = String.valueOf(game.getCurrentGoal());
                    }
                    if (stopAnimation)
                        animation.cancel();
                }
            });
            shootUp.start();

            Log.d(LOG_TAG, "Animation started");
        }
    }
    public void stopGoalAnimation(){
        stopAnimation = true;
        Log.d(LOG_TAG, "Animation stopped");
    }
    public boolean isPlaying(){
        return !stopAnimation;
    }
    private void onDrawSingle(Canvas canvas){
        if (!stopAnimation) {
            if (game.getGoalsWon(game.PLAYER1).contains(currentGoal)) {
                canvas.drawCircle(shapeRect.centerX(), shapeRect.centerY(), shapeDiam / 2, p1Paint);
                canvas.drawBitmap(checkMarkBitmap, null, shapeRect, null);
            } else if (game.getGoalsWon(game.PLAYER2).contains(currentGoal)) {
                canvas.drawCircle(shapeRect.centerX(), shapeRect.centerY(), shapeDiam / 2, p2Paint);
                canvas.drawBitmap(checkMarkBitmap, null, shapeRect, null);
            } else
                canvas.drawCircle(shapeRect.centerX(), shapeRect.centerY(), shapeDiam / 2, unPlayedPaint);


            canvas.drawText(currentGoal, shapeRect.centerX(), shapeRect.centerY() + textPaint.getTextSize() / 3, textPaint);
        }
    }
}
