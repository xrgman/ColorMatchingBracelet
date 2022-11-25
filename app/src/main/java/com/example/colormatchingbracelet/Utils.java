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

    public static int getColorRegionRgb(Bitmap image, int x, int y) {
        int nrOfSamp = 0;
        int red = 0, green = 0, blue = 0;

        for(int x_axis = x - 10; x_axis <= x + 10; x_axis++) {
            for(int y_axis = y - 10; y_axis <= y + 10; y_axis++) {
                int pixel = image.getPixel(x_axis, y_axis);

                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                nrOfSamp++;
            }
        }

        return Color.rgb(red/nrOfSamp, green/nrOfSamp, blue/nrOfSamp);
    }

    public static int getColorRegionHsvMaxV(Bitmap image, int x, int y) {
        int nrOfSamp = 0;
        float h = 0.0f, s = 0.0f, v = 0.0f;

        for(int x_axis = x - 10; x_axis <= x + 10; x_axis++) {
            for(int y_axis = y - 10; y_axis <= y + 10; y_axis++) {
                int pixel = image.getPixel(x_axis, y_axis);
                float[] hsv = new float[3];

                Color.colorToHSV(pixel, hsv);

                h += hsv[0];
                s += hsv[1];
                v += hsv[2];

                nrOfSamp++;
            }
        }

       return Color.HSVToColor(255, new float[] {h/nrOfSamp, s/nrOfSamp, 1.0f});
    }

    public static int getColorRgbMapColors(Bitmap image, int x, int y) {
        int nrOfSamp = 0;
        int red = 0, green = 0, blue = 0;

        for(int x_axis = x - 10; x_axis <= x + 10; x_axis++) {
            for(int y_axis = y - 10; y_axis <= y + 10; y_axis++) {
                int pixel = image.getPixel(x_axis, y_axis);

                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                nrOfSamp++;
            }
        }

        int averageRed = red/nrOfSamp;
        int averageGreen = green/nrOfSamp;
        int averageBlue = blue/nrOfSamp;

        int maxDiff = 20;

        //Check if colors are close to eachother:
        if(Math.abs(averageRed - averageGreen) < maxDiff
            && Math.abs(averageRed - averageBlue) < maxDiff
            && Math.abs(averageGreen - averageBlue) < maxDiff) {

            if(averageRed + averageBlue + averageGreen < 600) {
                //This is probably a grey scale color, which looks horrible on the ledstrip
                averageRed = averageGreen = averageBlue = 0;
            }
        }

        averageRed = 165;
        averageGreen = 42;
        averageBlue = 42;


        //Remap probably black to off:



        return Color.rgb(averageRed, averageGreen, averageBlue);
    }


}
