package es.uma.mi.firma.signature;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import es.uma.mi.firma.R;
import com.google.zxing.client.android.Intents;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import de.tsenger.androsmex.data.CANSpecDO;
import de.tsenger.androsmex.data.CANSpecDOStore;


public class ScanActivity extends AppCompatActivity {
    private CANSpecDOStore _canStore = null;

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(),
            result -> {
                if(result.getContents() == null) {
                    Intent originalIntent = result.getOriginalIntent();
                    if (originalIntent == null) {
                        Log.d("ScanActivity", "Cancelled scan");
                        Toast.makeText(ScanActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                        finish();
                    } else if(originalIntent.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)) {
                        Log.d("ScanActivity", "Cancelled scan due to missing camera permission");
                        Toast.makeText(ScanActivity.this, "Cancelled due to missing camera permission", Toast.LENGTH_LONG).show();
                        finish();
                    }
                } else {
                    Log.d("ScanActivity", "Scanned");
                    if (result.getContents().length() != 40) {
                        Toast.makeText(ScanActivity.this, "Código inválido", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        // Toast.makeText(ScanActivity.this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();

                        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();

                        editor.putString(getString(R.string.api_token_key), result.getContents());
                        editor.commit();

                        if(!_canStore.getAll().isEmpty()){
                           _canStore.delete(_canStore.getAll().firstElement());
                        }

                        LayoutInflater factory = LayoutInflater.from(ScanActivity.this);
                        final View canEntryView = factory.inflate(R.layout.sample_can, null);
                        final AlertDialog ad = new AlertDialog.Builder(ScanActivity.this).create();
                        ad.setCancelable(true);
                        ad.setIcon(R.drawable.alert_dialog_icon);
                        ad.setView(canEntryView);
                        ad.setButton(AlertDialog.BUTTON_POSITIVE, "Aceptar", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                EditText text = (EditText) ad.findViewById(R.id.can_edit);
                                _canStore.save(new CANSpecDO(text.getText().toString(), "", ""));
                                finish();
                            }
                        });
                        ad.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancelar", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which){
                                ad.dismiss();
                                finish();
                            }
                        });
                        ad.setCanceledOnTouchOutside(true);
                        ad.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                ad.dismiss();
                                finish();
                            }
                        });
                        ad.show();
                    }
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);

        _canStore = new CANSpecDOStore(this);
        barcodeLauncher.launch(new ScanOptions());
    }

    public void scanBarcode(View view) {
    }
}