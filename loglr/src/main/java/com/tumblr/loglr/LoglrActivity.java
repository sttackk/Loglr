package com.tumblr.loglr;

import android.app.ProgressDialog;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.tumblr.loglr.Exceptions.TumblrBundleException;
import com.tumblr.loglr.Exceptions.TumblrLoginException;

import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;

/**
 * Created by wits123 on 31/12/15.
 */
public class LoglrActivity extends AppCompatActivity {

    /**
     * A tag for logging
     */
    private static final String TAG = LoglrActivity.class.getSimpleName();

    /**
     * Tumblr Consumer and Secret keys on which basis the user is logged in
     */
    private static String TUMBLR_CONSUMER_KEY = "";
    private static String TUMBLR_SECRET_KEY = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tumblr_login);
        //Test if Bundle was transferred to the Activity
        if(getIntent().getExtras() != null) {
            //Extract Bundle
            Bundle keyBundle = getIntent().getExtras();
            //Extract Consumer Key
            if(keyBundle.containsKey(getResources().getString(R.string.tumblr_consumer_key))
                    && !TextUtils.isEmpty(keyBundle.getString(getResources().getString(R.string.tumblr_consumer_key))))
                TUMBLR_CONSUMER_KEY = keyBundle.getString(getResources().getString(R.string.tumblr_consumer_key));
            else
                throw new TumblrBundleException();

            //Extract Secret Key
            if(keyBundle.containsKey(getResources().getString(R.string.tumblr_consumer_secret_key))
                    && !TextUtils.isEmpty(keyBundle.getString(getResources().getString(R.string.tumblr_consumer_secret_key))))
                TUMBLR_SECRET_KEY = keyBundle.getString(getResources().getString(R.string.tumblr_consumer_secret_key));
            else
                //If key is not found
                throw new TumblrBundleException();

            //test if LoginListener was registered
            if(Loglr.getInstance().getLoginListener() != null) {
                if(Loglr.getInstance().getExceptionHandler() == null)
                    Log.w(TAG, "Continuing execution without ExceptionHandler. No Exception call backs will be sent. It is recommended to set one.");
                //Initiate an AsyncTask to begin TumblrLogin
                new TaskTumblrLogin().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                //If Exception handler was registered by the dev, use it to return a call back.
                //Otherwise, just throw the exception and make the application crash
                if (Loglr.getInstance().getExceptionHandler() != null)
                    Loglr.getInstance().getExceptionHandler().onLoginFailed(new TumblrLoginException());
                else
                    throw new TumblrLoginException();
            }
        } else
            throw new TumblrBundleException();
    }

    /**
     * The AsyncTask performs the following functions
     * 1) Manufactures OAuthConsumer object using Consumer and secret keys
     * 2) Manufactures OAuthProvider object using the URLs mentioned above
     * 3) Makes a network request to retrieve authorization URL. The user is to be navigated
     * to this URL so he may login by entering his/her user credentials.
     */
    private class TaskTumblrLogin extends AsyncTask<Void, RuntimeException, String> {

        /**
         * The OAuth provider
         */
        private CommonsHttpOAuthProvider commonsHttpOAuthProvider;

        /**
         * OAuth Consumer
         */
        private CommonsHttpOAuthConsumer commonsHttpOAuthConsumer;

        /**
         * A reference to a progress Dialog
         */
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //Show a progress Dialog while the request tokens are fetched
            progressDialog = ProgressDialog.show(LoglrActivity.this, null, getResources().getString(R.string.tumblrlogin_loading));
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                //Generate a new oAuthConsumer object
                commonsHttpOAuthConsumer
                        = new CommonsHttpOAuthConsumer(TUMBLR_CONSUMER_KEY, TUMBLR_SECRET_KEY);
                //Generate a new oAuthProvider object
                commonsHttpOAuthProvider
                        = new CommonsHttpOAuthProvider(
                        getResources().getString(R.string.tumblr_request),
                        getResources().getString(R.string.tumblr_access),
                        getResources().getString(R.string.tumblr_auth));
                //Retrieve the URL to which the user must be sent in order to authorize the consumer
                return commonsHttpOAuthProvider.retrieveRequestToken(commonsHttpOAuthConsumer,
                        getResources().getString(R.string.tumblr_callback_url));
            } catch (OAuthMessageSignerException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getMessage()));
                return null;
            } catch (OAuthNotAuthorizedException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getResponseBody()));
                return null;
            } catch (OAuthExpectationFailedException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getMessage()));
                return null;
            } catch (OAuthCommunicationException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getResponseBody()));
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(RuntimeException... values) {
            super.onProgressUpdate(values);
            if(values != null && values.length > 0) {
                RuntimeException exception = values[0];
                if(Loglr.getInstance().getExceptionHandler() != null)
                    Loglr.getInstance().getExceptionHandler().onLoginFailed(exception);
                else
                    finish();
            }
        }

        @Override
        protected void onPostExecute(String strAuthUrl) {
            super.onPostExecute(strAuthUrl);
            //Dismiss progress bar
            progressDialog.dismiss();
            if(!TextUtils.isEmpty(strAuthUrl)) {
                //instantiate web view to authorize user account
                final WebView webView = (WebView) findViewById(R.id.activity_tumblr_webview);
                //Enable JS support on web browser - important since TumblrLogin utilises JS components
                //Login page will not show up properly if this is not done
                webView.getSettings().setJavaScriptEnabled(true);
                //Set a web view client to monitor browser interactions
                webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String strUrl) {
                        //Log Current loading URL
                        Log.i(TAG, strUrl);
                        //Check if the Currently loading URL is that of the call back URL mentioned on top
                        if (strUrl.contains(
                                getResources().getString(R.string.tumblr_callback_url).toLowerCase())) {
                            //Parse string URL to conver to URI
                            Uri uri = Uri.parse(strUrl);
                            //instantiate String variables to store OAuth & Verifier tokens
                            String strOAuthToken = "";
                            String strOAuthVerifier = "";
                            //Iterate through Parameters retrieved on the URL
                            for (String strQuery : uri.getQueryParameterNames())
                                switch (strQuery) {
                                    case "oauth_token":
                                        //Save OAuth Token
                                        //Note : This is not the login token we require to set on JumblrToken
                                        strOAuthToken = uri.getQueryParameter(strQuery);
                                        break;

                                    case "oauth_verifier":
                                        //Save OAuthVerifier
                                        strOAuthVerifier = uri.getQueryParameter(strQuery);
                                        break;
                                }
                            //Execute a new AsyncTask to retrieve access tokens
                            //Performing this is important since communication using OAuthProvider
                            //can only be done in a background thread.
                            TaskRetrieveAccessToken taskRetrieveAccessToken = new TaskRetrieveAccessToken();
                            //Pass OAuthConsumer as an argument
                            taskRetrieveAccessToken.setOAuthConsumer(commonsHttpOAuthConsumer);
                            //Pass OAuthProvider as an argument
                            taskRetrieveAccessToken.setOAuthProvider(commonsHttpOAuthProvider);
                            //Pass OAuthVerifier as an argument
                            taskRetrieveAccessToken.setOAuthVerifier(strOAuthVerifier);
                            taskRetrieveAccessToken.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            return true;
                        }
                        return super.shouldOverrideUrlLoading(view, strUrl);
                    }
                });
                //Load URL
                webView.loadUrl(strAuthUrl);
            } else
                finish();
        }
    }

    /**
     * The asyncTask utilises the parameters passed and makes a network call to retrieve
     * the access tokens & save them to SharedPreferences.
     */
    private class TaskRetrieveAccessToken extends AsyncTask<Void, RuntimeException, LoginResult> {

        /**
         * The OAuth provider
         */
        private CommonsHttpOAuthProvider commonsHttpOAuthProvider;

        /**
         * OAuth Consumer
         */
        private CommonsHttpOAuthConsumer commonsHttpOAuthConsumer;

        /**
         * variables to hold verifier retrieved from Tumblr
         */
        private String strOAuthVerifier;

        /**
         * A reference to a progress Dialog
         */
        private ProgressDialog progressDialog;

        //Constructor
        public TaskRetrieveAccessToken() {
        }

        /**
         * Set the OAuthConsumer
         * @param OAuthConsumer TheOAuthConsumer to which tokens will be applied
         */
        public void setOAuthConsumer(CommonsHttpOAuthConsumer OAuthConsumer) {
            this.commonsHttpOAuthConsumer = OAuthConsumer;
        }

        /**
         * Set the OAuthProvider
         * @param OAuthProvider The OAuthProvider which makes the request for tokens
         */
        public void setOAuthProvider(CommonsHttpOAuthProvider OAuthProvider) {
            this.commonsHttpOAuthProvider = OAuthProvider;
        }

        /**
         * Set the OAuthVerifier
         * @param OAuthVerifier The OAuthVerifier token which is used as a param to retrieve access tokens
         */
        public void setOAuthVerifier(String OAuthVerifier) {
            this.strOAuthVerifier = OAuthVerifier;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //Show Progress Dialog while the user waits
            progressDialog = ProgressDialog.show(LoglrActivity.this, null, "Loading...");
        }

        @Override
        protected LoginResult doInBackground(Void... voids) {
            //Instantiate a new LoginResult object which will store the Consumer key and secret key
            //to be returned to the
            LoginResult loginResult = new LoginResult();
            try {
                //Queries the service provider for access tokens. The method does not return anything.
                //It stores the OAuthToken & OAuthToken secret in the commonsHttpOAuthConsumer object.
                commonsHttpOAuthProvider.retrieveAccessToken(commonsHttpOAuthConsumer, strOAuthVerifier);
                //Check if tokens were received. If Yes, save them to SharedPreferences for later use.
                if(!TextUtils.isEmpty(commonsHttpOAuthConsumer.getToken())) {
                    //Set the consumer key token in the LoginResult object
                    loginResult.setStrTumblrKey(commonsHttpOAuthConsumer.getToken());
                    Log.i(TAG, "OAuthToken : " + loginResult.getStrTumblrKey());
                }

                if(!TextUtils.isEmpty(commonsHttpOAuthConsumer.getTokenSecret())) {
                    //Set the Secret consumer key token in the LoginResult object
                    loginResult.setStrTumblrSecreyKey(commonsHttpOAuthConsumer.getTokenSecret());
                    Log.i(TAG, "OAuthSecretToken : " + loginResult.getStrTumblrSecreyKey());
                }
                //Return the login result with ConsumerKey and ConsumerSecret Key
                return loginResult;
            } catch (OAuthCommunicationException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getResponseBody()));
                return null;
            } catch (OAuthExpectationFailedException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getMessage()));
                return null;
            } catch (OAuthNotAuthorizedException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getResponseBody()));
                return null;
            } catch (OAuthMessageSignerException e) {
                e.printStackTrace();
                publishProgress(new TumblrLoginException(e.getMessage()));
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(RuntimeException... values) {
            super.onProgressUpdate(values);
            if(values != null && values.length > 0) {
                RuntimeException exception = values[0];
                if(Loglr.getInstance().getExceptionHandler() != null)
                    Loglr.getInstance().getExceptionHandler().onLoginFailed(exception);
                else
                    finish();
            }
        }

        @Override
        protected void onPostExecute(LoginResult loginResult) {
            super.onPostExecute(loginResult);
            //Dismiss progress bar
            progressDialog.dismiss();
            //Check if tokens were retrieved. If yes, Set result as successful and finish activity
            //otherwise, set as failed.
            if(loginResult != null)
                Loglr.getInstance().getLoginListener().onLoginSuccessful(loginResult);
            finish();
        }
    }
}