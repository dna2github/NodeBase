package seven.lab.wstun.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import seven.lab.wstun.R;
import seven.lab.wstun.server.ServiceManager;
import seven.lab.wstun.service.WSTunService;

/**
 * Fragment showing server status with nested tabs for:
 * - Running Instances
 * - Installed Services
 * - Marketplace
 */
public class StatusFragment extends Fragment implements WSTunService.ServiceListener {

    private WSTunService service;

    private TextView statusText;
    private TextView addressText;
    private Button toggleButton;
    
    // Nested tabs
    private TabLayout innerTabLayout;
    private ViewPager2 innerViewPager;
    
    // Child fragments
    private RunningInstancesFragment runningInstancesFragment;
    private InstalledServicesFragment installedServicesFragment;
    private MarketplaceFragment marketplaceFragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusText = view.findViewById(R.id.statusText);
        addressText = view.findViewById(R.id.addressText);
        toggleButton = view.findViewById(R.id.toggleButton);
        innerTabLayout = view.findViewById(R.id.innerTabLayout);
        innerViewPager = view.findViewById(R.id.innerViewPager);

        // Set up nested tabs
        setupInnerTabs();

        toggleButton.setOnClickListener(v -> toggleServer());

        updateUI();
    }
    
    private void setupInnerTabs() {
        runningInstancesFragment = new RunningInstancesFragment();
        installedServicesFragment = new InstalledServicesFragment();
        marketplaceFragment = new MarketplaceFragment();
        
        innerViewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return runningInstancesFragment;
                    case 1:
                        return installedServicesFragment;
                    case 2:
                        return marketplaceFragment;
                    default:
                        return runningInstancesFragment;
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(innerTabLayout, innerViewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_instances);
                    break;
                case 1:
                    tab.setText(R.string.tab_services);
                    break;
                case 2:
                    tab.setText(R.string.tab_marketplace);
                    break;
            }
        }).attach();
    }

    public void onServiceConnected(WSTunService service) {
        this.service = service;
        service.setListener(this);
        updateUI();
        
        // Pass service to child fragments
        if (runningInstancesFragment != null) {
            runningInstancesFragment.onServiceConnected(service);
        }
        if (installedServicesFragment != null) {
            installedServicesFragment.onServiceConnected(service);
        }
        if (marketplaceFragment != null) {
            marketplaceFragment.onServiceConnected(service);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateUI();
        // Refresh all child fragments when returning to the app
        if (runningInstancesFragment != null) {
            runningInstancesFragment.updateInstanceList();
        }
        if (installedServicesFragment != null) {
            installedServicesFragment.onResume();
        }
    }

    private void toggleServer() {
        if (service == null) return;

        if (service.isServerRunning()) {
            Intent intent = new Intent(getContext(), WSTunService.class);
            intent.setAction(WSTunService.ACTION_STOP);
            requireContext().startService(intent);
        } else {
            Intent intent = new Intent(getContext(), WSTunService.class);
            intent.setAction(WSTunService.ACTION_START);
            requireContext().startForegroundService(intent);
        }
    }

    private void updateUI() {
        if (getActivity() == null || !isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            boolean running = service != null && service.isServerRunning();
            
            if (running) {
                statusText.setText(R.string.server_running);
                statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                toggleButton.setText(R.string.stop_server);

                String protocol = service.isHttpsEnabled() ? "https" : "http";
                String ip = getLocalIpAddress();
                String address = protocol + "://" + ip + ":" + service.getPort();
                addressText.setText(address);
                addressText.setVisibility(View.VISIBLE);
            } else {
                statusText.setText(R.string.server_stopped);
                statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                toggleButton.setText(R.string.start_server);
                addressText.setVisibility(View.GONE);
            }
        });
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && !sAddr.contains(":")) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "localhost";
    }

    @Override
    public void onServerStarted() {
        updateUI();
        // Pass service to child fragments now that server is ready
        if (runningInstancesFragment != null) {
            runningInstancesFragment.onServiceConnected(service);
        }
        if (installedServicesFragment != null) {
            installedServicesFragment.onServiceConnected(service);
        }
        if (marketplaceFragment != null) {
            marketplaceFragment.onServiceConnected(service);
        }
    }

    @Override
    public void onServerStopped() {
        updateUI();
        if (runningInstancesFragment != null) {
            runningInstancesFragment.updateServiceList();
        }
    }

    @Override
    public void onServiceChanged() {
        if (getActivity() != null && runningInstancesFragment != null) {
            requireActivity().runOnUiThread(() -> runningInstancesFragment.updateServiceList());
        }
    }

    @Override
    public void onError(String message) {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(() -> 
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show()
            );
        }
    }
}
