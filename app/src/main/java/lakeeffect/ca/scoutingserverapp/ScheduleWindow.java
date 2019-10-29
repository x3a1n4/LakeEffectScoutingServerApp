package lakeeffect.ca.scoutingserverapp;

import android.os.AsyncTask;
import android.text.Html;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class ScheduleWindow extends AsyncTask<String, String, String> {
    @Override
    protected String doInBackground(String... strings) {
        BufferedReader br = null;

        try {

            URL url = new URL(strings[0]);
            br = new BufferedReader(new InputStreamReader(url.openStream()));

            String line;

            StringBuilder sb = new StringBuilder();

            while ((line = br.readLine()) != null) {

                sb.append(line);
                sb.append("\n");
            }

            String website = Html.fromHtml(sb.toString()).toString();
            return(website);
        } catch(Exception e){
            return(e.toString());
        }
    }
}
