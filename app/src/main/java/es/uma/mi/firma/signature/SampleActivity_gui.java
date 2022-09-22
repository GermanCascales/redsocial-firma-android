/*
 * "Ejemplo DNIe de uso de API", desarrollada por CNP-FNMT.
 *
 * La aplicación implementa un escenario de ejemplo de uso de la API para la interacción
 * con el DNIe 3.0. y realizar así firmas digitales.
 *
 * Copyright (C) 2019. Cuerpo Nacional de Policía - Fábrica Nacional de Moneda y Timbre.
 *
 * Esta aplicación puede ser redistribuida y/o modificada bajo los términos de la
 * Lesser GNU General Public License publicada por la Free Software Foundation,
 * tanto en la versión 3 de la Licencia, o en una versión posterior.
 *
 * Este programa es distribuido con la esperanza de que sea útil, pero
 * SIN NINGUNA GARANTÍA; incluso sin la garantía implícita de comercialización
 * o idoneidad para un propósito particular. Para más detalles vea GNU General Public
 * License.
 *
 * Debería recibir una copia de la GNU Lesser General Public License, si aplica, junto
 * con este programa. Si no, consúltelo en <http://www.gnu.org/licenses/>.
 */

package es.uma.mi.firma.signature;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import es.uma.mi.firma.R;
import es.uma.mi.firma.utils.Common;
import es.uma.mi.firma.utils.pki.Tool;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.tsenger.androsmex.data.CANSpecDO;
import de.tsenger.androsmex.data.CANSpecDOStore;
import es.gob.fnmt.dniedroid.gui.PasswordUI;
import es.gob.fnmt.dniedroid.help.Loader;
import es.gob.jmulticard.jse.provider.DnieProvider;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.view.View.OnClickListener;
import static android.view.View.VISIBLE;

/**
 * Ejemplo de firma de datos con el DNIe v3.0 reutilizando la interfaz gráfica de la librería.
 */
public class SampleActivity_gui extends AppCompatActivity implements NfcAdapter.ReaderCallback {
    private TextView _baseInfo = null;
    private TextView _resultInfo = null;
    private ImageView _ui_dnie = null;
    private Animation _ui_dnieanimation = null;
    private ExecutorService _executor;
    private String _can = null;
    private int _post_id;
    private String _post_desc = null;
    private String _api_token_key = null;
    private CANSpecDOStore _canStore = null;
    private Handler _handler;
    private byte[] _signature;

    private OkHttpClient _okHttpClient;
    private Retrofit _retrofit;
    private ApiInterface _apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        _executor = Executors.newSingleThreadExecutor();
        _handler = new Handler(Looper.getMainLooper());

        setContentView(R.layout.sample_activity);

        _canStore = new CANSpecDOStore(this);

        _post_id = getIntent().getIntExtra("post_id", 0);
        _post_desc = getIntent().getStringExtra("post_desc");
        _can = _canStore.getAll().firstElement().getCanNumber();

        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        _api_token_key = sharedPref.getString(getString(R.string.api_token_key), "null");

        _baseInfo = this.findViewById(R.id.base_info);
        _resultInfo = this.findViewById(R.id.result_info);

        PasswordUI.setAppContext(this);
        PasswordUI.setPasswordDialog(null);  //Diálogo de petición de contraseña por defecto

        Button back = findViewById(R.id.back2main);
        back.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        _ui_dnie = findViewById(R.id.dnieImg);
        _ui_dnieanimation = AnimationUtils.loadAnimation(this, R.anim.dnie30_grey);

