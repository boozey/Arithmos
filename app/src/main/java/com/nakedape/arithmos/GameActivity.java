package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.BaseGameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Random;

public class GameActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, GameBoard.OnAchievementListener,
        GameBoard.OnPlayCompleted, GameBoard.OnGameOverListener, GameBoard.OnStarEarnedListener,
        GameBoard.OnJewelListener, GameBoard.OnBombListener, GameBoard.OnCancelBombListener {

    private static final String LOG_TAG = "GameActivity";
    private static final String ACTIVITY_PREFS = "game_activity_prefs";

    // Intent Extra tags
    public static final String LEVEL_XML_RES_ID = "level_xml_res_id";
    public static final String SAVED_GAME = "saved_game";
    public static final String GRID_SIZE ="grid_size";
    public static final String GOAL_MODE = "goal_mode";
    public static final String GAME_BASE_FILE_NAME = "game_base_file_name";

    // Saving/recreating the activity state
    private static final String JEWEL_COUNT = "JEWEL_COUNT";
    private static final String ELAPSED_TIME = "ELAPSED_TIME";
    private static final String levelCacheFileName = "arithmos_level_cache";
    private static final String gameCacheFileName = "arithmos_game_cache";

    private Context context;
    private RelativeLayout rootLayout;
    private ArithmosGame game;
    private GameBoard gameBoard;
    private TextView scoreTextView;
    private int prevScore = 0, jewelCount = 0;
    private long elapsedMillis = 0;
    private boolean stopTimer = false;
    private SharedPreferences activityPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the Google Api Client with access to the Play Games services
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Games.API).addScope(Games.SCOPE_GAMES)
                .addApi(Drive.API).addScope(Drive.SCOPE_APPFOLDER)
                // add other APIs and scopes here as needed
                .build();

        setContentView(R.layout.activity_game);
        context = this;
        rootLayout = (RelativeLayout)findViewById(R.id.root_layout);
        gameBoard = (GameBoard)rootLayout.findViewById(R.id.game_board);
        gameBoard.setOnAchievementListener(this);
        gameBoard.setOnPlayCompleted(this);
        gameBoard.setOnGameOverListener(this);
        gameBoard.setOnStarEarnedListener(this);
        gameBoard.setOnJewelListener(this);
        gameBoard.setOnBombListener(this);
        gameBoard.setOnCancelBombListener(this);
        gameBase = new ArithmosGameBase();
        scoreTextView = (TextView)findViewById(R.id.score_textview);
        rootLayout.findViewById(R.id.bomb_button).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                BombLongClick(v);
                return true;
            }
        });
        rootLayout.findViewById(R.id.pencil_button).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PencilLongClick(v);
                return true;
            }
        });
        activityPrefs = getSharedPreferences(ACTIVITY_PREFS, MODE_PRIVATE);

        // Restore activity state or initialize
        if (savedInstanceState != null) {
            jewelCount = savedInstanceState.getInt(JEWEL_COUNT, 0);
            elapsedMillis = savedInstanceState.getLong(ELAPSED_TIME, 0);
        }
        if (loadCachedGame())
            gameBaseNeedsDownload = false;

        if (loadCachedLevel()) {
            if (game.hasTimeLimit()) {
                rootLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        startTimer();
                    }
                });
            }
        }
        else {
            if (getIntent().hasExtra(SAVED_GAME)){
                snapShotFileName = getIntent().getStringExtra(SAVED_GAME);
                loadSavedGame = true;
            }
            else if (getIntent().hasExtra(LEVEL_XML_RES_ID)) {
                LoadGameLevel(getIntent().getIntExtra(LEVEL_XML_RES_ID, R.xml.game_level_crazy_eights_6x6));
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putInt(JEWEL_COUNT, jewelCount);
        savedInstanceState.putLong(ELAPSED_TIME, elapsedMillis);
        stopTimer = true;

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                if (rootLayout.findViewById(R.id.game_over_popup) != null){
                    return true;
                }
                else if (rootLayout.findViewById(R.id.generic_popup) == null) {
                    if (game.hasTimeLimit()) {
                        showQuitPrompt();
                        return true;
                    }
                    else if (hasBeenPlayed) {
                        showSaveGamePrompt();
                        return true;
                    }
                    else {
                        finishLevelIncomplete();
                        return true;
                    }
                }
                else {
                    AnimatorSet set = Animations.slideDown(rootLayout.findViewById(R.id.generic_popup), 150, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(rootLayout.findViewById(R.id.generic_popup));
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    set.start();
                    return true;
                }
            default:
                return super.onKeyDown(keycode, e);
        }
    }


    // Game saving and loading
    private ArithmosGameBase gameBase;
    private File levelCache, gameCache;
    private String snapShotFileName;
    private boolean loadSavedGame = false, retrySaveGame = false,
            retrySaveGameState = false, saveLevel = false,
            gameBaseNeedsDownload = true, hasBeenPlayed = false;
    private Bitmap thumbNail;

    private void setupGameUi(){
        gameBoard.setGame(game);

        TextView scoreTextView = (TextView)rootLayout.findViewById(R.id.score_textview);
        scoreTextView.setText(String.valueOf(game.getScore(game.getCurrentPlayer())));
        scoreTextView.setVisibility(View.VISIBLE);
        prevScore = game.getScore(game.getCurrentPlayer());

        if (game.getGoalType() == ArithmosLevel.GOAL_301){
            TextView three01 = (TextView)rootLayout.findViewById(R.id.three01_textview);
            three01.setVisibility(View.VISIBLE);
            three01.setText("0");
        } else {
            final ListView goalList = (ListView) rootLayout.findViewById(R.id.upcoming_goal_listview);
            goalList.setAdapter(new ArrayAdapter<>(context, R.layout.goal_list_item, game.getUpcomingGoals()));
            goalList.setVisibility(View.VISIBLE);
        }

        if (game.hasTimeLimit()){
            TextView timeView = (TextView)rootLayout.findViewById(R.id.time_textview);
            timeView.setText(Utils.getDate(game.getTimeLimit(), "mm:ss"));
            timeView.setVisibility(View.VISIBLE);
        }

        // Show stars earned
        int numStars = game.getNumStars();
        if (numStars > 0)
            Animations.popIn(rootLayout.findViewById(R.id.one_star), 100, 0).start();
        if (numStars > 1)
            Animations.popIn(rootLayout.findViewById(R.id.two_star), 100, 100).start();
        if (numStars > 2)
            Animations.popIn(rootLayout.findViewById(R.id.three_star), 100, 200).start();

        // Show unavailabe operations
        rootLayout.findViewById(R.id.slash_div).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.slash_mult).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.slash_sub).setVisibility(View.VISIBLE);
        rootLayout.findViewById(R.id.slash_add).setVisibility(View.VISIBLE);

        for (String s : game.availableOperations()){
            switch (s){
                case ArithmosGame.ADD:
                    rootLayout.findViewById(R.id.slash_add).setVisibility(View.GONE);
                    break;
                case ArithmosGame.SUBTRACT:
                    rootLayout.findViewById(R.id.slash_sub).setVisibility(View.GONE);
                    break;
                case ArithmosGame.MULTIPLY:
                    rootLayout.findViewById(R.id.slash_mult).setVisibility(View.GONE);
                    break;
                case ArithmosGame.DIVIDE:
                    rootLayout.findViewById(R.id.slash_div).setVisibility(View.GONE);
                    break;
            }
        }
    }

    private void setupSpecials(){
        TextView bomb = (TextView)rootLayout.findViewById(R.id.bomb_button);
        int count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_BOMB);
        if (count > 0)
            Animations.popIn(bomb, 200, 100).start();
        if (count > 1)
            bomb.setText(String.valueOf(count));

        TextView calc = (TextView)rootLayout.findViewById(R.id.calc_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_OP_ORDER);
        if (count > 0)
            Animations.popIn(calc, 200, 175).start();
        if (count > 1)
            calc.setText(String.valueOf(count));

        TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_CHANGE);
        if (count > 0)
            Animations.popIn(pencil, 200, 250).start();
        if (count > 1)
            pencil.setText(String.valueOf(count));

        TextView arrow = (TextView)rootLayout.findViewById(R.id.skip_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_SKIP);
        if (count > 0)
            Animations.popIn(arrow, 200, 325).start();
        if (count > 1)
            arrow.setText(String.valueOf(count));
    }

    private void LoadGameLevel(int resId){
        final ArithmosLevel level = new ArithmosLevel(context, resId);
        game = new ArithmosGame(level);
        setupGameUi();
        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                showLevelInfoPopup(level);
            }
        });
    }

    private void showLevelInfoPopup(ArithmosLevel level){
        final View layout = getLayoutInflater().inflate(R.layout.level_info_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView titleView = (TextView)layout.findViewById(R.id.title_textview);
        titleView.setText(level.getChallengeDisplayNameId());

        TextView subTitleView = (TextView)layout.findViewById(R.id.subtitle_textview);
        subTitleView.setText(level.getLevelDisplayNameId());

        TextView oneStarView = (TextView)layout.findViewById(R.id.one_star);
        oneStarView.setText(String.valueOf(level.getStarLevels()[0]));

        TextView twoStarView = (TextView)layout.findViewById(R.id.two_star);
        twoStarView.setText(String.valueOf(level.getStarLevels()[1]));

        TextView threeStarView = (TextView)layout.findViewById(R.id.three_star);
        threeStarView.setText(String.valueOf(level.getStarLevels()[2]));

        TextView timeView = (TextView)layout.findViewById(R.id.time_limit_text);
        if (level.hasTimeLimit()) {
            timeView.setText(getString(R.string.time_limit_x, Utils.getDate(level.getTimeLimitMillis(), "mm:ss")));
        } else {
            timeView.setText(R.string.no_time_limit);
        }

        Button okButton = (Button)layout.findViewById(R.id.ok_button);
        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideDown(layout, 200, 0, rootLayout.getHeight() / 3);
                // Time is started in OnAnimationEnd
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                        if (game.hasTimeLimit())
                            startTimer();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 200, rootLayout.getHeight() / 3).start();
    }

    private void cacheGame(){
        if (gameCache == null)
            gameCache = new File(getCacheDir(), gameCacheFileName);
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(gameCache);
            byte[] data = gameBase.getByteData();
            outputStream.write(data, 0, data.length);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadCachedGame(){
        gameCache = new File(getCacheDir(), gameCacheFileName);
        if (gameCache.exists()){
            FileInputStream inputStream;
            try {
                inputStream = new FileInputStream(gameCache);
                byte[] data = new byte[inputStream.available()];
                if (inputStream.read(data) > 0) {
                    gameBase.loadByteData(data);
                    gameBaseNeedsDownload = false;
                    setupSpecials();
                    TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
                    Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
                    Log.d(LOG_TAG, "Game state loaded from cache");
                    return true;
                }
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private void cacheLevel(){
        if (levelCache == null)
            levelCache = new File(getCacheDir(), levelCacheFileName);
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(levelCache);
            byte[] data = game.getSaveGameData();
            outputStream.write(data, 0, data.length);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadCachedLevel(){
        levelCache = new File(getCacheDir(), levelCacheFileName);
        if (levelCache.exists()){
            FileInputStream inputStream;
            try {
                inputStream = new FileInputStream(levelCache);
                byte[] data = new byte[inputStream.available()];
                if (inputStream.read(data) > 0){
                    game = new ArithmosGame(data);
                    hasBeenPlayed = true;
                    setupGameUi();
                    Log.d(LOG_TAG, "Level loaded from cache");
                    return true;
                }
                inputStream.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        return false;
    }

    private void LoadSavedGame(){
        final View loadingPopup = Utils.progressPopup(context, R.string.loading_saved_game_message);
        rootLayout.addView(loadingPopup);
        Animations.slideUp(loadingPopup, 200, 0, rootLayout.getHeight() / 3).start();
        Games.Snapshots.open(mGoogleApiClient, snapShotFileName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
            @Override
            public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                try {
                    game = new ArithmosGame(openSnapshotResult.getSnapshot().getSnapshotContents().readFully());

                    setupGameUi();

                    rootLayout.removeView(loadingPopup);
                    loadSavedGame = false;
                } catch (IOException e) {e.printStackTrace();}
            }
        });

    }

    private void loadGameState(String gameFileName){
        if (gameBaseNeedsDownload)
            Games.Snapshots.open(mGoogleApiClient, gameFileName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    try {
                        if (gameBaseNeedsDownload) {
                            gameBase.loadByteData(openSnapshotResult.getSnapshot().getSnapshotContents().readFully());
                            setupSpecials();
                            TextView jewelText = (TextView) rootLayout.findViewById(R.id.jewel_count);
                            Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
                            gameBaseNeedsDownload = false;
                        }

                    } catch (IOException | NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            });

    }

    private void saveGameState(){
        stopTimer = true;
        if (gameBase.needsSaving()) {
            if (rootLayout.findViewById(R.id.loading_popup) == null) {
                View loadingPopup = Utils.progressPopup(context, R.string.saving_game_message);
                rootLayout.addView(loadingPopup);
                Animations.slideUp(loadingPopup, 200, 0, rootLayout.getHeight() / 3).start();
            }
            if (mGoogleApiClient.isConnected() && getIntent().hasExtra(GAME_BASE_FILE_NAME)) {
                retrySaveGameState = false;
                String gameFileName = getIntent().getStringExtra(GAME_BASE_FILE_NAME);
                Games.Snapshots.open(mGoogleApiClient, gameFileName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                    @Override
                    public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                        String desc = "Arithmos Game Data";
                        writeSnapshot(openSnapshotResult.getSnapshot(), gameBase.getByteData(), desc, null).setResultCallback(new ResultCallback<Snapshots.CommitSnapshotResult>() {
                            @Override
                            public void onResult(@NonNull Snapshots.CommitSnapshotResult commitSnapshotResult) {
                                Log.i(LOG_TAG, "Game state saved");
                                if (saveLevel) {
                                    finishLevelIncomplete();
                                } else
                                    finishLevelComplete();
                            }
                        });
                    }
                });
            } else {
                Log.d(LOG_TAG, "SaveGameState() GoogleAPIClient not connected, retying");
                retrySaveGameState = true;
                mGoogleApiClient.connect();
            }
        }
        else
            finishLevelComplete();
    }

    private void showQuitPrompt(){
        final View layout = getLayoutInflater().inflate(R.layout.generic_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView msgView = (TextView)layout.findViewById(R.id.textView);
        msgView.setText(R.string.quit_no_save_prompt);

        layout.findViewById(R.id.checkBox).setVisibility(View.GONE);

        Button button1 = (Button)layout.findViewById(R.id.button1);
        button1.setText(R.string.yes);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveLevel = false;
                ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.FORFEIT);
                result.isGameOver = true;
                OnGameOver(result);
                Animations.slideDown(layout, 200, 0, rootLayout.getHeight() / 3).start();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.no);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideDown(layout, 200, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                set.start();
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
    }

    private void captureScreenShot(){
        rootLayout.buildDrawingCache();
        Bitmap screenShot = rootLayout.getDrawingCache();

        thumbNail = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(thumbNail);
        if (screenShot.getHeight() > screenShot.getWidth()){
            float scale = 64f / screenShot.getWidth();
            screenShot = Bitmap.createScaledBitmap(screenShot, (int)(scale * screenShot.getWidth()), (int)(scale * screenShot.getHeight()), false);
            canvas.drawBitmap(screenShot, 0, 0, null);
        } else {
            float scale = 64f / screenShot.getHeight();
            screenShot = Bitmap.createScaledBitmap(screenShot, (int)(scale * screenShot.getWidth()), (int)(scale * screenShot.getHeight()), false);
            canvas.drawBitmap(screenShot, 0, 0, null);
        }
    }

    private void showSaveGamePrompt(){
        captureScreenShot();
        final View layout = getLayoutInflater().inflate(R.layout.generic_popup, null);

        // Prepare popup window
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView msgView = (TextView)layout.findViewById(R.id.textView);
        msgView.setText(R.string.save_game_prompt);

        layout.findViewById(R.id.checkBox).setVisibility(View.GONE);

        Button button1 = (Button)layout.findViewById(R.id.button1);
        button1.setText(R.string.yes);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rootLayout.removeView(layout);
                saveLevel = true;
                Animations.slideDown(layout, 200, 0, rootLayout.getHeight() / 3).start();
                SaveLevel();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.no);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveLevel = false;
                ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.FORFEIT);
                result.isGameOver = true;
                OnGameOver(result);
                Animations.slideDown(layout, 200, 0, rootLayout.getHeight() / 3).start();
            }
        });

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
    }
    private void SaveLevel() {
        if (rootLayout.findViewById(R.id.loading_popup) == null) {
            View loadingPopup = Utils.progressPopup(context, R.string.saving_game_message);
            rootLayout.addView(loadingPopup);
            Animations.slideUp(loadingPopup, 200, 0, rootLayout.getHeight() / 3).start();
        }
        if (snapShotFileName == null) {
            String unique = new BigInteger(281, new Random()).toString(13);
            snapShotFileName = "ArithmosLevel_" + unique;
        }
        if (mGoogleApiClient.isConnected()) {
            retrySaveGame = false;
            Games.Snapshots.open(mGoogleApiClient, snapShotFileName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    String desc = getString(ArithmosGameBase.getChallengeDisplayNameResId(game.getChallengeName())) + " " +
                            getString(ArithmosGameBase.getLevelDisplayNameResIds(game.getChallengeName())[game.getChallengeLevel()]);

                    writeSnapshot(openSnapshotResult.getSnapshot(), game.getSaveGameData(), desc, thumbNail).setResultCallback(new ResultCallback<Snapshots.CommitSnapshotResult>() {
                        @Override
                        public void onResult(@NonNull Snapshots.CommitSnapshotResult commitSnapshotResult) {
                            Log.d(LOG_TAG, "Level Snapshot Written");
                            saveGameState();
                        }
                    });
                }
            });
        }
        else {
            Log.d(LOG_TAG, "SaveLevel() GoogleAPIClient not connected, retying");
            retrySaveGame = true;
            mGoogleApiClient.connect();
        }
    }
    private PendingResult<Snapshots.CommitSnapshotResult> writeSnapshot(Snapshot snapshot, byte[] data, String desc, Bitmap bitmap) {

        // Set the data payload for the snapshot
        snapshot.getSnapshotContents().writeBytes(data);

        SnapshotMetadataChange metadataChange;
        if (bitmap != null) {
            // Create the change operation
            metadataChange = new SnapshotMetadataChange.Builder()
                    .setCoverImage(bitmap)
                    .setDescription(desc)
                    .build();
        } else {
            metadataChange = new SnapshotMetadataChange.Builder()
                    .setDescription(desc)
                    .build();
        }

        // Commit the operation
        return Games.Snapshots.commitAndClose(mGoogleApiClient, snapshot, metadataChange);
    }

    private void finishLevelComplete(){
        if (jewelCount > 0)
            Games.Achievements.increment(mGoogleApiClient, getString(R.string.achievement_collector), jewelCount);
        Intent data = new Intent();
        if (snapShotFileName != null) {
            data.putExtra(SAVED_GAME, snapShotFileName);
        }
        if (gameCache != null) gameCache.delete();
        if (levelCache != null) levelCache.delete();
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    private void finishLevelIncomplete(){
        if (jewelCount > 0)
            Games.Achievements.increment(mGoogleApiClient, getString(R.string.achievement_collector), jewelCount);
        Intent data = new Intent();
        if (hasBeenPlayed) {
            // Record level score
            if (game.getLeaderboardId() != null)
                Games.Leaderboards.submitScore(mGoogleApiClient, game.getLeaderboardId(), game.getScore(game.getCurrentPlayer()));

            if (snapShotFileName != null) {
                data.putExtra(SAVED_GAME, snapShotFileName);
            }
            if (gameCache != null) gameCache.delete();
            if (levelCache != null) levelCache.delete();
            setResult(Activity.RESULT_CANCELED, data);
            finish();
        } else {
            if (gameCache != null) gameCache.delete();
            if (levelCache != null) levelCache.delete();
            setResult(Activity.RESULT_CANCELED, data);
            finish();
        }
    }

    private void recordActivityTurnFinished(ArithmosGame.GameResult result){
        GameActivityItem newItem;
        if (game.isGameOver()) {
            newItem = new GameActivityItem(GameActivityItem.COMPLETED_LEVEL);
            newItem.description = getString(R.string.level_completed);
        } else {
            newItem = new GameActivityItem(GameActivityItem.INCOMPLETE_LEVEL);
            newItem.description = getString(R.string.level_incomplete);
        }
        newItem.timeStamp = System.currentTimeMillis();
        newItem.challengeName = game.getChallengeName();
        newItem.challengeLevel = game.getChallengeLevel();
        gameBase.addActivityItem(newItem);
    }

    private void recordAchievements(){
        if (game.isGameOver()) {
            // Record level score
            if (game.getLeaderboardId() != null)
                Games.Leaderboards.submitScore(mGoogleApiClient, game.getLeaderboardId(), game.getScore(game.getCurrentPlayer()));
            if (game.getNumStars() == 3) {
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_level_master));
                Games.Achievements.reveal(mGoogleApiClient, getString(R.string.achievement_challenge_master));
            }
            if (game.getGoalType() == ArithmosLevel.GOAL_301 && game.getRunCount() <= 3)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_301_in_3));
            if (game.getNumTilesRemaining() == 0)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_blackout));
            if (gameBase.getJewelCount() >= 1000)
                Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_the_one_percent));
        }
    }

    public void ExitButtonClick(View v){
        Animations.slideDown(findViewById(R.id.game_over_popup), 200, 0, rootLayout.getHeight() / 3).start();
        saveLevel = false;
        saveGameState();
    }

    // Game events
    private void startTimer(){
        final TextView timeText = (TextView)rootLayout.findViewById(R.id.time_textview);
        final long startMillis = SystemClock.elapsedRealtime() - elapsedMillis, endMillis = startMillis + game.getTimeLimit();
        Log.d(LOG_TAG, "elapsedMillis = " + elapsedMillis);
        Log.d(LOG_TAG, "startMillis = " + startMillis);
        new Thread(new Runnable() {
            @Override
            public void run() {
                do {
                    try {
                        Thread.sleep(200);
                    }catch (InterruptedException e) { e.printStackTrace(); }
                    timeText.post(new Runnable() {
                        @Override
                        public void run() {
                            elapsedMillis = SystemClock.elapsedRealtime() - startMillis;
                            timeText.setText(Utils.getDate(endMillis - SystemClock.elapsedRealtime(), "mm:ss"));
                            if (endMillis - SystemClock.elapsedRealtime() < 30000)
                                timeText.setTextColor(Color.RED);
                        }
                    });
                } while (SystemClock.elapsedRealtime() < endMillis && !stopTimer);
                if (!stopTimer)
                    rootLayout.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(LOG_TAG, "elapsedRealtime = " + SystemClock.elapsedRealtime());
                            Log.d(LOG_TAG, "endMillis = " + endMillis);
                            ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.TIME_UP);
                            result.isGameOver = true;
                            OnGameOver(result);
                        }
                    });
            }
        }).start();
    }

    @Override
    public void OnPlayCompleted(ArithmosGame.GameResult result){
        TextView calc = (TextView) rootLayout.findViewById(R.id.calc_button);
        if (result.result == ArithmosGame.GameResult.SUCCESS) {
            hasBeenPlayed = true;
            cacheLevel();
            // Animate score
            Animations.CountTo(scoreTextView, prevScore, prevScore + result.score);
            prevScore += result.score;
            if (game.getGoalType() == ArithmosLevel.GOAL_301) {
                TextView three01 = (TextView) rootLayout.findViewById(R.id.three01_textview);
                String value = String.valueOf(game.get301Total()), finalValue = "";
                for (int i = 0; i < value.length(); i++){
                    finalValue += value.charAt(i) + "\n";
                }
                three01.setText(finalValue.trim());
            } else {
                ListView upcomingGoalsList = (ListView)rootLayout.findViewById(R.id.upcoming_goal_listview);
                ArrayAdapter<String> adapter = (ArrayAdapter<String>)upcomingGoalsList.getAdapter();
                adapter.notifyDataSetChanged();
            }

            // If an evaluate left to right special was use, record and reset
            if (game.isEvalLeftToRight()) {
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_OP_ORDER);
                if (count < 1)
                    calc.setVisibility(View.GONE);
                else if (count < 2)
                    calc.setText("");
                else
                    calc.setText(String.valueOf(count));
                cacheGame();
            }
        }
        if (game.isEvalLeftToRight()) {
            calc.setBackground(null);
            game.setEvalLeftToRight(false);
        }
    }
    private void animateNewGoalNumber(){
        // Animate new goal if it is a type that changes
        if (game.getGoalType() == ArithmosLevel.GOAL_MULT_NUM) {
            final ListView upcomingGoalsList = (ListView)rootLayout.findViewById(R.id.upcoming_goal_listview);
            TextView currentGoal = (TextView)upcomingGoalsList.getChildAt(0).findViewById(R.id.textView1);
            AnimatorSet set = Animations.explodeFade(currentGoal, 200, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    // Highlight new goal
                    TextView nextGoal = (TextView)upcomingGoalsList.getChildAt(0).findViewById(R.id.textView1);
                    nextGoal.setAlpha(1f);
                    nextGoal.setScaleX(1f);
                    nextGoal.setScaleY(1f);
                    nextGoal.setTextColor(Color.RED);
                    ArrayAdapter<String> adapter = (ArrayAdapter<String>)upcomingGoalsList.getAdapter();
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
    }

    @Override
    public void OnStarEarned(int numStars){
        // Record stars
        gameBase.recordStars(game.getChallengeName(), game.getChallengeLevel(), numStars);
        cacheGame();

        // Animate star popup
        final View layout = getLayoutInflater().inflate(R.layout.star_popup, null);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        TextView score = (TextView)layout.findViewById(R.id.textView1);
        score.setText(String.valueOf(game.getPointsForStar(numStars)));
        if (numStars > 1)
            layout.findViewById(R.id.star2).setVisibility(View.VISIBLE);
        if (numStars > 2)
            layout.findViewById(R.id.star3).setVisibility(View.VISIBLE);

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
        AnimatorSet set = Animations.explodeFade(layout, 200, 1200);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                rootLayout.removeView(layout);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        set.start();

        // Animate small stars
        Animations.popIn(rootLayout.findViewById(R.id.one_star), 100, 0).start();
        if (numStars > 1)
            Animations.popIn(rootLayout.findViewById(R.id.two_star), 100, 100).start();
        if (numStars > 2)
            Animations.popIn(rootLayout.findViewById(R.id.three_star), 100, 200).start();

    }

    @Override
    public void OnGameOver(ArithmosGame.GameResult result){
        stopTimer = true;
        recordActivityTurnFinished(result);
        recordAchievements();
        final View layout = getLayoutInflater().inflate(R.layout.game_over_popup, null);

        if (game.isGameOver()) {
            int numStars = gameBase.getNumStars(game.getChallengeName(), game.getChallengeLevel());
            int[] nameId = gameBase.unlockNextLevel(game.getChallengeName(), game.getChallengeLevel());
            TextView levelView1 = (TextView)layout.findViewById(R.id.level_textview1);
            levelView1.setVisibility(View.VISIBLE);
            levelView1.setText(getString(R.string.unlock_level_x_x, getString(nameId[0]), getString(nameId[1])));
            if (numStars > 1) {
                nameId = gameBase.unlockNextLevel(game.getChallengeName(), game.getChallengeLevel() + 1);
                TextView levelView2 = (TextView)layout.findViewById(R.id.level_textview2);
                levelView2.setVisibility(View.VISIBLE);
                levelView2.setText(getString(R.string.unlock_level_x_x, getString(nameId[0]), getString(nameId[1])));
            }
        }

        // Prepare info to display
        if (result.result == ArithmosGame.GameResult.TIME_UP){
            TextView titleText = (TextView)layout.findViewById(R.id.title_textview);
            titleText.setText(R.string.times_up);
        }
        else if (result.result != ArithmosGame.GameResult.SUCCESS) {
            TextView titleText = (TextView)layout.findViewById(R.id.title_textview);
            titleText.setText(R.string.level_incomplete);
        }

        TextView scoreView = (TextView)layout.findViewById(R.id.score_textview);
        if (game.getNumStars() == 1)
            scoreView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_one_star_80dp, 0, 0, 0);
        else if (game.getNumStars() == 2)
            scoreView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_two_stars_80dp, 0, 0, 0);
        else if (game.getNumStars() == 3)
            scoreView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_three_stars_80dp, 0, 0, 0);
        Animations.CountTo(scoreView, 0, game.getScore(game.getCurrentPlayer()));

        TextView jewelView = (TextView)layout.findViewById(R.id.jewel_count);
        jewelView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_RED_JEWEL)));

        TextView balloonView = (TextView)layout.findViewById(R.id.balloon_count);
        balloonView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_BALLOONS)));

        TextView lockView = (TextView)layout.findViewById(R.id.lock_count);
        lockView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_OP_LOCK)));

        // Prepare touch
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        int delay = 0;
        if (result.result == ArithmosGame.GameResult.FORFEIT || result.result == ArithmosGame.GameResult.TIME_UP){
            if (gameBoard.showComputerRun()) delay = 3000;
        }
        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, delay, rootLayout.getHeight() / 3).start();
    }

    @Override
    public void OnAchievement(String label){
        Games.Achievements.unlock(mGoogleApiClient, label);
    }

    @Override
    public void OnJewel(){
        gameBase.recordJewels(1);
        jewelCount++;
        TextView jewelTextView = (TextView)rootLayout.findViewById(R.id.jewel_count);
        String text = String.valueOf(gameBase.getJewelCount());
        jewelTextView.setText(text);
        cacheGame();
    }

    @Override
    public void OnBomb(String operation){
        final View layout = getLayoutInflater().inflate(R.layout.bomb_popup, null);

        // Animate bomb popup
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        ImageView imageView = (ImageView) layout.findViewById(R.id.image_view);
        switch (operation){
            case ArithmosGame.ADD:
                Animations.popIn(rootLayout.findViewById(R.id.slash_add), 100, 0).start();
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_addition));
                break;
            case ArithmosGame.SUBTRACT:
                Animations.popIn(rootLayout.findViewById(R.id.slash_sub), 100, 0).start();
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_subtraction));
                break;
            case ArithmosGame.MULTIPLY:
                Animations.popIn(rootLayout.findViewById(R.id.slash_mult), 100, 0).start();
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_multiplication));
                break;
            case ArithmosGame.DIVIDE:
                Animations.popIn(rootLayout.findViewById(R.id.slash_div), 100, 0).start();
                imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.ic_slash_division));
                break;
        }

        rootLayout.addView(layout);
        Animations.slideUp(layout, 200, 0, rootLayout.getHeight() / 3).start();
        AnimatorSet set = Animations.explodeFade(layout, 200, 1200);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                rootLayout.removeView(layout);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        set.start();
    }

    @Override
    public void OnCancelBomb(String operation){
        View v = null;
        switch (operation){
            case ArithmosGame.ADD:
                v = rootLayout.findViewById(R.id.slash_add);
                break;
            case ArithmosGame.SUBTRACT:
                v = rootLayout.findViewById(R.id.slash_sub);
                break;
            case ArithmosGame.MULTIPLY:
                v = rootLayout.findViewById(R.id.slash_mult);
                break;
            case ArithmosGame.DIVIDE:
                v = rootLayout.findViewById(R.id.slash_div);
                break;
        }
        if (v != null){
            final View icon = v;
            AnimatorSet set = Animations.fadeOut(icon, 100, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    icon.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
        cacheLevel();
    }


    // Game specials
    public void SkipGoalNumberClick(View v){
        if (gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_SKIP) > 0) {
            int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_SKIP);
            game.skipGoalNumber();
            TextView skip = (TextView)rootLayout.findViewById(R.id.skip_button);
            if (count < 1)
                skip.setVisibility(View.GONE);
            else if (count < 2)
                skip.setText("");
            else
                skip.setText(String.valueOf(count));
        }
    }

    public void CalcButtonClick(View v){
        if (!game.isEvalLeftToRight()) {
            v.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.rounded_corner_primary_accent, null));
            game.setEvalLeftToRight(true);
        } else {
            v.setBackground(null);
            game.setEvalLeftToRight(false);
        }
    }

    private void BombLongClick(View v){
        gameBoard.setOnDragListener(new BombDragEventListener());
        View.DragShadowBuilder myShadow = new DragShadowBuilder(v, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_bomb, null));
        v.startDrag(null, myShadow, null, 0);
    }

    protected class BombDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    if (gameBoard.placeBomb(event.getX(), event.getY())){
                        TextView bomb = (TextView)rootLayout.findViewById(R.id.bomb_button);
                        int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_BOMB);
                        if (count < 1)
                            bomb.setVisibility(View.GONE);
                        else if (count < 2)
                            bomb.setText("");
                        else
                            bomb.setText(String.valueOf(count));
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void PencilLongClick(View v){
        gameBoard.setOnDragListener(new PencilDragEventListener());
        View.DragShadowBuilder myShadow = new DragShadowBuilder(v, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_pencil, null));
        v.startDrag(null, myShadow, null, 0);
    }

    protected class PencilDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    if (gameBoard.isChangeableNumber(event.getX(), event.getY())){
                        // Show number selection popup
                        showNumberSelectionPopup(event.getX(), event.getY());
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void showNumberSelectionPopup(final float x, final float y){
        // Prepare listview to show available numbers
        final ListView numberList = new ListView(context);
        numberList.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.pencil_border_vertical_white_background, null));
        ArithmosLevel level = new ArithmosLevel(context, ArithmosGameBase.getLevelXmlIds(game.getChallengeName())[game.getChallengeLevel()]);
        final ArrayList<Integer> numbers = new ArrayList<>(level.getGridNumbers().length + level.getGridSpecialNumbers().length);
        for (int n : level.getGridNumbers()) {
            if (!numbers.contains(n))
                numbers.add(n);
        }
        for (int n : level.getGridSpecialNumbers()) {
            if (!numbers.contains(n))
                numbers.add(n);
        }
        numberList.setAdapter(new ArrayAdapter<>(context, R.layout.goal_list_item, numbers));

        // Setup touch response
        numberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                gameBoard.changeNumber(x, y, String.valueOf(numbers.get(position)));
                TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_CHANGE);
                if (count < 1)
                    pencil.setVisibility(View.GONE);
                else if (count < 2)
                    pencil.setText("");
                else
                    pencil.setText(String.valueOf(count));
                rootLayout.removeView(numberList);
            }
        });
        gameBoard.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                rootLayout.removeView(numberList);
                gameBoard.setOnTouchListener(null);
                return false;
            }
        });

        // Position listview popup
        int width = 150, height = 300;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
        if (x > gameBoard.getRight() - width)
            params.leftMargin = gameBoard.getLeft() + (int)x - width;
        else
            params.leftMargin = gameBoard.getLeft() + (int)x;
        if (y > gameBoard.getBottom() - height)
            params.topMargin = gameBoard.getTop() + (int)y - height;
        else
            params.topMargin = gameBoard.getTop() + (int)y;
        numberList.setLayoutParams(params);

        // Animate listview popup
        numberList.setVisibility(View.INVISIBLE);
        rootLayout.addView(numberList);
        Animations.slideUp(numberList, 200, 0, rootLayout.getHeight() / 3).start();
    }

    // Google Play Games Services
    private GoogleApiClient mGoogleApiClient;

    private static int RC_SIGN_IN = 9001;

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;

    @Override
    public void onConnected(Bundle connectionHint) {
        if (loadSavedGame) LoadSavedGame();
        if (retrySaveGame) SaveLevel();
        if (retrySaveGameState) saveGameState();
        else if (getIntent().hasExtra(GAME_BASE_FILE_NAME) && gameBaseNeedsDownload)
            loadGameState(getIntent().getStringExtra(GAME_BASE_FILE_NAME));
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (mResolvingConnectionFailure) {
            // already resolving
            return;
        }

        // if the sign-in button was clicked or if auto sign-in is enabled,
        // launch the sign-in flow
        if (mSignInClicked || mAutoStartSignInFlow) {
            mAutoStartSignInFlow = false;
            mSignInClicked = false;
            mResolvingConnectionFailure = true;

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."
            if (!BaseGameUtils.resolveConnectionFailure(this,
                    mGoogleApiClient, connectionResult,
                    RC_SIGN_IN, getString(R.string.gamehelper_sign_in_failed))) {
                mResolvingConnectionFailure = false;
                retrySaveGame = false;
            }
        }

        // Put code here to display the sign-in button
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Attempt to reconnect
        mGoogleApiClient.connect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == RC_SIGN_IN) {
            mSignInClicked = false;
            mResolvingConnectionFailure = false;
            if (resultCode == RESULT_OK) {
                mGoogleApiClient.connect();
            } else {
                // Bring up an error dialog to alert the user that sign-in
                // failed. The R.string.signin_failure should reference an error
                // string in your strings.xml file that tells the user they
                // could not be signed in, such as "Unable to sign in."
                BaseGameUtils.showActivityResultError(this,
                        requestCode, resultCode, R.string.gamehelper_sign_in_failed);
            }
        }

    }
}
