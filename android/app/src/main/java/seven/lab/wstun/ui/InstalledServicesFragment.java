package seven.lab.wstun.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import seven.lab.wstun.R;
import seven.lab.wstun.marketplace.InstalledService;
import seven.lab.wstun.server.LocalServiceManager;
import seven.lab.wstun.server.ServiceManager;
import seven.lab.wstun.service.WSTunService;

/**
 * Fragment showing installed services.
 */
public class InstalledServicesFragment extends Fragment {

    private WSTunService service;

    private RecyclerView servicesRecyclerView;
    private TextView emptyText;
    private TextInputEditText filterInput;

    private InstalledServiceAdapter adapter;
    private List<InstalledServiceAdapter.ServiceItem> allItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_installed_services, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        servicesRecyclerView = view.findViewById(R.id.servicesRecyclerView);
        emptyText = view.findViewById(R.id.emptyText);
        filterInput = view.findViewById(R.id.filterInput);

        servicesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InstalledServiceAdapter(new InstalledServiceAdapter.ServiceActionListener() {
            @Override
            public void onEnableDisable(String serviceName, boolean enable) {
                if (service == null || service.getLocalServiceManager() == null) return;
                
                if (enable) {
                    service.getLocalServiceManager().enableService(serviceName);
                } else {
                    service.getLocalServiceManager().disableService(serviceName, service.getServiceManager());
                }
                refreshList();
            }

            @Override
            public void onUninstall(String serviceName) {
                if (service == null || service.getLocalServiceManager() == null) return;
                
                InstalledService svc = service.getLocalServiceManager().getInstalledService(serviceName);
                if (svc != null && "builtin".equals(svc.getSource())) {
                    Toast.makeText(getContext(), "Cannot uninstall built-in service", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                service.getLocalServiceManager().uninstallService(serviceName, service.getServiceManager());
                refreshList();
                Toast.makeText(getContext(), "Service uninstalled", Toast.LENGTH_SHORT).show();
            }
        });
        servicesRecyclerView.setAdapter(adapter);

        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterServices(s.toString());
            }
        });

        refreshList();
    }

    public void onServiceConnected(WSTunService service) {
        this.service = service;
        refreshList();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshList();
    }

    private void filterServices(String query) {
        if (query == null || query.isEmpty()) {
            adapter.setServices(allItems);
        } else {
            String lowerQuery = query.toLowerCase();
            List<InstalledServiceAdapter.ServiceItem> filtered = new ArrayList<>();
            for (InstalledServiceAdapter.ServiceItem item : allItems) {
                if (item.displayName.toLowerCase().contains(lowerQuery) ||
                    item.name.toLowerCase().contains(lowerQuery)) {
                    filtered.add(item);
                }
            }
            adapter.setServices(filtered);
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (adapter.getItemCount() == 0) {
            emptyText.setVisibility(View.VISIBLE);
            servicesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            servicesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void refreshList() {
        if (getActivity() == null || !isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (service == null || service.getLocalServiceManager() == null) {
                allItems.clear();
                adapter.setServices(allItems);
                emptyText.setVisibility(View.VISIBLE);
                servicesRecyclerView.setVisibility(View.GONE);
                return;
            }

            LocalServiceManager lsm = service.getLocalServiceManager();
            ServiceManager sm = service.getServiceManager();
            
            allItems.clear();
            Map<String, InstalledService> installed = lsm.getInstalledServices();
            
            for (Map.Entry<String, InstalledService> entry : installed.entrySet()) {
                String name = entry.getKey();
                InstalledService svc = entry.getValue();
                
                int instanceCount = 0;
                if (sm != null) {
                    instanceCount = sm.getInstanceCountForService(name);
                }
                
                allItems.add(new InstalledServiceAdapter.ServiceItem(
                    name,
                    svc.getDisplayName(),
                    svc.getManifest() != null ? svc.getManifest().getDescription() : null,
                    svc.isEnabled(),
                    "builtin".equals(svc.getSource()),
                    instanceCount
                ));
            }

            // Apply current filter
            String filter = filterInput.getText() != null ? filterInput.getText().toString() : "";
            filterServices(filter);
        });
    }
}
