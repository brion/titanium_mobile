/**
 * 
 */
package ti.modules.titanium.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

import org.appcelerator.titanium.ITiMenuDispatcherListener;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiRootActivity;
import org.appcelerator.titanium.kroll.KrollBridge;
import org.appcelerator.titanium.proxy.TiActivityWindowProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiActivitySupportHelper;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiFileHelper;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUIActivityWindow;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Messenger;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

public abstract class TiBaseActivity extends Activity
	implements TiActivitySupport
{
	private static final String LCAT = "TiBaseActivity";
	private static boolean DBG = TiConfig.LOGD;

	protected TiContext tiContext;
	protected String activitySource;
	protected TiCompositeLayout layout;
	protected ActivityProxy currentActivity;
	protected IntentProxy currentIntent;
	protected TiActivityWindowProxy currentWindow;
	protected TiActivitySupportHelper supportHelper;
	
	private HashMap<Integer, MenuItemProxy> itemMap;

	private CountDownLatch jsLoadedLatch;

	protected TiBaseActivity() {
		
	}
	
	public TiBaseActivity(String activitySource) 
	{
		this.activitySource = activitySource; //Our app.js equivalent
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		
		if (activitySource == null) {
			Intent intent = getIntent();
			if (intent != null && intent.getDataString() != null) {
				activitySource = intent.getDataString();
			} else {
				throw new IllegalStateException("activitySource required.");
			}
		}
		
		itemMap = new HashMap<Integer, MenuItemProxy>();
		
		tiContext = TiContext.createTiContext(this, null); // TODO baseurl
		
		AndroidModule androidModule = tiContext.getTiApp().getModuleByClass(AndroidModule.class);
		currentActivity = new ActivityProxy(tiContext);
		currentActivity.handleCreationArgs(androidModule, new Object[] { this });
		currentIntent = new IntentProxy(tiContext);
		currentIntent.handleCreationArgs(androidModule, new Object[] { getIntent() });
		currentWindow = new TiActivityWindowProxy(tiContext);
		
		KrollBridge bridge = tiContext.getKrollBridge();
		bridge.bindContextSpecific("Android", "currentActivity", currentActivity);
		bridge.bindContextSpecific("Android", "currentIntent", currentIntent);
		// currentWindow
		bridge.bindContextSpecific("UI", "currentWindow", currentWindow);
		
		
		Intent intent = getIntent();

		boolean fullscreen = false;
		boolean navbar = true;
		boolean modal = false;
		Messenger messenger = null;
		Integer messageId = null;
		boolean vertical = false;

		if (intent != null) {
			if (intent.hasExtra("modal")) {
				modal = intent.getBooleanExtra("modal", modal);
			}
			if (intent.hasExtra("fullscreen")) {
				fullscreen = intent.getBooleanExtra("fullscreen", fullscreen);
			}
			if (intent.hasExtra("navBarHidden")) {
				navbar = !intent.getBooleanExtra("navBarHidden", navbar);
			}
			if (intent.hasExtra("messenger")) {
				messenger = (Messenger) intent.getParcelableExtra("messenger");
				messageId = intent.getIntExtra("messageId", -1);
			}
			if (intent.hasExtra("vertical")) {
				vertical = intent.getBooleanExtra("vertical", vertical);
			}
		}

		layout = new TiCompositeLayout(this, vertical);

		if (modal) {
			Log.w(LCAT, "modal not supported yet.");
			// setTheme(android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
		} else {
			if (fullscreen) {
				getWindow().setFlags(
					WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}

			if (navbar) {
				this.requestWindowFeature(Window.FEATURE_LEFT_ICON); // TODO Keep?
				this.requestWindowFeature(Window.FEATURE_RIGHT_ICON);
				this.requestWindowFeature(Window.FEATURE_PROGRESS);
				this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
			} else {
				this.requestWindowFeature(Window.FEATURE_NO_TITLE);
			}
		}


		layout = new TiCompositeLayout(this, false);

		setContentView(layout);
			
		TiUIActivityWindow win = new TiUIActivityWindow(currentWindow, this, layout);
		// Load "app.js"
		jsLoadedLatch = new CountDownLatch(1);
		new Thread(new Runnable(){

			@Override
			public void run() {
				try {
					if (DBG) {
						Log.i(LCAT, "eval " + activitySource);
					}
					tiContext.evalFile(activitySource);
				} catch (IOException e) {
					e.printStackTrace();
					finish();
				} finally {
					Log.i(LCAT, "Signal JS loaded");
					jsLoadedLatch.countDown(); //Release UI thread
				}
			}
		}).start();
		
		try {
			Log.i(LCAT, "About to wait for JS to load");
			jsLoadedLatch.await();
		} catch (InterruptedException e) {
			Log.w(LCAT, "Wait for JS Load interrupted.");
		}

		if (currentActivity.hasListeners("create")) {
			currentActivity.fireEvent("create", null);
		}

		win.open();
	}

	
	@Override
	protected void onStart() 
	{
		super.onStart();
		try {
			Log.i(LCAT, "About to wait for JS to load");
			jsLoadedLatch.await();
		} catch (InterruptedException e) {
			Log.w(LCAT, "Wait for JS Load interrupted.");
		}
		Log.i(LCAT, "JSLOADED!!!!");
		if (currentActivity.hasListeners("start")) {
			currentActivity.fireEvent("start", null);
		}
	}

	@Override
	protected void onDestroy() 
	{
		super.onDestroy();
		if (currentActivity.hasListeners("destroy")) {
			currentActivity.fireEvent("destroy", null);
		}
		if (currentActivity != null) {
			currentActivity.release();
			currentActivity = null;
		}
	}

	@Override
	protected void onPause() 
	{
		super.onPause();
		if (currentActivity.hasListeners("pause")) {
			currentActivity.fireEvent("pause", null);
		}
		((TiApplication) getApplication()).setCurrentActivity(this, null);
	}

	@Override
	protected void onResume() 
	{
		super.onResume();
		((TiApplication) getApplication()).setCurrentActivity(this, this);

		if (currentActivity.hasListeners("resume")) {
			currentActivity.fireEvent("resume", null);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (currentActivity.hasListeners("stop")) {
			currentActivity.fireEvent("stop", null);
		}

	}	
	
	@Override
	public void finish() 
	{
		Intent intent = getIntent();
		if (intent != null) {
			if (intent.getBooleanExtra("closeOnExit", false)) {
				Application app = getApplication();
				if (app != null && app instanceof TiApplication) {
					TiApplication tiApp = (TiApplication) app;
					if (tiApp != null) {
						TiRootActivity rootActivity = tiApp.getRootActivity();
						if (rootActivity != null) {
							rootActivity.finish();
						}
					}
				}
			}
		}
	
		super.finish();
	}

	// ------- MENU SUPPORT
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		return currentActivity.hasProperty("menu");
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		MenuItemProxy mip = itemMap.get(item.getItemId());
		if (mip != null) {
			mip.fireEvent("click", null);
			return true;
		}
		return false;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) 
	{
		menu.clear();
		MenuProxy mp = (MenuProxy) currentActivity.getProperty("menu");
		if (mp != null) {
			ArrayList<MenuItemProxy> menuItems = mp.getMenuItems();
			itemMap = new HashMap<Integer, MenuItemProxy>(menuItems.size());
			int id = 0;

			for (MenuItemProxy mip : menuItems) {
				String title = TiConvert.toString(mip.getProperty("title"));
				if (title != null) {
					MenuItem mi = menu.add(0, id, 0, title);
					itemMap.put(id, mip);
					id += 1;

					String iconPath = TiConvert.toString(mip.getProperty("icon"));
					if (iconPath != null) {
						Drawable d = null;
						TiFileHelper tfh = new TiFileHelper(this);
						d = tfh.loadDrawable(tiContext, iconPath, false);
						if (d != null) {
							mi.setIcon(d);
						}
					}
				}
			}
			return true;
		}
		return false;
	}
	
	// Activity Support
	public int getUniqueResultCode() {
		if (supportHelper == null) {
			this.supportHelper = new TiActivitySupportHelper(this);
		}
		return supportHelper.getUniqueResultCode();
	}

	public void launchActivityForResult(Intent intent, int code, TiActivityResultHandler resultHandler)
	{
		if (supportHelper == null) {
			this.supportHelper = new TiActivitySupportHelper(this);
		}
		supportHelper.launchActivityForResult(intent, code, resultHandler);
	}
	
	public void setMenuDispatchListener(ITiMenuDispatcherListener listener) {
		// TODO This just a no-op for now, remove when we refactor
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		supportHelper.onActivityResult(requestCode, resultCode, data);
	}
}
