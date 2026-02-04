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
 * Adapter for displaying marketplace services.
 */
public class MarketplaceAdapter extends RecyclerView.Adapter<MarketplaceAdapter.ViewHolder> {

    private List<MarketplaceItem> services = new ArrayList<>();
    private final InstallListener listener;

    public interface InstallListener {
        void onInstall(String serviceName);
    }

    public static class MarketplaceItem {
        public final String name;
        public final String displayName;
        public final String description;
        public final String version;
        public final boolean installed;

        public MarketplaceItem(String name, String displayName, String description, 
                               String version, boolean installed) {
            this.name = name;
            this.displayName = displayName != null ? displayName : name;
            this.description = description;
            this.version = version;
            this.installed = installed;
        }
    }

    public MarketplaceAdapter(InstallListener listener) {
        this.listener = listener;
    }

    public void setServices(List<MarketplaceItem> services) {
        this.services = services;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_marketplace_service, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MarketplaceItem item = services.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView descText;
        private final TextView versionText;
        private final Button installButton;

        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.serviceNameText);
            descText = itemView.findViewById(R.id.serviceDescText);
            versionText = itemView.findViewById(R.id.versionText);
            installButton = itemView.findViewById(R.id.installButton);
        }

        void bind(MarketplaceItem item, InstallListener listener) {
            nameText.setText(item.displayName);
            
            if (item.description != null && !item.description.isEmpty()) {
                descText.setText(item.description);
                descText.setVisibility(View.VISIBLE);
            } else {
                descText.setVisibility(View.GONE);
            }

            versionText.setText("v" + item.version);

            if (item.installed) {
                installButton.setText("Installed");
                installButton.setEnabled(false);
            } else {
                installButton.setText("Install");
                installButton.setEnabled(true);
                installButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onInstall(item.name);
                    }
                });
            }
        }
    }
}
