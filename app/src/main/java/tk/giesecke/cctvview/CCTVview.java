package tk.giesecke.cctvview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import tk.giesecke.cctvview.Onvif.OnvifDevice;
import tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities;
import tk.giesecke.cctvview.Onvif.OnvifDeviceInformation;
import tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces;
import tk.giesecke.cctvview.Onvif.OnvifDeviceScopes;
import tk.giesecke.cctvview.Onvif.OnvifMediaProfiles;
import tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri;
import tk.giesecke.cctvview.Onvif.OnvifPtzAbsoluteMove;
import tk.giesecke.cctvview.Onvif.OnvifPtzContinuousMove;
import tk.giesecke.cctvview.Onvif.OnvifPtzConfiguration;
import tk.giesecke.cctvview.Onvif.OnvifPtzConfigurations;
import tk.giesecke.cctvview.Onvif.OnvifPtzNode;
import tk.giesecke.cctvview.Onvif.OnvifPtzNodes;
import tk.giesecke.cctvview.Onvif.OnvifPtzStop;
import tk.giesecke.cctvview.Rtsp.RtspClient;

import static tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities.capabilitiesToString;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities.getCapabilitiesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceCapabilities.parseCapabilitiesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceDNS.getDNSCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceInformation.deviceInformationToString;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceInformation.getDeviceInformationCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceInformation.parseDeviceInformationResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkDefaultGateway.getNetGatewayCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces.getNetInterfacesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces.interfacesToString;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkInterfaces.parseNetworkInterfacesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceNetworkProtocols.getNetProtocolsCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceScopes.getScopesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceScopes.parseScopesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifDeviceScopes.scopesToString;
import static tk.giesecke.cctvview.Onvif.OnvifHeaderBody.getAuthorizationHeader;
import static tk.giesecke.cctvview.Onvif.OnvifHeaderBody.getEnvelopeEnd;
import static tk.giesecke.cctvview.Onvif.OnvifHeaderBody.simpleSoapFormatter;
import static tk.giesecke.cctvview.Onvif.OnvifMediaOSDs.getOSDsCommand;
import static tk.giesecke.cctvview.Onvif.OnvifMediaProfiles.getProfilesCommand;
import static tk.giesecke.cctvview.Onvif.OnvifMediaProfiles.parseProfilesResponse;
import static tk.giesecke.cctvview.Onvif.OnvifMediaProfiles.profilesToString;
import static tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri.getStreamUriCommand;
import static tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri.parseStreamUriResponse;
import static tk.giesecke.cctvview.Onvif.OnvifMediaStreamUri.streamUriToString;

