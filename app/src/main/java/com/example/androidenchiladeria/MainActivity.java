package com.example.androidenchiladeria;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.example.androidenchiladeria.Remote.ICloudFunctions;
import com.example.androidenchiladeria.Remote.RetrofitCloudClient;
import com.facebook.accountkit.AccessToken;
import com.facebook.accountkit.AccountKit;
import com.facebook.accountkit.AccountKitLoginResult;
import com.facebook.accountkit.ui.AccountKitActivity;
import com.facebook.accountkit.ui.AccountKitConfiguration;
import com.facebook.accountkit.ui.LoginType;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import dmax.dialog.SpotsDialog;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private static int APP_REQUEST_CODE = 7171; //Número de llave
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener listener;
    private AlertDialog dialog;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();
    private ICloudFunctions cloudFunctions;

    @Override
    protected void onStart() {
        super.onStart();
        firebaseAuth.addAuthStateListener(listener);
    }

    @Override
    protected void onStop() {
        if(listener!=null)
            firebaseAuth.removeAuthStateListener(listener);
        compositeDisposable.clear();
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
    }

    private void init() {
        firebaseAuth = FirebaseAuth.getInstance();
        dialog = new SpotsDialog.Builder().setCancelable(false).setContext(this).build();
        cloudFunctions = RetrofitCloudClient.getInstance().create(ICloudFunctions.class);
        listener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null)
                {
                    //Ya está logueado
                    Toast.makeText(MainActivity.this,"Already logged",Toast.LENGTH_SHORT).show();
                }
                else
                {
                    //Nuevo logueo
                    AccessToken accessToken = AccountKit.getCurrentAccessToken();
                    if(accessToken != null)
                    {
                        getCustomToken(accessToken);
                    }
                    else
                    {
                        phoneLogin();
                    }
                }
            }
        };
    }

    private void phoneLogin() {
        Intent intent = new Intent(MainActivity.this, AccountKitActivity.class);
        AccountKitConfiguration.AccountKitConfigurationBuilder configurationBuilder =
                new AccountKitConfiguration.AccountKitConfigurationBuilder(LoginType.PHONE,
                        AccountKitActivity.ResponseType.TOKEN);
        intent.putExtra(AccountKitActivity.ACCOUNT_KIT_ACTIVITY_CONFIGURATION,configurationBuilder.build());
        startActivityForResult(intent,APP_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == APP_REQUEST_CODE)
            handleFacebooLoginResult(resultCode,data);
    }

    private void handleFacebooLoginResult(int resultCode, Intent data) {
        AccountKitLoginResult result = data.getParcelableExtra(AccountKitLoginResult.RESULT_KEY);
        if(result.getError() != null)
        {
            Toast.makeText(this,result.getError().getUserFacingMessage(), Toast.LENGTH_SHORT).show();
        }
        else if(result.wasCancelled() || resultCode == RESULT_CANCELED)
        {
            finish();
        }
        else
        {
            if(result.getAccessToken() != null)
            {
                getCustomToken(result.getAccessToken());
                Toast.makeText(this,"Sign in ok", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void getCustomToken(AccessToken accessToken) {
        dialog.show();
        compositeDisposable.add(cloudFunctions.getCustomToken(accessToken.getToken())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(responseBody -> {
            String customToken = responseBody.string();
            signInWithCustomToken(customToken);
        }, throwable -> {
            dialog.dismiss();
            Toast.makeText(this,""+throwable.getMessage(),Toast.LENGTH_SHORT).show();
        }));
    }

    private void signInWithCustomToken(String customToken) {
        dialog.dismiss();
        firebaseAuth.signInWithCustomToken(customToken)
                .addOnCompleteListener(task -> {
                   if(!task.isSuccessful())
                       Toast.makeText(this,"Authentication failed",Toast.LENGTH_SHORT).show();
                });
    }
}
