package com.example.fileuploader;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.Toast;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


//https://stackoverflow.com/questions/23874842/how-to-select-a-file-with-selecting-from-gallery-or-file-manager-in-android
//https://stackoverflow.com/questions/41193219/how-to-select-file-on-android-using-intent
//https://www.baeldung.com/guide-to-okhttp


public class MainActivity extends AppCompatActivity {

    Button upload_button;
    String tag="findme";
    private Uri videoUri=null;
    final int ACTIVITY_CHOOSE_FILE = 0;
    private static int VIDEO_REQUEST=101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    private String getPath(Uri uri) {
        String[] projection = { MediaStore.Video.Media.DATA };
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
    public void captureVideo(View view) {
        Intent videoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if(videoIntent.resolveActivity(getPackageManager())!=null){
            startActivityForResult(videoIntent,VIDEO_REQUEST);
        }
    }
    public void selectImage(View v) {
        Intent intent = new Intent();
        intent.setType("*/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 1);
    }

    public byte[] uriToByteArray(Uri uri) throws IOException {
        // this dynamically extends to take the bytes you read
        InputStream inputStream = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }
    public static String getMimeType(Context context, Uri uri) {
        String extension;

        //Check uri format to avoid null
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            //If scheme is a content
            final MimeTypeMap mime = MimeTypeMap.getSingleton();
            extension = mime.getExtensionFromMimeType(context.getContentResolver().getType(uri));
        } else {
            //If scheme is a File
            //This will replace white spaces with %20 and also other special characters. This will avoid returning null values on file name with spaces and special characters.
            extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());
        }
        return extension;
    }
    public String getFileName(Context context,Uri uri){

        String[] projection = { MediaStore.Video.Media.DISPLAY_NAME };
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        String file_path = "";

        if (requestCode == VIDEO_REQUEST && resultCode == RESULT_OK) {
            videoUri = data.getData();
            upload_file(videoUri);

//            Log.e(tag, "hello from capture!! <- " + file_path);
//            Log.e(tag, videoUri.toString());
        }

        if (requestCode == 1 && resultCode == RESULT_OK) {
            videoUri = data.getData();
            String file_name = getFileName(this, videoUri);
            Log.e(tag, file_name);


            try {
                byte[] bytes = uriToByteArray(videoUri);
                upload_file_byte(bytes, file_name);

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public  void upload_file(Uri uri){

        String file_path=getPath(uri);

        Log.e(tag,file_path);

        OkHttpClient client = new OkHttpClient();
        String baseUrl = "http://192.168.0.103:8080/upload/";
        //String baseUrl="http://192.168.0.115:8080/upload/";
        Log.e(tag,"in connect server");

        try
        {

            MediaType MEDIA_TYPE_ALL = MediaType.parse("*/*");

            File file = new File(file_path);
            byte[] fileContent = Files.readAllBytes(file.toPath());

            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).
                    addFormDataPart("file_type","video").
                    addFormDataPart("file", file_path, RequestBody.create(MEDIA_TYPE_ALL, fileContent)).build();

            Request request = new Request.Builder().url(baseUrl).header("Accept", "application/json").
                    header("Content-Type", "multipart/form-data").post(body).build();


            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(tag,"failed to post!" + e.getMessage());
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String jsonData = response.body().string();
                    try {
                        JSONObject Jobject = new JSONObject(jsonData);
                        Log.e(tag,Jobject.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.e("findme",response.body().toString());
                    // Log.e(tag,String.valueOf(response.code()));
                }
            });

        }
        catch(Exception e)
        {
            System.out.println("ee");
        }

    }
    public  void upload_file_byte(byte[] fileContent,String file_name){

        OkHttpClient client = new OkHttpClient();
        String baseUrl = "http://192.168.0.103:8080/upload/";
        Log.e(tag,"in connect server");

        try
        {

            MediaType MEDIA_TYPE_ALL = MediaType.parse("*/*");

            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).
                    addFormDataPart("file_type","video").
                    addFormDataPart("file", file_name, RequestBody.create(MEDIA_TYPE_ALL, fileContent)).build();

            Request request = new Request.Builder().url(baseUrl).header("Accept", "application/json").
                    header("Content-Type", "multipart/form-data").post(body).build();


            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(tag,"failed to post!" + e.getMessage());
                    e.printStackTrace();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String jsonData = response.body().string();
                    try {
                        JSONObject Jobject = new JSONObject(jsonData);
                        Log.e(tag,Jobject.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Log.e("findme",response.body().toString());
                    // Log.e(tag,String.valueOf(response.code()));
                }
            });

        }
        catch(Exception e)
        {
            System.out.println("ee");
        }

    }
    
    private void onBrowse(View view) {

        Intent chooseFile, intent;
        chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("*/*");
        intent = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
    }
    private void internet_check() {
        ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        boolean msg = netInfo != null && netInfo.isConnectedOrConnecting();
        Log.e("findme","internet: "+String.valueOf(msg));
    }
    private void file_check(){
        // File file = new File("/sdcard/Download/hello.txt");
        File file = new File("/sdcard/Download/hello.jpg");

        Log.e(tag,"existence: "+String.valueOf(file.exists()));
        Log.e(tag,"read: "+String.valueOf(file.canRead()));
        Log.e(tag,"write: "+String.valueOf(file.canWrite()));
    }
    private void permission_check(){

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED){
            Toast.makeText(MainActivity.this, "Permission Granted!", Toast.LENGTH_SHORT).show();
        }else{
            get_permission();
        }
    }
    private void get_permission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale( this,Manifest.permission.WRITE_EXTERNAL_STORAGE )){
            new AlertDialog.Builder(this).setTitle("paisi");
        }else{
            ActivityCompat.requestPermissions( this,new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
        }
    }
//    public  void connectServer(View view){
//        upload_file();
//    }

}