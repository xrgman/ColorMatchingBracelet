package com.example.colormatchingbracelet;

import android.app.ProgressDialog;
import android.content.Context;

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


}
