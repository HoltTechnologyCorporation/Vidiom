package au.com.infiniterecursion.vidiom.activity;

import java.io.File;
import java.security.acl.LastOwnerException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.http.AccessToken;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import au.com.infiniterecursion.vidiom.VidiomApp;
import au.com.infiniterecursion.vidiom.facebook.LoginButton;
import au.com.infiniterecursion.vidiom.utils.DBUtils;
import au.com.infiniterecursion.vidiom.utils.DatabaseHelper;
import au.com.infiniterecursion.vidiom.utils.PublishingUtils;
import au.com.infiniterecursion.vidiompro.R;

/*
 * Vidiom Library Activity 
 * 
 * AUTHORS:
 * 
 * Andy Nicholson
 * 
 * 2010
 * Copyright Infinite Recursion Pty Ltd.
 * http://www.infiniterecursion.com.au
 */

public class LibraryActivity extends ListActivity implements VidiomActivity {

	// Database
	private DBUtils dbutils;
	private String[] video_absolutepath;
	private String[] video_filename;
	private Integer[] video_ids;

	// Variable to hold the last chosen hosted URL for a video which has more
	// than one uploaded URL available.
	private String hosted_url_chosen;

	private boolean videos_available;

	// Context MENU
	private static final int MENU_ITEM_1 = Menu.FIRST;
	private static final int MENU_ITEM_2 = MENU_ITEM_1 + 1;
	private static final int MENU_ITEM_3 = MENU_ITEM_2 + 1;
	private static final int MENU_ITEM_4 = MENU_ITEM_3 + 1;
	private static final int MENU_ITEM_5 = MENU_ITEM_4 + 1;
	private static final int MENU_ITEM_6 = MENU_ITEM_5 + 1;
	private static final int MENU_ITEM_7 = MENU_ITEM_6 + 1;
	private static final int MENU_ITEM_8 = MENU_ITEM_7 + 1;
	private static final int MENU_ITEM_9 = MENU_ITEM_8 + 1;

	// options MENU
	private static final int MENU_ITEM_10 = MENU_ITEM_9 + 1;
	private static final int MENU_ITEM_11 = MENU_ITEM_10 + 1;

	// Context MENU
	private static final int MENU_ITEM_12 = MENU_ITEM_11 + 1;
	private static final int MENU_ITEM_13 = MENU_ITEM_12 + 1;
	// options MENU
	private static final int MENU_ITEM_14 = MENU_ITEM_13 + 1;
	// edit
	private static final int MENU_ITEM_15 = MENU_ITEM_14 + 1;

	private LoginButton lb;
	private AlertDialog fb_dialog;

	private static final String TAG = "VidiomTag-Library";
	private static final int NOTIFICATION_ID = 2;

	// Activity codes for launchers , when using multiple hosted URL picker.
	private static final int EMAIL = 0;
	private static final int VIEW = 1;
	private static final int TWEET = 2;

	private PublishingUtils pu;

	private Handler handler;
	private String emailPreference;
	private SimpleCursorAdapter listAdapter;
	private Cursor libraryCursor;

	private VidiomApp mainapp;

	private String moviePath;
	private String moviefilename;
	private String[] hosted_urls;
	private long sdrecord_id;

	private SharedPreferences prefs;

	private Resources res;

	private boolean importPreference;
	private boolean got_facebook_sso_callback;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		res = getResources();
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		emailPreference = prefs.getString("emailPreference", null);
		importPreference = prefs.getBoolean("importPreference", true);

		Log.d(TAG, " onCreate ");
		mainapp = (VidiomApp) getApplication();
		dbutils = mainapp.getDBUtils();

		pu = new PublishingUtils(getResources(), mainapp);
		handler = new Handler();

