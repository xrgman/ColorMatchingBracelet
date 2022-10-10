package com.example.colormatchingbracelet.ui.gallery;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothConnection;
import com.example.colormatchingbracelet.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        TextView connStatusBlt = root.findViewById(R.id.bluetoothConnStatus);
        Button connectBltBtn = root.findViewById(R.id.buttonConnBlt);

        Handler handler = new Handler();

        handler.postDelayed((Runnable) () -> BluetoothConnection.stopScan(), 1000 * 20);

        connectBltBtn.setOnClickListener(view -> {
            BluetoothConnection.startScan(getContext());
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}