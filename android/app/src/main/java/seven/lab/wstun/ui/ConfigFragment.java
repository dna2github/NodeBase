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
    private TextView certInfo;
    private Button saveButton;
    private CheckBox debugLogsCheckbox;
    private TextView debugLogsUrl;
    private Button viewLogsButton;

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
        certInfo = view.findViewById(R.id.certInfo);
        saveButton = view.findViewById(R.id.saveButton);
        debugLogsCheckbox = view.findViewById(R.id.debugLogsCheckbox);
        debugLogsUrl = view.findViewById(R.id.debugLogsUrl);
        viewLogsButton = view.findViewById(R.id.viewLogsButton);

        loadConfig();

        httpsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            certInfo.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        
        debugLogsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setDebugLogsEnabled(isChecked);
            updateDebugLogsUrl();
        });

        saveButton.setOnClickListener(v -> saveConfig());
        viewLogsButton.setOnClickListener(v -> showLogViewer());
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
        debugLogsCheckbox.setChecked(config.isDebugLogsEnabled());
        updateDebugLogsUrl();
    }
    
    private void updateDebugLogsUrl() {
        if (config.isDebugLogsEnabled()) {
            String protocol = config.isHttpsEnabled() ? "https" : "http";
            String url = protocol + "://[device-ip]:" + config.getPort() + "/debug/logs";
            debugLogsUrl.setText("Access " + url + " in browser to view live server logs");
        } else {
            debugLogsUrl.setText("Enable to access /debug/logs endpoint for live server logs");
        }
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

        Toast.makeText(getContext(), "Configuration saved", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (getActivity() == null || !isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            boolean canEdit = service == null || !service.isServerRunning();
            portInput.setEnabled(canEdit);
            httpsCheckbox.setEnabled(canEdit);
            corsOriginsInput.setEnabled(canEdit);
            saveButton.setEnabled(canEdit);
            
            updateDebugLogsUrl();
        });
    }
    
    // Log viewer thread and process
    private Thread logViewerThread;
    private Process logViewerProcess;
    private volatile boolean logViewerRunning = false;
    
    // Track if user has scrolled manually (disable auto-scroll)
    private volatile boolean userScrolling = false;
    private String logFilter = "";
    
    private void showLogViewer() {
        // Create a dialog with a scrollable TextView for logs
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Live Logs");
        
        // Create main layout
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(requireContext());
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        
        // Create filter input
        android.widget.LinearLayout filterLayout = new android.widget.LinearLayout(requireContext());
        filterLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        filterLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        final android.widget.EditText filterInput = new android.widget.EditText(requireContext());
        filterInput.setHint("Filter keywords...");
        filterInput.setSingleLine(true);
        filterInput.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        final android.widget.CheckBox autoScrollCheckbox = new android.widget.CheckBox(requireContext());
        autoScrollCheckbox.setText("Auto-scroll");
        autoScrollCheckbox.setChecked(true);
        
        filterLayout.addView(filterInput);
        filterLayout.addView(autoScrollCheckbox);
        mainLayout.addView(filterLayout);
        
        // Create a scrollable TextView
        final android.widget.ScrollView scrollView = new android.widget.ScrollView(requireContext());
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 600));
        
        final TextView logTextView = new TextView(requireContext());
        logTextView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logTextView.setTextSize(10);
        logTextView.setPadding(8, 8, 8, 8);
        logTextView.setText("Starting log capture...\n");
        logTextView.setTextIsSelectable(true);
        scrollView.addView(logTextView);
        mainLayout.addView(scrollView);
        
        // Handle filter changes
        filterInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                logFilter = s.toString().toLowerCase();
            }
        });
        
        // Handle auto-scroll toggle
        autoScrollCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            userScrolling = !isChecked;
        });
        
        // Detect manual scroll
        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN ||
                event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                userScrolling = true;
                autoScrollCheckbox.setChecked(false);
            }
            return false;
        });
        
        builder.setView(mainLayout);
        builder.setNeutralButton("Clear", null); // Will set listener after show()
        builder.setNegativeButton("Close", (dialog, which) -> {
            stopLogViewer();
            dialog.dismiss();
        });
        
        android.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> stopLogViewer());
        dialog.show();
        
        // Set clear button to not dismiss dialog
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            logTextView.setText("");
        });
        
        // Start log capture thread
        userScrolling = false;
        logFilter = "";
        logViewerRunning = true;
        logViewerThread = new Thread(() -> {
            java.io.BufferedReader reader = null;
            try {
                // Clear logcat first and then start capturing
                Runtime.getRuntime().exec("logcat -c").waitFor();
                logViewerProcess = Runtime.getRuntime().exec("logcat -v time *:D");
                reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(logViewerProcess.getInputStream()));
                
                String line;
                final StringBuilder buffer = new StringBuilder();
                int lineCount = 0;
                
                while (logViewerRunning && (line = reader.readLine()) != null) {
                    // Apply filter
                    String currentFilter = logFilter;
                    if (currentFilter.isEmpty() || line.toLowerCase().contains(currentFilter)) {
                        buffer.append(line).append("\n");
                        lineCount++;
                    }
                    
                    // Update UI every 5 lines
                    if (lineCount % 5 == 0 && buffer.length() > 0) {
                        final String newText = buffer.toString();
                        buffer.setLength(0);
                        
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // Save scroll position before update if not auto-scrolling
                                final int scrollY = scrollView.getScrollY();
                                
                                logTextView.append(newText);
                                
                                // Limit text length to prevent memory issues
                                if (logTextView.getText().length() > 50000) {
                                    String text = logTextView.getText().toString();
                                    logTextView.setText(text.substring(text.length() - 40000));
                                }
                                
                                // Handle scroll position
                                if (userScrolling) {
                                    // Restore scroll position when user is reading
                                    scrollView.post(() -> scrollView.scrollTo(0, scrollY));
                                } else {
                                    // Auto-scroll to bottom
                                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                                }
                            });
                        }
                    }
                }
                
                // Flush remaining buffer
                if (buffer.length() > 0 && getActivity() != null) {
                    final String remaining = buffer.toString();
                    getActivity().runOnUiThread(() -> logTextView.append(remaining));
                }
                
            } catch (Exception e) {
                if (getActivity() != null) {
                    final String error = "Error: " + e.getMessage() + "\n";
                    getActivity().runOnUiThread(() -> logTextView.append(error));
                }
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (logViewerProcess != null) {
                    logViewerProcess.destroy();
                }
            }
        });
        logViewerThread.setName("LogViewer");
        logViewerThread.start();
    }
    
    private void stopLogViewer() {
        logViewerRunning = false;
        if (logViewerProcess != null) {
            logViewerProcess.destroy();
            logViewerProcess = null;
        }
        if (logViewerThread != null) {
            logViewerThread.interrupt();
            logViewerThread = null;
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopLogViewer();
    }
}
