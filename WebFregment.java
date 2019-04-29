package com.pro.readme.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.appmediation.sdk.AMInterstitial;
import com.appmediation.sdk.AMRewardedVideo;
import com.appmediation.sdk.listeners.AMRewardedListener;
import com.appmediation.sdk.models.AMError;
import com.pro.readme.App;
import com.pro.readme.Config;
import com.pro.readme.GetFileInfo;
import com.pro.readme.R;
import com.pro.readme.widget.webview.WebToAppChromeClient;
import com.pro.readme.widget.webview.WebToAppWebClient;
import com.pro.readme.activity.MainActivity;
import com.pro.readme.widget.AdvancedWebView;
import com.pro.readme.widget.scrollable.ToolbarWebViewScrollListener;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutionException;

public class WebFragment extends Fragment implements AdvancedWebView.Listener, SwipeRefreshLayout.OnRefreshListener{

    //Layouts
    public FrameLayout rl;
    public AdvancedWebView browser;
    public SwipeRefreshLayout swipeLayout;
    public ProgressBar progressBar;

    //WebView Clients
    public WebToAppChromeClient chromeClient;
    public WebToAppWebClient webClient;

    //WebView Session
    public String mainUrl = null;
    static String URL = "url";
    public int firstLoad = 0;
    private boolean clearHistory = false;

    //Keep track of the interstitials we show
    private int interstitialCount = -1;

    //JsInterface

    public WebFragment() {
        // Required empty public constructor
    }

    public static WebFragment newInstance(String url) {
        WebFragment fragment = new WebFragment();
        Bundle args = new Bundle();
        args.putString(URL, url);
        fragment.setArguments(args);
        return fragment;
    }

    public void setBaseUrl(String url){
        this.mainUrl = url;
        this.clearHistory = true;
        browser.loadUrl(mainUrl);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && mainUrl == null) {
            mainUrl = getArguments().getString(URL);
            firstLoad = 0;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rl = (FrameLayout) inflater.inflate(R.layout.fragment_observable_web_view, container,
                false);

        progressBar = (ProgressBar) rl.findViewById(R.id.progressbar);
        browser = (AdvancedWebView) rl.findViewById(R.id.scrollable);
        swipeLayout = (SwipeRefreshLayout) rl.findViewById(R.id.swipe_container);

        return rl;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (Config.PULL_TO_REFRESH)
            swipeLayout.setOnRefreshListener(this);
        else
            swipeLayout.setEnabled(false);

        // Setting the webview listeners
        browser.setListener(this, this);

        // Setting the scroll listeners (if applicable)
        if (MainActivity.getCollapsingActionBar()) {

            ((MainActivity) getActivity()).showToolbar(this);

            browser.setOnScrollChangeListener(browser, new ToolbarWebViewScrollListener() {
                @Override
                public void onHide() {
                    ((MainActivity) getActivity()).hideToolbar();
                }

                @Override
                public void onShow() {
                    ((MainActivity) getActivity()).showToolbar(WebFragment.this);
                }
            });

        }

        // set javascript and zoom and some other settings
        browser.requestFocus();
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setBuiltInZoomControls(false);
        browser.getSettings().setAppCacheEnabled(true);
        browser.getSettings().setDatabaseEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        // Below required for geolocation
        browser.setGeolocationEnabled(true);
        // 3RD party plugins (on older devices)
        browser.getSettings().setPluginState(PluginState.ON);

        if (Config.MULTI_WINDOWS) {
            browser.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
            browser.getSettings().setSupportMultipleWindows(true);
        }

        final AppmediationWebInterface appmediationJsInterface = new AppmediationWebInterface();
        appmediationJsInterface.registerInterface(browser);
        webClient = new WebToAppWebClient(this, browser){
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Inject appmediation web interface into web view
                appmediationJsInterface.init(getActivity(), view);
            }
        };
        browser.setWebViewClient(webClient);

        chromeClient = new WebToAppChromeClient(this, rl, browser, swipeLayout, progressBar);
        browser.setWebChromeClient(chromeClient);

