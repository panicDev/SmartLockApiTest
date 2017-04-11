package id.paniclabs.smartlockapitest;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import id.paniclabs.smartlockapitest.manager.SmartLockManager;

public class ContentActivity extends AppCompatActivity implements SmartLockManager.ProgressListener {

    private SmartLockManager smartLockManager;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_activty);
        smartLockManager = SmartLockManager.GetInstance();
        smartLockManager.build(this, savedInstanceState);
        smartLockManager.setProgressListener(this);

        Button removeCredentialsButton = (Button)findViewById(R.id.remove_smartlock);
        removeCredentialsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                smartLockManager.deleteCredentials(new SmartLockManager.DeletionListener() {
                    @Override
                    public void credentialsDeleted() {
                        showMessage("Credentials Deleted");
                        clearCredentialUI();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        showMessage(errorMessage);
                    }
                });
            }
        });
    }

    private void clearCredentialUI() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
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
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
