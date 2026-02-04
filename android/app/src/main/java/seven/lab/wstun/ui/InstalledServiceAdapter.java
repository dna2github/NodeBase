package seven.lab.wstun.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import seven.lab.wstun.R;

/**
 * Adapter for displaying installed services.
 */
public class InstalledServiceAdapter extends RecyclerView.Adapter<InstalledServiceAdapter.ViewHolder> {

    private List<ServiceItem> services = new ArrayList<>();
    private final ServiceActionListener listener;

    public interface ServiceActionListener {
        void onEnableDisable(String serviceName, boolean enable);
        void onUninstall(String serviceName);
    }

    public static class ServiceItem {
        public final String name;
        public final String displayName;
        public final String description;
        public final boolean enabled;
        public final boolean builtin;
        public final int instanceCount;

        public ServiceItem(String name, String displayName, String description, 
                          boolean enabled, boolean builtin, int instanceCount) {
            this.name = name;
            this.displayName = displayName != null ? displayName : name;
            this.description = description;
            this.enabled = enabled;
            this.builtin = builtin;
            this.instanceCount = instanceCount;
        }
    }

    public InstalledServiceAdapter(ServiceActionListener listener) {
        this.listener = listener;
    }

    public void setServices(List<ServiceItem> services) {
        this.services = services;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_service, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ServiceItem item = services.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView descText;
        private final TextView statusText;
        private final TextView instancesText;
        private final Button enableButton;
        private final Button uninstallButton;

        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.serviceNameText);
            descText = itemView.findViewById(R.id.serviceDescText);
            statusText = itemView.findViewById(R.id.statusText);
            instancesText = itemView.findViewById(R.id.instancesText);
            enableButton = itemView.findViewById(R.id.enableButton);
            uninstallButton = itemView.findViewById(R.id.uninstallButton);
        }

        void bind(ServiceItem item, ServiceActionListener listener) {
            nameText.setText(item.displayName);
            
            if (item.description != null && !item.description.isEmpty()) {
                descText.setText(item.description);
                descText.setVisibility(View.VISIBLE);
            } else {
                descText.setVisibility(View.GONE);
            }

            if (item.enabled) {
                statusText.setText("Enabled");
                statusText.setTextColor(0xFF4CAF50);
                enableButton.setText("Disable");
            } else {
                statusText.setText("Disabled");
                statusText.setTextColor(0xFF9E9E9E);
                enableButton.setText("Enable");
            }

            if (item.instanceCount > 0) {
                instancesText.setText(item.instanceCount + " instance(s)");
                instancesText.setVisibility(View.VISIBLE);
            } else {
                instancesText.setVisibility(View.GONE);
            }

            enableButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEnableDisable(item.name, !item.enabled);
                }
            });

            if (item.builtin) {
                uninstallButton.setVisibility(View.GONE);
            } else {
                uninstallButton.setVisibility(View.VISIBLE);
                uninstallButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onUninstall(item.name);
                    }
                });
            }
        }
    }
}
