package com.robotemi.sdk.sample.nlp;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.robotemi.sdk.sample.R;
//import com.robotemi.sdk.sample.utils.ChoicesDialog;
import com.robotemi.sdk.sample.utils.PrefsHelper;

public class DataIO {
    private static final String TAG = DataIO.class.getSimpleName();

    private final Context context;
    private final RequestQueue requestQueue;
    private final Callback callback;

    private StringRequest stringRequest;
    private Map <String, String> mHeaders = new HashMap<>();
//    private ChoicesDialog dialog;

    public interface Callback{
        void onDataReceived(JSONObject data);
    }

    public DataIO(Context context, Callback callback){
        this.context = context;
        this.callback = callback;
        this.requestQueue = Volley.newRequestQueue(context);
    }

    public void prepareRequest(JSONObject data, String APIname){
        String url = context.getString(R.string.nlp_server_address) + APIname;
        final String requestBody = data.toString();
        Log.d(TAG, "requestBody: " + requestBody);
        stringRequest = new StringRequest(Request.Method.POST, url, response -> {
            try {
                if(response != null && !response.isEmpty()){
                    Log.e(TAG, "response:" + response);
                    clearProgressDialog();
                    if(callback != null){
                        if (isJSONString(response))
                            callback.onDataReceived(new JSONObject(response));
                        else{
                            Log.e(TAG, "got error:" + response);
                            callback.onDataReceived(null);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }, error -> {
            //TODO: CATCH CONNECTION ERROR HERE
            Log.e(TAG, "Some error occurred:" + error.getMessage());
            clearProgressDialog();
        }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() {
                return requestBody.getBytes(StandardCharsets.UTF_8);
            }
/*
            @Override
            protected Response<String> parseNetworkResponse(NetworkResponse response) {
                if (response != null) {
                    //String json = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
                    String json = new String(response.data, StandardCharsets.UTF_8);
                    return Response.success(json, HttpHeaderParser.parseCacheHeaders(response));
                }
                return null;
            }

 */

            @Override
            protected Response <String> parseNetworkResponse (NetworkResponse response) {
                if (response != null) {
                    String json = new String(response.data, StandardCharsets.UTF_8);

                    Map<String, String> responseHeaders = response.headers;
                    String rawCookies = responseHeaders.get("Set-Cookie");
                    Log.e(TAG, "Get Cookies:" + rawCookies);

                    Log.e(TAG, String.format("Check Cookies in Pref: [%s]", PrefsHelper.getString(context, "session_id", "")));
                    if (rawCookies != null) {
                        PrefsHelper.put(context, "session_id", rawCookies);
                    }
                    Log.e(TAG, String.format("Set Cookies in Pref: [%s]", PrefsHelper.getString(context, "session_id", "")));

                    return Response.success(json, HttpHeaderParser.parseCacheHeaders(response));
                }
                return null;
            }


            @Override
            public Map <String, String> getHeaders () {
                setSendCookie(PrefsHelper.getString(context, "session_id", ""));
                Log.e(TAG, String.format("Retrieve Cookies from Pref and set in post header %%: [%s]", mHeaders.get ("Cookie")));
                return mHeaders;
            }

            public void setSendCookie (String cookie) {
                mHeaders.put("Cookie", cookie);
            }
        };

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(10000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
    }

    public void submitData(String message){
        if(message !=null)
            showProgressDialog(message);
        requestQueue.add(stringRequest);
    }

    private boolean isJSONString(String input){
        try {
            new JSONObject(input);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void showProgressDialog(String msg) {
//        dialog = new ChoicesDialog(context, null);
//        dialog.setText(msg);
//        dialog.showNegative(false);
//        dialog.showPositive(false);
//        dialog.show();
    }

    private void clearProgressDialog() {
//        if (dialog != null)
//            dialog.dismiss();
    }
}

