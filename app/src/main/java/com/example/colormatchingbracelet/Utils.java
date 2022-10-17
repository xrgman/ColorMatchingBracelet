package com.example.colormatchingbracelet;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

public class Utils {

    /**
     * Create a basic progress dialog object.
     * @param context - Application context.
     * @param title - Title of the progress dialog box.
     * @param spinner - Enable or disable the spinner inside the dialog.
     * @param cancelable - Set dialog cancelable.
     * @return Progress dialog.
     */
    public static ProgressDialog CreateProgressDialog(Context context, String title, boolean spinner, boolean cancelable) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(title);
        progressDialog.setProgressStyle(spinner ? ProgressDialog.STYLE_SPINNER : ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(cancelable);
        progressDialog.show();

        return progressDialog;
    }

//    public static Color getAverageColor(Bitmap bitmap) {
//        long redBucket = 0;
//        long greenBucket = 0;
//        long blueBucket = 0;
//        long pixelCount = 0;
//
//        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
//
//        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
//
//        for (int i = 0; i < pixels.length; i++)
//        {
//                int pixel = pixels[i];
//
//                redBucket += Color.red(pixel);
//                greenBucket += Color.green(pixel);
//                blueBucket += Color.blue(pixel);
//                // does alpha matter?
//
//        }
//
//        return Color.valueOf(redBucket / pixels.length, greenBucket / pixels.length,blueBucket / pixels.length, 100.0f);
//    }


}
