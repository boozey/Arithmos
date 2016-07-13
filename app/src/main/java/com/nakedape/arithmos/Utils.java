package com.nakedape.arithmos;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by Nathan on 5/10/2016.
 */
public class Utils {

    // Image utilities
    public static Bitmap decodeBitmapFromUri(ContentResolver r, Uri uri) throws IOException{
        InputStream inputStream = r.openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream);
    }

    public static Bitmap decodeSampledBitmapFromContentResolver(ContentResolver r, Uri uri, int reqWidth, int reqHeight) throws IOException {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        InputStream inputStream = r.openInputStream(uri);
        BitmapFactory.decodeStream(inputStream, null, options);

        Point dimensions = getScaledDimension(options.outWidth, options.outHeight, reqWidth, reqHeight);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, dimensions.x, dimensions.y);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;

        inputStream.close();
        inputStream = r.openInputStream(uri);
        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Point getScaledDimension(int origWidth, int origHeight, int maxWidth, int maxHeight){
        return getScaledDimension(new Point(origWidth, origHeight), new Point(maxWidth, maxHeight));
    }

    public static Point getScaledDimension(Point imgSize, Point boundary) {

        float original_width = imgSize.x;
        float original_height = imgSize.y;
        float bound_width = boundary.x;
        float bound_height = boundary.y;
        float new_width = original_width;
        float new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Point(Math.round(new_width), Math.round(new_height));
    }


    // Popups
    public static View progressPopup(Context context, int resId){
        return progressPopup(context, context.getResources().getString(resId));
    }
    public static View progressPopup(Context context, String message){
        LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View layout = inflater.inflate(R.layout.loading_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView msgView = (TextView)layout.findViewById(R.id.textView1);
        msgView.setText(message);

        return layout;
    }



    // Time & Dates
    /**
     * Return date in specified format.
     * @param milliSeconds Date in milliseconds
     * @param dateFormat Date format
     * @return String representing date in specified format
     */
    public static String getDate(long milliSeconds, String dateFormat) {
        // Create a DateFormatter object for displaying date in specified format.
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.getDefault());

        // Create a calendar object that will convert the date and time value in milliseconds to date.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliSeconds);
        return formatter.format(calendar.getTime());
    }

    public static final long SEC_IN_MILLIS = 1000;
    public static final long MIN_IN_MILLIS = SEC_IN_MILLIS * 60;
    public static final long HOUR_IN_MILLIS = MIN_IN_MILLIS * 60;
    public static final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
    public static final long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;
    public static final long MONTH_IN_MILLIS = DAY_IN_MILLIS * 30;

    public static String getTimeAgo(Resources res, long millis){
        long elapsedMillis = System.currentTimeMillis() - millis;

        if (elapsedMillis < MIN_IN_MILLIS)
            return res.getString(R.string.just_now);
        else if (elapsedMillis < 2 * MIN_IN_MILLIS)
            return res.getQuantityString(R.plurals.minute_ago_plurals, 1, 1);
        else if (elapsedMillis < 45 * MIN_IN_MILLIS) {
            int minutes = (int)(elapsedMillis / MIN_IN_MILLIS);
            return res.getQuantityString(R.plurals.minute_ago_plurals, minutes, minutes);
        } else if (elapsedMillis < 1.5 * HOUR_IN_MILLIS){
            return res.getQuantityString(R.plurals.hour_ago_plurals, 1, 1);
        }
        else if (elapsedMillis < 20 * HOUR_IN_MILLIS){
            int hours = (int)(elapsedMillis / HOUR_IN_MILLIS);
            return res.getQuantityString(R.plurals.hour_ago_plurals, hours, hours);
        }
        else if (elapsedMillis < WEEK_IN_MILLIS) {
            int days = Math.max(1, (int)(elapsedMillis / DAY_IN_MILLIS));
            return res.getQuantityString(R.plurals.day_ago_plurals, days, days);
        }
        else if (elapsedMillis < MONTH_IN_MILLIS) {
            int weeks = (int)(elapsedMillis / WEEK_IN_MILLIS);
            return res.getQuantityString(R.plurals.week_ago_plurals, weeks, weeks);
        }
        else if (elapsedMillis < MONTH_IN_MILLIS * 6){
            int months = (int)(elapsedMillis / MONTH_IN_MILLIS);
            return res.getQuantityString(R.plurals.month_ago_plurals, months, months);
        }
        else {
            return res.getString(R.string.long_time_ago);
        }
    }


    // File I/O
    public static File getTempFile(Context context, String fileName) {
        File file = null;
        try {
            file = File.createTempFile(fileName, null, context.getCacheDir());
        } catch (IOException e) {
            // Error while creating file
            e.printStackTrace();
        }
        return file;
    }
    public static void CopyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }
}
