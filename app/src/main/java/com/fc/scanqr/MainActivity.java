package com.fc.scanqr;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@OptIn(markerClass = ExperimentalGetImage.class)
public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ?
            new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE } :
            new String[] { Manifest.permission.CAMERA };

    private PreviewView previewView;
    private View scanAreaOverlay;
    private EditText qrContentEditText;
    private View scanButton;
    private View makeButton;
    private View clearButton;
    private View copyButton;
    private View galleryButton;
    private View scanNotification;

    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner barcodeScanner;
    private ExecutorService cameraExecutor;
    private boolean isScanningEnabled = false;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        scanQRFromImage(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Force status bar color
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().getDecorView().setSystemUiVisibility(
            getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        );
        
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
        initializeCamera();
    }

    private void initializeViews() {
        previewView = findViewById(R.id.previewView);
        scanAreaOverlay = findViewById(R.id.scanAreaOverlay);
        qrContentEditText = findViewById(R.id.qrContentEditText);
        scanButton = findViewById(R.id.scanButtonContainer);
        makeButton = findViewById(R.id.makeButtonContainer);
        clearButton = findViewById(R.id.clearButtonContainer);
        copyButton = findViewById(R.id.copyButtonContainer);
        galleryButton = findViewById(R.id.galleryButton);
        scanNotification = findViewById(R.id.scanNotification);

        // Initialize barcode scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupListeners() {
        scanButton.setOnClickListener(v -> toggleScanning());
        makeButton.setOnClickListener(v -> generateQRCode());
        clearButton.setOnClickListener(v -> qrContentEditText.setText(""));
        copyButton.setOnClickListener(v -> copyToClipboard());
        galleryButton.setOnClickListener(v -> openGallery());

        qrContentEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (qrContentEditText.hasFocus()) {
                    stopScanning();
                }
            }
        });
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, getString(R.string.error_scanning_qr), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleScanning() {
        if (!isScanningEnabled) {
            if (checkPermissions()) {
                startScanning();
            } else {
                requestPermissions();
            }
        } else {
            stopScanning();
        }
    }

    private boolean checkPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (!permission.isEmpty() &&
                    ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startScanning();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                startScanning();
            } else {
                showPermissionRationale();
            }
        }
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.permission_camera_rationale)
                .setPositiveButton(R.string.ok, (dialog, which) -> requestPermissions())
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    private void startScanning() {
        if (cameraProvider == null) return;

        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            isScanningEnabled = true;
            previewView.setVisibility(View.VISIBLE);
            scanAreaOverlay.setVisibility(View.GONE);
            scanNotification.setVisibility(View.GONE);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.error_scanning_qr), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopScanning() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        isScanningEnabled = false;
        previewView.setVisibility(View.GONE);
        scanAreaOverlay.setVisibility(View.VISIBLE);
        scanNotification.setVisibility(View.VISIBLE);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        try {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            if (barcode.getRawValue() != null) {
                                runOnUiThread(() -> {
                                    String currentText = qrContentEditText.getText().toString();
                                    String newText = currentText + barcode.getRawValue();
                                    qrContentEditText.setText(newText);
                                });
                                stopScanning();
                                break;
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } catch (Exception e) {
            imageProxy.close();
        }
    }

    private void generateQRCode() {
        String content = qrContentEditText.getText().toString();
        if (content.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_creating_qr), Toast.LENGTH_SHORT).show();
            return;
        }

        List<Bitmap> qrBitmaps = new ArrayList<>();

        try {
            // Create encoding hints for UTF-8
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            // Split content into chunks if it exceeds 400 bytes
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            if (contentBytes.length > 400) {
                List<String> chunks = splitContent(content);
                for (String chunk : chunks) {
                    BitMatrix bitMatrix = new MultiFormatWriter().encode(
                            chunk,
                            BarcodeFormat.QR_CODE,
                            461,
                            461,
                            hints
                    );
                    qrBitmaps.add(createBitmapFromBitMatrix(bitMatrix));
                }
            } else {
                BitMatrix bitMatrix = new MultiFormatWriter().encode(
                        content,
                        BarcodeFormat.QR_CODE,
                        461,
                        461,
                        hints
                );
                qrBitmaps.add(createBitmapFromBitMatrix(bitMatrix));
            }

            if (!qrBitmaps.isEmpty()) {
                showQRDialog(qrBitmaps);
            } else {
                Toast.makeText(this, getString(R.string.error_creating_qr), Toast.LENGTH_SHORT).show();
            }
        } catch (WriterException e) {
            Toast.makeText(this, getString(R.string.error_creating_qr), Toast.LENGTH_SHORT).show();
        }
    }

    private List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        int maxBytes = 400;  // Maximum bytes per QR code
        
        int startIndex = 0;
        while (startIndex < content.length()) {
            int endIndex = startIndex;
            int currentChunkBytes = 0;
            
            // Try to add characters until we hit the byte limit
            while (endIndex < content.length()) {
                String nextChar = content.substring(endIndex, Math.min(endIndex + 1, content.length()));
                int nextCharBytes = nextChar.getBytes(StandardCharsets.UTF_8).length;
                
                // If adding next character would exceed the limit, break
                if (currentChunkBytes + nextCharBytes > maxBytes) {
                    break;
                }
                
                currentChunkBytes += nextCharBytes;
                endIndex++;
            }
            
            // If we couldn't add even one character (shouldn't happen with 400 byte limit)
            if (endIndex == startIndex) {
                endIndex = startIndex + 1;  // Force include at least one character
            }
            
            // Add the chunk
            chunks.add(content.substring(startIndex, endIndex));
            startIndex = endIndex;
        }
        
        return chunks;
    }

    private Bitmap createBitmapFromBitMatrix(BitMatrix bitMatrix) {
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];
        
        // Convert bit matrix to pixel array
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        
        // Create the bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        
        return bitmap;
    }

    private void showQRDialog(List<Bitmap> qrBitmaps) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_display, null);
        
        ViewPager2 viewPager = dialogView.findViewById(R.id.qrViewPager);
        TextView pageIndicator = dialogView.findViewById(R.id.pageIndicator);
        
        viewPager.setOffscreenPageLimit(1);
        viewPager.setUserInputEnabled(true);
        
        QRPagerAdapter adapter = new QRPagerAdapter(qrBitmaps);
        viewPager.setAdapter(adapter);
        
        if (qrBitmaps.size() > 1) {
            pageIndicator.setVisibility(View.VISIBLE);
            pageIndicator.setText(String.format("1/%d", qrBitmaps.size()));
            
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    pageIndicator.setText(String.format("%d/%d", position + 1, qrBitmaps.size()));
                }
            });
        } else {
            pageIndicator.setVisibility(View.GONE);
        }
        
        builder.setView(dialogView)
               .setPositiveButton(android.R.string.ok, null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Set the button text color based on the current theme
        int textColor;
        int nightModeFlags = getResources().getConfiguration().uiMode & 
                             android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            // Night mode is active, use white text
            textColor = 0xFFFFFFFF;
        } else {
            // Day mode is active, use black text
            textColor = 0xFF000000;
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor);
    }

    private void saveQRCodes(List<Bitmap> qrBitmaps) {
        int index = 0;
        for (Bitmap bitmap : qrBitmaps) {
            String fileName = "QR_" + System.currentTimeMillis() + "_" + index + ".png";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(imageUri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Toast.makeText(this, getString(R.string.qr_saved), Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, getString(R.string.error_saving_qr), Toast.LENGTH_SHORT).show();
                }
            }
            index++;
        }
    }

    private void copyToClipboard() {
        String content = qrContentEditText.getText().toString();
        if (!content.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("QR Content", content);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void scanQRFromImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            Barcode barcode = barcodes.get(0);
                            if (barcode.getRawValue() != null) {
                                String currentText = qrContentEditText.getText().toString();
                                String newText = currentText + barcode.getRawValue();
                                qrContentEditText.setText(newText);
                            }
                        } else {
                            Toast.makeText(this, getString(R.string.no_qr_detected), Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.error_scanning_qr), Toast.LENGTH_SHORT).show());
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.error_scanning_qr), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}