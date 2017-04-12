package id.paniclabs.smartlockapitest.manager;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.CredentialRequest;
import com.google.android.gms.auth.api.credentials.CredentialRequestResult;
import com.google.android.gms.auth.api.credentials.IdentityProviders;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

/**
 * @author paniclabs.
 * @created on 4/12/17.
 * @email panic.inc.dev@gmail.com
 * @projectName SmartLockApiTest
 */
public class SmartLockManager implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final int RC_SIGN_IN = 1;
    private static final int RC_CREDENTIALS_READ = 2;
    private static final int RC_CREDENTIALS_SAVE = 3;

    private static final String TAG = SmartLockManager.class.getSimpleName();
    private static final String KEY_IS_RESOLVING = "is_resolving";
    private static final String KEY_CREDENTIAL = "key_credential";
    private static final String KEY_CREDENTIAL_TO_SAVE = "key_credential_to_save";

    private Credential credentialToSave;
    private GoogleApiClient client;
    private FragmentActivity activity;
    private Credential credential;

    private boolean isResolving = false;
    private boolean saveNextTimeClientConnect;
    private ProgressListener progressListener;
    private SaveListener tempSaveListener;
    private LoadListener tempLoadListener;
    private DeletionListener tempDeleteListener;

    public void build(FragmentActivity fragmentActivity) {
        build(fragmentActivity, null, null);
    }

    public void build(FragmentActivity activity, BuildClientListener listener) {
        this.activity = activity;
        buildGoogleApiClient(null, listener);
    }

    public void build(FragmentActivity activity, Bundle savedData, BuildClientListener listener) {
        build(activity, listener);
        checkSavedData(savedData);
    }

    public void build(FragmentActivity activity, Bundle savedData) {
        build(activity, null, null);
        checkSavedData(savedData);
    }

    private void checkSavedData(Bundle savedInstance) {
        if (savedInstance != null) {
            isResolving = savedInstance.getBoolean(KEY_IS_RESOLVING, false);
            credential = savedInstance.getParcelable(KEY_CREDENTIAL);
            credentialToSave = savedInstance.getParcelable(KEY_CREDENTIAL_TO_SAVE);
        }
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    private void buildGoogleApiClient(String accountName, BuildClientListener listener) {
        Log.d(TAG, "Building google api client with google account:" + (accountName != null ? accountName : "null"));
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail();

        if (accountName != null) {
            builder.setAccountName(accountName);
        }

        if (client != null) {
            client.stopAutoManage(activity);
        }

        GoogleApiClient.Builder clientBuilder = new GoogleApiClient.Builder(activity)
                .addConnectionCallbacks(this)
                .enableAutoManage(activity, this)
                .addApi(Auth.CREDENTIALS_API)
                .addApi(Auth.GOOGLE_SIGN_IN_API, builder.build());

        client = clientBuilder.build();

        Log.d(TAG, "Google API client built");
        if (listener != null) {
            listener.clientBuilt();
        }
    }

    public void saveWithGoogleAccount(SaveWithGoogleSignInListener listener) {
        if (!client.isConnected()) {
            clearCredentialData();
            saveNextTimeClientConnect = true;
            tempSaveListener = listener;
            client.connect();
        } else {
            Log.d(TAG, "Client disconnected, trying to save credentials from Google account after reconnecting..");
            tempSaveListener = listener;
            Intent intent = Auth.GoogleSignInApi.getSignInIntent(client);
            activity.startActivityForResult(intent, RC_SIGN_IN);
        }
    }

    private void clearCredentialData() {
        credentialToSave = null;
        credential = null;
        tempDeleteListener = null;
        tempSaveListener = null;
        tempLoadListener = null;
    }

    public void loadCredentials(LoadListener listener, boolean onlyPasswords) {
        if (!isResolving) {
            tempLoadListener = listener;
            requestCredentials(onlyPasswords, true, listener);
        }
    }

    public void loadCredentials(LoadListener listener) {
        loadCredentials(listener, true);
    }

    private void requestCredentials(boolean onlyPasswords, final boolean shouldResolve, final LoadListener listener) {
        CredentialRequest.Builder crBuilder = new CredentialRequest.Builder()
                .setPasswordLoginSupported(true);
        Log.d(TAG, "Loading credentials");

        if (!onlyPasswords) {
            crBuilder.setAccountTypes(IdentityProviders.GOOGLE);
        }

        showProgress();
        Auth.CredentialsApi.request(client, crBuilder.build()).setResultCallback(
                new ResultCallback<CredentialRequestResult>() {
                    @Override
                    public void onResult(@NonNull CredentialRequestResult credentialRequestResult) {
                        hideProgress();
                        handleCredentialRequestStatus(credentialRequestResult);
                    }

                    private void handleCredentialRequestStatus(CredentialRequestResult credentialRequestResult) {
                        Status status = credentialRequestResult.getStatus();

                        if (status.isSuccess()) {
                            setCredential(credentialRequestResult.getCredential());
                            // Auto sign-in success
                            Log.d(TAG, "Credentials saved. Info:" + credential.getAccountType() + ":" + credential.getId());
                            if (listener != null) {
                                listener.credentialsLoaded(credential);
                            }
                            signInWithGoogleIfPossible(credential.getAccountType(), credential.getId());
                        } else if (status.getStatusCode() == CommonStatusCodes.RESOLUTION_REQUIRED
                                && shouldResolve) {
                            // Getting credential needs to show some UI, loadCredentials resolution
                            Log.w(TAG, "Credential load request need some resolution, showing UI..");
                            resolveResult(status, RC_CREDENTIALS_READ);
                        } else if (status.getStatusCode() == CommonStatusCodes.SIGN_IN_REQUIRED) {
                            Log.w(TAG, "Credential load request failed, it needs user to be signed in");
                            clearCredentialData();
                            if (listener != null) {
                                listener.signInRequired();
                            }
                        } else {
                            String errorMessage = "There was an error loading your credentials";
                            Log.w(TAG, errorMessage);
                            if (listener != null) {
                                listener.onError(errorMessage);
                            }
                        }

                    }
                });
    }

    private void hideProgress() {
        if (progressListener != null) {
            progressListener.hideProgress();
        }
    }

    private void showProgress() {
        if (progressListener != null) {
            progressListener.showProgress();
        }
    }

    private void signInWithGoogleIfPossible(String accountType, String id) {
        if (IdentityProviders.GOOGLE.equals(accountType)) {
            // Google account, rebuild GoogleApiClient to set account name and then try
            buildGoogleApiClient(id, null);
            googleSilentSignIn();
        } else {
            // Email/password account
            String message = String.format("Signed in as %s", id);
            if (progressListener != null) {
                progressListener.showMessage(message);
            }
        }
    }

    private void setCredential(Credential credential) {
        this.credential = credential;
    }

    private void resolveResult(Status status, int requestCode) {
        if (!isResolving) {
            try {
                status.startResolutionForResult(activity, requestCode);
                isResolving = true;
            } catch (IntentSender.SendIntentException e) {
                Log.w(TAG, "Failed to send Credentials intent.", e);
                isResolving = false;
            }
        }
    }

    private void googleSilentSignIn() {
        // Try silent sign-in with Google Sign In API
        OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(client);
        if (opr.isDone()) {
            GoogleSignInResult gsr = opr.get();
            handleGoogleSignIn(gsr);
        } else {
            showProgress();
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(@NonNull GoogleSignInResult googleSignInResult) {
                    hideProgress();
                    handleGoogleSignIn(googleSignInResult);
                }
            });
        }
    }

    private void handleGoogleSignIn(GoogleSignInResult gsr) {
        Log.d(TAG, "handleGoogleSignIn:" + (gsr == null ? "null" : gsr.getStatus()));

        boolean isSignedIn = (gsr != null) && gsr.isSuccess();
        if (isSignedIn) {
            // Display signed-in UI
            GoogleSignInAccount gsa = gsr.getSignInAccount();
            String status;
            if (gsa != null) {
                status = String.format("Signed in as %s (%s)", gsa.getDisplayName(),
                        gsa.getEmail());
            } else {
                status = "There was an error trying to sign in with Google";
            }
            if (progressListener != null) {
                progressListener.showMessage(status);
            }
        } else {
            // Display signed-out UI
            if (progressListener != null) {
                progressListener.showMessage("Signed out");
            }
        }
    }

    public void saveCredentials(CredentialBuilder credentialBuilder, SaveListener listener) {
        saveCredentials(credentialBuilder.build(), listener);
    }

    private void saveCredentials(final Credential credential, final SaveListener listener) {
        if (credential == null) {
            return;
        }
        credentialToSave = credential;

        // Save Credential if the GoogleApiClient is connected, otherwise the
        // Credential is cached and will be saved when onConnected is next called.
        if (!client.isConnected()) {
            Log.d(TAG, "Client disconnected, trying to save credentials after connecting..");
            saveNextTimeClientConnect = true;
            tempSaveListener = listener;
            client.connect();
        } else {
            Auth.CredentialsApi.save(client, credentialToSave).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            if (status.isSuccess()) {
                                handleCredentialSaved(status.isSuccess());
                                Log.d(TAG, "Credentials successfully saved");
                                if (listener != null) {
                                    listener.credentialsSaved(credential);
                                }
                            } else {
                                if (status.hasResolution()) {
                                    // Try to resolve the save request. This will prompt the user if
                                    // the credential is new.
                                    try {
                                        Log.d(TAG, "Trying to resolve save request");
                                        tempSaveListener = listener;
                                        status.startResolutionForResult(activity, RC_CREDENTIALS_SAVE);
                                    } catch (IntentSender.SendIntentException e) {
                                        // Could not resolve the request
                                        Log.w(TAG, "Credentials save failed::" + status);
                                        credentialToSave = null;
                                        if (listener != null) {
                                            listener.onError("There was an error saving your credentials: " + status.getStatusMessage());
                                        }
                                    }
                                } else {
                                    Log.w(TAG, "save:FAILURE:" + status);
                                    credentialToSave = null;
                                    if (listener != null) {
                                        listener.onError("There was an error saving your credentials: " + status.getStatusMessage());
                                    }
                                }
                            }
                        }
                    });
        }
    }

    private void handleCredentialSaved(boolean statusSuccess) {
        Log.d(TAG, "save:SUCCESS:" + statusSuccess);
        setCredential(credentialToSave);
        credentialToSave = null;
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult gsr = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleGoogleSignIn(gsr);

            // Save Google Sign credentials into SmartLock
            GoogleSignInAccount signInAccount = gsr.getSignInAccount();
            if (signInAccount != null) {
                CredentialBuilder credentialBuilder = new CredentialBuilder()
                        .withId(signInAccount.getEmail())
                        .withImage(signInAccount.getPhotoUrl());
                saveCredentials(credentialBuilder, tempSaveListener);
            }

            return true;
        } else if (requestCode == RC_CREDENTIALS_READ) {
            isResolving = false;
            if (resultCode == Activity.RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                setCredential(credential);
                if (tempLoadListener != null) {
                    tempLoadListener.credentialsLoaded(credential);
                    tempLoadListener = null;
                }
                signInWithGoogleIfPossible(credential.getAccountType(), credential.getId());
            }
            return true;
        } else if (requestCode == RC_CREDENTIALS_SAVE) {
            handleCredentialSaved(true);
            isResolving = false;
            if (resultCode == Activity.RESULT_OK) {
                if (tempSaveListener != null) {
                    tempSaveListener.credentialsSaved(credential);
                    tempSaveListener = null;
                }
            } else {
                Log.w(TAG, "Credential save failed.");
            }
            return true;
        }
        return false;
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (saveNextTimeClientConnect) {
            saveNextTimeClientConnect = false;
            if (credentialToSave != null) {
                saveCredentials(credentialToSave, tempSaveListener);
            }
        } else if (tempDeleteListener != null) {
            deleteCredentials(tempDeleteListener);
            tempDeleteListener = null;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (saveNextTimeClientConnect) {
            saveNextTimeClientConnect = false;
            if (credentialToSave != null) {
                credentialToSave = null;
            }
        }
    }

    public void deleteCredentials(final DeletionListener listener) {
        if (credential != null) {
            showProgress();
            if (!client.isConnected()) {
                tempDeleteListener = listener;
                client.connect();
                Log.d(TAG, "Client disconnected, trying to delete credentials after connecting..");
                return;
            }
            Auth.CredentialsApi.delete(client, credential).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    hideProgress();
                    if (status.isSuccess()) {
                        // Credential was deleted successfully
                        clearCredentialData();
                        Log.d(TAG, "Credentials successfully deleted");
                        if (listener != null) {
                            listener.credentialsDeleted();
                        }
                    }
                }
            });
        } else {
            if (listener != null) {
                String errorMessage = "Cannot delete credentials because there wasn't loaded previously";
                Log.w(TAG, errorMessage);
                listener.onError(errorMessage);
            }
        }
    }

    public Credential getCredential() {
        return credential;
    }

    public GoogleApiClient getClient() {
        return client;
    }

    //region Listeners

    public interface BuildClientListener {
        void clientBuilt();
    }

    public interface ProgressListener {
        void showProgress();

        void hideProgress();

        void showMessage(String message);
    }

    public interface DeletionListener {
        void credentialsDeleted();

        void onError(String errorMessage);
    }

    public interface SaveListener extends GoogleSignInListener {
        void credentialsSaved(Credential credential);

        void onError(String errorMessage);
    }

    public interface SaveWithGoogleSignInListener extends SaveListener {
    }

    public interface LoadListener extends GoogleSignInListener {
        void credentialsLoaded(Credential credential);

        void onError(@NonNull String errorMessage);
    }

    public interface GoogleSignInListener {
        void signInRequired();
    }

    public void GotoSmartLockSettings(Activity activity){
//        Intent intent = new Intent(activity, com.google.android.gms.auth.api.credentials.ui.CredentialsSettingsActivity);

    }

    //endregion

    //region Singleton pattern

    private static class InstanceHolder {
        private static SmartLockManager instance = new SmartLockManager();
    }

    private SmartLockManager() {
    }

    public static SmartLockManager GetInstance() {
        return InstanceHolder.instance;
    }

    //endregion
}
