package seven.lab.wstun.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import seven.lab.wstun.R;
import seven.lab.wstun.server.ServiceManager;

/**
 * RecyclerView adapter for displaying registered services.
 */
public class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ServiceViewHolder> {

    public interface KickCallback {
        void onKick(ServiceManager.ServiceEntry service);
    }

    private List<ServiceManager.ServiceEntry> services = new ArrayList<>();
    private final KickCallback callback;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public ServiceAdapter(KickCallback callback) {
        this.callback = callback;
    }

    public void setServices(List<ServiceManager.ServiceEntry> services) {
        this.services = new ArrayList<>(services);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_service, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        ServiceManager.ServiceEntry service = services.get(position);
        holder.bind(service);
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    class ServiceViewHolder extends RecyclerView.ViewHolder {
        private final TextView serviceName;
        private final TextView serviceType;
        private final TextView serviceEndpoint;
        private final Button kickButton;

        ServiceViewHolder(View itemView) {
            super(itemView);
            serviceName = itemView.findViewById(R.id.serviceName);
            serviceType = itemView.findViewById(R.id.serviceType);
            serviceEndpoint = itemView.findViewById(R.id.serviceEndpoint);
            kickButton = itemView.findViewById(R.id.kickButton);
        }

        void bind(ServiceManager.ServiceEntry service) {
            serviceName.setText(service.getName());
            serviceType.setText(service.getType() + " - Connected at " + 
                dateFormat.format(new Date(service.getRegisteredAt())));
            serviceEndpoint.setText("/" + service.getName() + "/main");

            kickButton.setOnClickListener(v -> {
                if (callback != null) {
                    callback.onKick(service);
                }
            });
        }
    }
}