		got_facebook_sso_callback = false;

	}

	public void onResume() {
		super.onResume();

		Log.d(TAG, " onResume ");
		setContentView(R.layout.library_layout);

		makeCursorAndAdapter();

		registerForContextMenu(getListView());

		lb = new LoginButton(this);
		lb.init(mainapp.getFacebook(), VidiomApp.FB_LOGIN_PERMISSIONS, this);

		if (importPreference) {
			ImporterThread importer = new ImporterThread();
			importer.run();
		}

		if (got_facebook_sso_callback) {
			got_facebook_sso_callback = false;
			// show facebook dialog again
			Log.d(TAG, "We have been called back by fb sso, showing dialog");
			showFacebookOptionsMenu();

		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, " onDestroy ");
		if (libraryCursor != null) {
			libraryCursor.close();
		}
		if (dbutils != null) {
			dbutils.close();
		}
	}

	@Override
	public void onPause() {

		super.onPause();
		Log.d(TAG, "On pause");

		if (fb_dialog != null && fb_dialog.isShowing()) {
			Log.d(TAG, "Dismissing fb dialog");
			fb_dialog.dismiss();
			lb = null;
		}

	}

	/**
	 * 
	 * We get this callback after the facebook SSO
	 * 
	 */

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		Log.i(TAG, " got result , calling into facebook authoriseCallback");
		mainapp.getFacebook().authorizeCallback(requestCode, resultCode, data);
		got_facebook_sso_callback = true;

	}

	private class ImporterThread implements Runnable {

		public void run() {
			// Kick off the importing of existing videos.
			scanForExistingVideosAndImport();

			reloadList();

			// Set importPreference to false, to stop it running again.
			Editor editor = prefs.edit();
			editor.putBoolean("importPreference", false);
			editor.commit();
		}

		public void scanForExistingVideosAndImport() {
			boolean mExternalStorageAvailable = false;
			boolean mExternalStorageWriteable = false;
			File rootfolder = Environment.getExternalStorageDirectory();

			String state = Environment.getExternalStorageState();
			if (Environment.MEDIA_MOUNTED.equals(state)) {
				mExternalStorageAvailable = mExternalStorageWriteable = true;
			} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
				mExternalStorageAvailable = true;
				mExternalStorageWriteable = false;
			} else {
				mExternalStorageAvailable = mExternalStorageWriteable = false;
			}

			// OK, start, if we support v10 or higher
			if (mExternalStorageAvailable && mainapp.support_v10) {
				Log.d(TAG,
						"Starting import. OUR Directory path is : "
								+ Environment.getExternalStorageDirectory()
										.getAbsolutePath()
								+ res.getString(R.string.rootSDcardFolder));
				directoryScanRecurse(rootfolder);
			}

			Log.d(TAG, " Import FINISHED !");
		}

		@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
		public void directoryScanRecurse(File directory) {

			if (directory == null) {
				return;
			}

			// Recursive routine for finding existing videos.
			// Dont import from our own directory.
			// Log.d(TAG, "Scanning directory " + directory.getAbsolutePath());
			if (!directory.getAbsolutePath().equals(
					Environment.getExternalStorageDirectory().getAbsolutePath()
							+ res.getString(R.string.rootSDcardFolder))) {

				File[] files = directory.listFiles();

				if (files == null) {
					return;
				}

				for (File f : files) {
					if (f.isDirectory()) {
						// recurse
						directoryScanRecurse(f);
					} else if (f.isFile()) {
						// Check its a video file
						String name = f.getName();
						if (name.matches(".*\\.mp4")
								|| name.matches(".*\\.3gp")) {
							// found a video file.
							// add it to the library.
							// Log.d(TAG, "Found " + f.getAbsolutePath());

							// Check this path isnt already in DB
							String[] fp = new String[] { f.getAbsolutePath() };
							boolean alreadyInDB = dbutils.checkFilePathInDB(fp);

							// If not, insert a record into the DB
							if (!alreadyInDB) {
								String filename = f.getName();

								MediaMetadataRetriever thumber = new MediaMetadataRetriever();
								String audio_video_codec = "unknown";
								String title = "Untitled";
								long duration_millis = 0;

								try {
									thumber.setDataSource(fp[0]);
									String duration = thumber
											.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
									Log.d(TAG,
											"Found filepath "
													+ f.getAbsolutePath()
													+ " has duration "
													+ duration);
									duration_millis = Long.parseLong(duration);
									audio_video_codec = thumber
											.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
									title = thumber
											.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);

									if (title == null) {
										title = "Untitled";
									}

								} catch (Exception e) {
									e.printStackTrace();
								}

								dbutils.createSDFileRecordwithNewVideoRecording(
										f.getAbsolutePath(), filename,
										(int) (duration_millis / 1000),
										audio_video_codec, title, "");

								// Send the info to the inbuilt Android Media
								// Scanner

								// Save the name and description of a video in a
								// ContentValues map.
								ContentValues values = new ContentValues(2);
								values.put(MediaStore.Video.Media.MIME_TYPE,
										"video/mp4");
								values.put(MediaStore.Video.Media.DATA, fp[0]);

								// Add a new record (identified by uri), but
								// with the values
								// just set.
								Uri uri = getContentResolver()
										.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
												values);

								sendBroadcast(new Intent(
										Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
										uri));

							}
						}

					}

				}

			} else {
				// shouldnt log the directories files.

				// Log.w(TAG, "Not logging.");
				return;
			}

			return;
		}

	}

	private class VideoFilesSimpleCursorAdapter extends SimpleCursorAdapter {

		public VideoFilesSimpleCursorAdapter(Context context, int layout,
				Cursor c, String[] from, int[] to) {
			super(context, layout, c, from, to);

		}

		@Override
		public void setViewImage(ImageView v, String s) {

			Log.d(TAG, "Finding a thumbnail : We have filepath " + s);

			String[] mediaColumns = { MediaStore.Video.Media._ID,
					MediaStore.Video.Media.DATA };

			Cursor thumb_cursor = managedQuery(
					MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaColumns,
					MediaStore.Video.Media.DATA + " = ? ", new String[] { s },
					null);

			if (thumb_cursor == null) {
				// set default icon
				if (v != null) {
					v.setImageResource(R.drawable.icon);
				}
				return;
			}

			if (thumb_cursor.moveToFirst()) {
				// Found entry for video via file path. Now we have its
				// video_id, we can check for a cache thumbnail or request to
				// generate one.

				// XXX Check cache, and load from filepath, using code above.
				
				  Log .d( TAG, "In thumb_cursor , we have id " + thumb_cursor
				  .getLong(thumb_cursor
				  .getColumnIndexOrThrow(MediaStore.Video.Media._ID)));
				 

				Bitmap bm = MediaStore.Video.Thumbnails
						.getThumbnail(
								getContentResolver(),
								thumb_cursor.getLong(thumb_cursor
										.getColumnIndexOrThrow(MediaStore.Video.Media._ID)),
								MediaStore.Video.Thumbnails.MINI_KIND, null);
				if (bm != null && v != null) {
					v.setImageBitmap(bm);
				}

			} else {
				
				Log.d(TAG, " Thumb_cursor is empty ");
				
				// set default icon
				v.setImageResource(R.drawable.icon);

				
				// Perhaps the MediaStore has been reset. We need to re-enable it
				// Send the info to the inbuilt Android Media Scanner

				// Save the name and description of a video in a
				// ContentValues map.
				ContentValues values = new ContentValues(2);
				values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
				values.put(MediaStore.Video.Media.DATA, s);

				Uri uri = getContentResolver()
						.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
								values);

				sendBroadcast(new Intent(
						Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri));
				
			}

		}
	}

	private void makeCursorAndAdapter() {
		dbutils.genericWriteOpen();

		// SELECT VIDEOS -- with dummy columns to dynamically update in list
		// view.

		String join_sql = " SELECT a.filename as filename , a.filepath as filepath, a.filepath as filepath2, a.length_secs as length_secs , a.created_datetime as created_datetime, "
				+ "a._id as _id , a.title as title, a.description as description, a.filepath as host_video_url, a.filepath as progressBar1 "
				+ " FROM videofiles a " + " ORDER BY a.created_datetime DESC ";
		libraryCursor = dbutils.rawQuery(join_sql, new String[] {});

		// This query is for videofiles (unused)
		// libraryCursor = dbutils.query(DatabaseHelper.SDFILERECORD_TABLE_NAME,
		// null, null, null, null, null,
		// DatabaseHelper.SDFileRecord.DEFAULT_SORT_ORDER);

		/*
		 * unused join String join_sql =
		 * " SELECT a.filename as filename , a.filepath as filepath, a.filepath as filepath2, a.length_secs as length_secs , a.created_datetime as created_datetime, a._id as _id , a.title as title, a.description as description, "
		 * +
		 * " b.host_uri as host_uri , b.host_video_url as host_video_url, b.host_video_url as progressBar1 FROM "
		 * + " videofiles a LEFT OUTER JOIN hosts b ON" +
		 * " a._id = b.sdrecord_id " + " ORDER BY a.created_datetime DESC ";
		 */

		if (libraryCursor == null) {
			return;
		}

		if (libraryCursor.moveToFirst()) {
			ArrayList<Integer> video_ids_al = new ArrayList<Integer>();
			ArrayList<String> video_paths_al = new ArrayList<String>();
			ArrayList<String> video_filenames_al = new ArrayList<String>();

			do {
				long video_id = libraryCursor
						.getLong(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord._ID));
				video_ids_al.add((int) video_id);

				String video_path = libraryCursor
						.getString(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILEPATH));
				video_paths_al.add(video_path);

				String video_filename = libraryCursor
						.getString(libraryCursor
								.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILENAME));
				video_filenames_al.add(video_filename);

			} while (libraryCursor.moveToNext());

			video_ids = video_ids_al.toArray(new Integer[video_ids_al.size()]);
			video_absolutepath = video_paths_al
					.toArray(new String[video_paths_al.size()]);
			video_filename = video_filenames_al
					.toArray(new String[video_filenames_al.size()]);

			videos_available = true;

		} else {

			videos_available = false;

		}

		// Make Cursor Adapter

		String[] from = new String[] { DatabaseHelper.SDFileRecord.FILENAME,
				DatabaseHelper.SDFileRecord.LENGTH_SECS,
				DatabaseHelper.SDFileRecord.CREATED_DATETIME,
				DatabaseHelper.HostDetails.HOST_VIDEO_URL,
				DatabaseHelper.SDFileRecord.TITLE,
				DatabaseHelper.SDFileRecord.DESCRIPTION,
				DatabaseHelper.SDFileRecord.FILEPATH,
				// Dummy columns to fill in more information
				"filepath2", "progressBar1"

		};

		// The list of view IDs that the above columns map to.
		int[] to = new int[] { android.R.id.text1, android.R.id.text2,
				R.id.text3, R.id.text4, R.id.text5, R.id.text6,
				R.id.videoThumbnailimageView, R.id.text7, R.id.progressBar1 };

		listAdapter = new VideoFilesSimpleCursorAdapter(this,
				R.layout.library_list_item, libraryCursor, from, to);

		listAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {

			public boolean setViewValue(View view, Cursor cursor,
					int columnIndex) {

				// Uploading progress bar
				if (columnIndex == cursor.getColumnIndexOrThrow("progressBar1")) {

					// check with dbutils if this particular video is uploading,

					long video_id = cursor.getLong(cursor
							.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord._ID));
					Log.d(TAG, "Checking ID " + video_id
							+ " for any uploads in progress.");
					HashSet<Integer> uploading_in_progress = mainapp
							.isSDFileRecordUploadingToAnyService(video_id);
					ProgressBar pb = (ProgressBar) view
							.findViewById(R.id.progressBar1);

					// We need the views' parent to get to the textview
					View p = view.getRootView();
					TextView tvpb = (TextView) p.findViewById(R.id.textPB1);
					if (tvpb != null) {
						if (uploading_in_progress == null
								|| uploading_in_progress.size() == 0) {
							Log.d(TAG, "No uploads in progress for ID:"
									+ video_id);
							pb.setIndeterminate(false);
							tvpb.setText(R.string.no_uploads_in_progress);
						} else {
							Log.d(TAG, "Some uploads in progress for ID:"
									+ video_id);
							pb.setIndeterminate(true);

							// show textually what services are being uploaded
							// to.
							String services = "";
							Iterator<Integer> iter = uploading_in_progress
									.iterator();
							while (iter.hasNext()) {
								Integer service_code = iter.next();
								String service = PublishingUtils
										.getVideoServiceStringFromServiceCode(
												getBaseContext(), service_code);

								String sep = "";
								if (iter.hasNext()) {
									sep = ",";
								}
								services = services + service + sep;
							}
							// Set the progress bar underneath text.
							tvpb.setText(getString(R.string.uploading_in_progress_)
									+ getString(R.string._services_being_used_)
									+ services);

						}
					}
					return true;
				}

				if (columnIndex == cursor.getColumnIndexOrThrow("filepath2")) {

					TextView filesizeview = (TextView) view
							.findViewById(R.id.text7);

					// Log.d(TAG, " filesizeview is " + filesizeview +
					// " filepath is " +
					// cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILEPATH)));

					String[] mediaColumns = { MediaStore.Video.Media._ID,
							MediaStore.Video.Media.SIZE };

					Cursor thumb_cursor = managedQuery(
							MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
							mediaColumns,
							MediaStore.Video.Media.DATA + " = ? ",
							new String[] { cursor.getString(cursor
									.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.FILEPATH)) },
							null);

					if (thumb_cursor != null && thumb_cursor.moveToFirst()) {

						// compute video filesize in KB
						String size_in_bytes = thumb_cursor.getString(thumb_cursor
								.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE));
						Long kilobytes = 0L;
						try {
							Long bytes = Long.valueOf(size_in_bytes);
							kilobytes = bytes / 1024;
						} catch (NumberFormatException nfe) {
							nfe.printStackTrace();
							Log.e(TAG, "Caught number format exception with "
									+ size_in_bytes);
						}
						// set size in kilobytes
						filesizeview.setText(kilobytes.toString());

					} else {
						// No file size data available
						filesizeview.setText("Unknown");
					}

					return true;
				}

				// Transform the text4 specifically, from a blank entry
				// repr. into a string saying "not uploaded yet"
				if (columnIndex == cursor
						.getColumnIndexOrThrow(DatabaseHelper.HostDetails.HOST_VIDEO_URL)) {
					long video_id = cursor.getLong(cursor
							.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord._ID));
					Log.d(TAG, "Checking ID " + video_id
							+ " for any hosted URLs.");

					// Grab all URLs from dbutils
					String[] urls = dbutils
							.getHostedURLsFromID(new String[] { Long
									.toString(video_id) });
					String url_repr = "";

					TextView host_details = (TextView) view
							.findViewById(R.id.text4);
					if (urls == null || urls.length == 0) {
						url_repr = "Not uploaded yet.";
					} else {
						for (int i = 0; i < urls.length; i++) {
							url_repr = url_repr + urls[i] + "\n";
						}
					}
					host_details.setText(url_repr);

					return true;
				}

				// Transform the text3 specifically, from time in millis to text
				// repr.
				if (columnIndex == cursor
						.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.CREATED_DATETIME)) {

					long time_in_mills = cursor.getLong(cursor
							.getColumnIndexOrThrow(DatabaseHelper.SDFileRecord.CREATED_DATETIME));
					TextView datetime = (TextView) view
							.findViewById(R.id.text3);
					datetime.setText(PublishingUtils.showDate(time_in_mills));
					return true;
				}
				return false;
			}
		});

		setListAdapter(listAdapter);

		dbutils.close();
	}

	private void launchURLsPickerDialog(final String[] urls,
			final int activity_code) {

		AlertDialog.Builder urls_picker = new AlertDialog.Builder(this);
		urls_picker.setTitle("Choose a URL");

		urls_picker.setItems(urls, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Save url chosen by user
				Log.d(TAG, "launchURLsPickerDialog - " + urls[which]);
				hosted_url_chosen = urls[which];

				switch (activity_code) {
				case EMAIL:
					doEmailActivity();
					break;
				case VIEW:
					doViewActivity();
					break;
				case TWEET:
					doTweetonThread();
					break;
				}
			}
		});

		urls_picker.show();
	}

	@Override
	protected void onListItemClick(ListView l, View v, final int position,
			long id) {
		super.onListItemClick(l, v, position, id);

		// play this selection.
		// String movieurl = video_absolutepath[(int) position];
		// Log.d(TAG, " operation on " + movieurl);

		// pu.launchVideoPlayer(this, movieurl);

		l.showContextMenuForChild(v);
	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {

		// PLAY
		MenuItem p = menu.add(0, MENU_ITEM_1, 0, R.string.library_menu_play);
		// p.setIcon(R.drawable.video48);

		// EDIT
		MenuItem ed = menu.add(0, MENU_ITEM_15, 0, R.string.library_menu_edit);
		// ed.setIcon(R.drawable.edit48);

		// TITLE / DESCRIPTION
		MenuItem rename = menu.add(0, MENU_ITEM_8, 0, R.string.rename_video);
		// rename.setIcon(R.drawable.edittitle48);

		// PUBLISHING OPTIONS
		MenuItem vb = menu.add(0, MENU_ITEM_3, 0,
				R.string.menu_publish_to_videobin);
		// vb.setIcon(R.drawable.upload48);

		MenuItem fb = menu.add(0, MENU_ITEM_5, 0,
				R.string.menu_publish_to_facebook);
		// fb.setIcon(R.drawable.fb48);

		menu.add(0, MENU_ITEM_7, 0, R.string.menu_youtube);

		menu.add(0, MENU_ITEM_6, 0, R.string.menu_ftp);

		// WORKING WITH VIDEO + HOSTED URL OPTIONS
		menu.add(0, MENU_ITEM_13, 0, R.string.menu_tweet);

		menu.add(0, MENU_ITEM_4, 0, R.string.menu_send_via_email);

		menu.add(0, MENU_ITEM_9, 0, R.string.menu_send_hosted_url_via_email);

		menu.add(0, MENU_ITEM_12, 0, R.string.menu_launch_hosted_url);

		// DELETE
		menu.add(0, MENU_ITEM_2, 0, R.string.library_menu_delete);

	}

	public boolean onContextItemSelected(MenuItem item) {

		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item
				.getMenuInfo();
		Log.d(TAG, " got " + item.getItemId() + " at position " + info.position);

		if (!videos_available) {
			return true;
		}

		moviePath = video_absolutepath[info.position];
		sdrecord_id = video_ids[info.position];
		moviefilename = video_filename[info.position];
		// hosted_url = hosted_urls[info.position];

		hosted_urls = dbutils.getHostedURLsFromID(new String[] { Long
				.toString(sdrecord_id) });

		Log.d(TAG, " operation on " + moviePath + " id " + sdrecord_id
				+ " filename " + moviefilename);

		switch (item.getItemId()) {

		case MENU_ITEM_1:
			// play
			pu.launchVideoPlayer(this, moviePath);
			break;

		case MENU_ITEM_15:
			// edit
			// shuffle off to a new Activity
			if (!mainapp.support_v10) {
				// Sorry!!
				//
				AlertDialog gingerbread_mr1 = new AlertDialog.Builder(this)
						.setMessage(
								"Sorry! Your phone doesn't support obtaining image thumbnails at arbitrary video frames.\nTo support this you need to have Android 2.3.3 or above installed.\nYou can still edit your videos but you cannot see the video thumbnails.")
						.setPositiveButton(R.string.yes,
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int whichButton) {

										Intent intent2 = new Intent().setClass(
												LibraryActivity.this,
												EditorActivity.class);
										intent2.putExtra(
												getString(R.string.EditorActivityFilenameKey),
												moviePath);

										LibraryActivity.this
												.startActivity(intent2);

									}
								}).show();
			} else {

				// just run it directly..
				Intent intent2 = new Intent().setClass(this,
						EditorActivity.class);
				intent2.putExtra(getString(R.string.EditorActivityFilenameKey),
						moviePath);

				this.startActivity(intent2);
			}

			break;

		case MENU_ITEM_2:
			// delete

			// ask if sure they want to delete ?
			AlertDialog delete_dialog = new AlertDialog.Builder(this)
					.setMessage(R.string.really_delete_video)
					.setPositiveButton(R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

									// deleting files,
									if (!pu.deleteVideo(moviePath)) {
										Log.w(TAG, "Cant delete file "
												+ moviePath);

									}
									// and removing DB records!
									if (dbutils.deleteSDFileRecord(sdrecord_id) == -1) {
										Log.w(TAG, "Cant delete record "
												+ sdrecord_id);
									}

									reloadList();

								}
							})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

								}
							}).show();

			break;

		case MENU_ITEM_3:
			// publish to video bin

			// Don't allow simultaneous uploads to video bin for the same video.
			if (!mainapp.isSDFileRecordUploading(sdrecord_id,
					PublishingUtils.TYPE_VB)) {

				mainapp.addSDFileRecordIDtoUploadingTrack(sdrecord_id,
						PublishingUtils.TYPE_VB);

				String[] strs_vb = dbutils
						.getTitleAndDescriptionFromID(new String[] { Long
								.toString(sdrecord_id) });
				// start videobin thread
				pu.videoUploadToVideoBin(this, handler, moviePath, strs_vb[0],
						strs_vb[1], emailPreference, sdrecord_id);

				reloadList();

			} else {
				showAlertDialogUploadingInProgress();
			}

			break;

		case MENU_ITEM_4:
			// email
			pu.launchEmailIntentWithCurrentVideo(this, moviePath);
			break;

		case MENU_ITEM_5:
			// facebook upload
			if (fb_dialog != null) {
				Log.d(TAG, "Dismissing fb dialog");
				fb_dialog.dismiss();
				lb = null;
				lb = new LoginButton(this);
				lb.init(mainapp.getFacebook(), VidiomApp.FB_LOGIN_PERMISSIONS,
						this);
			}
			// This launches the facebook upload , via a dialog.
			showFacebookOptionsMenu();
			break;

		case MENU_ITEM_6:
			// FTP server upload

			// Don't allow simultaneous uploads to FTP for the same video.
			if (!mainapp.isSDFileRecordUploading(sdrecord_id,
					PublishingUtils.TYPE_FTP)) {

				mainapp.addSDFileRecordIDtoUploadingTrack(sdrecord_id,
						PublishingUtils.TYPE_FTP);

				pu.videoUploadToFTPserver(this, handler, moviefilename,
						moviePath, emailPreference, sdrecord_id);

				reloadList();

			} else {
				showAlertDialogUploadingInProgress();
			}

			break;

		case MENU_ITEM_7:
			// youtube

			doYouTubeUpload();
			break;

		case MENU_ITEM_8:
			// Title and Description of Video

			showTitleDescriptionDialog();

			break;

		case MENU_ITEM_9:
			// Email the HOSTED URL field of the currently selected video

			if (hosted_urls != null && hosted_urls.length > 0
					&& hosted_urls[0].length() != 0) {
				if (hosted_urls[0].length() > 1) {
					launchURLsPickerDialog(hosted_urls, EMAIL);
				} else {
					hosted_url_chosen = hosted_urls[0];
					// launch activity
					doEmailActivity();
				}

			} else {
				Toast.makeText(
						this,
						R.string.video_is_not_uploaded_yet_no_hosted_url_available_,
						Toast.LENGTH_LONG).show();
			}

			break;

		case MENU_ITEM_12:
			// View the HOSTED URL field of the currently selected video in a
			// web browser.

			if (hosted_urls != null && hosted_urls.length > 0
					&& hosted_urls[0].length() != 0) {

				if (hosted_urls[0].length() > 1) {
					launchURLsPickerDialog(hosted_urls, VIEW);
				} else {
					hosted_url_chosen = hosted_urls[0];
					// launch activity
					doViewActivity();
				}

			} else {
				Toast.makeText(
						this,
						R.string.video_is_not_uploaded_yet_no_hosted_url_available_,
						Toast.LENGTH_LONG).show();
			}
			break;

		case MENU_ITEM_13:
			// Tweet the video hosted URL
			
			// Get which URL from user if more than one!!
			if (hosted_urls != null && hosted_urls.length > 0
					&& hosted_urls[0].length() != 0) {

				if (hosted_urls[0].length() > 1) {
					launchURLsPickerDialog(hosted_urls, TWEET);
				} else {
					hosted_url_chosen = hosted_urls[0];
					doTweetonThread();
				}
			} else {
				// No URL available to tweet..

				Toast.makeText(
						LibraryActivity.this,
						R.string.video_is_not_uploaded_yet_no_hosted_url_available_,
						Toast.LENGTH_LONG).show();

			}

			

			break;

		}

		return true;

	}

	/**
	 * 
	 */
	private void doYouTubeUpload() {
		String possibleEmail = null;
		// We need a linked google account for youtube.
		Account[] accounts = AccountManager.get(this).getAccountsByType(
				"com.google");
		for (Account account : accounts) {
			// TODO: Check possibleEmail against an email regex or treat
			// account.name as an email address only for certain
			// account.type values.
			possibleEmail = account.name;
			Log.d(TAG, "Could use : " + possibleEmail);
		}
		if (possibleEmail != null) {
			Log.d(TAG, "Using account name for youtube upload .. "
					+ possibleEmail);

			// Don't allow simultaneous uploads to Youtube for the same
			// video.
			if (!mainapp.isSDFileRecordUploading(sdrecord_id,
					PublishingUtils.TYPE_YT)) {

				mainapp.addSDFileRecordIDtoUploadingTrack(sdrecord_id,
						PublishingUtils.TYPE_YT);

				// This launches the youtube upload process
				pu.getYouTubeAuthTokenWithPermissionAndUpload(this,
						possibleEmail, moviePath, handler, emailPreference,
						sdrecord_id);

				reloadList();

			} else {
				showAlertDialogUploadingInProgress();
			}

		} else {

			// throw up dialog
			AlertDialog no_email = new AlertDialog.Builder(this)
					.setMessage(R.string.no_email_account_for_youtube)
					.setPositiveButton(R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int whichButton) {

								}
							}).show();
		}
	}

	/**
	 * 
	 */
	private void doTweetonThread() {
		new Thread(new Runnable() {
			public void run() {
				
				// Check there is a valid twitter OAuth tokens.
				String twitterToken = prefs.getString("twitterToken", null);
				String twitterTokenSecret = prefs.getString(
						"twitterTokenSecret", null);

				if (twitterToken != null && twitterTokenSecret != null) {
					
					runOnUiThread(new Runnable() {
						public void run() {
							Toast.makeText(LibraryActivity.this,
									R.string.tweeting_starting, Toast.LENGTH_LONG)
									.show();
						}
					});
					
					// Ok, now we can tweet this URL
					AccessToken a = new AccessToken(twitterToken,
							twitterTokenSecret);
					Twitter twitter = new TwitterFactory().getInstance();
					twitter.setOAuthConsumer(
							TwitterOAuthActivity.consumerKey,
							TwitterOAuthActivity.consumerSecret);
					twitter.setOAuthAccessToken(a);

					String status = "New video:" + hosted_url_chosen;
					try {
						// Twitter update
						twitter.updateStatus(status);
						// Toast
						runOnUiThread(new Runnable() {
							public void run() {
								Toast.makeText(LibraryActivity.this,
										R.string.tweeted_ok,
										Toast.LENGTH_LONG).show();
							}
						});
					} catch (TwitterException e) {
						// XXX Toast ?!
						e.printStackTrace();
						Log.e(TAG, "Twittering failed " + e.getMessage());
					}
				} else {

					// Need to authorise.
					runOnUiThread(new Runnable() {
						public void run() {
							Toast.makeText(
									LibraryActivity.this,
									R.string.launching_twitter_authorisation_come_back_and_tweet_after_you_have_done_this_,
									Toast.LENGTH_LONG).show();

						}
					});

					// Launch Twitter Authorisation
					Intent intent2 = new Intent().setClass(
							LibraryActivity.this,
							TwitterOAuthActivity.class);
					LibraryActivity.this.startActivity(intent2);

				}

			}
		}).start();
	}

	/**
	 * 
	 */
	private void doViewActivity() {
		Intent i2 = new Intent(Intent.ACTION_VIEW);
		i2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i2.setData(Uri.parse(hosted_url_chosen));
		this.startActivity(i2);
	}

	/**
	 * Email activity with chosen hosted URL.
	 */
	private void doEmailActivity() {

		Log.d(TAG, "Emailing the following URL " + hosted_url_chosen);

		Intent i = new Intent(Intent.ACTION_SEND);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.setType("message/rfc822");
		i.putExtra(Intent.EXTRA_TEXT, hosted_url_chosen);
		try {
			this.startActivity(i);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
			Log.e(TAG,
					"Emailing caught ActivityNotFoundException - can't email URL ");
		}
	}

	private void showAlertDialogUploadingInProgress() {
		// throw up dialog
		AlertDialog alreadydoingit = new AlertDialog.Builder(this)
				.setMessage(
						R.string.this_video_is_in_the_process_of_uploading_already_)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {

							}
						}).show();
	}

	private void showTitleDescriptionDialog() {
		// Launch Title/Description Edit View
		LayoutInflater inflater = (LayoutInflater) getApplicationContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		final View title_descr = inflater
				.inflate(R.layout.title_and_desc, null);
		// preload any existing title and description
		String[] strs = dbutils
				.getTitleAndDescriptionFromID(new String[] { Long
						.toString(sdrecord_id) });
		final EditText title_edittext = (EditText) title_descr
				.findViewById(R.id.EditTextTitle);
		final EditText desc_edittext = (EditText) title_descr
				.findViewById(R.id.EditTextDescr);

		title_edittext.setText(strs[0]);
		desc_edittext.setText(strs[1]);

		final Dialog d = new Dialog(this);
		Window w = d.getWindow();
		w.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
				WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
		d.setTitle(R.string.rename_video);
		// the edit layout defined in an xml file (in res/layout)
		d.setContentView(title_descr);
		// Cancel
		Button cbutton = (Button) d.findViewById(R.id.button2Cancel);
		cbutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				d.dismiss();
			}
		});
		// Edit
		Button ebutton = (Button) d.findViewById(R.id.button1Edit);
		ebutton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// save title and description to DB.
				String title_str = title_edittext.getText().toString();
				String desc_str = desc_edittext.getText().toString();
				String[] ids = new String[] { Long.toString(sdrecord_id) };

				Log.d(TAG, "New title and description is " + title_str + ":"
						+ desc_str);

				dbutils.updateTitleAndDescription(title_str, desc_str, ids);

				reloadList();

				d.dismiss();
			}
		});
		d.show();
	}

	private void reloadList() {
		// Refresh the list view
		runOnUiThread(new Runnable() {
			public void run() {

				if (libraryCursor != null && !libraryCursor.isClosed()) {
					libraryCursor.close();
				}

				makeCursorAndAdapter();

				listAdapter.notifyDataSetChanged();

			}
		});
	}

	public boolean isUploading() {
		return mainapp.isUploading();
	}

	public void startedUploading(int service_code) {
		// Show notification of uploading
		Resources res = getResources();

		String service = PublishingUtils.getVideoServiceStringFromServiceCode(
				getBaseContext(), service_code);

		this.createNotification(res.getString(R.string.starting_upload) + " "
				+ service);

		mainapp.setUploading();
	}

	public void finishedUploading(boolean success) {
		// Reload the gallery list
		reloadList();

		mainapp.setNotUploading();
	}

	public void createNotification(String notification_text) {
		Resources res = getResources();
		CharSequence contentTitle = res.getString(R.string.notification_title);
		CharSequence contentText = notification_text;

		final Notification notifyDetails = new Notification(R.drawable.icon,
				notification_text, System.currentTimeMillis());

		Intent notifyIntent = new Intent(this, LibraryActivity.class);

		PendingIntent intent = PendingIntent.getActivity(this, 0, notifyIntent,
				PendingIntent.FLAG_UPDATE_CURRENT
						| Notification.FLAG_AUTO_CANCEL);

		notifyDetails.setLatestEventInfo(this, contentTitle, contentText,
				intent);
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mNotificationManager.notify(NOTIFICATION_ID, notifyDetails);

	}

	private void showFacebookOptionsMenu() {
		Log.d(TAG, "Showing facebook menu alert dialog");
		fb_dialog = new AlertDialog.Builder(this)
				.setMessage(R.string.request_facebook_login)
				.setView(lb)
				.setPositiveButton(R.string.videopost_ok,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {
								// If they succeeded in login, then the
								// session will be valid.

								if (mainapp.getFacebook().isSessionValid()
										&& !mainapp.isSDFileRecordUploading(
												sdrecord_id,
												PublishingUtils.TYPE_FB)) {
									// we have a valid session, and we arent
									// already uploading this file.

									mainapp.addSDFileRecordIDtoUploadingTrack(
											sdrecord_id,
											PublishingUtils.TYPE_FB);

									String[] strs = dbutils
											.getTitleAndDescriptionFromID(new String[] { Long
													.toString(sdrecord_id) });
									// add our branding to the description.
									pu.videoUploadToFacebook(
											LibraryActivity.this, handler,
											mainapp.getFacebook(), moviePath,
											strs[0], strs[1], emailPreference,
											sdrecord_id);

									reloadList();
								} else {
									showAlertDialogUploadingInProgress();
								}

							}
						})

				.setNegativeButton(R.string.videopost_cancel,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int whichButton) {

								// logout of facebook, if the session is
								// valid.
								if (mainapp.getFacebook().isSessionValid()) {
									lb.logout();
								}

							}
						})

				.show();

		resetFacebookMenuButtons();

	}

	public void resetFacebookMenuButtons() {
		if (fb_dialog != null && mainapp.getFacebook() != null) {
			// set the POST video button according to the session
			fb_dialog.getButton(fb_dialog.BUTTON_POSITIVE).setEnabled(
					mainapp.getFacebook().isSessionValid());

			// set the logout button similarily, ie also needs to be logged in,
			// to logout out.
			fb_dialog.getButton(fb_dialog.BUTTON_NEGATIVE).setEnabled(
					mainapp.getFacebook().isSessionValid());
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		Log.i(TAG, "OnPrepareOptionsMenu called");

		menu.clear();

		addConstantMenuItems(menu);

		return true;
	}

	private void addConstantMenuItems(Menu menu) {
		// ALWAYS ON menu items.
		MenuItem menu_about = menu
				.add(0, MENU_ITEM_10, 0, R.string.menu_import);
		menu_about.setIcon(R.drawable.refresh48);

		// Toogle twitter depending on auth'd or not.

		// Check there is a valid twitter OAuth tokens.
		String twitterToken = prefs.getString("twitterToken", null);
		String twitterTokenSecret = prefs.getString("twitterTokenSecret", null);

		if (twitterToken != null && twitterTokenSecret != null) {

			MenuItem menu_twitter_deauth = menu.add(0, MENU_ITEM_14, 0,
					R.string.twitter_deauth);
			menu_twitter_deauth.setIcon(R.drawable.twitter48);

		} else {

			MenuItem menu_twitter = menu.add(0, MENU_ITEM_11, 0,
					R.string.twitter_auth);
			menu_twitter.setIcon(R.drawable.twitter48);

		}

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuitem) {
		int menuNum = menuitem.getItemId();

		Log.d("MENU", "Option " + menuNum + " selected");

		switch (menuitem.getItemId()) {

		// Importing
		case MENU_ITEM_10:
			ImporterThread importer = new ImporterThread();
			importer.run();
			break;

		// Authorising Twitter.
		case MENU_ITEM_11:
			// Tweet the video hosted URL
			runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(LibraryActivity.this,
							R.string.tweeting_starting, Toast.LENGTH_LONG)
							.show();
				}
			});

			Intent intent2 = new Intent().setClass(this,
					TwitterOAuthActivity.class);
			// this.startActivityForResult(intent2, 0);
			this.startActivity(intent2);

			break;

		// De-Authorising Twitter.
		case MENU_ITEM_14:
			// Tweet the video hosted URL
			runOnUiThread(new Runnable() {
				public void run() {
					Toast.makeText(LibraryActivity.this,
							R.string.tweeting_deauth, Toast.LENGTH_LONG).show();
				}
			});

			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(getBaseContext());
			Editor editor = prefs.edit();
			editor.putString("twitterToken", null);
			editor.putString("twitterTokenSecret", null);
			editor.commit();

			break;

		}
		return true;
	}

}
