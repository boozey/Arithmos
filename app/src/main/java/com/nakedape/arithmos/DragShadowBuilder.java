package com.nakedape.arithmos;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Created by Nathan on 5/29/2016.
 */
public class DragShadowBuilder extends View.DragShadowBuilder {
    // The drag shadow image, defined as a drawable thing
    private static Drawable shadow;

    // Defines the constructor for myDragShadowBuilder
    public DragShadowBuilder(View v, Drawable image) {

        // Stores the View parameter passed to myDragShadowBuilder.
        super(v);

        // Creates a draggable image that will fill the Canvas provided by the system.
        shadow = image;
    }

    // Defines a callback that sends the drag shadow dimensions and touch point back to the
    // system.
    @Override
    public void onProvideShadowMetrics (Point size, Point touch) {
        // Defines local variables
        int width, height;

        // Sets the width and height of the shadow
        width = getView().getWidth();
        height = getView().getHeight();

        // The drag shadow is a ColorDrawable. This sets its dimensions to be the same as the
        // Canvas that the system will provide. As a result, the drag shadow will fill the
        // Canvas.
        shadow.setBounds(0, 0, width, height);

        // Sets the size parameter's width and height values. These get back to the system
        // through the size parameter.
        size.set(width, height);

        // Sets the touch point's position to be in the middle of the drag shadow
        touch.set(width * 4/5, height * 4/5);
    }

    // Defines a callback that draws the drag shadow in a Canvas that the system constructs
    // from the dimensions passed in onProvideShadowMetrics().
    @Override
    public void onDrawShadow(Canvas canvas) {

        // Draws the ColorDrawable in the Canvas passed in from the system.
        shadow.draw(canvas);
    }
}
