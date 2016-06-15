package com.nakedape.arithmos;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * Created by Nathan on 5/14/2016.
 */
public class Animations {
    // Animations
    public static AnimatorSet shrinkOut(View v, int duration) {
        ObjectAnimator shrinkX = ObjectAnimator.ofFloat(v, "ScaleX", 1f, 0f);
        ObjectAnimator shrinkY = ObjectAnimator.ofFloat(v, "ScaleY", 1f, 0f);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(shrinkX, shrinkY, fadeOut);
        set.setDuration(duration);
        return set;
    }

    public static AnimatorSet popIn(View v, int duration, int delay) {
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        ObjectAnimator expandX = ObjectAnimator.ofFloat(v, "ScaleX", 0f, 1f);
        ObjectAnimator expandY = ObjectAnimator.ofFloat(v, "ScaleY", 0f, 1f);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(expandX, expandY, fadeIn);
        set.setInterpolator(new OvershootInterpolator());
        set.setStartDelay(delay);
        set.setDuration(duration);
        return set;
    }

    public static AnimatorSet explodeFade(View v, int duration, int delay){
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);
        ObjectAnimator explodeX = ObjectAnimator.ofFloat(v, "ScaleX", 1f, 10f);
        ObjectAnimator explodeY = ObjectAnimator.ofFloat(v, "ScaleY", 1f, 10f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fadeOut, explodeX, explodeY);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet fadeIn(View v, int duration, int delay){
        v.setVisibility(View.VISIBLE);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(v, "alpha",01f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new DecelerateInterpolator());
        set.play(fadeOut);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet slideRight(View v, int duration, int delay, float amount){
        ObjectAnimator slide = ObjectAnimator.ofFloat(v, "TranslationX", v.getTranslationX(), v.getTranslationX() + amount);

        AnimatorSet set = new AnimatorSet();
        set.play(slide);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet slideUp(View v, int duration, int delay, float amount){
        v.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        ObjectAnimator slide = ObjectAnimator.ofFloat(v, "TranslationY", v.getTranslationY() + amount, v.getTranslationY());
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(v, "alpha", 0f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new DecelerateInterpolator());
        set.playTogether(slide, fadeIn);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet slideDown(View v, int duration, int delay, float amount){
        ObjectAnimator slide = ObjectAnimator.ofFloat(v, "TranslationY", v.getTranslationY(), v.getTranslationY() + amount);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new AccelerateInterpolator());
        set.playTogether(slide, fadeIn);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static AnimatorSet fadeOut(View v, int duration, int delay){
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(v, "alpha", 1f, 0f);

        AnimatorSet set = new AnimatorSet();
        set.setInterpolator(new DecelerateInterpolator());
        set.play(fadeOut);
        set.setStartDelay(delay);
        set.setDuration(duration);

        return set;
    }

    public static void CountTo(final TextView textView, int start, int end){
        // Animate score
        ValueAnimator countUp = ValueAnimator.ofInt(start, end);
        countUp.setDuration(1000);
        countUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                textView.setText(String.valueOf((int) animation.getAnimatedValue()));
            }
        });
        countUp.start();
    }

    public static void CountTo(final Resources resources, final int stringResId, final TextView textView, int start, int end){
        // Animate score
        ValueAnimator countUp = ValueAnimator.ofInt(start, end);
        countUp.setDuration(1000);
            countUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    textView.setText(resources.getString(stringResId, (int)animation.getAnimatedValue()));
                }
            });
        countUp.start();
    }
}
