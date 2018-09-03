package ca.orienteeringbc.smsradiocontrolclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.text.format.DateUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        SmsMessage[] smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (smsMessages.length > 0) {
            String message = smsMessages[0].getDisplayMessageBody();
            if (message != null && message.startsWith("SMSRC")) {
                new SendRequestTask(sharedPreferences).execute(message);
            } else {
                Log.v(TAG, "Found uninteresting message (ie not starting with SMSRC)");
            }
        } else {
            Log.e(TAG, "Received intent but no messages!");
        }
    }

    private static class SendRequestTask extends AsyncTask<String, Void, Void> {
        private final SharedPreferences sharedPreferences;

        SendRequestTask(SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        protected Void doInBackground(String... strings) {
            String[] parts = strings[0].split(" ");
            if (parts.length != 4) {
                Log.e(TAG, "Expected 4 parts of string, but found " + parts.length + " instead for string " + strings[0]);
                return null;
            }

            // Fetch URL from SharedPrefs
            String urlString = sharedPreferences.getString("meos_input_protocol_server", null);
            if (urlString == null)
                return null;

            String competitionId = sharedPreferences.getString("meos_competition_id", "1");

            // Create HttpRequest and set fields appropriately
            URL url;
            try {
                url = new URL(urlString);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);

                // Convert the raw seconds sent into HH:MM:SS
                long totalTime = Long.parseLong(parts[3]);
                final String timeString = DateUtils.formatElapsedTime(totalTime);

                Uri.Builder builder = new Uri.Builder()
                        .appendQueryParameter("submit", "true")
                        .appendQueryParameter("cmp", competitionId)
                        .appendQueryParameter("ctrl", parts[1])
                        .appendQueryParameter("user", parts[2])
                        .appendQueryParameter("time", timeString);

                String query = builder.build().getEncodedQuery();

                OutputStream os = conn.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(os, "UTF-8"));
                writer.write(query);
                writer.flush();
                writer.close();
                os.close();

                conn.disconnect();

            } catch (MalformedURLException e) {
                Log.e(TAG, "Malformed URL set: " + urlString);
            } catch (IOException e) {
                Log.e(TAG, "Caught IOException!");
                e.printStackTrace();
            }

            return null;
        }
    }
}
