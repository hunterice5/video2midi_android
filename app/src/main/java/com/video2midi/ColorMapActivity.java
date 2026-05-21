package com.video2midi;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.video2midi.core.VideoProcessor;
import com.video2midi.model.ColorMap;
import com.video2midi.model.Preferences;

import java.util.ArrayList;
import java.util.List;

public class ColorMapActivity extends AppCompatActivity {
    private static final String TAG = "ColorMapActivity";
    
    private RecyclerView recyclerView;
    private ColorMapAdapter adapter;
    private Button btnSave;
    private Button btnCancel;
    private Button btnAddColor;
    
    private Preferences preferences;
    private VideoProcessor videoProcessor;
    private String videoPath;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_colormap);
        
        videoPath = getIntent().getStringExtra("videoPath");
        
        preferences = new Preferences();
        preferences.load(this);

        if (videoPath != null) {
            videoProcessor = new VideoProcessor(this, videoPath, preferences);
        }

        initViews();
        setupListeners();
    }
    
    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewColors);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        btnAddColor = findViewById(R.id.btnAddColor);
        
        adapter = new ColorMapAdapter(preferences.getKeypColors());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
    
    private void setupListeners() {
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveColors();
            }
        });
        
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        btnAddColor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNewColor();
            }
        });
    }
    
    private void saveColors() {
        preferences.setKeypColors(adapter.getColors());
        preferences.save(this);
        
        setResult(RESULT_OK);
        finish();
    }
    
    private void addNewColor() {
        if (adapter.getItemCount() < 24) {
            adapter.addColor(new ColorMap(128, 128, 128));
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoProcessor != null) {
            videoProcessor.release();
        }
    }
    
    // Adapter для RecyclerView
    private class ColorMapAdapter extends RecyclerView.Adapter<ColorMapAdapter.ViewHolder> {
        private List<ColorMap> colors;
        
        public ColorMapAdapter(List<ColorMap> colors) {
            this.colors = new ArrayList<>(colors);
        }
        
        public List<ColorMap> getColors() {
            return colors;
        }
        
        public void addColor(ColorMap color) {
            colors.add(color);
            notifyItemInserted(colors.size() - 1);
        }
        public void setColors(List<ColorMap> new_colors) {
            colors = new_colors;
            //notifyItemInserted(colors.size() - 1);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color_map, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            ColorMap color = colors.get(position);
            holder.bind(color, position);
        }
        
        @Override
        public int getItemCount() {
            return colors.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            private View colorView;
            private TextView tvColorInfo;
            private TextView tvChannel;
            private Button btnEdit;
            private Button btnDelete;
            private Button btnChannelUp;
            private Button btnChannelDown;
            
            public ViewHolder(View itemView) {
                super(itemView);
                colorView = itemView.findViewById(R.id.colorView);
                tvColorInfo = itemView.findViewById(R.id.tvColorInfo);
                tvChannel = itemView.findViewById(R.id.tvChannel);
                btnEdit = itemView.findViewById(R.id.btnEdit);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                btnChannelUp = itemView.findViewById(R.id.btnChannelUp);
                btnChannelDown = itemView.findViewById(R.id.btnChannelDown);
            }
            
            public void bind(final ColorMap color, final int position) {
                // Устанавливаем цвет
                colorView.setBackgroundColor(color.toAndroidColor());
                
                // Информация о цвете
                tvColorInfo.setText(String.format("RGB(%d, %d, %d)", 
                    color.getR(), color.getG(), color.getB()));
                
                // Канал
                int channel = 0;
                if (position < preferences.getChannelAccordance().size()) {
                    channel = preferences.getChannelAccordance().get(position);
                }
                tvChannel.setText("Ch: " + (channel + 1));
                
                final int currentChannel = channel;
                
                // Кнопка редактирования
                btnEdit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showColorPicker(color, position);
                    }
                });
                
                // Кнопка удаления
                btnDelete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        color.setR(0);
                        color.setG(0);
                        color.setB(0);
                        notifyItemChanged(position);
                    }
                });
                
                // Увеличить канал
                btnChannelUp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int newChannel = Math.min(15, currentChannel + 1);
                        if (position < preferences.getChannelAccordance().size()) {
                            preferences.getChannelAccordance().set(position, newChannel);
                        }
                        notifyItemChanged(position);
                    }
                });
                
                // Уменьшить канал
                btnChannelDown.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int newChannel = Math.max(0, currentChannel - 1);
                        if (position < preferences.getChannelAccordance().size()) {
                            preferences.getChannelAccordance().set(position, newChannel);
                        }
                        notifyItemChanged(position);
                    }
                });
            }
        }
        
        private void showColorPicker(final ColorMap color, final int position) {
            // Простой диалог для ввода RGB значений
            View dialogView = LayoutInflater.from(ColorMapActivity.this)
                .inflate(R.layout.dialog_color_picker, null);
            
            final android.widget.SeekBar seekR = dialogView.findViewById(R.id.seekR);
            final android.widget.SeekBar seekG = dialogView.findViewById(R.id.seekG);
            final android.widget.SeekBar seekB = dialogView.findViewById(R.id.seekB);
            final TextView tvR = dialogView.findViewById(R.id.tvR);
            final TextView tvG = dialogView.findViewById(R.id.tvG);
            final TextView tvB = dialogView.findViewById(R.id.tvB);
            final View previewColor = dialogView.findViewById(R.id.previewColor);
            
            seekR.setMax(255);
            seekG.setMax(255);
            seekB.setMax(255);
            
            seekR.setProgress(color.getR());
            seekG.setProgress(color.getG());
            seekB.setProgress(color.getB());
            
            tvR.setText("R: " + color.getR());
            tvG.setText("G: " + color.getG());
            tvB.setText("B: " + color.getB());
            
            previewColor.setBackgroundColor(color.toAndroidColor());
            
            android.widget.SeekBar.OnSeekBarChangeListener listener = 
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, 
                                            int progress, boolean fromUser) {
                    tvR.setText("R: " + seekR.getProgress());
                    tvG.setText("G: " + seekG.getProgress());
                    tvB.setText("B: " + seekB.getProgress());
                    
                    int previewColorInt = Color.rgb(
                        seekR.getProgress(),
                        seekG.getProgress(),
                        seekB.getProgress()
                    );
                    previewColor.setBackgroundColor(previewColorInt);
                }
                
                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
            };
            
            seekR.setOnSeekBarChangeListener(listener);
            seekG.setOnSeekBarChangeListener(listener);
            seekB.setOnSeekBarChangeListener(listener);
            
            new AlertDialog.Builder(ColorMapActivity.this)
                .setTitle("Edit Color")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    color.setR(seekR.getProgress());
                    color.setG(seekG.getProgress());
                    color.setB(seekB.getProgress());
                    notifyItemChanged(position);
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
}
