package com.landonkey.nfc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.Tag;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.nfc.NfcAdapter;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;

import com.android.nfc.NfcDiscoveryParameters;
import com.android.nfc.dhimpl.NativeNfcManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.android.nfc.NfcService;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    ListView listViewInfo;
    ArrayAdapter tagAdapter;
    ArrayList<TagClone> tags;
    File sdcard = Environment.getExternalStorageDirectory();
    File uidFile = new File(sdcard,"uid.bin");
    File tagsFile = new File(sdcard,"tags.json");
    String[] perms = { "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.WRITE_SECURE_SETTINGS" };
    int permsRequestCode = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            Process p = Runtime.getRuntime().exec("su");
        } catch (IOException e) {

        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listViewInfo = (ListView) findViewById(R.id.scan_list);
        tags = new ArrayList<>();
        tagAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, tags);
        listViewInfo.setAdapter(tagAdapter);
        listViewInfo.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TagClone t = (TagClone) parent.getItemAtPosition(position);
                saveUIDFile(t);
                restartNfc();
                Toast.makeText(getBaseContext(), "Tag Set: " + t.toString(), Toast.LENGTH_SHORT).show();
            }
        });


        if(!checkReadWriteExternalPermission() || !checkWriteSecureSettingsPermission())
            requestPermissions(perms, permsRequestCode);

        loadTags();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        restartNfc();

        if(nfcAdapter == null){
            Toast.makeText(this, "NFC NOT supported on this devices!", Toast.LENGTH_LONG).show();
            finish();
        }else if(!nfcAdapter.isEnabled()){
            Toast.makeText(this, "NFC NOT Enabled!", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void loadTags() {
        if(checkReadWriteExternalPermission()) {
            if(tagsFile.exists()) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(tagsFile));
                    Type listType = new TypeToken<ArrayList<TagClone>>(){}.getType();
                    tags = new Gson().fromJson(br, listType);
                    tagAdapter.addAll(tags);
                    tagAdapter.notifyDataSetChanged();
                }catch(Exception ex){
                    tags = new ArrayList<>();
                    tagAdapter.addAll(tags);
                    tagAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private void saveUIDFile(TagClone t) {
        if(checkReadWriteExternalPermission()) {
            try {
                if (!uidFile.exists()) uidFile.createNewFile();
                FileOutputStream uidStream = new FileOutputStream(uidFile, false);
                uidStream.write(t.UID);
                uidStream.close();
            } catch (Exception ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveTagsFile(ArrayList<TagClone> tags) {
        try {
            if (!tagsFile.exists()) tagsFile.createNewFile();
            BufferedWriter bw = new BufferedWriter(new FileWriter(tagsFile, false));
            String json = new Gson().toJson(tags.toArray());
            bw.write(json);
            bw.close();
        }
        catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            //Toast.makeText(this, "onResume() - ACTION_TAG_DISCOVERED", Toast.LENGTH_SHORT).show();

            loadTags();

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if(tag == null) {
                Toast.makeText(this, "tag == null", Toast.LENGTH_SHORT).show();
            }
            else {
                if(checkReadWriteExternalPermission()) {
                    TagClone t = new TagClone(tag);

                    saveUIDFile(t);

                    //if(checkWriteSecureSettingsPermission()) {
                        restartNfc();
                    //}

                    Toast.makeText(getBaseContext(), "Tag Set: " + t.toString(), Toast.LENGTH_SHORT).show();

                    boolean exists = false;

                    for (TagClone tc : tags) {
                        if (tc.toString() == t.toString()) exists = true;
                    }

                    if (!exists) {
                        tags.add(t);

                        saveTagsFile(tags);

                        tagAdapter.add(t);
                        tagAdapter.notifyDataSetChanged();
                    }
                }
                else {
                    Toast.makeText(this, "You must grant read/write permissions", Toast.LENGTH_LONG).show();
                    requestPermissions(perms, permsRequestCode);
                }
            }
        }
        else {
            //Toast.makeText(this, "onResume() : " + action, Toast.LENGTH_SHORT).show();
        }
    }

    private void restartNfc() {
        new Thread("restartNfc") {
            public void run() {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "NFC Restarting", Toast.LENGTH_SHORT).show();
                        }
                    });


                    Thread thread = changeNfcEnabled(false);
                    thread.join();
                    thread = changeNfcEnabled(true);
                    thread.join();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "NFC Restarted", Toast.LENGTH_SHORT).show();
                        }
                    });
                }catch(Exception ex){
                }
            }
        }.start();
    }

    private boolean checkReadWriteExternalPermission() {
        int res1 = this.getApplicationContext().checkCallingOrSelfPermission("android.permission.READ_EXTERNAL_STORAGE");
        int res2 = this.getApplicationContext().checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
        return (res1 == PackageManager.PERMISSION_GRANTED && res2 == PackageManager.PERMISSION_GRANTED);
    }

    private boolean checkWriteSecureSettingsPermission() {
        int res = this.getApplicationContext().checkCallingOrSelfPermission("android.permission.WRITE_SECURE_SETTINGS");
        return res == PackageManager.PERMISSION_GRANTED;
    }

    public Thread changeNfcEnabled(boolean enabled) {
        Context context = this.getApplicationContext();
        final boolean desiredState = enabled;

        if (nfcAdapter == null) {
            // NFC is not supported
            return null;
        }

         Thread t = new Thread("toggleNFC") {
            public void run() {
                boolean success = false;
                Class<?> NfcManagerClass;
                Method setNfcEnabled, setNfcDisabled;
                boolean Nfc;
                if (desiredState) {
                    try {
                        NfcManagerClass = Class.forName("android.nfc.NfcAdapter");
                        setNfcEnabled   = NfcManagerClass.getDeclaredMethod("nfc_enable"); //If you are not running my custom android build then this will not work...
                        setNfcEnabled.setAccessible(true);
                        Nfc             = (Boolean) setNfcEnabled.invoke(nfcAdapter);
                        success         = Nfc;
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        //If you are here you are probably not running my android build that allows this code to run
                        e.printStackTrace();
                    }
                } else {
                    try {
                        NfcManagerClass = Class.forName("android.nfc.NfcAdapter");
                        setNfcDisabled  = NfcManagerClass.getDeclaredMethod("nfc_disable"); //If you are not running my custom android build then this will not work...
                        setNfcDisabled.setAccessible(true);
                        Nfc             = (Boolean) setNfcDisabled.invoke(nfcAdapter);
                        success         = Nfc;
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        //If you are here you are probably not running my android build that allows this code to run
                        e.printStackTrace();
                    }
                }
                if (success) {
                } else {
                }
            }
        };
        t.start();
        return t;
    }
}