public class CCTVview extends AppCompatActivity
		implements View.OnClickListener {

	/** Debug tag */
	public static final String DEBUG_LOG_TAG = "CCTV_VIEW";

	/** Shared preferences */
	public static SharedPreferences sharedPref;

	/** User selected device for streaming */
	public static OnvifDevice selectedDevice = null;

	/** Screen lock to keep screen on while streaming the video */
	private PowerManager.WakeLock screenLock;

	/** Screen width in pixel of this device */
	private static int screenWidth;
	/** Flag for streaming video */
	private boolean isPlaying = false;

	/** Counter for initial device information collection */
	private int initComCnt;
	/** View of main UI */
	private RelativeLayout rlUiId;
	/** View for start up message */
	private TextView tvStartup;
	/** Action menu */
	private Menu actionMenu;

	/** PTZ movement button up */
	private ImageButton ptzUp;
	/** PTZ movement button down */
	private ImageButton ptzDown;
	/** PTZ movement button left */
	private ImageButton ptzLeft;
	/** PTZ movement button right */
	private ImageButton ptzRight;

	// For RtspClient
	/** View for RtspClient */
	private SurfaceView svRtsp;
	/** RtspClient */
	private RtspClient viewClient;

	/** Flag for selected player */
	private String selectedPlayer = "";

	@Override
	protected void onSaveInstanceState(final Bundle outState) {
		outState.putSerializable("selectedPlayer", selectedPlayer);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.cctv_view);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Enable access to internet
		/* ThreadPolicy to get permission to access internet */
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		// Get layout views
		rlUiId = (RelativeLayout) findViewById(R.id.rl_ui);
		tvStartup = (TextView) findViewById(R.id.tv_startup);

		// Get PTZ control buttons
		ptzUp = (ImageButton) findViewById(R.id.ib_move_up);
		ptzDown = (ImageButton) findViewById(R.id.ib_move_down);
		ptzLeft = (ImageButton) findViewById(R.id.ib_move_left);
		ptzRight = (ImageButton) findViewById(R.id.ib_move_right);

		// For RtspClient
		svRtsp = (SurfaceView) findViewById(R.id.sv_rtsp);

		// Show the start up message instead of the main UI
		rlUiId.setVisibility(View.GONE);
		tvStartup.setVisibility(View.VISIBLE);
		togglePTZView(false);
	}

	@Override
	public void onResume() {
		// Get screen size
		Display display = getWindowManager().getDefaultDisplay();
		Point size = new Point();
		display.getSize(size);
		screenWidth = size.x;
		/* Screen Height in pixel of this device */
		int screenHeight = size.y;
		// Get screen orientation
		/* Screen orientation */
		boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "Display resolution: "
				+ screenWidth + "x"
				+ screenHeight + " in "
				+ ((isLandscape)?"landscape":"portrait"));

		// Get saved preferences
		sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		// Get views
		if (rlUiId == null) rlUiId = (RelativeLayout) findViewById(R.id.rl_ui);
		if (tvStartup == null) tvStartup = (TextView) findViewById(R.id.tv_startup);

		// Check if we are on the local WiFi network
		if (!isHomeWiFi(this)) {
			tvStartup.setText(getString(R.string.NO_WIFI));
		} else {
			selectedDevice = new OnvifDevice();

			// If no camera URL is given, start the preferences
			if (sharedPref.getString(OnvifSettings.PREF_IP, "").equalsIgnoreCase("")) {
				Intent myIntent = new Intent(this, OnvifSettings.class);
				this.startActivity(myIntent);
			} else { // else try to connect to the previously selected camera
				selectedDevice.baseUrl = sharedPref.getString(OnvifSettings.PREF_IP, "");
				selectedDevice.webPath = sharedPref.getString(OnvifSettings.PREF_WEB_PATH, "/onvif/device_service");
				try {
					selectedDevice.webPort = Integer.parseInt(sharedPref.getString(OnvifSettings.PREF_WEB_PORT, "5000"));
				} catch (NumberFormatException ignore) {
					selectedDevice.webPort = 5000;
				}
				selectedDevice.rtspPath = sharedPref.getString(OnvifSettings.PREF_RTSP_PATH, "/onvif1");
				try {
					selectedDevice.rtspPort = Integer.parseInt(sharedPref.getString(OnvifSettings.PREF_RTSP_PORT, "554"));
				} catch (NumberFormatException ignore) {
					selectedDevice.rtspPort = 554;
				}
				selectedDevice.userName = sharedPref.getString(OnvifSettings.PREF_USER, "");
				selectedDevice.passWord = sharedPref.getString(OnvifSettings.PREF_PWD, "");
				try {
					selectedDevice.discoveredURL = new URL("http",selectedDevice.baseUrl,selectedDevice.webPort,selectedDevice.webPath);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}

				selectedDevice.webURL = selectedDevice.discoveredURL.toString();
				selectedDevice.rtspURL = "rtsp://"
						+ selectedDevice.baseUrl + ":"
						+ selectedDevice.rtspPort
						+ selectedDevice.rtspPath;

				selectedDevice.devInfo = new OnvifDeviceInformation();
				selectedDevice.scopes = new OnvifDeviceScopes();
				selectedDevice.devCapabilities = new OnvifDeviceCapabilities();
				selectedDevice.mediaProfiles[0] = new OnvifMediaProfiles();
				selectedDevice.mediaProfiles[1] = new OnvifMediaProfiles();
				selectedDevice.devNetInterface = new OnvifDeviceNetworkInterfaces();
				selectedDevice.mediaStreamUri = new OnvifMediaStreamUri();

				// Get initial camera information
				initComCnt = 0;
				new ONVIFcommunication().execute(getScopesCommand(), "scopes", "init");
				new ONVIFcommunication().execute(getCapabilitiesCommand(), "capabilities", "init");
				new ONVIFcommunication().execute(getProfilesCommand(), "profiles", "init");
			}
		}

		super.onResume();
	}

	@Override
	protected void onPause() {
		stopPlaybacks();
		super.onPause();
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.ib_move_up:
				new ONVIFcommunication().execute
						(OnvifPtzContinuousMove.getContinuousMoveCommand(0, 1, 0, selectedDevice.mediaProfiles[0].ptzNodeToken), "move", "");
				break;
			case R.id.ib_move_left:
				new ONVIFcommunication().execute
						(OnvifPtzContinuousMove.getContinuousMoveCommand(-1, 0, 0, selectedDevice.mediaProfiles[0].ptzNodeToken), "move", "");
				break;
			case R.id.ib_move_right:
				new ONVIFcommunication().execute
						(OnvifPtzContinuousMove.getContinuousMoveCommand(1, 0, 0, selectedDevice.mediaProfiles[0].ptzNodeToken), "move", "");
				break;
			case R.id.ib_move_down:
				new ONVIFcommunication().execute
						(OnvifPtzContinuousMove.getContinuousMoveCommand(0, -1, 0, selectedDevice.mediaProfiles[0].ptzNodeToken), "move", "");
				break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.onvif_menu, menu);
		actionMenu = menu;
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.action_commands:
				parseOnvifCmdButtons();
				break;
			case R.id.action_players:
				if (isPlaying) {
					stopPlaybacks();
					isPlaying = false;
					actionMenu.getItem(0).setIcon(getDrawable(android.R.drawable.ic_media_play));
					screenLock.release();
				} else {
					startRtspClientPlayer();
					isPlaying = true;
					actionMenu.getItem(0).setIcon(getDrawable(android.R.drawable.ic_media_pause));
					screenLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(
							PowerManager.PARTIAL_WAKE_LOCK, "TAG");
					screenLock.acquire();
				}
				break;
			case R.id.action_settings:
				Intent myIntent = new Intent(this, OnvifSettings.class);
				this.startActivity(myIntent);
				finish();
				break;
			case R.id.action_exit:
				stopPlaybacks();
				finish();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Communication in Async Task between Android and Arduino Yun
	 */
	private class ONVIFcommunication extends AsyncTask<String, String, String[]> {

		/**
		 * Background process of communication
		 *
		 * @param params
		 * 		params[0] = soapRequest as string
		 * @return <code>String</code>
		 * SOAP XML response from ONVIF device
		 */
		@Override
		protected String[] doInBackground(String... params) {

			String[] result = new String[4];
			result[0] = params[0]; // Get Onvif request
			result[1] = params[1]; // Get type of request (get, set or movement)
			if (params.length == 3) {
				result[2] = params[2]; // Display parsed results or not
			} else {
				result[2] = "";
			}
			result[3] = "ok"; // Result of communication attempt, used by onPostExecute


			/* A HTTP client to access the ONVIF device */
			// Set timeout to 5 minutes in case we have a lot of data to load
			OkHttpClient client = new OkHttpClient.Builder()
					.connectTimeout(300, TimeUnit.SECONDS)
					.writeTimeout(10, TimeUnit.SECONDS)
					.readTimeout(300, TimeUnit.SECONDS)
					.build();

			MediaType reqBodyType = MediaType.parse("application/soap+xml; charset=utf-8;");

			RequestBody reqBody = RequestBody.create(reqBodyType,
					getAuthorizationHeader() + result[0] + getEnvelopeEnd());

			/* Request to ONVIF device */
			Request request = null;
			try {
				request = new Request.Builder()
						.url(selectedDevice.webURL)
						.post(reqBody)
						.build();
			} catch (IllegalArgumentException e) {
				result[0] = e.getMessage();
			}

			if (request != null) {
				try {
					/* Response from ONVIF device */
					Response response = client.newCall(request).execute();
					if (response.code() != 200) {
						result[0] = response.code() + " - " + response.message();
						result[3] = "failed";
					} else {
						result[0] = response.body().string();
					}
				} catch (IOException e) {
					result[0] = e.getMessage();
					result[3] = "failed";
				}
			}
			return result;
		}

		/**
		 * Called when AsyncTask background process is finished
		 *
		 * @param result
		 * 		St_CommResult with requester ID and result of communication
		 */
		protected void onPostExecute(String[] result) {
			parseOnvifResponses(result);
		}
	}

	private void parseOnvifCmdButtons() {
		final String[] onvifCmds = new String[] {
				getString(R.string.bt_txt_devinfo),
				getString(R.string.bt_txt_scopes),
				getString(R.string.bt_txt_capabilities),
				getString(R.string.bt_txt_profiles),
				getString(R.string.bt_txt_netgateway),
				getString(R.string.bt_txt_dns),
				getString(R.string.bt_txt_netinterfaces),
				getString(R.string.bt_txt_netprotocols),
				getString(R.string.bt_txt_streamuri),
				getString(R.string.bt_txt_osds),
				getString(R.string.bt_txt_ptzgetnodes),
				getString(R.string.bt_txt_ptzgetnode),
				getString(R.string.bt_txt_ptzgetconfigs),
				getString(R.string.bt_txt_ptzgetconfig),
				getString(R.string.bt_txt_ptzstop),
				getString(R.string.bt_txt_ptztest1)};

		AlertDialog.Builder onvifCmdsBuilder = new AlertDialog.Builder(this);
		onvifCmdsBuilder.setTitle(getString(R.string.bt_txt_onvifcmds));
		onvifCmdsBuilder.setItems(onvifCmds, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int item) {
				switch (item) {
					case 0: // Device info
						new ONVIFcommunication().execute(getDeviceInformationCommand(), "devinfo", "");
						break;
					case 1: // Device scopes
						new ONVIFcommunication().execute(getScopesCommand(), "scopes", "");
						break;
					case 2: // Device capabilities
						new ONVIFcommunication().execute(getCapabilitiesCommand(), "capabilities", "");
						break;
					case 3: // Device profiles
						new ONVIFcommunication().execute(getProfilesCommand(), "profiles", "");
						break;
					case 4: // Network default gateway
						new ONVIFcommunication().execute(getNetGatewayCommand(), "netgate", "");
						break;
					case 5: // Network DNS info
						new ONVIFcommunication().execute(getDNSCommand(), "dns", "");
						break;
					case 6: // Network interfaces
						new ONVIFcommunication().execute(getNetInterfacesCommand(), "ifaces", "");
						break;
					case 7: // Network protocols
						new ONVIFcommunication().execute(getNetProtocolsCommand(), "netproto", "");
						break;
					case 8: // Stream URI
						new ONVIFcommunication().execute(getStreamUriCommand(), "streamuri", "");
						break;
					case 9: // OSDs
						new ONVIFcommunication().execute(getOSDsCommand(selectedDevice.mediaProfiles[0].videoSourceConfigToken), "osds", "");
						break;
					case 10: // PTZ get nodes
						new ONVIFcommunication().execute(OnvifPtzNodes.getNodesCommand(), "getNodes");
						break;
					case 11: // PTZ get node
						new ONVIFcommunication().execute(OnvifPtzNode.getNodeCommand(selectedDevice.mediaProfiles[0].ptzConfigToken), "getNode");
						break;
					case 12: // PTZ get configurations
						new ONVIFcommunication().execute(OnvifPtzConfigurations.getConfigsCommand(), "getConfigs");
						break;
					case 13: // PTZ get configuration
						new ONVIFcommunication().execute(OnvifPtzConfiguration.getConfigCommand(selectedDevice.mediaProfiles[0].ptzConfigToken), "getConfig");
						break;
					case 14: // PTZ stop
						new ONVIFcommunication().execute(OnvifPtzStop.getStopCommand(selectedDevice.mediaProfiles[0].ptzNodeToken), "stopPTZ");
						break;
					case 15: // PTZ absolute move
						new ONVIFcommunication().execute(OnvifPtzAbsoluteMove.getAbsoluteMoveCommand(100, 100, 0, selectedDevice.mediaProfiles[0].ptzNodeToken), "move", "");
						break;
				}
				dialog.dismiss();
			}
		});

		onvifCmdsBuilder.setCancelable(true)
				.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						//When clicked on CANCEL button the dalog will be dismissed
						dialog.dismiss();
					}

				});
		AlertDialog onvifCmdsDialog = onvifCmdsBuilder.create();
		onvifCmdsDialog.show();
	}

	private void parseOnvifResponses(String[] result) {
		String parsedResult = "Parsing failed";
		if (result[3].equalsIgnoreCase("failed")) {
			parsedResult = "Communication error:\n\n" + result[0];
			tvStartup.setText(parsedResult);
		} else {
			if (result[1].equalsIgnoreCase("devinfo")) {
				if (parseDeviceInformationResponse(result[0], selectedDevice.devInfo)) {
					parsedResult = deviceInformationToString(selectedDevice.devInfo);
				}
			} else if (result[1].equalsIgnoreCase("scopes")) {
				if (parseScopesResponse(result[0], selectedDevice.scopes)) {
					parsedResult = scopesToString(selectedDevice.scopes);
				}
			} else if (result[1].equalsIgnoreCase("capabilities")) {
				if (parseCapabilitiesResponse(result[0], selectedDevice.devCapabilities)) {
					parsedResult = capabilitiesToString(selectedDevice.devCapabilities);
				}
			} else if (result[1].equalsIgnoreCase("profiles")) {
				String part1Profile = result[0].substring(result[0].indexOf("<trt:Profiles token"),
						result[0].indexOf("</trt:Profiles>") + 15);
				if (parseProfilesResponse(part1Profile, selectedDevice.mediaProfiles[0])) {
					parsedResult = profilesToString(selectedDevice.mediaProfiles[0]);
				}
				part1Profile = result[0].substring(result[0].lastIndexOf("<trt:Profiles token"),
						result[0].lastIndexOf("</trt:Profiles>") + 15);
				if (parseProfilesResponse(part1Profile, selectedDevice.mediaProfiles[1])) {
					parsedResult += profilesToString(selectedDevice.mediaProfiles[1]);
				}
			} else if (result[1].equalsIgnoreCase("netgate")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parseNetworkDefaultGatewayResponse(result[0], selectedDevice.devDefaultGateway)) {
//					parsedResult = defaultGatewayToString(selectedDevice.devDefaultGateway);
//				}
			} else if (result[1].equalsIgnoreCase("dns")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parseDNSResponse(result[0], selectedDevice.devDNS)) {
//					parsedResult = dnsToString(selectedDevice.devDNS);
//				}
			} else if (result[1].equalsIgnoreCase("ifaces")) {
				if (parseNetworkInterfacesResponse(result[0], selectedDevice.devNetInterface)) {
					parsedResult = interfacesToString(selectedDevice.devNetInterface);
				}
			} else if (result[1].equalsIgnoreCase("netproto")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parseNetworkProtocolResponse(result[0], selectedDevice.devNetProtocols)) {
//					parsedResult = netProtocolToString(selectedDevice.devNetProtocols);
//				}
			} else if (result[1].equalsIgnoreCase("streamuri")) {
				if (parseStreamUriResponse(result[0], selectedDevice.mediaStreamUri)) {
					parsedResult = streamUriToString(selectedDevice.mediaStreamUri);
				}
			} else if (result[1].equalsIgnoreCase("osds")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parseOSDSResponse(result[0], selectedDevice.mediaOSDs)) {
//					parsedResult = osdsToString(selectedDevice.mediaOSDs);
//				}
			} else if (result[1].equalsIgnoreCase("getNode")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parsePtzNodeResponse(result[0], selectedDevice.ptzNode)) {
//					parsedResult = ptzNodeToString(selectedDevice.ptzNode);
//				}
			} else if (result[1].equalsIgnoreCase("getNodes")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parsePtzNodesResponse(result[0], selectedDevice.ptzNodes)) {
//					parsedResult = ptzNodesToString(selectedDevice.ptzNodes);
//				}
			} else if (result[1].equalsIgnoreCase("getConfigs")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parsePtzConfigurationsResponse(result[0], selectedDevice.ptzConfigs)) {
//					parsedResult = ptzConfigsToString(selectedDevice.ptzConfigs);
//				}
			} else if (result[1].equalsIgnoreCase("getConfig")) {
				parsedResult = simpleSoapFormatter(
						result[0].substring(
								result[0].indexOf("<SOAP-ENV:Body>")+15,
								result[0].indexOf("</SOAP-ENV:Body>")));
//				if (parsePtzConfigurationResponse(result[0], selectedDevice.ptzConfig)) {
//					parsedResult = ptzConfigToString(selectedDevice.ptzConfig);
//				}
			} else if (result[1].equalsIgnoreCase("move")) {
				parsedResult = "";
				new ONVIFcommunication().execute(OnvifPtzStop.getStopCommand(selectedDevice.mediaProfiles[0].ptzNodeToken), "stopPTZ");
			} else if (result[1].equalsIgnoreCase("zoom")) {
				new ONVIFcommunication().execute(OnvifPtzStop.getStopCommand(selectedDevice.mediaProfiles[0].ptzNodeToken), "stopZ");
				parsedResult = "";
			} else if (result[1].equalsIgnoreCase("stopPTZ")) {
				parsedResult = "";
			} else if (result[1].equalsIgnoreCase("stopZ")) {
				parsedResult = "";
			}

			if (result[2].equalsIgnoreCase("init")) {
				parsedResult = "";
				initComCnt += 1;
				if (initComCnt == 3) {
					rlUiId.setVisibility(View.VISIBLE);
					tvStartup.setVisibility(View.GONE);

					stopPlaybacks();
					startRtspClientPlayer();
					isPlaying = true;
					actionMenu.getItem(0).setIcon(getDrawable(android.R.drawable.ic_media_pause));
				}
			}
		}

		if (!parsedResult.isEmpty()) {
			Snackbar mySnackbar = Snackbar.make(findViewById(android.R.id.content),
					parsedResult,
					Snackbar.LENGTH_INDEFINITE);
			mySnackbar.setAction("OK", mOnClickListener);
			View snackbarView = mySnackbar.getView();
			TextView tv = (TextView) snackbarView.findViewById(android.support.design.R.id.snackbar_text);
			tv.setMaxLines(300);
			mySnackbar.show();
		}
	}

	private void stopPlaybacks() {

		if (selectedPlayer.equalsIgnoreCase("RtspClient")) {
			if (viewClient.isStarted()) {
				viewClient.abort();
				viewClient.shutdown();
			}
		}
		svRtsp.setVisibility(View.GONE);
		togglePTZView(false); // Hide PTZ controls
	}

	private void startRtspClientPlayer() {
		selectedPlayer = "RtspClient";
		svRtsp.setVisibility(View.VISIBLE);

		if ((viewClient != null) && (viewClient.isStarted())) { // Check if streaming is already running
			return;
		}

		// Try to set the surface view size matching for orientation
		/* View height to match screen resolution */
		int viewHeight =
				(screenWidth * selectedDevice.mediaProfiles[0].videoSourceHeight)
						/ selectedDevice.mediaProfiles[0].videoSourceWidth;
		RelativeLayout.LayoutParams clParams =
				new RelativeLayout.LayoutParams(
						RelativeLayout.LayoutParams.MATCH_PARENT,
						viewHeight);
		clParams.addRule(RelativeLayout.CENTER_IN_PARENT);
		svRtsp.setLayoutParams(clParams);

		if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "VideoWidth: " + svRtsp.getWidth() + " VideoHeight: " + svRtsp.getHeight());

		viewClient = new RtspClient(selectedDevice.rtspURL);
		viewClient.setSurfaceView(svRtsp);

		viewClient.setOnStreamStarted(new RtspClient.onStreamStartedListener() {
			@Override
			public void onStreamStarted(RtspClient.SDPInfo sdpInfo) {
				// Do what is needed after the streaming has / is starting
				if (BuildConfig.DEBUG) Log.d(DEBUG_LOG_TAG, "sdpInfo: " + sdpInfo.PPS + " -- " + sdpInfo.SPS);
			}
		});

		viewClient.start();
		togglePTZView(true);
	}

	private void togglePTZView(boolean showPTZ) {
		int visibilityVal = View.GONE;
		if (showPTZ) {
			visibilityVal = View.VISIBLE;
		}
		ptzUp.setVisibility(visibilityVal);
		ptzDown.setVisibility(visibilityVal);
		ptzLeft.setVisibility(visibilityVal);
		ptzRight.setVisibility(visibilityVal);
	}

	/**
	 * Check WiFi connection and return SSID
	 *
	 * @param thisAppContext
	 * 		application context
	 * @return <code>String</code>
	 * SSID name or empty string if not connected
	 */
	@SuppressWarnings("deprecation")
	private static Boolean isHomeWiFi(Context thisAppContext) {
		/* Access to connectivity manager */
		ConnectivityManager cm = (ConnectivityManager) thisAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		/* WiFi connection information  */
		NetworkInfo wifiOn = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		if (!wifiOn.isConnected()) {
			return false;
		} else {
			/* WiFi manager to check current connection */
			@SuppressLint("WifiManagerPotentialLeak")
			final WifiManager wifiManager = (WifiManager) thisAppContext.getSystemService(Context.WIFI_SERVICE);
			/* Info of current connection */
			final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
			if (connectionInfo != null && !TextUtils.isEmpty(connectionInfo.getSSID())) {
				String currentSSID = connectionInfo.getSSID();
				currentSSID = currentSSID.substring(1, currentSSID.length() - 1);
				String primLocalSSID = thisAppContext.getResources().getString(R.string.LOCAL_SSID);
				String altLocalSSID = thisAppContext.getResources().getString(R.string.ALT_LOCAL_SSID);
				return ((currentSSID.equalsIgnoreCase(primLocalSSID)) || (currentSSID.equalsIgnoreCase(altLocalSSID)));
			}
		}
		return false;
	}

	public static final View.OnClickListener mOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) { // Nothing to do here (for now)
		}
	};
}
