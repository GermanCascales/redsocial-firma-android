package es.uma.mi.firma;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import es.uma.mi.firma.datareader.SampleActivity_read_data;
import es.uma.mi.firma.signature.ApiInterface;
import es.uma.mi.firma.signature.Post;
import es.uma.mi.firma.signature.SampleActivity_gui;
import es.uma.mi.firma.signature.ScanActivity;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.util.Arrays;
import java.util.Vector;

import de.tsenger.androsmex.data.CANSpecDO;
import de.tsenger.androsmex.data.CANSpecDOStore;
import es.dniedroidfnmt.BuildConfig;
import es.gob.fnmt.dniedroid.net.http.CookiePolicyHandler;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.view.View.OnClickListener;

import androidx.core.text.HtmlCompat;

public class MainActivity extends Activity {
    public static final String APP_TAG = "mi_uma_firma";

    private CANSpecDOStore _canStore = null;
    public Post[] postArray = null;

    public static CookieManager _cookieManager = new CookieManager();
    public static CookiePolicyHandler _cookiePolicy = new CookiePolicyHandler();

    static{
        _cookieManager.setCookiePolicy(_cookiePolicy);
        CookieHandler.setDefault(_cookieManager);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sample_activity_main);
        Button read = (Button)findViewById(R.id.reader_button);
        Button scan = (Button)findViewById(R.id.scan_button);
        Button sign = (Button)findViewById(R.id.signature_button);

        _canStore = new CANSpecDOStore(this);

        String dnieFroidLibVersionName = BuildConfig.LIBRARY_PACKAGE_NAME + " v" +BuildConfig.VERSION+" vc"+BuildConfig.VERSION_REVISION;
        ((TextView)findViewById(R.id.infoVersion)).setText(dnieFroidLibVersionName);

