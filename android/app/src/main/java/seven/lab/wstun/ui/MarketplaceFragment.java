package seven.lab.wstun.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import seven.lab.wstun.R;
import seven.lab.wstun.marketplace.MarketplaceService;
import seven.lab.wstun.service.WSTunService;

/**
 * Fragment for marketplace service browsing and installation.
 */
public class MarketplaceFragment extends Fragment {

    private WSTunService service;

    private TextInputEditText urlInput;
    private Button fetchButton;
    private ProgressBar progressBar;
    private RecyclerView servicesRecyclerView;
    private TextView emptyText;
    private TextView errorText;

    private MarketplaceAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_marketplace, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        urlInput = view.findViewById(R.id.marketplaceUrlInput);
        fetchButton = view.findViewById(R.id.fetchButton);
        progressBar = view.findViewById(R.id.progressBar);
        servicesRecyclerView = view.findViewById(R.id.servicesRecyclerView);
        emptyText = view.findViewById(R.id.emptyText);
        errorText = view.findViewById(R.id.errorText);

        servicesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MarketplaceAdapter(this::installService);
        servicesRecyclerView.setAdapter(adapter);

        fetchButton.setOnClickListener(v -> fetchMarketplace());

        // Load saved marketplace URL
        if (service != null && service.getLocalServiceManager() != null) {
            String savedUrl = service.getLocalServiceManager().getMarketplaceService().getMarketplaceUrl();
            if (savedUrl != null && !savedUrl.isEmpty()) {
                urlInput.setText(savedUrl);
            }
        }
    }

    public void onServiceConnected(WSTunService service) {
        this.service = service;
        
        if (urlInput != null && service.getLocalServiceManager() != null) {
            String savedUrl = service.getLocalServiceManager().getMarketplaceService().getMarketplaceUrl();
            if (savedUrl != null && !savedUrl.isEmpty()) {
                urlInput.setText(savedUrl);
            }
        }
    }

    private void fetchMarketplace() {
        if (service == null || service.getLocalServiceManager() == null) {
            showError("Service not connected");
            return;
        }

        String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
        if (url.isEmpty()) {
            showError("Please enter a marketplace URL");
            return;
        }

        hideError();
        showLoading(true);

        MarketplaceService marketplace = service.getLocalServiceManager().getMarketplaceService();
        marketplace.setMarketplaceUrl(url);
        
        marketplace.listMarketplace(url, new MarketplaceService.MarketplaceCallback<List<JsonObject>>() {
            @Override
            public void onSuccess(List<JsonObject> result) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    displayServices(result);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showError("Failed to fetch: " + error);
                    adapter.setServices(new ArrayList<>());
                });
            }
        });
    }

    private void displayServices(List<JsonObject> services) {
        List<MarketplaceAdapter.MarketplaceItem> items = new ArrayList<>();
        MarketplaceService marketplace = service.getLocalServiceManager().getMarketplaceService();
        
        for (JsonObject svc : services) {
            String name = svc.has("name") ? svc.get("name").getAsString() : null;
            if (name == null) continue;
            
            String displayName = svc.has("displayName") ? svc.get("displayName").getAsString() : name;
            String description = svc.has("description") ? svc.get("description").getAsString() : null;
            String version = svc.has("version") ? svc.get("version").getAsString() : "1.0.0";
            boolean installed = marketplace.isInstalled(name);
            
            items.add(new MarketplaceAdapter.MarketplaceItem(name, displayName, description, version, installed));
        }
        
        adapter.setServices(items);
        
        if (items.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            servicesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            servicesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void installService(String serviceName) {
        if (service == null || service.getLocalServiceManager() == null) {
            showError("Service not connected");
            return;
        }

        String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
        if (url.isEmpty()) {
            showError("No marketplace URL");
            return;
        }

        showLoading(true);
        
        MarketplaceService marketplace = service.getLocalServiceManager().getMarketplaceService();
        marketplace.installService(url, serviceName, new MarketplaceService.MarketplaceCallback<seven.lab.wstun.marketplace.InstalledService>() {
            @Override
            public void onSuccess(seven.lab.wstun.marketplace.InstalledService result) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(getContext(), "Service installed successfully", Toast.LENGTH_SHORT).show();
                    fetchMarketplace(); // Refresh list
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showError("Install failed: " + error);
                });
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        fetchButton.setEnabled(!show);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorText.setVisibility(View.GONE);
    }
}
