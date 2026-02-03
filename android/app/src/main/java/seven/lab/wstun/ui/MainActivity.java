package seven.lab.wstun.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import seven.lab.wstun.R;
import seven.lab.wstun.service.WSTunService;

/**
 * Main activity with tabs for status and configuration.
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private WSTunService service;
    private boolean bound = false;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    private StatusFragment statusFragment;
    private ConfigFragment configFragment;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            WSTunService.LocalBinder localBinder = (WSTunService.LocalBinder) binder;
            service = localBinder.getService();
            bound = true;

            // Notify fragments
            if (statusFragment != null) {
                statusFragment.onServiceConnected(service);
            }
            if (configFragment != null) {
                configFragment.onServiceConnected(service);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();
        initViews();
        bindService();
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        statusFragment = new StatusFragment();
        configFragment = new ConfigFragment();

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 0) {
                    return statusFragment;
                } else {
                    return configFragment;
                }
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(R.string.tab_status);
            } else {
                tab.setText(R.string.tab_config);
            }
        }).attach();
    }

    private void bindService() {
        Intent intent = new Intent(this, WSTunService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public WSTunService getWSTunService() {
        return service;
    }

    public boolean isServiceBound() {
        return bound;
    }
}
