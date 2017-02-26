package seven.drawalive.nodebase;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;


public class NodeBase extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      // setContentView(R.layout.activity_node_base);

      _permissionSdcard = false;

      LinearLayout view = prepareLayout();
      prepareEvents();
      preparePermissions();

      setContentView(view);
   }

   @Override
   protected void onDestroy() {
      nodeStop();
      super.onDestroy();
   }

   protected LinearLayout prepareLayout() {
      LinearLayout view, subview;
      TextView label;

      view = new LinearLayout(this);
      view.setOrientation(LinearLayout.VERTICAL);

      _labelIp = new TextView(this);
      _labelIp.setText(String.format("Network (%s)", Utils.getIPv4(this)));
      view.addView(_labelIp);

      label = new TextView(this);
      label.setText("App Root Dir:");
      view.addView(label);

      _txtAppRootDir = new EditText(this);
      _txtAppRootDir.setText("/sdcard/.nodebase");
      view.addView(_txtAppRootDir);

      subview = new LinearLayout(this);
      subview.setOrientation(LinearLayout.HORIZONTAL);
      _btnShare = new Button(this);
      _btnShare.setText("Share");
      subview.addView(_btnShare);
      _btnRefreshAppList = new Button(this);
      _btnRefreshAppList.setText("Refresh");
      subview.addView(_btnRefreshAppList);
      _btnBinaryReset = new Button(this);
      _btnBinaryReset.setText("Update");
      subview.addView(_btnBinaryReset);
      view.addView(subview);

      ScrollView scroll = new ScrollView(this);
      _panelAppList = new LinearLayout(this);
      _panelAppList.setOrientation(LinearLayout.VERTICAL);
      scroll.addView(_panelAppList);
      view.addView(scroll);

      return view;
   }

   protected void prepareEvents() {
      _btnBinaryReset.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            Log.i("UI:Button", "Update node js binary ...");
            Utils.resetNodeJS(NodeBase.this, getApplicationInfo().dataDir);
            refreshAppList();
         }
      });

      _btnRefreshAppList.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            Log.i("UI:Button", "Refresh app list ...");
            String appdir = _txtAppRootDir.getText().toString();
            Utils.prepareNodeDirectory("", appdir);
            refreshAppList();
         }
      });

      _btnShare.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            Utils.shareInformation(
                  NodeBase.this, "Share", "NodeBase",
                  "Service is running at: " + _labelIp.getText(), null);
         }
      });
   }

   protected void preparePermissions() {
      int permission;
      permission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
      if (permission != PackageManager.PERMISSION_GRANTED) {
         ActivityCompat.requestPermissions(
               this,
               new String[] {
                     Manifest.permission.WRITE_EXTERNAL_STORAGE,
                     Manifest.permission.READ_EXTERNAL_STORAGE
               },
               Utils.PERMISSIONS_EXTERNAL_STORAGE);
      }
   }

   @Override
   public void onRequestPermissionsResult (
         int requestCode, String[] permissions, int[] grantResults) {
      switch (requestCode) {
         case Utils.PERMISSIONS_EXTERNAL_STORAGE:
            if (grantResults.length == 0) {
               _permissionSdcard = false;
            } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
               _permissionSdcard = false;
            } else if (grantResults[1] == PackageManager.PERMISSION_DENIED) {
               _permissionSdcard = false;
            } else {
               _permissionSdcard = true;
            }

            if (!_permissionSdcard) {
               _txtAppRootDir.setText(
                     String.format("%s%s", getApplicationInfo().dataDir, "/.nodebase"));
            }
            return;
      }

   }

   protected void nodeSignal(String[] args) {
      Log.i("NodeBase:Signal", "Start Service");
      Log.i("NodeBase:Signal", String.format("Command - %s", args[0]));
      Utils.setServiceAuthentication("NodeBase");
      Intent intent = new Intent(this, NodeBaseServer.class);
      intent.putExtra("signal", args);
      startService(intent);
   }

   protected void nodeStop() {
      Log.i("NodeBase:Signal", "Stop Service");
      Intent intent = new Intent(this, NodeBaseServer.class);
      stopService(intent);
   }

   protected void refreshAppList() {
      String dirname = _txtAppRootDir.getText().toString();
      File approot = new File(dirname);
      _panelAppList.removeAllViews();
      if (!approot.isDirectory()) {
         Toast.makeText(
               getApplicationContext(),
               String.format("\"%s\" is not a directory", dirname),
               Toast.LENGTH_SHORT).show();
         return;
      }
      try {
         File[] files = approot.listFiles();
         for (File f : files) {
            if (!f.isDirectory()) continue;
            if ("node_modules".compareTo(f.getFileName()) == 0) continue;
            Log.i("UI:AppList", f.getAbsolutePath());
            _panelAppList.addView(
                  new NodeBaseApp(this, new AppAction(this), f));
         }
      } catch (Exception e) {
         Log.w("UI:NodeBase", "fail", e);
      }
   }

   public static class AppAction {
      AppAction(NodeBase nodebase) {
         _nodebase = nodebase;
      }

      public void signal(String[] args) {
         _nodebase.nodeSignal(args);
      }

      public void stop() {
         _nodebase.nodeStop();
      }

      private NodeBase _nodebase;
   }

   private boolean _permissionSdcard;

   private TextView _labelIp;
   private EditText _txtAppRootDir;
   private Button _btnShare, _btnRefreshAppList, _btnBinaryReset;
   private LinearLayout _panelAppList;
}
