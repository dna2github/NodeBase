package seven.drawalive.nodebase;

import android.content.Intent;
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
import java.util.ArrayList;
import java.util.HashMap;


public class NodeBase extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      // setContentView(R.layout.activity_node_base);

      config = new Configuration(this);
      config.prepareEnvironment();

      LinearLayout view = prepareLayout();
      prepareState();
      prepareEvents();
      Permission.request(this);

      setContentView(view);
   }

   @Override
   protected void onDestroy() {
      stopNodeService();
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
            showNicIps();
            break;
         case 102: // Show NodeJS Version
            showNodeVersion();
            break;
         case 103: // Upgrade NodeJS
            copyBinNodeFromNodebaseWorkdir();
            break;
         case 199: // reset
            Log.i("UI:ActionButton", "Update node js binary ...");
            // copyBinNodeFromNodebaseWorkdir();
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
      _labelIp.setText(String.format("Network (%s)", Network.getWifiIpv4(this)));
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
            Storage.makeDirectory(config.workDir());
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
         File[] files = Storage.listDirectories(dirname);
         for (File f : files) {
            String name = f.getName();
            // skip the folders of node_modules and which whose name starts with '.'
            if ("node_modules".compareTo(name) == 0) continue;
            if (name.indexOf('.') == 0) continue;
            Log.i("UI:AppList", f.getAbsolutePath());
            HashMap<String, Object> env = new HashMap<>();
            env.put("appdir", f);
            env.put("datadir", config.dataDir());
            NodeBaseApp app = new NodeBaseApp(this, new AppAction(this), env);
            _appList.add(app);
            _panelAppList.addView(app);
         }
         if (_appList.size() > 0) {
            _txtAppFilter.setText("");
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
         _nodebase.sendNodeSignal(args);
      }

      public void stop(String name) {
         if (name == null) {
            _nodebase.stopNodeService();
         } else {
            _nodebase.sendNodeSignal(new String[]{
                  NodeService.AUTH_TOKEN,
                  "stop", name
            });
         }
      }

      private NodeBase _nodebase;
   }

   protected void sendNodeSignal(String[] args) {
      Log.i("NodeBase:Signal", "Start Service");
      Log.i("NodeBase:Signal", String.format("Command - %s", args[1]));
      Intent intent = new Intent(this, NodeService.class);
      intent.putExtra(NodeService.ARGV, args);
      startService(intent);
   }

   protected void stopNodeService() {
      Log.i("NodeBase:Signal", "Stop Service");
      Intent intent = new Intent(this, NodeService.class);
      stopService(intent);
   }

   private void copyBinNodeFromNodebaseWorkdir() {
      String dirname = config.workDir();
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
      String nodeBin = config.nodeBin();
      if (!Storage.copy(upgrade_node_filename, nodeBin)) {
         Log.e("NodeBase:upgrade_node",
               "Cannot copy binary file of \"node\"");
      }
      Storage.executablize(nodeBin);
   }

   private void showNodeVersion() {
      String version = NodeService.checkOutput(new String[] {
            String.format("%s/node/node", config.dataDir()), "--version"
      });
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

   private void showNicIps() {
      HashMap<String, String[]> name_ip = Network.getNicIps();
      StringBuffer nic_list = new StringBuffer();
      for (String name : name_ip.keySet()) {
         nic_list.append(name);
         nic_list.append(':');
         for (String ip : name_ip.get(name)) {
            nic_list.append(' ');
            nic_list.append('[');
            nic_list.append(ip);
            nic_list.append(']');
         }
         nic_list.append('\n');
      }
      CharSequence text = new String(nic_list);
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setMessage(text).setTitle("NetworkInterface(s)");
      builder.create().show();
   }

   // state
   private Configuration config;
   private ArrayList<NodeBaseApp> _appList;

   // view components
   private TextView _labelIp;
   private EditText _txtAppRootDir;
   private Button _btnRefreshAppList;
   private EditText _txtAppFilter;
   private LinearLayout _panelAppList;
}
