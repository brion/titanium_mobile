/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.kroll.KrollBridge;
import org.appcelerator.titanium.kroll.KrollContext;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.util.TiFileHelper2;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class TiContext implements TiEvaluator, ITiMenuDispatcherListener, ErrorReporter
{
	private static final String LCAT = "TiContext";
	private static final boolean DBG = TiConfig.LOGD;
	@SuppressWarnings("unused")
	private static final boolean TRACE = TiConfig.LOGV;

	private long mainThreadId;

	private String baseUrl;
	private String currentUrl;

	private WeakReference<Activity> weakActivity;
	private TiEvaluator	tiEvaluator;
	private TiApplication tiApp;
	protected KrollContext krollContext;
	
	private List<WeakReference<OnLifecycleEvent>> lifecycleListeners;
	private OnMenuEvent menuEventListener;
	private WeakReference<OnConfigurationChanged> weakConfigurationChangedListeners;

	public static interface OnLifecycleEvent
	{
		void onStart();
		void onResume();
		void onPause();
		void onStop();
		void onDestroy();
	}

	public static interface OnConfigurationChanged
	{
		public void configurationChanged(Configuration newConfig);
	}

	public static interface OnMenuEvent
	{
		public boolean hasMenu();
		public boolean prepareMenu(Menu menu);
		public boolean menuItemSelected(MenuItem item);
	}

	public TiContext(Activity activity, String baseUrl)
	{
		this.mainThreadId = Looper.getMainLooper().getThread().getId();

		this.tiApp = (TiApplication) activity.getApplication();
		this.weakActivity = new WeakReference<Activity>(activity);
		lifecycleListeners = Collections.synchronizedList(new ArrayList<WeakReference<OnLifecycleEvent>>());
		if (baseUrl == null) {
			this.baseUrl = "app://";
		} else {
			this.baseUrl = baseUrl;
			if (!baseUrl.endsWith("/")) {
				this.baseUrl += "/";
			}
		}

		if (activity instanceof TiActivity) {
			((TiActivity)activity).addTiContext(this);
		}

		if (DBG) {
			Log.e(LCAT, "BaseURL for context is " + baseUrl);
		}
	}

	public boolean isUIThread() {
		return Thread.currentThread().getId() == mainThreadId;
	}

	public TiEvaluator getJSContext() {
		return tiEvaluator;
	}

	public void setJSContext(TiEvaluator evaluator) {
		if (DBG) {
			Log.d(LCAT, "Setting JS Context on " + this + " to " + evaluator);
		}
		tiEvaluator = evaluator;
	}
	
	public KrollBridge getKrollBridge() {
		if (tiEvaluator instanceof KrollBridge) {
			return (KrollBridge)tiEvaluator;
		} else if (tiEvaluator instanceof TiContext) {
			return ((TiContext)tiEvaluator).getKrollBridge();
		}
		return null;
	}

	public Activity getActivity() {
		Activity activity = weakActivity.get();
		return activity;
	}

	public TiApplication getTiApp() {
		return tiApp;
	}

	public TiRootActivity getRootActivity() {
		return getTiApp().getRootActivity();
	}

	public TiFileHelper getTiFileHelper() {
		return new TiFileHelper(getTiApp());
	}

	public String absoluteUrl(String defaultScheme, String url)
	{
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (scheme == null) {
				String path = uri.getPath();
				String fname = null;
				int lastIndex = path.lastIndexOf("/");
				if (lastIndex > 0) {
					fname = path.substring(lastIndex+1);
					path = path.substring(0, lastIndex);
				}

				if (path.startsWith("../") || path.equals("..")) {
					String[] right = path.split("/");
					String[] left = null;
					if (baseUrl.contains("://")) {
						String[] tmp = baseUrl.split("://");
						if (tmp.length > 1)
						{
							left = tmp[1].split("/");
						}
						else
						{
							left = new String[] {};
						}
					} else {
						left = baseUrl.split("/");
					}

					int rIndex = 0;
					int lIndex = left.length;

					while(rIndex < right.length && right[rIndex].equals("..")) {
						lIndex--;
						rIndex++;
					}
					String sep = "";
					StringBuilder sb = new StringBuilder();
					for (int i = 0; i < lIndex; i++) {
						sb.append(sep).append(left[i]);
						sep = "/";
					}
					for (int i = rIndex; i < right.length; i++) {
						sb.append(sep).append(right[i]);
						sep = "/";
					}
					String bUrl = sb.toString();
					if (!bUrl.endsWith("/")) {
						bUrl = bUrl + "/";
					}
					url = TiFileHelper2.joinSegments(defaultScheme + "//",bUrl, fname);
				}
			}
		} catch (URISyntaxException e) {
			Log.w(LCAT, "Error parsing url: " + e.getMessage(), e);
		}

		return url;
	}

	public String resolveUrl(String scheme, String path)
	{
		return resolveUrl(scheme, path, getBaseUrl());
	}

	public String resolveUrl(String scheme, String path, String relativeTo)
	{
		if (!TiFileFactory.isLocalScheme(path)) {
			return path;
		}

		String result = null;
		if (scheme == null) {
			scheme = "app:";
		}

		if (path.startsWith("../")) {
			path = absoluteUrl(scheme, path);
		}

		Uri uri = Uri.parse(path);
		if (uri.getScheme() == null) {
			if (!path.startsWith("/")) {
				result = relativeTo + path;
			} else {
				result = scheme + "/" + path;
			}
		} else {
			result = path;
		}

		if (!result.startsWith("file:")) {
			String[] p = { result };
			TiBaseFile tbf = TiFileFactory.createTitaniumFile(this, p, false);
			result = tbf.nativePath();
		}

		return result;
	}

	public String getBaseUrl() {
		return baseUrl;
	}
	
	public String getCurrentUrl() {
		return currentUrl;
	}

	// Javascript Support

	public Object evalFile(String filename, Messenger messenger, int messageId) throws IOException {
		Object result = null;
		
		this.currentUrl = filename;
		TiEvaluator jsContext = getJSContext();
		if (jsContext == null) {
			if (DBG) {
				Log.w(LCAT, "Cannot eval file '" + filename + "'. Context has been released already.");
			}
			return null;
		}

		result = jsContext.evalFile(filename);
		if (messenger != null) {
			try {
				Message msg = Message.obtain();
				msg.what = messageId;
				messenger.send(msg);
				if (DBG) {
					Log.d(LCAT, "Notifying caller that evalFile has completed");
				}
			} catch(RemoteException e) {
				Log.w(LCAT, "Failed to notify caller that eval completed");
			}
		}
		return result;
	}

	public Object evalFile(String filename) throws IOException {
		return evalFile(filename, null, -1);
	}

	public Object evalJS(String src) {
		TiEvaluator evaluator = getJSContext();
		if (evaluator == null)
		{
			Log.e(LCAT,"on evalJS, evaluator is null and shouldn't be");
		}
		return evaluator.evalJS(src);
	}

	@Override
	public Scriptable getScope() {
		return getJSContext().getScope();
	}

	public void addOnLifecycleEventListener(OnLifecycleEvent listener) {
		lifecycleListeners.add(new WeakReference<OnLifecycleEvent>(listener));
	}

	public void removeOnLifecycleEventListener(OnLifecycleEvent listener)
	{
		synchronized(lifecycleListeners) {
			for (WeakReference<OnLifecycleEvent> ref : lifecycleListeners) {
				OnLifecycleEvent l = ref.get();
				if (l != null) {
					if (l.equals(listener)) {
						lifecycleListeners.remove(ref);
						break;
					}
				}
			}
		}
	}

	public void setOnMenuEventListener(OnMenuEvent listener) {
		if (listener != null) {
			menuEventListener = listener;
			TiActivitySupport tis = (TiActivitySupport) getActivity();
			if (tis != null) {
				tis.setMenuDispatchListener(this);
			}
		} else {
			menuEventListener = null;
			TiActivitySupport tis = (TiActivitySupport) getActivity();
			if (tis != null) {
				tis.setMenuDispatchListener(null);
			}
		}
	}

	public boolean dispatchHasMenu()
	{
		if (menuEventListener != null) {
			return menuEventListener.hasMenu();
		}

		return false;
	}

	public boolean dispatchPrepareMenu(Menu menu)
	{
		if (menuEventListener != null) {
			return menuEventListener.prepareMenu(menu);
		}

		return false;
	}

	public boolean dispatchMenuItemSelected(MenuItem item)
	{
		if (menuEventListener != null) {
			return menuEventListener.menuItemSelected(item);
		}

		return false;
	}

	public void setOnConfigurationChangedListener(OnConfigurationChanged listener) {
		if (listener == null) {
			weakConfigurationChangedListeners = null;
		} else {
			weakConfigurationChangedListeners = new WeakReference<OnConfigurationChanged>(listener);
		}
	}
	public void dispatchOnConfigurationChanged(Configuration newConfig)
	{
		if (weakConfigurationChangedListeners != null) {
			OnConfigurationChanged listener = weakConfigurationChangedListeners.get();
			if (listener != null) {
				listener.configurationChanged(newConfig);
			}
		}
	}

	public void dispatchOnStart()
	{
		synchronized(lifecycleListeners) {
			for(WeakReference<OnLifecycleEvent> ref : lifecycleListeners) {
				OnLifecycleEvent listener = ref.get();
				if (listener != null) {
					try {
						listener.onStart();
					} catch (Throwable t) {
						Log.e(LCAT, "Error dispatching onStart  event: " + t.getMessage(), t);
					}
				} else {
					Log.w(LCAT, "lifecycleListener has been garbage collected");
				}
			}
		}
	}

	public void dispatchOnResume() {
		synchronized(lifecycleListeners) {
			for(WeakReference<OnLifecycleEvent> ref : lifecycleListeners) {
				OnLifecycleEvent listener = ref.get();
				if (listener != null) {
					try {
						listener.onResume();
					} catch (Throwable t) {
						Log.e(LCAT, "Error dispatching onResume  event: " + t.getMessage(), t);
					}
				} else {
					Log.w(LCAT, "lifecycleListener has been garbage collected");
				}
			}
		}
	}

	public void dispatchOnPause() {
		synchronized (lifecycleListeners) {
			for(WeakReference<OnLifecycleEvent> ref : lifecycleListeners) {
				OnLifecycleEvent listener = ref.get();
				if (listener != null) {
					try {
						listener.onPause();
					} catch (Throwable t) {
						Log.e(LCAT, "Error dispatching onPause  event: " + t.getMessage(), t);
					}
				} else {
					Log.w(LCAT, "lifecycleListener has been garbage collected");
				}
			}
		}
	}

	public void dispatchOnStop() {
		synchronized(lifecycleListeners) {
			for(WeakReference<OnLifecycleEvent> ref : lifecycleListeners) {
				OnLifecycleEvent listener = ref.get();
				if (listener != null) {
					try {
						listener.onStop();
					} catch (Throwable t) {
						Log.e(LCAT, "Error dispatching onStop  event: " + t.getMessage(), t);
					}
				} else {
					Log.w(LCAT, "lifecycleListener has been garbage collected");
				}
			}
		}
	}

	public void dispatchOnDestroy() {
		synchronized(lifecycleListeners) {
			for(WeakReference<OnLifecycleEvent> ref : lifecycleListeners) {
				OnLifecycleEvent listener = ref.get();
				if (listener != null) {
					try {
						listener.onDestroy();
					} catch (Throwable t) {
						Log.e(LCAT, "Error dispatching onDestroy  event: " + t.getMessage(), t);
					}
				} else {
					Log.w(LCAT, "lifecycleListener has been garbage collected");
				}
			}
		}
	}


	@Override
	public void error(String message, String sourceName, int line, String lineSource, int lineOffset)
	{
		doRhinoDialog("Error", message, sourceName, line, lineSource, lineOffset);
	}

	@Override
	public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset)
	{
		doRhinoDialog("Runtime Error", message, sourceName, line, lineSource, lineOffset);
		return null;
	}

	@Override
	public void warning(String message, String sourceName, int line, String lineSource, int lineOffset)
	{
		doRhinoDialog("Warning", message, sourceName, line, lineSource, lineOffset);
	}

	public static TiContext createTiContext(Activity activity, String baseUrl)
	{
		TiContext tic = new TiContext(activity, baseUrl);
		KrollContext kroll = KrollContext.createContext(tic);
		tic.setKrollContext(kroll);
		KrollBridge krollBridge = new KrollBridge(kroll);
		tic.setJSContext(krollBridge);
		return tic;
	}

	private void doRhinoDialog(final String title, final String message, final String sourceName, final int line,
			final String lineSource, final int lineOffset)
	{
		final Semaphore s = new Semaphore(0);
		final Activity activity = getActivity();
		if (activity == null || activity.isFinishing() ) {
			Log.w(LCAT, "Wanted to display an alert dialog in Javascript, but activity is finished.  Details: " + title  + " / " + message + " / " + sourceName + " / " + line + " / " + lineSource);
			return;
		}
		activity.runOnUiThread(new Runnable(){

			@Override
			public void run()
			{
				OnClickListener listener = new OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						Process.killProcess(Process.myPid());
					}
				};

				FrameLayout layout = new FrameLayout(activity);
				layout.setBackgroundColor(Color.rgb(128, 0, 0));

				LinearLayout vlayout = new LinearLayout(activity);
				vlayout.setOrientation(LinearLayout.VERTICAL);
				vlayout.setPadding(10, 10, 10, 10);

				layout.addView(vlayout);

				TextView sourceInfoView = new TextView(activity);
				sourceInfoView.setBackgroundColor(Color.WHITE);
				sourceInfoView.setTextColor(Color.BLACK);
				sourceInfoView.setPadding(4, 5, 4, 0);
				sourceInfoView.setText("[" + line + "," + lineOffset + "] " + sourceName);

				TextView messageView = new TextView(activity);
				messageView.setBackgroundColor(Color.WHITE);
				messageView.setTextColor(Color.BLACK);
				messageView.setPadding(4, 5, 4, 0);
				messageView.setText(message);

				TextView sourceView = new TextView(activity);
				sourceView.setBackgroundColor(Color.WHITE);
				sourceView.setTextColor(Color.BLACK);
				sourceView.setPadding(4, 5, 4, 0);
				sourceView.setText(lineSource);

				TextView infoLabel = new TextView(activity);
				infoLabel.setText("Location: ");
				infoLabel.setTextColor(Color.WHITE);
				infoLabel.setTextScaleX(1.5f);

				TextView messageLabel = new TextView(activity);
				messageLabel.setText("Message: ");
				messageLabel.setTextColor(Color.WHITE);
				messageLabel.setTextScaleX(1.5f);

				TextView sourceLabel = new TextView(activity);
				sourceLabel.setText("Source: ");
				sourceLabel.setTextColor(Color.WHITE);
				sourceLabel.setTextScaleX(1.5f);

				vlayout.addView(infoLabel);
				vlayout.addView(sourceInfoView);
				vlayout.addView(messageLabel);
				vlayout.addView(messageView);
				vlayout.addView(sourceLabel);
				vlayout.addView(sourceView);

				new AlertDialog.Builder(activity).setTitle(title)
						.setView(layout).setPositiveButton("Kill", listener)
						.setNeutralButton("Continue", new OnClickListener() {
							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								s.release();
							}
						}).setCancelable(false).create().show();

			}
		});

		try {
			s.acquire();
		} catch (InterruptedException e) {
			// Ignore
		}
	}
	
	public KrollContext getKrollContext() {
		return krollContext;
	}
	
	public void setKrollContext(KrollContext krollContext) {
		this.krollContext = krollContext;
	}
	
	public static TiContext getCurrentTiContext() {
		KrollContext currentCtx = KrollContext.getCurrentKrollContext();
		if (currentCtx == null) {
			return null;
		}
		return currentCtx.getTiContext();
	}
	
	public void release()
	{
		if (tiEvaluator != null && tiEvaluator instanceof KrollBridge)
		{
			((KrollBridge)tiEvaluator).release();
			tiEvaluator = null;
		}
		
		if (lifecycleListeners != null) {
			lifecycleListeners.clear();
		}
		menuEventListener = null;
		
	}
}