        // load url (if connection available
        if (webClient.hasConnectivity(mainUrl, true)) {
            String pushurl = ((App) getActivity().getApplication()).getPushUrl();
            if (pushurl != null){
                browser.loadUrl(pushurl);
            } else {
                browser.loadUrl(mainUrl);
            }
        } else {
            try {
                ((MainActivity) getActivity()).hideSplash();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRefresh() {
        browser.reload();
    }

    @SuppressLint("NewApi")
    @Override
    public void onPause() {
        super.onPause();
        browser.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        browser.onDestroy();
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
        browser.onResume();
    }

    @SuppressLint("NewApi")
    @Override
    public void onDownloadRequested(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        if (!hasPermissionToDownload(getActivity())) return;

        String filename = null;
        try {
            filename = new GetFileInfo().execute(url).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        if (filename == null) {
            String fileExtenstion = MimeTypeMap.getFileExtensionFromUrl(url);
            filename = URLUtil.guessFileName(url, null, fileExtenstion);
        }


        if (AdvancedWebView.handleDownload(getActivity(), url, filename)) {
            Toast.makeText(getActivity(), getResources().getString(R.string.download_done), Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(getActivity(), getResources().getString(R.string.download_fail), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean hasPermissionToDownload(final Activity context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED )
            return true;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.download_permission_explaination);
        builder.setPositiveButton(R.string.common_permission_grant, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Fire off an async request to actually get the permission
                // This will show the standard permission request dialog UI
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    context.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},1);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();

        return false;
    }

    @Override
    public void onPageStarted(String url, Bitmap favicon) {
        if (firstLoad == 0 && MainActivity.getCollapsingActionBar()){
            ((MainActivity) getActivity()).showToolbar(this);
            firstLoad = 1;
        } else if (firstLoad == 0){
            firstLoad = 1;
        }
    }

    /**
     * Show an interstitial ad
     */
    private void showInterstitial(){
        // removed this check that you had for AdMob
//        if (getResources().getString(R.string.ad_interstitial_id).length() == 0) return;
        if (Config.INTERSTITIAL_PAGE_INTERVAL == 0) return;

        if (interstitialCount == (Config.INTERSTITIAL_PAGE_INTERVAL - 1)) {
            AMInterstitial.show(getActivity());
            // You don't need separate AdMob integration as appmediation also has AdMob ads
            /*final InterstitialAd mInterstitialAd = new InterstitialAd(getActivity());
            mInterstitialAd.setAdUnitId(getResources().getString(R.string.ad_interstitial_id));
            AdRequest adRequestInter = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    super.onAdLoaded();
                    mInterstitialAd.show();
                }
                @Override
                public void onAdClosed() {
                    super.onAdClosed();
                }
            });
            mInterstitialAd.loadAd(adRequestInter);*/

            interstitialCount = 0;
        } else {
            interstitialCount++;
        }

    }

    @Override
    public void onPageFinished(String url) {
        if (!url.equals(mainUrl))
            showInterstitial();

        try {
            ((MainActivity) getActivity()).hideSplash();
        } catch (Exception e){
            e.printStackTrace();
        }
        
        if (clearHistory)
        {
            clearHistory = false;
            browser.clearHistory();
        }


        hideErrorScreen();
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onExternalPageRequest(String url) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        browser.onActivityResult(requestCode, resultCode, data);
    }

    // sharing
    public void shareURL() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String appName = getString(R.string.app_name);
        shareIntent
                .putExtra(
                        Intent.EXTRA_TEXT,
                        String.format(getString(R.string.share_body), browser.getTitle(), appName + " https://play.google.com/store/apps/details?id=" + getActivity().getPackageName()));
        startActivity(Intent.createChooser(shareIntent,
                getText(R.string.sharetitle)));
    }

    public void showErrorScreen(String message) {
        final View stub = rl.findViewById(R.id.empty_view);
        stub.setVisibility(View.VISIBLE);

        ((TextView) stub.findViewById(R.id.title)).setText(message);
        ((Button) stub.findViewById(R.id.retry_button)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                browser.reload();
            }
        });
    }

    public void hideErrorScreen(){
        final View stub = rl.findViewById(R.id.empty_view);
        if (stub.getVisibility() == View.VISIBLE)
        stub.setVisibility(View.GONE);
    }

    private static class AppmediationWebInterface implements AMRewardedListener {
        private static final String INTERFACE_NAME = "AppmediationSDK";
        private WeakReference<Activity> activityRef;
        private WeakReference<WebView> webViewRef;

        public void init(Activity activity, WebView webView) {
            this.activityRef = new WeakReference<>(activity);
            this.webViewRef = new WeakReference<>(webView);
            registerInterface();
            injectJs("appmediationJsInterfaceInit();");
            AMRewardedVideo.setListener(this);
        }

        private void registerInterface() {
            registerInterface(webViewRef != null ? webViewRef.get() : null);
        }

        @SuppressLint("AddJavascriptInterface")
        private void registerInterface(WebView webView) {
            if (webView == null) return;
            webView.addJavascriptInterface(this, INTERFACE_NAME);
        }

        @JavascriptInterface
        public void rewardedVideoRequest() {
            if (activityRef == null) return;
            AMRewardedVideo.show(activityRef.get());
        }

        @JavascriptInterface
        public boolean isRewardedVideoAvailable() {
            return AMRewardedVideo.isReady();
        }

        @Override
        public void onClicked() {
            injectJs("onRewardedVideoClicked();");
        }

        @Override
        public void onClosed() {
            injectJs("onRewardedVideoClosed();");
        }

        @Override
        public void onCompleted(String currency, String amount) {
            injectJs("onRewardedVideoCompleted('"+currency+"','"+amount+"');");
        }

        @Override
        public void onFailed(AMError amError) {
            injectJs("onRewardedVideoFailed('"+amError.name()+"');");
        }

        @Override
        public void onLoaded(String currency, String amount) {
            injectJs("onRewardedVideoLoaded('"+currency+"','"+amount+"');");
        }

        @Override
        public void onShowed() {
            injectJs("onRewardedVideoShowed();");
        }

        private void injectJs(String javaScript) {
            WebView webView = webViewRef != null ? webViewRef.get() : null;
            if (webView == null) return;
            /*webView.loadUrl("javascript:(function() { "
                    + javaScript +
                    "})()");*/
            webView.loadUrl("javascript:" + javaScript);
        }
    }
}
