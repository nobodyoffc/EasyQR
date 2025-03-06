package com.fc.scanqr;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import java.util.List;
import androidx.annotation.NonNull;

public class QRDisplayActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_display);

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        List<Bitmap> qrBitmaps = getIntent().getParcelableArrayListExtra("qr_bitmaps");
        
        QRPagerAdapter adapter = new QRPagerAdapter(qrBitmaps);
        viewPager.setAdapter(adapter);
    }
    
    private static class QRPagerAdapter extends RecyclerView.Adapter<QRPagerAdapter.QRViewHolder> {
        private final List<Bitmap> qrBitmaps;
        
        QRPagerAdapter(List<Bitmap> qrBitmaps) {
            this.qrBitmaps = qrBitmaps;
        }
        
        @NonNull
        @Override
        public QRViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_qr_display, parent, false);
            return new QRViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull QRViewHolder holder, int position) {
            holder.imageView.setImageBitmap(qrBitmaps.get(position));
        }
        
        @Override
        public int getItemCount() {
            return qrBitmaps.size();
        }
        
        static class QRViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            
            QRViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.qrImageView);
            }
        }
    }
} 