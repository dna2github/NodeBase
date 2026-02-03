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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import seven.lab.wstun.R;
import seven.lab.wstun.server.ServiceManager;
import seven.lab.wstun.service.WSTunService;

/**
 * Fragment showing server status and registered services.
 */
public class StatusFragment extends Fragment implements WSTunService.ServiceListener {

    private WSTunService service;

    private TextView statusText;
    private TextView addressText;
    private Button toggleButton;
    private RecyclerView servicesRecyclerView;
    private TextView emptyText;

    private ServiceAdapter adapter;

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
        servicesRecyclerView = view.findViewById(R.id.servicesRecyclerView);
        emptyText = view.findViewById(R.id.emptyText);

        servicesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ServiceAdapter(service -> {
            if (this.service != null) {
                this.service.kickService(service.getName());
            }
        });
        servicesRecyclerView.setAdapter(adapter);

        toggleButton.setOnClickListener(v -> toggleServer());

        updateUI();
    }

    public void onServiceConnected(WSTunService service) {
        this.service = service;
        service.setListener(this);
        updateUI();
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
            if (service != null && service.isServerRunning()) {
                statusText.setText(R.string.server_running);
                statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                toggleButton.setText(R.string.stop_server);

                String protocol = service.isHttpsEnabled() ? "https" : "http";
                String ip = getLocalIpAddress();
                String address = protocol + "://" + ip + ":" + service.getPort();
                addressText.setText(address);
                addressText.setVisibility(View.VISIBLE);

                updateServiceList();
            } else {
                statusText.setText(R.string.server_stopped);
                statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                toggleButton.setText(R.string.start_server);
                addressText.setVisibility(View.GONE);
                
                adapter.setServices(Collections.emptyList());
                emptyText.setVisibility(View.VISIBLE);
                servicesRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void updateServiceList() {
        if (service == null || service.getServiceManager() == null) return;

        List<ServiceManager.ServiceEntry> services = service.getServiceManager().getAllServices();
        adapter.setServices(services);

        if (services.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            servicesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            servicesRecyclerView.setVisibility(View.VISIBLE);
        }
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
    }

    @Override
    public void onServerStopped() {
        updateUI();
    }

    @Override
    public void onServiceChanged() {
        if (getActivity() != null) {
            requireActivity().runOnUiThread(this::updateServiceList);
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
