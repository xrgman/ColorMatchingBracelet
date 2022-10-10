package com.example.colormatchingbracelet.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private IBluetoothService bluetoothServiceLink;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textHome;
        homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        Button test = root.findViewById(R.id.sendTestMsg);
        test.setEnabled(bluetoothServiceLink.getConnectionState() == BluetoothService.STATE_CONNECTED);

        test.setOnClickListener(view -> {
            if(bluetoothServiceLink.getConnectionState() == BluetoothService.STATE_CONNECTED) {
                bluetoothServiceLink.sendMessage("Letsgooo");
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        bluetoothServiceLink = (IBluetoothService) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        bluetoothServiceLink = null;
    }
}