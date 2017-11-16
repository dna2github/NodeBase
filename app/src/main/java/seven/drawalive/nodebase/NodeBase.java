package seven.drawalive.nodebase;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


public class NodeBase extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      // setContentView(R.layout.activity_node_base);

      _permissionSdcard = false;

      LinearLayout view = prepareLayout();
      prepareState();
      prepareEvents();
      preparePermissions();

      setContentView(view);
   }

   @Override
   protected void onDestroy() {
      nodeStop();
      super.onDestroy();
   }

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      menu.add(Menu.NONE, 101, Menu.NONE, "NICs");
      menu.add(Menu.NONE, 102, Menu.NONE, "Node Version");
      menu.add(Menu.NONE, 103, Menu.NONE, "Node Upgrade");
      menu.add(Menu.NONE, 199, Menu.NONE, "Reset");
      return true;
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
         case 101:
            Log.i("UI:ActionButton", "Show NIC IP ...");
            StringBuffer nic_list = new StringBuffer();
            try {
               for (NetworkInterface nic :
                     Collections.list(NetworkInterface.getNetworkInterfaces())) {
                  List<InterfaceAddress> nic_addr = nic.getInterfaceAddresses();
                  if (nic_addr.size() == 0) continue;
                  StringBuilder nic_one = new StringBuilder();
                  nic_one.append(nic.getName());
                  nic_one.append(':');
                  for (InterfaceAddress ia : nic_addr) {
                     nic_one.append(' ');
                     nic_one.append('[');
                     String addr = ia.getAddress().getHostAddress();
                     if (addr.indexOf('%') >= 0) {
                        addr = addr.split("%")[0];
                     }
                     nic_one.append(addr);
                     nic_one.append(']');
                  }
                  nic_list.append('\n');
                  nic_list.append(nic_one);
               }
            } catch (Exception e) {
               nic_list.append(String.format("\nError: %s", e.getMessage()));
            }
            CharSequence text = new String(nic_list);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(text).setTitle("NetworkInterface(s)");
            builder.create().show();
            break;
         case 102: // Show NodeJS Version
            show_node_version();
            break;
         case 103: // Upgrade NodeJS
            copy_bin_node_from_nodebase_workdir();
            break;
         case 199: // reset
            Log.i("UI:ActionButton", "Update node js binary ...");
            Utils.resetNodeJS(NodeBase.this, getApplicationInfo().dataDir);
            refreshAppList();
            break;
         default:
            return super.onOptionsItemSelected(item);
      }
      return true;
   }

   protected void prepareState() {
      _appList = new ArrayList<>();
   }

   protected LinearLayout prepareLayout() {
      LinearLayout view, subview;
      TextView label;
      LinearLayout.LayoutParams param;

      view = new LinearLayout(this);
      view.setOrientation(LinearLayout.VERTICAL);

      _labelIp = new TextView(this);
      _labelIp.setText(String.format("Network (%s)", Utils.getIPv4(this)));
      view.addView(_labelIp);

      label = new TextView(this);
      label.setText("App Root Dir:");
      view.addView(label);

      subview = new LinearLayout(this);
      subview.setOrientation(LinearLayout.HORIZONTAL);
      _txtAppRootDir = new EditText(this);
      _txtAppRootDir.setText("/sdcard/.nodebase");
      param = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
      _txtAppRootDir.setLayoutParams(param);
      subview.addView(_txtAppRootDir);
      _btnRefreshAppList = new Button(this);
      _btnRefreshAppList.setText("Refresh");
      subview.addView(_btnRefreshAppList);
      view.addView(subview);

      _txtEnv = new EditText(this);
      _txtEnv.setText("");
      _txtEnv.setHint("Environment KeyValue ...");
      _txtEnv.setVisibility(View.GONE);
      view.addView(_txtEnv);

      _txtAppFilter = new EditText(this);
      _txtAppFilter.setText("");
      _txtAppFilter.setHint("Filter app ...");
      _txtAppFilter.setVisibility(View.GONE);
      view.addView(_txtAppFilter);

      ScrollView scroll = new ScrollView(this);
      _panelAppList = new LinearLayout(this);
      _panelAppList.setOrientation(LinearLayout.VERTICAL);
      scroll.addView(_panelAppList);
      view.addView(scroll);

      return view;
   }

   protected void prepareEvents() {
      _btnRefreshAppList.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            Log.i("UI:Button", "Refresh app list ...");
            String appdir = _txtAppRootDir.getText().toString();
            Utils.prepareNodeDirectory("", appdir);
            refreshAppList();
         }
      });

      _txtAppFilter.addTextChangedListener(new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            for (NodeBaseApp app : _appList) {
               if (s.length() == 0) {
                  app.setVisibility(View.VISIBLE);
               } else if (app.getAppName().indexOf(s.toString()) >= 0) {
                  app.setVisibility(View.VISIBLE);
               } else {
                  app.setVisibility(View.GONE);
               }
            }
         }

         @Override
         public void afterTextChanged(Editable s) {}
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
         _appList.clear();
         File[] files = approot.listFiles();
         for (File f : files) {
            if (!f.isDirectory()) continue;
            String name = f.getName();
            // skip the folders of node_modules and which whose name starts with '.'
            if ("node_modules".compareTo(name) == 0) continue;
            if (name.indexOf('.') == 0) continue;
            Log.i("UI:AppList", f.getAbsolutePath());
            HashMap<String, Object> env = new HashMap<>();
            env.put("appdir", f);
            env.put("txtenv", _txtEnv);
            NodeBaseApp app = new NodeBaseApp(this, new AppAction(this), env);
            _appList.add(app);
            _panelAppList.addView(app);
         }
         if (_appList.size() > 0) {
            _txtAppFilter.setText("");
            _txtEnv.setVisibility(View.VISIBLE);
            _txtAppFilter.setVisibility(View.VISIBLE);
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

   private void copy_bin_node_from_nodebase_workdir() {
      String dirname = _txtAppRootDir.getText().toString();
      String upgrade_node_filename = String.format("%s/.bin/node", dirname);
      File f = new File(upgrade_node_filename);
      if (!f.exists()) {
         AlertDialog.Builder builder = new AlertDialog.Builder(this);
         builder.setMessage(
                 String.format("%s does not exists.", upgrade_node_filename)
         ).setTitle("Upgrade Failed");
         builder.create().show();
         return;
      }
      try {
         FileInputStream fr = new FileInputStream(f);
         Utils.prepareNode(getApplicationInfo().dataDir, fr, true);
         fr.close();
      } catch (Exception e) {
         Log.e("NodeBase:upgrade_node",
                 "Cannot copy binary file of \"node\"");
      }
   }

   private void show_node_version() {
      String version = NodeBaseServer.nodeVersion(getApplicationInfo().dataDir);
      String text = null;
      if (version == null) {
         text = "NodeJS: (not found)";
      } else {
         text = String.format("NodeJS: %s", version);
      }
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(text).setTitle("Node Version");
      builder.create().show();
   }

   private boolean _permissionSdcard;

   // state
   private ArrayList<NodeBaseApp> _appList;

   // view components
   private TextView _labelIp;
   private EditText _txtAppRootDir;
   private Button _btnRefreshAppList;
   private EditText _txtEnv;
   private EditText _txtAppFilter;
   private LinearLayout _panelAppList;
}
