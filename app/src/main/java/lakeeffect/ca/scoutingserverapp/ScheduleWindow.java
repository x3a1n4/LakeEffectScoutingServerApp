package lakeeffect.ca.scoutingserverapp;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.text.Html;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleWindow extends AsyncTask<String, String, String> {
    @Override
    protected String doInBackground(String... strings) {
        String sBody = "";
        try {
            Document document = Jsoup.connect(strings[0]).get();
            Elements headers = document.select("h3");
            Elements blueTeams = new Elements();
            Elements redTeams = new Elements();

            //get the one that says "Qualification Results"
            for (Element header: headers) {
                System.out.println(header.html());
                if(header.html().equals("Qualification Results")){
                    Element matchResult = header.nextElementSibling();
                    blueTeams.addAll(matchResult.select(".blue"));
                    redTeams.addAll(matchResult.select(".red"));

                }
            }




            //for some reason I get two of each team, but I use that for alternating blue and red teams
            for (int i = 0; i < blueTeams.size(); i++) {
                Element team;
                if(i % 6 >= 3){
                    team = redTeams.get(i);
                }else{
                    team = blueTeams.get(i);
                }
                String teamNum = team.select("a").html();
                //String matchNum = team.select("svg").attr("data-match");
                sBody += teamNum + ",";

                //if it's done one match, then add a new line
                if(i % 6 == 5){
                    sBody += "\n";
                }
                //System.out.println(teamNum);
            }

        } catch(Exception e){
            System.out.println("Error in async");
            return(e.toString());
        }

        //make file
        //from https://stackoverflow.com/questions/8152125/how-to-create-text-file-and-insert-data-to-that-file-on-android/8152217#8152217
        String sFileName = "schedule.csv";
        File root = new File(Environment.getExternalStorageDirectory(), "#ScoutingSchedule");
        //delete all the files currently there
        try {
            FileUtils.deleteDirectory(root);
        }catch(IOException e){
            e.printStackTrace();
        }

        if (!root.exists()) {
            root.mkdirs();
        }
        File gpxfile = new File(root, sFileName);
        try {
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(sBody);
            writer.flush();
            writer.close();
        }catch(IOException e){
            e.printStackTrace();
        }
        System.out.println("It worked!");
        //Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT).show();


        return("TODO");

    }
}
