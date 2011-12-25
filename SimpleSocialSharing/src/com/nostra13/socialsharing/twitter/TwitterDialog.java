package com.nostra13.socialsharing.twitter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import twitter4j.AsyncTwitter;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 */
class TwitterDialog extends Dialog {
	public static final String TAG = "twitter";

	static final int TW_BLUE = 0xFFC0DEED;
	static final float[] DIMENSIONS_LANDSCAPE = {460, 260};
	static final float[] DIMENSIONS_PORTRAIT = {280, 420};
	static final FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
	static final int MARGIN = 4;
	static final int PADDING = 2;

	static final String JS_HTML_EXTRACTOR = "javascript:window.HTMLOUT.processHTML('<head>'+document.getElementsByTagName('html')[0].innerHTML+'</head>');";
	static final String OAUTH_PIN_BLOCK_REGEXP = "id=\\\"oauth_pin((.|\\n)*)(\\d{7})";
	static final String OAUTH_PIN_REGEXP = "\\d{7}";

	private ProgressDialog spinner;
	private WebView browser;
	private LinearLayout content;

	private AsyncTwitter twitter;
	private RequestToken requestToken;

	public TwitterDialog(Context context, AsyncTwitter twitter) {
		super(context);
		this.twitter = twitter;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		spinner = new ProgressDialog(getContext());
		spinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
		spinner.setMessage("Loading...");
		spinner.setCancelable(false);

		content = new LinearLayout(getContext());
		content.setOrientation(LinearLayout.VERTICAL);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setUpWebView();

		Display display = getWindow().getWindowManager().getDefaultDisplay();
		final float scale = getContext().getResources().getDisplayMetrics().density;
		float[] dimensions = display.getWidth() < display.getHeight() ? DIMENSIONS_PORTRAIT : DIMENSIONS_LANDSCAPE;
		addContentView(content, new FrameLayout.LayoutParams((int) (dimensions[0] * scale + 0.5f), (int) (dimensions[1] * scale + 0.5f)));

		retrieveRequestToken();
	}

	@Override
	public void show() {
		super.show();
		if (requestToken == null) {
			dismiss();
		} else {
			spinner.show();
			browser.loadUrl(requestToken.getAuthorizationURL());
		}
	}

	private void retrieveRequestToken() {
		try {
			requestToken = twitter.getOAuthRequestToken();
		} catch (TwitterException e) {
			Log.e(TAG, e.getErrorMessage(), e);
			String errorMessage = e.getErrorMessage();
			if (errorMessage == null) {
				errorMessage = e.getMessage();
			}
			TwitterEvents.onLoginError(errorMessage);
			dismiss();
		}
	}

	private void setUpWebView() {
		browser = new WebView(getContext());
		browser.setVerticalScrollBarEnabled(false);
		browser.setHorizontalScrollBarEnabled(false);
		browser.setWebViewClient(new TwitterDialog.TwWebViewClient());
		browser.getSettings().setJavaScriptEnabled(true);
		browser.addJavascriptInterface(new MyJavaScriptInterface(), "HTMLOUT");
		browser.setLayoutParams(FILL);
		content.addView(browser);
	}

	private class TwWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			view.loadUrl(url);
			return true;
		}

		@Override
		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			TwitterEvents.onLoginError(description);
			TwitterDialog.this.dismiss();
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			Log.d(TAG, "WebView loading URL: " + url);
			super.onPageStarted(view, url, favicon);
			if (spinner.isShowing()) {
				spinner.dismiss();
			}
			spinner.show();
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			browser.loadUrl(JS_HTML_EXTRACTOR);
		}
	}

	class MyJavaScriptInterface {
		public void processHTML(String html) {
			String blockWithPin = findExpression(html, OAUTH_PIN_BLOCK_REGEXP);
			if (blockWithPin != null) {
				String pin = findExpression(blockWithPin, OAUTH_PIN_REGEXP);
				if (pin != null) {
					autorizeApp(pin);
					spinner.dismiss();
					dismiss();
				}
			}
			spinner.dismiss();
		}

		private String findExpression(String text, String regExp) {
			Pattern p = Pattern.compile(regExp);
			Matcher m = p.matcher(text);
			if (m.find()) {
				return m.group(0);
			} else {
				return null;
			}
		}

		private void autorizeApp(String pin) {
			try {
				AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, pin);
				TwitterSessionStore.save(accessToken, getContext());
				TwitterEvents.onLoginSuccess();
			} catch (TwitterException e) {
				Log.e(TAG, e.getMessage(), e);
				TwitterEvents.onLoginError(e.getErrorMessage());
			}
		}
	}
}