        _okHttpClient = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request newRequest = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + _api_token_key)
                        .build();
                return chain.proceed(newRequest);
            }
        }).build();

        _retrofit = new Retrofit.Builder()
                .client(_okHttpClient)
                .baseUrl("http://192.168.5.220/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();


        _apiService = _retrofit.create(ApiInterface.class);

        Common.EnableReaderMode(this);
        getRead();
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        reading();

        try {
            //Clase 'Initializer' que nos devuelve directamente el keystore
            Loader.InitInfo initInfo= Loader.init(new String[]{_can}, tag);
            Tool.SignatureCertificateBean certificateBean = Tool.selectCertificate(this, initInfo.getKeyStore());
            if(certificateBean == null) {
                Common.showDialog(SampleActivity_gui.this,"Error obteniendo certificado","No se han encontrado certificados en la tarjeta.");
                getRead();
            }
            else {
                //Clase 'Initializer' que nos permite actualizar la BBDD de CAN de la App
                CANSpecDO canSpecDO;
                if(initInfo.getKeyStoreType().equalsIgnoreCase(DnieProvider.KEYSTORE_TYPE_AVAILABLE.get(1))) {
                    canSpecDO = new CANSpecDO(_can, Tool.getCN(certificateBean.getCertificate()), Tool.getNIF(certificateBean.getCertificate()));
                }else{
                    canSpecDO = new CANSpecDO(_can, Tool.getCN(certificateBean.getCertificate()), "");
                }
                Loader.saveCan2DB(canSpecDO, this);
                final PrivateKey privateKey = (PrivateKey) initInfo.getKeyStore().getKey(certificateBean.getAlias(), null);
                _executor.execute(() -> {
                    final String result = doInBackground(privateKey);
                    _handler.post(() -> {
                        //UI Thread work here
                        updateInfo(result == null ? "Firma realizada." : "Error en proceso de firma.",
                                result == null ? Base64.encodeToString(_signature, Base64.DEFAULT) : result);
                        try {
                            Log.d("TAG", "onTagDiscovered: " + Tool.getNIF(certificateBean.getCertificate()));
                            Log.d("TAG", "onTagDiscovered: " + Tool.getCN(certificateBean.getCertificate()));

                            SignRequest signRequest = new SignRequest(
                                    Tool.getNIF(certificateBean.getCertificate()),
                                    Tool.getCN(certificateBean.getCertificate()),
                                    Base64.encodeToString(_signature, Base64.DEFAULT));
                            Call<ResponseBody> call = _apiService.signPost(_post_id, signRequest);

                            call.enqueue(new Callback<ResponseBody>() {
                                @Override
                                public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                                    Log.d("ApiInterface", "onResponse: " + response.body());
                                }

                                @Override
                                public void onFailure(Call<ResponseBody> call, Throwable t) {
                                    Log.d("ApiInterface", "onFailure" + t.getMessage());
                                }
                            });
                        } catch (CertificateEncodingException e) {
                            e.printStackTrace();
                        }
                    });
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            _handler.post(new Runnable() {
                @Override
                public void run() {
                    Common.showDialog(SampleActivity_gui.this,"Error leyendo el DNIe",e.getMessage());
                }
            });
            getRead();
        }
    }

    /**
     *
     * @param privateKey
     * @return
     */
    private String doInBackground(PrivateKey privateKey){
        try{
            _signature = Common.getSignature(privateKey, _post_desc);
        }
        catch (Exception e){
            return e.getMessage();
        }
        return null;
    }

    /**
     *
     */
    private void getRead(){
        _handler.post(new Runnable() {
            @Override
            public void run() {
                updateInfo("Aproxime el DNIe al dispositivo", null);
                _ui_dnie.setImageResource(R.drawable.dni30_grey_peq);
                _ui_dnie.setVisibility(View.VISIBLE);
                _ui_dnie.startAnimation(_ui_dnieanimation);
            }
        });
    }

    /**
     *
     */
    private void reading(){
        _handler.post(new Runnable() {
            @Override
            public void run() {
                updateInfo(getString(R.string.lib_process_title), getString(R.string.lib_process_msg_read));
                _ui_dnie.clearAnimation();
                _ui_dnie.setImageResource(R.drawable.dni30_peq);
                _ui_dnie.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     *
     * @param info
     * @param extra
     */
    public void updateInfo(final String info, final String extra){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(info!=null){
                    _baseInfo.setText(info);
                }
                if(extra!=null){
                    _resultInfo.setVisibility(VISIBLE);
                    _resultInfo.setText(extra);
                }else
                    _resultInfo.setVisibility(View.GONE);
            }
        });
    }
}