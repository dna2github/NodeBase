package seven.lab.wstun.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import seven.lab.wstun.R;
import seven.lab.wstun.marketplace.InstalledService;
import seven.lab.wstun.server.LocalServiceManager;
import seven.lab.wstun.server.ServiceManager;
import seven.lab.wstun.service.WSTunService;

/**
 * Fragment showing running service instances (rooms).
 */
public class RunningInstancesFragment extends Fragment {

    private WSTunService service;

    private RecyclerView instancesRecyclerView;
    private TextView emptyText;

    private InstanceAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_running_instances, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        instancesRecyclerView = view.findViewById(R.id.servicesRecyclerView);
        emptyText = view.findViewById(R.id.emptyText);

        instancesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InstanceAdapter(instance -> {
            if (service != null && service.getServiceManager() != null) {
                service.getServiceManager().destroyInstance(instance.getUuid());
                updateInstanceList();
            }
        });
        instancesRecyclerView.setAdapter(adapter);

        updateInstanceList();
    }

    public void onServiceConnected(WSTunService service) {
        this.service = service;
        updateInstanceList();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateInstanceList();
    }

    public void updateInstanceList() {
        if (getActivity() == null || !isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (service == null || service.getServiceManager() == null) {
                adapter.setInstances(new ArrayList<>());
                emptyText.setVisibility(View.VISIBLE);
                instancesRecyclerView.setVisibility(View.GONE);
                return;
            }

            LocalServiceManager lsm = service.getLocalServiceManager();
            ServiceManager sm = service.getServiceManager();
            
            // Collect all instances across all services
            List<InstanceAdapter.InstanceItem> items = new ArrayList<>();
            
            if (lsm != null) {
                Map<String, InstalledService> installed = lsm.getInstalledServices();
                for (String serviceName : installed.keySet()) {
                    List<ServiceManager.ServiceInstance> instances = sm.getInstancesForService(serviceName);
                    InstalledService svc = installed.get(serviceName);
                    String displayName = svc != null ? svc.getDisplayName() : serviceName;
                    
                    for (ServiceManager.ServiceInstance inst : instances) {
                        items.add(new InstanceAdapter.InstanceItem(
                            inst.getUuid(),
                            inst.getName(),
                            serviceName,
                            displayName,
                            inst.getUserCount()
                        ));
                    }
                }
            }

            adapter.setInstances(items);

            if (items.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                instancesRecyclerView.setVisibility(View.GONE);
            } else {
                emptyText.setVisibility(View.GONE);
                instancesRecyclerView.setVisibility(View.VISIBLE);
            }
        });
    }
    
    // Renamed method to match StatusFragment expectations
    public void updateServiceList() {
        updateInstanceList();
    }
}
