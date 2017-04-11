package id.paniclabs.smartlockapitest.manager;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.IdToken;

import java.util.List;

/**
 * @author paniclabs.
 * @created on 4/12/17.
 * @email panic.inc.dev@gmail.com
 * @projectName SmartLockApiTest
 */
public class CredentialBuilder {
    private Uri image;
    private String name, password, id;
    private List<IdToken> tokens;

    public CredentialBuilder withTokens(List<IdToken> tokens) {
        this.tokens = tokens;
        return this;
    }

    public CredentialBuilder withId(@NonNull String id) {
        this.id = id;
        return this;
    }

    public CredentialBuilder withImage(Uri imageUri) {
        this.image = imageUri;
        return this;
    }

    public CredentialBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public CredentialBuilder withPassword(String password) {
        this.password = password;
        return this;
    }

    public Credential build() {
        if (id == null) {
            throw new IllegalStateException("Id cannot be null");
        }

        Credential.Builder builder = new Credential.Builder(id);
        if (name != null) {
            builder.setName(name);
        }
        if (password != null) {
            builder.setPassword(password);
        }
        if (image != null) {
            builder.setProfilePictureUri(image);
        }
        return builder.build();
    }
}
