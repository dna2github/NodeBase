package seven.lab.wstun.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import seven.lab.wstun.R;
import seven.lab.wstun.config.ServerConfig;
import seven.lab.wstun.service.WSTunService;

/**
 * Fragment for server configuration.
 */
public class ConfigFragment extends Fragment {

    private WSTunService service;
    private ServerConfig config;

    private TextInputEditText portInput;
    private TextInputEditText corsOriginsInput;
    private CheckBox httpsCheckbox;
    private CheckBox fileshareCheckbox;
    private CheckBox chatCheckbox;
    private TextView certInfo;
    private Button saveButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_config, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        config = new ServerConfig(requireContext());

        portInput = view.findViewById(R.id.portInput);
        corsOriginsInput = view.findViewById(R.id.corsOriginsInput);
        httpsCheckbox = view.findViewById(R.id.httpsCheckbox);
        fileshareCheckbox = view.findViewById(R.id.fileshareCheckbox);
        chatCheckbox = view.findViewById(R.id.chatCheckbox);
        certInfo = view.findViewById(R.id.certInfo);
        saveButton = view.findViewById(R.id.saveButton);

        loadConfig();

        httpsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            certInfo.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        saveButton.setOnClickListener(v -> saveConfig());
    }

    public void onServiceConnected(WSTunService service) {
        this.service = service;
        updateUI();
    }

    private void loadConfig() {
        portInput.setText(String.valueOf(config.getPort()));
        httpsCheckbox.setChecked(config.isHttpsEnabled());
        certInfo.setVisibility(config.isHttpsEnabled() ? View.VISIBLE : View.GONE);
        corsOriginsInput.setText(config.getCorsOrigins());
        fileshareCheckbox.setChecked(config.isFileshareEnabled());
        chatCheckbox.setChecked(config.isChatEnabled());
    }

    private void saveConfig() {
        // Validate port
        String portStr = portInput.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                Toast.makeText(getContext(), "Port must be between 1 and 65535", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid port number", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if server is running
        if (service != null && service.isServerRunning()) {
            Toast.makeText(getContext(), "Stop server before changing configuration", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate CORS origins
        String corsOrigins = corsOriginsInput.getText().toString().trim();
        if (corsOrigins.isEmpty()) {
            corsOrigins = "*";
        }

        // Save config
        config.setPort(port);
        config.setHttpsEnabled(httpsCheckbox.isChecked());
        config.setCorsOrigins(corsOrigins);
        config.setFileshareEnabled(fileshareCheckbox.isChecked());
        config.setChatEnabled(chatCheckbox.isChecked());

        Toast.makeText(getContext(), "Configuration saved", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (getActivity() == null || !isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            boolean canEdit = service == null || !service.isServerRunning();
            portInput.setEnabled(canEdit);
            httpsCheckbox.setEnabled(canEdit);
            corsOriginsInput.setEnabled(canEdit);
            fileshareCheckbox.setEnabled(canEdit);
            chatCheckbox.setEnabled(canEdit);
            saveButton.setEnabled(canEdit);
        });
    }
}
