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
 * Adapter for displaying running instances (rooms).
 */
public class InstanceAdapter extends RecyclerView.Adapter<InstanceAdapter.ViewHolder> {

    private List<InstanceItem> instances = new ArrayList<>();
    private final InstanceActionListener listener;

    public interface InstanceActionListener {
        void onDestroy(InstanceItem instance);
    }

    public static class InstanceItem {
        public final String uuid;
        public final String name;
        public final String serviceName;
        public final String serviceDisplayName;
        public final int userCount;

        public InstanceItem(String uuid, String name, String serviceName, 
                           String serviceDisplayName, int userCount) {
            this.uuid = uuid;
            this.name = name;
            this.serviceName = serviceName;
            this.serviceDisplayName = serviceDisplayName;
            this.userCount = userCount;
        }
        
        public String getUuid() {
            return uuid;
        }
    }

    public InstanceAdapter(InstanceActionListener listener) {
        this.listener = listener;
    }

    public void setInstances(List<InstanceItem> instances) {
        this.instances = instances;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_instance, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstanceItem item = instances.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return instances.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView serviceText;
        private final TextView usersText;
        private final TextView uuidText;
        private final Button destroyButton;

        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.instanceNameText);
            serviceText = itemView.findViewById(R.id.serviceNameText);
            usersText = itemView.findViewById(R.id.usersText);
            uuidText = itemView.findViewById(R.id.uuidText);
            destroyButton = itemView.findViewById(R.id.destroyButton);
        }

        void bind(InstanceItem item, InstanceActionListener listener) {
            nameText.setText(item.name);
            serviceText.setText(item.serviceDisplayName);
            usersText.setText(item.userCount + " user(s)");
            uuidText.setText(item.uuid);

            destroyButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDestroy(item);
                }
            });
        }
    }
}
