package com.example.colormatchingbracelet.ui.home;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private IBluetoothService bluetoothServiceLink;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

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