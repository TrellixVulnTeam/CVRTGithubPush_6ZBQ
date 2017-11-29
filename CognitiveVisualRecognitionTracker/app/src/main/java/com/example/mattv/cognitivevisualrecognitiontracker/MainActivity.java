package com.example.mattv.cognitivevisualrecognitiontracker;

import clarifai2.api.ClarifaiBuilder;
import clarifai2.api.ClarifaiClient;
import clarifai2.api.ClarifaiResponse;
import clarifai2.dto.input.ClarifaiInput;
import clarifai2.dto.input.image.ClarifaiImage;
import clarifai2.dto.model.output.ClarifaiOutput;
import clarifai2.dto.prediction.Concept;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageButton;
import android.app.Activity;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.View;
import android.view.View.OnClickListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.OkHttpClient.Builder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.example.mattv.cognitivevisualrecognitiontracker.R.id.activity_main;
import static com.example.mattv.cognitivevisualrecognitiontracker.R.id.imageView;
import static com.example.mattv.cognitivevisualrecognitiontracker.R.id.imgButtonMetrics;

public class MainActivity extends Activity {
    private final int REQ_CODE_SPEECH_INPUT = 100;
    public static String question = "";
    public static String TFanswer = "";
    public static String wolframAnswer = "";
    public static String DiagnosisAnswer = "";
    public static String DiagnosticAccuracy = "";
    public static String DiagnosticQuestion = "";
    public static String SymptomsAnswer = "";
    public static JSONArray SymptomsArr;
    public static Boolean inDiagnosis = false;
    public static String PossibleCondition = "";
    public static String matchedID = "";
    public static String matchedLabel = "";
    public static JSONArray ChoicesArr;
    public static JSONObject evidenceData = new JSONObject();
    public static String[] ChoicesIDs = new String[20];
    public static String[] ChoicesLabels = new String[20];
    public static boolean labelMatch = false;
    public static String imgName = "";
    public Bitmap icon;
    public String encodedImage = "";
    ImageButton imgButtonMic;
    ImageButton imgButtonGallery;
    ImageButton imgButtonMetrics;
    final Random rnd = new Random();
    TextToSpeech t1;
    private static int RESULT_LOAD_IMAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.UK);
                    //t1.setPitch(90);

                }
            }
        });
        ImageView img = (ImageView) findViewById(imageView);
        //String str = "img_" + rnd.nextInt(5);
        //System.out.println(str + " On Load");
        img.setImageDrawable
                (
                        getResources().getDrawable(getResourceID("main", "drawable",
                                getApplicationContext()))
                );
        imgButtonMic = (ImageButton) findViewById(R.id.imgButtonMic);
        imgButtonGallery = (ImageButton) findViewById(R.id.imgButtonGallery);
        imgButtonMetrics = (ImageButton) findViewById(R.id.imgButtonMetrics);

        imgButtonMic.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                System.out.println("Mic Clicked!");
                promptSpeechInput();;
            }
        });
        imgButtonGallery.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                System.out.println("Gallery Clicked!");
                Intent i = new Intent(
                        Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

                startActivityForResult(i, RESULT_LOAD_IMAGE);
            }
        });
        imgButtonMetrics.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                System.out.println("Metrics Clicked!");
                Intent myIntent = new Intent(view.getContext(), MetricsActivity.class);
                startActivity(myIntent);
            }
        });
    }

    public class CallTensorFlowModel extends AsyncTask<Void, Void, Boolean> {
        protected Boolean doInBackground(Void... voids) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            try {
                icon.compress(Bitmap.CompressFormat.PNG, 100, stream); //bm is the bitmap object
                byte[] byteArray = stream.toByteArray();

                String encodedImage = Base64.encodeToString(byteArray, Base64.DEFAULT);
                try {
                    httpTensorFlowPost();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //Add Wolfram Alpha API Call
                try {
                    httpWolframPost();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    httpSymptomsPost();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    httpDiagnosisPost();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                /*
                final ClarifaiClient client = new ClarifaiBuilder("KKQIegBW9uOl_3vaMSzqq4QCfPNyNBvB7XNBz1vE", "xsY48eiDhhsFo5M7HE3F71ZYkB_tEQmemlWekTgG")
                        .client(new OkHttpClient()) // OPTIONAL. Allows customization of OkHttp by the user
                        .buildSync(); // or use .build() to get a Future<ClarifaiClient>
                client.getToken();
                try{
                    ClarifaiResponse response = client.getDefaultModels().generalModel().predict()
                            .withInputs(
                                    //ClarifaiInput.forImage(ClarifaiImage.of(encodedImage)) //PASS BYTES
                                    ClarifaiInput.forImage(ClarifaiImage.of(byteArray))
                            )
                            .executeSync();
                    List<ClarifaiOutput<Concept>> predictions = (List<ClarifaiOutput<Concept>>) response.get();
                    if (predictions.isEmpty()) {
                        System.out.println("No Predictions");
                    }
                    List<Concept> data = predictions.get(0).data();
                    for (int i = 0; i < data.size(); i++) {
                        System.out.println(data.get(i).name() + " - " + data.get(i).value());
                        //image.drawText(data.get(i).name(), (int)Math.floor(Math.random()*x), (int) Math.floor(Math.random()*y), HersheyFont.ASTROLOGY, 20, RGBColour.RED);
                    }
                    TFanswer = "This image is related to " + data.get(0).name() + ", " + data.get(1).name() + ", " + data.get(2).name() + " and " + data.get(3).name();
                    data.clear();
                    predictions.clear();
                    byteArray = null;
                    icon = null;
                    stream = null;
                }
                catch (NoSuchElementException b)
                {
                    TFanswer = "No Such Element Exception";
                    b.printStackTrace();
                }
*/
            }
            catch(NullPointerException a){
                TFanswer = "Null Pointer Exception";
                a.printStackTrace();
            }

            //} catch (IOException e) {
            //    e.printStackTrace();
            //}
            return true;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            //String toSpeak = TFanswer;
            //Logic to determine which answer is more accurate
           String toSpeak = "";
            if(wolframAnswer.length() > 20 && inDiagnosis == false)
            {
                toSpeak = wolframAnswer;
                // 1. Instantiate an AlertDialog.Builder with its constructor
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                // 2. Chain together various setter methods to set the dialog characteristics
                builder.setMessage(wolframAnswer)
                        .setTitle("Answer");

                builder.setPositiveButton("Thanks", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User clicked OK button
                    }
                });
                inDiagnosis = false;
                // 3. Get the AlertDialog from create()
                AlertDialog dialog = builder.create();
                dialog.show();
            }
            else if (DiagnosisAnswer != "")
            {
                if(Double.parseDouble(DiagnosticAccuracy) > .3)
                {
                    toSpeak = "I am  " + DiagnosticAccuracy + "% confident that you have " + DiagnosisAnswer;
                    //toSpeak = "You might have " + DiagnosisAnswer;
                    Toast.makeText(getApplicationContext(), toSpeak,Toast.LENGTH_LONG).show();
                    inDiagnosis = false;
                }
                else
                {
                    toSpeak = DiagnosticQuestion;
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                    // 2. Chain together various setter methods to set the dialog characteristics
                    builder.setMessage(DiagnosticQuestion)
                            .setTitle("Question");

                    builder.setPositiveButton("Please click Mic Button to answer", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // User clicked OK button
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    inDiagnosis = true;
                }
            }
            else
            {
                toSpeak = TFanswer;
                Toast.makeText(getApplicationContext(), toSpeak,Toast.LENGTH_LONG).show();
                inDiagnosis = false;
            }
            t1.speak(toSpeak, TextToSpeech.QUEUE_FLUSH, null);
            wolframAnswer = "";
            TFanswer = "";
            DiagnosisAnswer = "";
            DiagnosticQuestion = "";
            SymptomsAnswer = "";
            toSpeak = "";
        }

    }

    public void classifyImage() throws Exception {
        ImageView img = (ImageView) findViewById(imageView);
        icon=((BitmapDrawable)img.getDrawable()).getBitmap();
        new CallTensorFlowModel().execute();
        //httpTensorFlowPost();
        logResultToDB();
    }
    public void logResultToDB()
    {
        //DateFormat df = new SimpleDateFormat("dd MM yyyy, HH:mm");
        DateFormat df = new SimpleDateFormat("MM/dd/yyyy");
        String date = df.format(Calendar.getInstance().getTime());
        //File file = Environment.getExternalStorageDirectory();
        //System.out.println(file.toString());
        SQLiteDatabase database = openOrCreateDatabase("metrics.db", MODE_PRIVATE, null);
        //database.execSQL("drop table tblHistoryLog");
        database.execSQL("create table if not exists tblHistoryLog(Question text,TFanswer text,ImgName text,Date date)");
        database.execSQL("insert into tblHistoryLog values('" + question + "','" + TFanswer + "','" + icon.toString() + "','" + date + "')");
        database.close();
    }
    private void httpTensorFlowPost() throws Exception
    {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Map<String, String> params = new HashMap<String, String>();
        params.put("question", question);
        JSONObject parameter = new JSONObject(params);

        //OkHttpClient client = new OkHttpClient();
        Builder b = new Builder();
        b.readTimeout(20000, TimeUnit.MILLISECONDS);
        b.writeTimeout(20000, TimeUnit.MILLISECONDS);
        b.connectTimeout(20000, TimeUnit.MILLISECONDS);
        OkHttpClient client = b.build();

        RequestBody body = RequestBody.create(JSON, parameter.toString());
        Request request = new Request.Builder()
                .url("http://192.168.56.1:8080")
                //.url("http://192.168.86.129:8080")
                //.url("https://api.github.com/users/codepath")
                //.get()
                .post(body)
                .addHeader("content-type", "application/json; charset=utf-8")
                .build();

        Response response = client.newCall(request).execute();
        if(response.isSuccessful()) {
            String resStr = response.body().string().toString();
            try {
                JSONObject json = new JSONObject(resStr);
                JSONArray jsonArray= (JSONArray) json.get("results");
                TFanswer = jsonArray.getJSONObject(0).getString("val");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        //client.newCall(request).enqueue(new Callback() {
/*
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                //Log.e("response", call.request().body().toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                   //Log.e("response", response.body().string());

                String resStr = response.body().string().toString();
                try {
                    JSONObject json = new JSONObject(resStr);
                    JSONArray jsonArray= (JSONArray) json.get("results");
                    //TFanswer = json.optString("val");
                    //TFanswer = jsonArray.toString().substring(26,);
                    TFanswer = jsonArray.getJSONObject(0).getString("val");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }


        });
*/
        /*
        Request request = new Request.Builder()
                .url("http://192.168.56.1")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
            @Override
            public void onResponse(Call call, final Response response) throws IOException {
                // ... check for failure using `isSuccessful` before proceeding

                // Read data on the worker thread
                final String responseData = response.body().string();
                System.out.println(responseData);
                //Toast.makeText(MainActivity.this, responseData, Toast.LENGTH_LONG).show();
            }
        });
        */
    }
    private void httpWolframPost() throws Exception
    {
        try {
            //appid = 28W43H-6P86G7XK79
            String WolframQuestion = question.replaceAll("\\s+","+");
            System.out.println(WolframQuestion);
            URL url = new URL("http://api.wolframalpha.com/v2/query?appid=28W43H-6P86G7XK79&includepodid=Definition:WordData&input=" + WolframQuestion + "&format=plaintext&output=json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : "
                        + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    (conn.getInputStream())));

            String output;
            StringBuilder everything = new StringBuilder();
            while ((output = br.readLine()) != null) {
                everything.append(output);
            }
            String str = everything.toString();
            JSONObject json = new JSONObject(str);
            JSONArray jsonArr = json.getJSONObject("queryresult").getJSONArray("pods").getJSONObject(0).getJSONArray("subpods");

            for(int i=0; i<jsonArr.length(); i++){
                JSONObject jsonObj = jsonArr.getJSONObject(i);
                System.out.println(jsonObj.getString("plaintext"));
                wolframAnswer = jsonObj.getString("plaintext");
            }

            conn.disconnect();

        } catch (MalformedURLException e) {

            e.printStackTrace();

        } catch (IOException e) {

            e.printStackTrace();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void httpSymptomsPost() throws Exception
    {
        System.out.println("Symptoms Function: " + question);
        if(inDiagnosis == true)
        {
            for(int i = 0; i < ChoicesIDs.length; i++)
            {
                if(question.equals(ChoicesLabels[i].toLowerCase()))
                {
                    labelMatch = true;
                    matchedID = ChoicesIDs[i];
                    //matchedLabel = ChoicesLabels[i];
                    //SymptomsArr.put("id", PossibleCondition);
                }
            }
        }
        //appID = e79a1672
        //appKey = 02f8df1a61c43f1dc5e93c791750bbef
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        Map<String, String> params = new HashMap<String, String>();
        params.put("text", question);
        JSONObject parameter = new JSONObject(params);

        //OkHttpClient client = new OkHttpClient();
        Builder b = new Builder();
        b.readTimeout(20000, TimeUnit.MILLISECONDS);
        b.writeTimeout(20000, TimeUnit.MILLISECONDS);
        b.connectTimeout(20000, TimeUnit.MILLISECONDS);
        OkHttpClient client = b.build();

        RequestBody body = RequestBody.create(JSON, parameter.toString());
        Request request = new Request.Builder()
                .url("https://api.infermedica.com/v2/parse")
                .post(body)
                .addHeader("content-type", "application/json")
                .addHeader("App-Id", "e79a1672")
                .addHeader("App-Key", "02f8df1a61c43f1dc5e93c791750bbef")
                .build();

        Response response = client.newCall(request).execute();
        if(response.isSuccessful()) {
            String resStr = response.body().string().toString();
            try {
                JSONObject json = new JSONObject(resStr);
                if(inDiagnosis == true)
                {
                    //JSONArray tempArr = new JSONArray();
                    //tempArr = (JSONArray) json.get("mentions");
                    //for(int i = 0; i < tempArr.length(); i++)
                    //{
                      //  SymptomsArr.put(tempArr.get(i));
                    //}
                }
                else
                {
                    SymptomsArr = (JSONArray) json.get("mentions");
                    SymptomsAnswer = SymptomsArr.getJSONObject(0).getString("id");
                    inDiagnosis = false;
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private void httpDiagnosisPost() throws Exception
    {
        if(inDiagnosis == false)
        {
            evidenceData = new JSONObject();
        }
        System.out.println("Diagnosis Function: " +question);
        //appID = e79a1672
        //appKey = 02f8df1a61c43f1dc5e93c791750bbef
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        //Map<String, String> params = new HashMap<String, String>();
        JSONObject json1 = new JSONObject();
        json1.put("sex", "female");
        json1.put("age", "65");
        JSONArray evidence = new JSONArray();
        for(int i = 0; i < SymptomsArr.length(); i++)
        {
            evidenceData.put("id", SymptomsArr.getJSONObject(i).getString("id"));
            evidenceData.put("choice_id", SymptomsArr.getJSONObject(i).getString("choice_id"));
        }
        if(labelMatch == true)
        {
            evidenceData.put("id", PossibleCondition);
            evidenceData.put("choice_id", matchedID);
        }
        //evidenceData.put("id", SymptomsAnswer);
        //evidenceData.put("choice_id", "present");
        evidence.put(evidenceData);
        json1.put("evidence", evidence);
        //params.put("id", "s_1193");
        //JSONObject parameter = new JSONObject(params);

        //OkHttpClient client = new OkHttpClient();
        Builder b = new Builder();
        b.readTimeout(20000, TimeUnit.MILLISECONDS);
        b.writeTimeout(20000, TimeUnit.MILLISECONDS);
        b.connectTimeout(20000, TimeUnit.MILLISECONDS);
        OkHttpClient client = b.build();

        RequestBody body = RequestBody.create(JSON, json1.toString());
        Request request = new Request.Builder()
                .url("https://api.infermedica.com/v2/diagnosis")
                .post(body)
                .addHeader("content-type", "application/json")
                .addHeader("App-Id", "e79a1672")
                .addHeader("App-Key", "02f8df1a61c43f1dc5e93c791750bbef")
                .build();

        Response response = client.newCall(request).execute();
        if(response.isSuccessful()) {
            String resStr = response.body().string().toString();
            try {
                JSONObject json = new JSONObject(resStr);
                JSONArray jsonArray = (JSONArray) json.get("conditions");
                JSONObject question = (JSONObject) json.get("question");
                //JSONArray jsonArray2 = (JSONArray) json.get("question");
                DiagnosisAnswer = jsonArray.getJSONObject(0).getString("common_name");
                DiagnosticAccuracy = jsonArray.getJSONObject(0).getString("probability");
                DiagnosticQuestion = question.getString("text");
                JSONArray labelsArr = (JSONArray) question.getJSONArray("items");
                PossibleCondition = labelsArr.getJSONObject(0).getString("id");
                //ChoicesArr = (JSONArray) labelsArr.getJSONArray(0).getString("choices");
                //.getJSONArray("pods").getJSONObject(0).getJSONArray("subpods");
                ChoicesArr = question.getJSONArray("items").getJSONObject(0).getJSONArray("choices");
                for(int i = 0; i < ChoicesArr.length(); i++)
                {
                    JSONObject loopChoices = ChoicesArr.getJSONObject(i);
                    //Store in Arrays
                    ChoicesIDs[i] = loopChoices.getString("id");
                    ChoicesLabels[i] = loopChoices.getString("label");
                }
                labelMatch = false;
                matchedID = "";
                matchedLabel = "";
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }
    /**
     * Showing google speech input dialog
     * */
    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,

                "What is your question?");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(MainActivity.this,
                    "Speech not supported",
                    Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Receiving speech input
     * */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        System.out.println("Entering Activity Result Function");
        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();

            ImageView imageView = (ImageView) findViewById(R.id.imageView);

            Bitmap bmp = null;
            try {
                bmp = getBitmapFromUri(selectedImage);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            imageView.setImageBitmap(bmp);

        }
        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    //txtSpeechInput.setText(result.get(0));
                   /* Toast.makeText(MainActivity.this,
                            "You said '" + result.get(0) + "'.", Toast.LENGTH_LONG).show();
                    }*/
                    try {
                        classifyImage();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    question = result.get(0);

                }
                break;
            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    protected final static int getResourceID
            (final String resName, final String resType, final Context ctx)
    {
        final int ResourceID =
                ctx.getResources().getIdentifier(resName, resType,
                        ctx.getApplicationInfo().packageName);
        if (ResourceID == 0)
        {
            throw new IllegalArgumentException
                    (
                            "No resource string found with name " + resName
                    );
        }
        else
        {
            return ResourceID;
        }
    }
}