        read.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(_canStore.getAll().isEmpty()){
                    warnNoCAN();
                }
                else {
                     showCanList(SampleActivity_read_data.class);
                }
            }
        });

        scan.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(_canStore.getAll().isEmpty()){
                    //warnNoCAN();
                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                    startActivity(intent);
                }
                else {
                    //showCanList(SampleActivity_age_verification.class);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("¿Estás seguro?")
                            .setMessage("Si vuelves a autenticarte, necesitarás escanear el QR y añadir el CAN de nuevo.")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(MainActivity.this, ScanActivity.class);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
            }
        });
        sign.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_canStore.getAll().isEmpty()) {
                    warnNoCAN();
                } else {
                    GetPosts();
                }
            }
        });
    }

    private void showPostList(final Class intentClass) {
        LayoutInflater factory = LayoutInflater.from(MainActivity.this);
        final View canListView = factory.inflate(R.layout.post_list, null);
        final AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(true);
        ad.setIcon(R.drawable.dnie_logo);
        ad.setView(canListView);
        ListView listW = (ListView) canListView.findViewById(R.id.canList);
        PostAdapter adapter = new PostAdapter(getApplicationContext(), listW);
        listW.setAdapter(adapter);
        listW.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Post item = (Post)parent.getItemAtPosition(position);
                Intent intent = new Intent(MainActivity.this, intentClass);
                intent.putExtra("post_id", item.getId());
                intent.putExtra("post_desc", item.getDescription());
                startActivity(intent);
                ad.dismiss();
            }
        });
        ad.show();
    }

    /**
     *
     * @param intentClass
     */
    private void showCanList(final Class intentClass){
        LayoutInflater factory = LayoutInflater.from(MainActivity.this);
        final View canListView = factory.inflate(R.layout.can_list, null);
        final AlertDialog ad = new AlertDialog.Builder(this).create();
        ad.setCancelable(true);
        ad.setIcon(R.drawable.dnie_logo);
        ad.setView(canListView);
        ListView listW = (ListView) canListView.findViewById(R.id.canList);
        SampleAdapter adapter = new SampleAdapter(getApplicationContext(), listW);
        listW.setAdapter(adapter);
        listW.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CANSpecDO item = (CANSpecDO)parent.getItemAtPosition(position);
                Intent intent = new Intent(MainActivity.this, intentClass);
                intent.putExtra("CAN", item.getCanNumber());
                startActivity(intent);
                ad.dismiss();
            }
        });
        ad.show();
    }

    /**
     *
     */
    private void warnNoCAN(){
        Toast warn = Toast.makeText(MainActivity.this, "Escanea el QR desde la web de mi.uma para identificarte en la aplicación", Toast.LENGTH_SHORT );
        warn.setGravity(Gravity.CENTER_VERTICAL,0,0);
        warn.show();
    }

    public void GetPosts() {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request newRequest = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer " + "DwiUYb9bSxBQc0tzUXI7pgtCXMnFRmDZ6ehYZxE1")
                        .build();
                return chain.proceed(newRequest);
            }
        }).build();

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl("http://192.168.5.220/api/")
                //.baseUrl("http://192.168.5.67/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiInterface apiService = retrofit.create(ApiInterface.class);

        Call<Post[]> call = apiService.getPosts();

        call.enqueue(new Callback<Post[]>() {
            @Override
            public void onResponse(Call<Post[]> call, retrofit2.Response<Post[]> response) {
                Log.d("ApiInterface", "onResponse: " + response.body());
                postArray = response.body();

                showPostList(SampleActivity_gui.class);
            }

            @Override
            public void onFailure(Call<Post[]> call, Throwable t) {
                Log.d("ApiInterface", "onFailure" + t.getMessage());
            }
        });
    }

    public class PostAdapter extends ArrayAdapter<Post> {
        private Vector<Post> items;
        private final LayoutInflater vi;
        private final ListView parentView;

        public PostAdapter(Context context, ListView parent) {
            super(context,0, postArray);
            this.items = new Vector<>(Arrays.asList(postArray));
            parentView = parent;
            vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            final Post post = items.get(position);
            if (post != null)
            {
                v = vi.inflate(R.layout.list_post_row, null);
                final TextView title = (TextView)v.findViewById(R.id.row_title);
                final TextView description = (TextView)v.findViewById(R.id.row_description);

                if (title != null && !post.getTitle().isEmpty()) {
                    title.setText(post.getTitle());
                }

                if (description != null && !post.getDescription().isEmpty()) {
                    description.setText(HtmlCompat.fromHtml(post.getDescription(), HtmlCompat.FROM_HTML_MODE_LEGACY).toString());
                }
            }
            return v;
        }
    }

    /**
     *
     */
    public class SampleAdapter extends ArrayAdapter<CANSpecDO> {
        private Vector<CANSpecDO> items;
        private final LayoutInflater vi;
        private final ListView parentView;

        public SampleAdapter(Context context, ListView parent) {
            super(context,0, _canStore.getAll());
            this.items = _canStore.getAll();
            parentView = parent;
            vi = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            final CANSpecDO ei = items.get(position);
            if (ei != null)
            {
                v = vi.inflate(R.layout.list_mrtd_row, null);
                final TextView title = (TextView)v.findViewById(R.id.row_title);
                final TextView name = (TextView)v.findViewById(R.id.row_description);
                final TextView nif = (TextView)v.findViewById(R.id.row_nif);

                if(title != null) {
                    title.setText(ei.getCanNumber());
                }
                if(name != null && !ei.getUserName().isEmpty() ) {
                    name.setText(ei.getUserName());
                }
                if(nif != null && !ei.getUserNif().isEmpty()) {
                    nif.setText("DNI " + ei.getUserNif());
                }

                Button deleteImageView = (Button)  v.findViewById(R.id.Btn_DESTROYENTRY);
                deleteImageView.setOnClickListener(new OnClickListener() {
                    public void onClick(View v) {
                        RelativeLayout vwParentRow = (RelativeLayout)v.getParent();
                        int position = parentView.getPositionForView(vwParentRow);
                        _canStore.delete(items.get(position));
                        SampleAdapter.this.remove(items.get(position));
                        items = _canStore.getAll();
                    }
                });
            }
            return v;
        }
    }
}