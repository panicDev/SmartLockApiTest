package id.paniclabs.smartlockapitest;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.auth.api.credentials.Credential;

import id.paniclabs.smartlockapitest.manager.CredentialBuilder;
import id.paniclabs.smartlockapitest.manager.SmartLockManager;

public class MainActivity extends AppCompatActivity implements
        SmartLockManager.ProgressListener {

    static final String TAG = "MainActivity";
    String username = "1234";
    String password = "1234";
    EditText usernameEditText, passwordEditText;
    Button signInButton;
    private RelativeLayout mRootLayout;
    private SmartLockManager smartLockManager;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        smartLockManager = SmartLockManager.GetInstance();
        smartLockManager.build(this, savedInstanceState);
        smartLockManager.setProgressListener(this);

        mRootLayout = (RelativeLayout) findViewById(R.id.root_layout);
        usernameEditText = (EditText) findViewById(R.id.usernameEditText);
        passwordEditText = (EditText) findViewById(R.id.passwordEditText);
        signInButton = (Button) findViewById(R.id.signInButton);

        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username1 = usernameEditText.getText().toString();
                String password1 = passwordEditText.getText().toString();
                if (!(username.equals(username1) && password.equals(password1))) {
                    Toast.makeText(MainActivity.this, "Username dan Password salah, Coba lagi", Toast.LENGTH_SHORT).show();
                    return;
                }
                CredentialBuilder credentialBuilder = new CredentialBuilder();
                credentialBuilder = credentialBuilder.withId(username1);
                credentialBuilder = credentialBuilder.withPassword(password1);

                smartLockManager.saveCredentials(credentialBuilder, new SmartLockManager.SaveListener() {
                    @Override
                    public void signInRequired() {
                        showMessage("SignIn Required");
                    }

                    @Override
                    public void credentialsSaved(Credential credential) {
                        saveCredential(credential);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        showMessage(errorMessage);
                    }

                });
            }
        });
    }

    protected void saveCredential(Credential credential) {
        // Credential is valid so save it.
        Log.w(TAG, "Credential saved");
        Toast.makeText(MainActivity.this, "Credential saved", Toast.LENGTH_SHORT).show();
        goToContent();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!smartLockManager.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
        Log.w(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" + data);
    }

    /**
     * Start the Content Activity and finish this one.
     */
    protected void goToContent() {
        startActivity(new Intent(this, ContentActivity.class));
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        smartLockManager.loadCredentials(new SmartLockManager.LoadListener() {
            @Override
            public void credentialsLoaded(Credential credential) {
                saveCredential(credential);
            }

            @Override
            public void onError(@NonNull String errorMessage) {
                showMessage(errorMessage);
            }

            @Override
            public void signInRequired() {
                showMessage("Please, introduce your credentials and then click the sign in button");
            }

        }, false);

    }

    @Override
    public void showProgress() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setIndeterminate(true);
            progressDialog.setMessage("Loading...");
        }
        progressDialog.show();
    }

    @Override
    public void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void showMessage(String message) {
        Snackbar.make(mRootLayout, message, Snackbar.LENGTH_SHORT).show();
    }
}
