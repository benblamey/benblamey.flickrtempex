

import benblamey.core.DateUtil;
import benblamey.core.Math2;
import benblamey.core.MySQLWebCache;
import com.mysql.jdbc.NotImplemented;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class flickr_tempex {

    public static void main(String[] args) throws FileNotFoundException, IOException, NotImplemented {

        final int per_page = 500; // max = 500.
        final int max_pages = 10; // max unknown.
        long endof_2012_millis = (new DateTime(2013, 1, 1, 0, 0)).getMillis() / 1000; // static url for caching
        long startof_2012_millis = (new DateTime(2012, 1, 1, 0, 0)).getMillis() / 1000; // static url for caching

        String[] searches = {
            //            // Seasons
            //            "spring", "summer", "autumn", "winter", "fall",
            //            // misc anglo/euro
            //            "new years",
            //            // holidays
            //"inauguration day", "groundhog day", 
            //"valentine", "st. patrick","halloween", "bonfire", "guy fawkes", "martin luther king day", "memorial day",
            // "thanksgiving", "easter",

            //"lent", "michealmas",
            //            "snow", "st. george", "veterans",};

            //"snow",

            // ==== FINAL LIST ====

            //            "April Fools Day",
            "April Bank Holiday",
            "Bonfire night",
            "Christmas",
            "New Year's Eve",
            "Halloween",
            "Graduation",
            "Freshers Week",
            "Ocktoberfest",
            "Reading Festival",
            "Red nose day",
            "Valentine's Day",
            "Last Day of School",
            //        
            "Winter",
            "Spring",
            "Summer",
            "Autumn"
        };

        for (String search : searches) {

            String filename = "C:/work/data/output/tempex/flickr_timestamps/" + search.replace(" ", "").replace("'", "") + ".txt";
            ExportPhotoTimestamps(filename, search, max_pages, per_page, endof_2012_millis, startof_2012_millis);

            //DoStats(search,filename);  
            DoEM(search, filename);

        }

        System.out.println("DONE! Press any key to exit.");
        //System.in.read();

    }

    private static JSONObject getFlickrApi(String query) throws IOException, JSONException {
        final String baseurl = "http://api.flickr.com/services/rest/?format=json&api_key=41e39b844692dc99107231a67a0b9bc3&method=";
        String url = baseurl + query;
        final String mimeType = "*/*";
        String json = MySQLWebCache.internalGetMySQL(url, mimeType);
        if (json == null) {
            json = MySQLWebCache.get(url, mimeType);
            if (json.startsWith("jsonFlickrApi(")) {
                MySQLWebCache.Put(url, json, mimeType);
            } else {
                throw new IOException("Invalid JSON");
            }
        }

        json = json.substring("jsonFlickrApi(".length(), json.length() - 1);
        JSONObject results = new JSONObject(json);
        return results;
    }

    private static List<Integer> SearchForWord(String search, final int max_pages, final int per_page, long endof_2012_millis, long startof_2012_millis) throws IOException, JSONException, NumberFormatException {
        System.out.println("### searching for: " + search + " ###");

        ArrayList<Integer> times = new ArrayList<Integer>();

        search = search.replace(" ", "%20");

        for (int page = 1; page < max_pages; page++) {

            //System.err.println("Page:" + Integer.toString(page));
            String searchQuery = "flickr.photos.search&text=" + search + "&page=" + Integer.toString(page)
                    + "&per_page=" + Integer.toString(per_page)
                    + "&sort=relevance"
                    + "&max_taken_date=" + Long.toString(endof_2012_millis)
                    + "&min_taken_date=" + Long.toString(startof_2012_millis);

            JSONObject results = getFlickrApi(searchQuery);

            JSONObject data = results.getJSONObject("photos");

            JSONArray jsonArray = data.getJSONArray("photo");
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject photo = jsonArray.getJSONObject(i);

                try {
                    JSONObject photoInfo = getFlickrApi("flickr.photos.getInfo&photo_id=" + photo.getString("id"));

                    //System.out.print(photoInfo.toString());
                    JSONObject jsonObject = photoInfo.getJSONObject("photo").getJSONObject("dates");

                    String taken = jsonObject.getString("taken");

                    // 2012-08-18 03:17:30
                    DateTimeFormatter pattern = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");
                    DateTime takenDateTime = DateTime.parse(taken, pattern);

                    // Print out taken time.
                    // Gnuplot uses mill time.
                    int ToMilleniumTime = DateUtil.ToMilleniumTime(takenDateTime);

                    if (times.size() % 100 == 0) {
                        System.out.println("Number of photos downloaded = " + times.size());
                    }

                    times.add(ToMilleniumTime);
                    //int takenDateTimeUnix = (int) (takenDateTime.getMillis() / 1000);
                    //System.out.print(ToMilleniumTime + "\n");

                    // ##############################################################
                    // Work out "upload-lag"
                    // String uploadedString = photoInfo.getJSONObject("photo").getString("dateuploaded");
                    // int uploadedUnix = Integer.parseInt(uploadedString);
                    // System.out.print((takenDateTimeUnix - uploadedUnix) + "\n");
                    // ##############################################################
                } catch (Exception e) {
                    // i--;
                    System.err.println("exception " + e.toString());
                    continue;
                }
            }

            //System.out.print(json);
        }

        return times;
    }

    private static void ExportPhotoTimestamps(String filename, String search, final int max_pages, final int per_page, long endof_2012_millis, long startof_2012_millis) {
        File f = new File(filename);
        if (f.exists()) {
            return;
        }
        while (true) {
            try {
                List<Integer> SearchForWord = SearchForWord(search, max_pages, per_page, endof_2012_millis, startof_2012_millis);
                PrintWriter out = new PrintWriter(filename);
                try {
                    for (Integer i : SearchForWord) {
                        out.println(i);
                    }
                } finally {
                    out.close();
                }
                // Finished with this word, continue.
                break;
            } catch (Exception ex) {
                continue;
            }
        }
    }

    private static void DoStats(String search, String filename) throws FileNotFoundException, IOException, NotImplemented {

        final DateTime START_OF_2012 = new DateTime(2012, 1, 1, 0, 0);
        final DateTime END_OF_2012 = START_OF_2012.plusYears(1);
        final long START_OF_2012_MS = START_OF_2012.getMillis();
        final long END_OF_2012_MS = END_OF_2012.getMillis();
        final long MS_IN_A_YEAR = END_OF_2012_MS - START_OF_2012_MS;
        final long SECONDS_IN_A_YEAR = MS_IN_A_YEAR / 1000;

        System.out.println("Start of 2012 in mill time: " + DateUtil.ToMilleniumTime(START_OF_2012));
        System.out.println("End of 2012 in mill time: " + DateUtil.ToMilleniumTime(END_OF_2012));

        ArrayList<Long> times = new ArrayList<Long>();
        {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            String line = null;
            while ((line = reader.readLine()) != null) {
                DateTime time = DateUtil.FromMilleniumTime(Integer.parseInt(line));
                if (time.isAfter(END_OF_2012) || time.isBefore(START_OF_2012)) {
                    continue;
                }

                long timeMS = time.getMillis();
                times.add(timeMS);
            }
            reader.close();
        }

        double totalSin = 0;
        double totalCos = 0;
        for (long timeMS : times) {
            totalSin += Math.sin(
                    ((double) (timeMS - START_OF_2012_MS))
                    / (MS_IN_A_YEAR) * 2 * Math.PI);
            totalCos += Math.cos(
                    ((double) (timeMS - START_OF_2012_MS))
                    / (MS_IN_A_YEAR) * 2 * Math.PI);
        }

        double atan = Math.atan2(totalSin, totalCos);
        if (atan < 0) {
            atan += 2 * Math.PI;
        }

        double meanSeconds2012 = atan / (2 * Math.PI) * (SECONDS_IN_A_YEAR);
        DateTime meanTime = START_OF_2012.plusSeconds((int) meanSeconds2012);

        double total = 0;
        int n = 0;

        ArrayList<Double> seconds2012 = new ArrayList<Double>();

        for (long timeMS : times) {
            double t_seconds2012 = (timeMS - START_OF_2012_MS) / 1000;
            seconds2012.add(t_seconds2012);
        }

        double sd_seconds = Math2.standardDeviationUnderModulo(seconds2012, meanSeconds2012, SECONDS_IN_A_YEAR);
        //double totalfoo = total;

//            double distSeconds = Math2.distanceUnderModulo(t_seconds2012, meanSeconds2012, SECONDS_IN_A_YEAR);
//            total += distSeconds * distSeconds;
//            if (totalfoo > total) {
//                "".toString();
//            }
//                        
//            n++;
//        }     
//        
//        double sd_seconds = Math.sqrt(total/n);
        System.out.println("Mean of " + search + " is " + meanTime
                //);

                + " in mill_time= " + DateUtil.ToMilleniumTime(meanTime)
                + " sd = " + sd_seconds + " seconds = " + sd_seconds / (60 * 60 * 24) + " days.");

    }

    private static void DoEM(String search, String filename) throws FileNotFoundException, IOException, NotImplemented {

        final DateTime START_OF_2012 = new DateTime(2012, 1, 1, 0, 0);
        final DateTime END_OF_2012 = START_OF_2012.plusYears(1);

        DateTime midday = START_OF_2012.hourOfDay().setCopy(12);
        ArrayList<Double> x = new ArrayList<>();
        do {
            x.add((double) DateUtil.ToMilleniumTime(midday));
            midday = midday.plusDays(1);
        } while (midday.getYear() == 2012);

        ArrayList<Double> timestamps = new ArrayList<Double>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line = null;
        while ((line = reader.readLine()) != null) {
            DateTime time = DateUtil.FromMilleniumTime(Integer.parseInt(line));
            if (time.isAfter(END_OF_2012) || time.isBefore(START_OF_2012)) {
                continue;
            }

            double time_seconds_mill = (double) time.getMillis() / 1000;
            timestamps.add(time_seconds_mill);
        }
        reader.close();

        ArrayList<Double> f_x = new ArrayList<Double>();
        for (int n = 0; n < 366; n++) {
            f_x.add(0.0);
        }

        for (Double timestamp : timestamps) {
            DateTime FromMilleniumTime = DateUtil.FromMilleniumTime(timestamp.intValue());
            int dayOfYear = FromMilleniumTime.getDayOfYear();
            f_x.set(dayOfYear, f_x.get(dayOfYear) + 1);
        }

        final long START_OF_2012_MS = START_OF_2012.getMillis();
        final long END_OF_2012_MS = END_OF_2012.getMillis();
        final long MS_IN_A_YEAR = END_OF_2012_MS - START_OF_2012_MS;
        final long SECONDS_IN_A_YEAR = MS_IN_A_YEAR / 1000;
//
//        System.out.println("Start of 2012 in mill time: " + DateUtil.ToMilleniumTime(START_OF_2012));
//        System.out.println("End of 2012 in mill time: " + DateUtil.ToMilleniumTime(END_OF_2012));

        ArrayList<Double> mean = new ArrayList<>();
        ArrayList<Double> sd = new ArrayList<>();

        mean.add((double) DateUtil.ToMilleniumTime(new DateTime(2012, 1, 5, 0, 0)));
        sd.add(5.0 * 24 * 60 * 60);
        mean.add((double) DateUtil.ToMilleniumTime(new DateTime(2012, 5, 1, 0, 0)));
        sd.add(5.0 * 24 * 60 * 60);
        mean.add((double) DateUtil.ToMilleniumTime(new DateTime(2012, 11, 5, 0, 0)));
        sd.add(10.0 * 24 * 60 * 60);

        ArrayList<Double> a_old = new ArrayList<>();

        // Compute values for f_x (the function we are trying to decompose).
//        {
//            final double f_x_kde_sd = 60*60*24;
//            final double foo_1 = 1/(f_x_kde_sd* Math.sqrt(2*Math.PI));
//            final double foo_2 = 2 * f_x_kde_sd*f_x_kde_sd;
//            
//            for (double x_j : x) {
//                double f_x_j = 0;
//                for (double x_k : timestamps) {
//                    f_x_j += computeNormal(x_j, x_k, f_x_kde_sd);
//                    
////                    f_x_j += foo_1 
////                            * Math.exp(- Math.pow(Math2.distUndermod(x_j, x_k, SECONDS_IN_A_YEAR),2)/foo_2);
//                }
//                f_x.add(f_x_j);
//            }
//        }
        // Create the y's.
        ArrayList<ArrayList<Double>> y = new ArrayList<ArrayList<Double>>();
        for (int j = 0; j < x.size(); j++) {
            y.add(new ArrayList<Double>());
        }

        for (int i = 0; i < mean.size(); i++) {
            // Initialize the a's.
            a_old.add(1.0 / mean.size());
        }

        // The iteration.
        do {

            System.out.println(" mean_0=" + mean.get(0) + " mean_1=" + mean.get(1)
                    + " mean_2=" + mean.get(2));
            System.out.println(
                    " mean_0=" + DateUtil.FromMilleniumTime((int) (double) mean.get(0))
                    + " mean_1=" + DateUtil.FromMilleniumTime((int) (double) mean.get(1))
                    + " mean_2=" + DateUtil.FromMilleniumTime((int) (double) mean.get(2)));
            System.out.println(" a_0=" + a_old.get(0) + " a_1=" + a_old.get(1)
                    + " a_2=" + a_old.get(2));

            "".toCharArray();

            // Compute the y's from the old params.
            for (int i = 0; i < mean.size(); i++) {
                for (int j = 0; j < x.size(); j++) {

                    Double f_x_j = f_x.get(j);
                    if (f_x_j == 0) {
                        y.get(i).add(0.0);
                    } else {
                        double y_ij = a_old.get(i) * computeNormal(x.get(j), mean.get(i), sd.get(i))
                                / f_x.get(j);
                        y.get(i).add(y_ij);
                    }
                }
            }

            // Compute new a's from old params.
            ArrayList<Double> a_new = new ArrayList<Double>();
            for (int i = 0; i < mean.size(); i++) {
                double sum_of_old_ys = 0;
                for (int j = 0; j < x.size(); j++) {
                    sum_of_old_ys = y.get(i).get(j);
                }

                a_new.add(sum_of_old_ys / mean.size());
            }

            // Compute new means from old params.
            ArrayList<Double> mean_new = new ArrayList<>();
            {
                for (int i = 0; i < mean.size(); i++) {
                    double top = 0.0;
                    double bottom = 0.0;

                    for (int j = 0; j < mean.size(); j++) {
                        top += y.get(i).get(j) * x.get(j);
                        bottom += y.get(i).get(j);
                    }

                    double mean_i = top / bottom;

                    if (Double.isNaN(mean_i)) {
                        "".toString();
                    }

                    mean_new.add(mean_i);
                }
            }

            // Swap new for old.
            mean = mean_new;
            a_old = a_new;

        } while (true);
    }

    private static Double computeNormal(Double x, Double mean, Double sd) throws NotImplemented {
        final DateTime START_OF_2012 = new DateTime(2012, 1, 1, 0, 0);
        final DateTime END_OF_2012 = START_OF_2012.plusYears(1);
        final long START_OF_2012_MS = START_OF_2012.getMillis();
        final long END_OF_2012_MS = END_OF_2012.getMillis();
        final long MS_IN_A_YEAR = END_OF_2012_MS - START_OF_2012_MS;
        final long SECONDS_IN_A_YEAR = MS_IN_A_YEAR / 1000;

        final double foo_1 = 1.0 / (sd * Math.sqrt(2.0 * Math.PI));
        final double foo_2 = 2.0 * sd * sd;
        double result = foo_1 * Math.exp(-Math.pow(Math2.distUndermod(x, mean, SECONDS_IN_A_YEAR), 2.0) / foo_2);
        return result;
    }
}
