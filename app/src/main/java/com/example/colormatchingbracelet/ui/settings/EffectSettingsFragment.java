package com.example.colormatchingbracelet.ui.settings;

import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.colormatchingbracelet.LedStrip.LedStripEffectType;
import com.example.colormatchingbracelet.R;
import com.example.colormatchingbracelet.bluetooth.BluetoothService;
import com.example.colormatchingbracelet.bluetooth.IBluetoothService;
import com.example.colormatchingbracelet.bluetooth.MessageType;
import com.example.colormatchingbracelet.databinding.FragmentEffectSettingsBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link EffectSettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class EffectSettingsFragment extends DialogFragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_BLUETOOTH_SERVICE = "bluetoothService";
    private static final String ARG_NAME = "name";

    // TODO: Rename and change types of parameters
    private IBluetoothService bluetoothService;
    private LedStripEffectType effectType;
    private String name;

    public EffectSettingsFragment() {
        // Required empty public constructor
    }

    // TODO: Rename and change types and number of parameters
    public static EffectSettingsFragment newInstance(IBluetoothService bluetoothService, LedStripEffectType effectType, String name) {
        EffectSettingsFragment fragment = new EffectSettingsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        fragment.effectType = effectType;
        fragment.bluetoothService = bluetoothService;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_ColorMatchingBracelet);

        if (getArguments() != null) {
            name = getArguments().getString(ARG_NAME);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        FragmentEffectSettingsBinding binding = FragmentEffectSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        TextView title = root.findViewById(R.id.effectSettingsTitle);
        title.setText(name + " effect settings");

        Button addGestureButton = root.findViewById(R.id.addGestureBtn);

        ListView gestures = root.findViewById(R.id.effectSettingsList);

        List<String> gestureList = new ArrayList<>();

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return gestureList.size();
            }

            @Override
            public Object getItem(int i) {
                return null;
            }

            @Override
            public long getItemId(int i) {
                return 0;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                view = getLayoutInflater().inflate(R.layout.gesture_layout, null);
                TextView text = view.findViewById(R.id.gestureName);
                text.setText(gestureList.get(i));
                Button removeButton = view.findViewById(R.id.removeGestureButton);

                removeButton.setOnClickListener(v -> {
                    bluetoothService.sendMessage(MessageType.REMOVE_GESTURE, new byte[0]);
                    gestureList.remove(i);
                    this.notifyDataSetChanged();
                });

                return view;
            }
        };

        gestures.setAdapter(adapter);

        addGestureButton.setOnClickListener(view -> {
            byte[] data = new byte[1];
            data[0] = (byte) effectType.getValue();

            bluetoothService.sendMessage(MessageType.ADD_GESTURE, data);
            gestureList.add(new SimpleDateFormat("yy-MM-dd HH:mm:ss").format(new Date()));
            adapter.notifyDataSetChanged();
        });

        return root;
    }
}