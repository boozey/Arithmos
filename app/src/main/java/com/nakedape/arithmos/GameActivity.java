package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
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
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.BaseGameUtils;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Random;

public class GameActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, GameBoard.OnAchievementListener,
        GameBoard.OnPlayCompleted, GameBoard.OnGameOverListener, GameBoard.OnStarEarnedListener,
        GameBoard.OnJewelListener, GameBoard.OnBombListener, GameBoard.OnCancelBombListener {

    private static final String LOG_TAG = "GameActivity";

    // Intent Extra tags
    public static final String LEVEL_XML_RES_ID = "level_xml_res_id";
    public static final String SAVED_GAME = "saved_game";

    // Saving/recreating the activity state
    private static final String HAS_LEVEL_PASSED_SHOWN = "HAS_LEVEL_PASSED_SHOWN";
    private static final String IS_GOALVIEW_PLAYING = "IS_GOALVIEW_PLAYING";
    private static final String ELAPSED_TIME = "ELAPSED_TIME";
    public static final String levelCacheFileName = "arithmos_level_cache";
    private static final String gameCacheFileName = MainActivity.gameCacheFileName;

    private Context context;
    private RelativeLayout rootLayout;
    private ArithmosGame game;
    private GameBoard gameBoard;
    private TextView scoreTextView;
    private int prevScore = 0;
    private long elapsedMillis = 0;
    private boolean stopTimer = false;
    private int activityStartCount;
    private FirebaseAnalytics mFirebaseAnalytics;
    SharedPreferences generalPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase analytics
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Initialize Google Play Services
        generalPrefs = getSharedPreferences(MainActivity.GENERAL_PREFS, MODE_PRIVATE);
        useGooglePlay = generalPrefs.getBoolean(MainActivity.AUTO_SIGN_IN, true);
        if (!useGooglePlay) Log.d(LOG_TAG, "Google Play Services disabled");

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

        // Restore activity state or initialize
        if (savedInstanceState != null){
            hasLevelPassedShown = savedInstanceState.getBoolean(HAS_LEVEL_PASSED_SHOWN, false);
            isGoalViewPlaying = savedInstanceState.getBoolean(IS_GOALVIEW_PLAYING, false);
        }
        loadCachedGame();
        showAds();

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
                levelSnapShotName = getIntent().getStringExtra(SAVED_GAME);
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
        if (useGooglePlay)
            mGoogleApiClient.connect();
        gameBoard.startGame();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (game != null && isGoalViewPlaying) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    GoalView goalView = (GoalView) rootLayout.findViewById(R.id.goal_view);
                    goalView.startGoalAnimation();
                }
            });
        }
        if (game != null && game.hasTimeLimit() && elapsedMillis > 0)
            startTimer();
    }

    @Override
    protected void onPause(){
        super.onPause();
        stopTimer = true;
        if (game != null && game.getGoalType() == ArithmosLevel.GOAL_SINGLE_NUM) {
            GoalView goalView = (GoalView) rootLayout.findViewById(R.id.goal_view);
            goalView.stopGoalAnimation();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
        gameBoard.stopGame();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current game state
        savedInstanceState.putLong(ELAPSED_TIME, elapsedMillis);
        stopTimer = true;
        savedInstanceState.putBoolean(HAS_LEVEL_PASSED_SHOWN, hasLevelPassedShown);
        GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
        savedInstanceState.putBoolean(IS_GOALVIEW_PLAYING, goalView.isPlaying());

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

        switch (id){
            case R.id.action_start_tutorial:
                // Record Firebase Event
                Bundle bundle = new Bundle();
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.TUTORIAL_BEGIN, bundle);
                nextLesson(Tutorial.GOAL_TYPES);
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
                    if (game.hasTimeLimit() || !useGooglePlay) {
                        showQuitPrompt();
                        return true;
                    }
                    else if (hasBeenPlayed) {
                        showSaveLevelPrompt();
                        return true;
                    }
                    else {
                        isSaved = true;
                        finishLevelIncomplete();
                        return true;
                    }
                }
                else {
                    AnimatorSet set = Animations.slideOutDown(rootLayout.findViewById(R.id.generic_popup), 150, 0, rootLayout.getHeight() / 3);
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
    private String levelSnapShotName;
    private boolean loadSavedGame = false, gameBaseNeedsDownload = true, hasBeenPlayed = false,
                    isSaved = false, shouldFinish = false;
    private Bitmap thumbNail;

    private void setupGameUi(){
        gameBoard.setGame(game);

        TextView scoreTextView = (TextView)rootLayout.findViewById(R.id.score_textview);
        scoreTextView.setText(NumberFormat.getIntegerInstance().format(game.getScore(game.getCurrentPlayer())));
        scoreTextView.setVisibility(View.VISIBLE);
        prevScore = game.getScore(game.getCurrentPlayer());

        TextView jewelText = (TextView)rootLayout.findViewById(R.id.jewel_count);
        jewelText.setText(NumberFormat.getIntegerInstance().format(gameBase.getJewelCount() + game.getJewelCount()));

        if (game.getGoalType() == ArithmosLevel.GOAL_301){
            rootLayout.findViewById(R.id.goal_view).setVisibility(View.GONE);
            TextView three01 = (TextView)rootLayout.findViewById(R.id.three01_textview);
            three01.setVisibility(View.VISIBLE);
            String value = String.valueOf(game.get301Total());
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                String finalValue = "";
                for (int i = 0; i < value.length(); i++) {
                    finalValue += value.charAt(i) + "\n";
                }
                three01.setText(finalValue.trim());
            } else
                three01.setText(value);
        } else {
            rootLayout.findViewById(R.id.three01_textview).setVisibility(View.GONE);
            final GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
            goalView.setVisibility(View.VISIBLE);
            goalView.setGame(game);
        }

        TextView timeView = (TextView)rootLayout.findViewById(R.id.time_textview);
        if (game.hasTimeLimit()){
            timeView.setText(Utils.getDate(game.getTimeLimit(), "mm:ss"));
            timeView.setTextColor(ResourcesCompat.getColor(getResources(), R.color.text_primary_dark, null));
            timeView.setVisibility(View.VISIBLE);
        } else {
            timeView.setVisibility(View.GONE);
        }

        // Show stars earned
        View oneStar = rootLayout.findViewById(R.id.one_star);
        oneStar.setAlpha(0f);
        View twoStar = rootLayout.findViewById(R.id.two_star);
        twoStar.setAlpha(0f);
        View threeStar = rootLayout.findViewById(R.id.three_star);
        threeStar.setAlpha(0f);
        int numStars = game.getNumStars();
        if (numStars > 0)
            Animations.popIn(oneStar, 100, 0).start();
        if (numStars > 1)
            Animations.popIn(twoStar, 100, 100).start();
        if (numStars > 2)
            Animations.popIn(threeStar, 100, 200).start();

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

        setupSpecials();
    }

    private void setupSpecials(){
        if (game == null || gameBase == null) return;
        TextView bomb = (TextView)rootLayout.findViewById(R.id.bomb_button);
        bomb.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                BombLongClick(v);
                return true;
            }
        });
        int count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_BOMB);
        if (count > 0 && bomb.getVisibility() != View.VISIBLE)
            Animations.popIn(bomb, 200, 100).start();
        if (count > 1) {
            bomb.setCompoundDrawablePadding(-12);
            bomb.setText(String.valueOf(count));
        }

        TextView calc = (TextView)rootLayout.findViewById(R.id.calc_button);
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_OP_ORDER);
        if (count > 0 && calc.getVisibility() != View.VISIBLE)
            Animations.popIn(calc, 200, 175).start();
        if (count > 1) {
            calc.setCompoundDrawablePadding(-12);
            calc.setText(String.valueOf(count));
        }

        TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
        pencil.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                PencilLongClick(v);
                return false;
            }
        });
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_CHANGE);
        if (count > 0 && pencil.getVisibility() != View.VISIBLE)
            Animations.popIn(pencil, 200, 250).start();
        if (count > 1) {
            pencil.setCompoundDrawablePadding(-12);
            pencil.setText(String.valueOf(count));
        }

        if (game.getGoalType() != ArithmosLevel.GOAL_301) {
            TextView arrow = (TextView) rootLayout.findViewById(R.id.skip_button);
            count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_SKIP);
            if (count > 0 && pencil.getVisibility() != View.VISIBLE)
                Animations.popIn(arrow, 200, 325).start();
            if (count > 1) {
                arrow.setCompoundDrawablePadding(-12);
                arrow.setText(String.valueOf(count));
            }
        }

        TextView zero = (TextView)rootLayout.findViewById(R.id.zero_button);
        zero.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ZeroButtonClick(v);
                return false;
            }
        });
        count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_ZERO);
        if (count > 0 && zero.getVisibility() != View.VISIBLE)
            Animations.popIn(zero, 200, 325).start();
        if (count > 1) {
            zero.setCompoundDrawablePadding(-8);
            zero.setText(String.valueOf(count));
        }

        if (game.getGoalType() != ArithmosLevel.GOAL_301){
            TextView autoView = (TextView)rootLayout.findViewById(R.id.auto_run_button);
            count = gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_AUTO_RUN);
            if (count > 0 && autoView.getVisibility() != View.VISIBLE)
                Animations.popIn(autoView, 200, 325).start();
            if (count > 1) {
                autoView.setCompoundDrawablePadding(-8);
                autoView.setText(String.valueOf(count));
            }
        }
    }

    private void LoadGameLevel(int resId){
        final ArithmosLevel level = new ArithmosLevel(context, resId);
        game = new ArithmosGame(level);
        animStartDelay = 0;
        hasLevelPassedShown = false;
        hasBeenPlayed = false;
        isSaved = false;
        elapsedMillis = 0;
        setupGameUi();
        gameBoard.startGame();
        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                AnimatorSet set = Animations.fadeIn(gameBoard, 300, 0);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        gameBoard.setAlpha(0f);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        showLevelInfoPopup(level);
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

        // Record Firebase Event
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Game Level");
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, game.getChallengeName() + game.getChallengeLevel());
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
    }

    private void retryLevel() {
        if (showAds && mInterstitialAd.isLoaded() && Math.random() < 0.5){
            AnimatorSet set = Animations.fadeOut(gameBoard, 300, 0);
            set.start();
            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdClosed() {
                    super.onAdClosed();
                    String challenge = game.getChallengeName();
                    int level = game.getChallengeLevel();
                    LoadGameLevel(ArithmosGameBase.getLevelXmlIds(challenge)[level]);
                    cacheLevel();
                    cacheGame();
                    loadInterstitialAd();
                }
            });
            mInterstitialAd.show();
        } else {
            AnimatorSet set = Animations.fadeOut(gameBoard, 300, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    String challenge = game.getChallengeName();
                    int level = game.getChallengeLevel();
                    LoadGameLevel(ArithmosGameBase.getLevelXmlIds(challenge)[level]);
                    cacheLevel();
                    cacheGame();
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

    private void loadNextLevel(){
        if (showAds && mInterstitialAd.isLoaded() && Math.random() < 0.75){
            AnimatorSet set = Animations.fadeOut(gameBoard, 300, 0);
            set.start();
            mInterstitialAd.setAdListener(new AdListener() {
                @Override
                public void onAdClosed() {
                    super.onAdClosed();
                    String challenge = game.getChallengeName();
                    int level = game.getChallengeLevel();
                    LoadGameLevel(ArithmosGameBase.getNextLevelXmlId(challenge, level));
                    setupSpecials();
                    cacheLevel();
                    cacheGame();
                    loadInterstitialAd();
                }
            });
            mInterstitialAd.show();
        } else {
            AnimatorSet set = Animations.fadeOut(gameBoard, 300, 0);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    String challenge = game.getChallengeName();
                    int level = game.getChallengeLevel();
                    LoadGameLevel(ArithmosGameBase.getNextLevelXmlId(challenge, level));
                    cacheLevel();
                    cacheGame();
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

        TextView descView = (TextView) layout.findViewById(R.id.description_textview);
        descView.setVisibility(View.VISIBLE);
        if (level.getGoalType() != ArithmosLevel.GOAL_301) {
            if (level.getNumGoalsToWin() < level.getGoalNumbers().length) {
                descView.setText(getString(R.string.find_x_of_x_numbers, level.getNumGoalsToWin(), level.getGoalNumbers().length));
            } else {
                descView.setText(getString(R.string.find_x_numbers, level.getNumGoalsToWin()));
            }
        } else {
            descView.setText(R.string.three01_description);
        }

        TextView oneStarView = (TextView)layout.findViewById(R.id.one_star);
        oneStarView.setText(NumberFormat.getIntegerInstance().format(level.getStarLevels()[0]));

        TextView twoStarView = (TextView)layout.findViewById(R.id.two_star);
        twoStarView.setText(NumberFormat.getIntegerInstance().format(level.getStarLevels()[1]));

        TextView threeStarView = (TextView)layout.findViewById(R.id.three_star);
        threeStarView.setText(NumberFormat.getIntegerInstance().format(level.getStarLevels()[2]));

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
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
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
                        GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
                        goalView.startGoalAnimation();
                        isGoalViewPlaying = true;
                        int levelPlays = gameBase.getInt(ArithmosGameBase.LEVEL_START_COUNT, 0);
                        gameBase.putInt(ArithmosGameBase.LEVEL_START_COUNT, ++levelPlays);
                        if (!showAds) showAds();
                        // Continue if appropriate
                        if (generalPrefs.getBoolean(GAME_PLAY_TUTORIAL_STARTED, true) &&
                                !generalPrefs.getBoolean(GAME_PLAY_TUTORIAL_FINISHED, false))
                            nextLesson(Tutorial.GOAL_TYPES);
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
        AnimatorSet set = Animations.slideUp(layout, 200, 200, rootLayout.getHeight() / 3);
        // Start tutorial, if appropriate
        if (generalPrefs.getBoolean(GAME_PLAY_TUTORIAL_STARTED, true) &&
                !generalPrefs.getBoolean(GAME_PLAY_TUTORIAL_FINISHED, false)) {
            // Record Firebase Event
            Bundle bundle = new Bundle();
            mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.TUTORIAL_BEGIN, bundle);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    nextLesson(Tutorial.LEVEL_TYPES);
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }
        set.start();
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
            Log.i(LOG_TAG, "Game state cached: " + Utils.getDate(gameBase.timeStamp(), "MM/dd/yy hh:mm:ss") );
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
                    gameBase.setSaved(false);
                    setupSpecials();
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
                    elapsedMillis = game.getElapasedTime();
                    setupGameUi();
                    gameBoard.startGame();
                    if (game.getGoalType() == ArithmosLevel.GOAL_SINGLE_NUM) {
                        rootLayout.post(new Runnable() {
                            @Override
                            public void run() {
                                GoalView goalView = (GoalView) rootLayout.findViewById(R.id.goal_view);
                                goalView.startGoalAnimation();
                                isGoalViewPlaying = true;
                            }
                        });
                    }
                    if (game.isLevelPassed()) showLevelPassedPopup();
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

    private void loadSavedLevel(){
        final View loadingPopup = Utils.progressPopup(context, R.string.loading_saved_game_message);
        rootLayout.addView(loadingPopup);
        Animations.slideUp(loadingPopup, 200, 0, rootLayout.getHeight() / 3).start();
        Games.Snapshots.open(mGoogleApiClient, levelSnapShotName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
            @Override
            public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                try {
                    game = new ArithmosGame(openSnapshotResult.getSnapshot().getSnapshotContents().readFully());

                    setupGameUi();
                    showLevelInfoPopup(new ArithmosLevel(context, ArithmosGameBase.getLevelXmlIds(game.getChallengeName())[game.getChallengeLevel()]));
                    rootLayout.removeView(loadingPopup);
                    loadSavedGame = false;
                } catch (IOException e) {e.printStackTrace();}
            }
        });

    }

    private void loadGameState(){
        if (gameBaseNeedsDownload) {
            SharedPreferences gamePrefs = getSharedPreferences(MainActivity.GAME_PREFS, MODE_PRIVATE);
            String gameFileName = gamePrefs.getString(MainActivity.GAME_FILE_NAME, null);
            if (gameFileName != null)
            Games.Snapshots.open(mGoogleApiClient, gameFileName, false).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    int status = openSnapshotResult.getStatus().getStatusCode();
                    Log.i(LOG_TAG, "Load Result status: " + status);
                    Snapshot snapshot = openSnapshotResult.getSnapshot(),resolvedSnapshot;
                    resolvedSnapshot = snapshot;
                    if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT) {
                        Snapshot conflictSnapshot = openSnapshotResult.getConflictingSnapshot();
                        if (snapshot.getMetadata().getLastModifiedTimestamp() <=
                                conflictSnapshot.getMetadata().getLastModifiedTimestamp()) {
                            resolvedSnapshot = conflictSnapshot;
                        }
                    }
                    try {
                            gameBase.loadByteData(resolvedSnapshot.getSnapshotContents().readFully());
                            cacheGame();
                            Log.d(LOG_TAG, "Game base updated from Google");
                            setupSpecials();
                            TextView jewelText = (TextView) rootLayout.findViewById(R.id.jewel_count);
                            Animations.CountTo(jewelText, 0, gameBase.getJewelCount());
                        gameBaseNeedsDownload = false;

                    } catch (IOException | NullPointerException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void saveGameAsync(){
        if (useGooglePlay) {
            if (mGoogleApiClient.isConnected()) {
                SharedPreferences prefs = getSharedPreferences(MainActivity.GAME_PREFS, MODE_PRIVATE);
                String gameFileName = prefs.getString(MainActivity.GAME_FILE_NAME, null);
                if (gameFileName != null)
                        Games.Snapshots.open(mGoogleApiClient, gameFileName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                    @Override
                    public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                        int status = openSnapshotResult.getStatus().getStatusCode();
                        Log.i(LOG_TAG, "Save Result status: " + status);
                        Snapshot snapshot = openSnapshotResult.getSnapshot(),resolvedSnapshot;
                        resolvedSnapshot = snapshot;
                        final String desc = "Arithmos Game Data";
                        if (status == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT) {
                            Snapshot conflictSnapshot = openSnapshotResult.getConflictingSnapshot();
                            if (snapshot.getMetadata().getLastModifiedTimestamp() <=
                                    conflictSnapshot.getMetadata().getLastModifiedTimestamp()) {
                                resolvedSnapshot = conflictSnapshot;
                                Games.Snapshots.resolveConflict(mGoogleApiClient, openSnapshotResult.getConflictId(), resolvedSnapshot).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                                    @Override
                                    public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                                        writeSnapshot(openSnapshotResult.getSnapshot(), gameBase.getByteData(), desc, null).setResultCallback(new ResultCallback<Snapshots.CommitSnapshotResult>() {
                                            @Override
                                            public void onResult(@NonNull Snapshots.CommitSnapshotResult commitSnapshotResult) {
                                                isSaved = true;
                                                gameBase.setSaved(true);
                                                Log.i(LOG_TAG, "Game state saved: " + Utils.getDate(commitSnapshotResult.getSnapshotMetadata().getLastModifiedTimestamp(), "MM/dd/yy hh:mm:ss"));
                                                cacheGame();

                                                if (shouldFinish) {
                                                    if (game.isLevelPassed())
                                                        finishLevelComplete();
                                                    else
                                                        finishLevelIncomplete();
                                                }
                                            }
                                        });
                                    }
                                });
                            }
                        } else {
                            writeSnapshot(resolvedSnapshot, gameBase.getByteData(), desc, null).setResultCallback(new ResultCallback<Snapshots.CommitSnapshotResult>() {
                                @Override
                                public void onResult(@NonNull Snapshots.CommitSnapshotResult commitSnapshotResult) {
                                    isSaved = true;
                                    gameBase.setSaved(true);
                                    Log.i(LOG_TAG, "Game state saved: " + Utils.getDate(commitSnapshotResult.getSnapshotMetadata().getLastModifiedTimestamp(), "MM/dd/yy hh:mm:ss"));
                                    cacheGame();

                                    if (shouldFinish) {
                                        if (game.isLevelPassed())
                                            finishLevelComplete();
                                        else
                                            finishLevelIncomplete();
                                    }
                                }
                            });
                        }
                    }
                });
            } else if (!mGoogleApiClient.isConnected() && useGooglePlay && connectAttempts++ < maxAttempts){
                Log.d(LOG_TAG, "SaveGameState() GoogleAPIClient not connected, retying");
                mGoogleApiClient.connect();
                saveGameAsync();
            }
        }
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
                ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.FORFEIT);
                result.isLevelPassed = false;
                OnGameOver(result);
                Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3).start();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.no);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
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

    private void showSaveLevelPrompt(){
        saveGameAsync();
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
                recordAchievements();
                Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3).start();
                SaveLevel();
            }
        });

        Button button2 = (Button)layout.findViewById(R.id.button2);
        button2.setText(R.string.no);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.FORFEIT);
                result.isLevelPassed = true;
                OnGameOver(result);
                AnimatorSet set = Animations.slideOutDown(layout, 200, 0, rootLayout.getHeight() / 3);
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

    private void SaveLevel() {
        showLoadingPopup();
        if (levelSnapShotName == null) {
            String unique = new BigInteger(281, new Random()).toString(13);
            levelSnapShotName = "ArithmosLevel_" + unique;
        }
        if (mGoogleApiClient.isConnected()) {
            Games.Snapshots.open(mGoogleApiClient, levelSnapShotName, true).setResultCallback(new ResultCallback<Snapshots.OpenSnapshotResult>() {
                @Override
                public void onResult(@NonNull Snapshots.OpenSnapshotResult openSnapshotResult) {
                    String desc = getString(ArithmosGameBase.getChallengeDisplayNameResId(game.getChallengeName())) + " " +
                            getString(ArithmosGameBase.getLevelDisplayNameResIds(game.getChallengeName())[game.getChallengeLevel()]);

                    writeSnapshot(openSnapshotResult.getSnapshot(), game.getSaveGameData(), desc, thumbNail).setResultCallback(new ResultCallback<Snapshots.CommitSnapshotResult>() {
                        @Override
                        public void onResult(@NonNull Snapshots.CommitSnapshotResult commitSnapshotResult) {
                            //saveGameState();
                            finishLevelIncomplete();
                            Log.d(LOG_TAG, "Level Snapshot Written");
                        }
                    });
                }
            });
        }
        else if (connectAttempts++ < maxAttempts){
            Log.d(LOG_TAG, "SaveLevel() GoogleAPIClient not connected, retying");
            mGoogleApiClient.connect();
            SaveLevel();
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
        if ((useGooglePlay && isSaved) || !useGooglePlay) {
            Intent data = new Intent();
            if (levelSnapShotName != null) {
                data.putExtra(SAVED_GAME, levelSnapShotName);
            }
            if (levelCache != null) levelCache.delete();
            setResult(Activity.RESULT_OK, data);
            finish();
        } else {
            shouldFinish = true;
            showLoadingPopup();
        }

    }

    private void finishLevelIncomplete(){
        if ((useGooglePlay && isSaved) || !useGooglePlay) {
            Intent data = new Intent();
            if (levelSnapShotName != null) {
                data.putExtra(SAVED_GAME, levelSnapShotName);
            }
            if (levelCache != null) levelCache.delete();
            setResult(Activity.RESULT_CANCELED, data);
            finish();
        } else {
            shouldFinish = true;
            showLoadingPopup();
        }
    }

    private void recordActivityTurnFinished(ArithmosGame.GameResult result){
        GameActivityItem newItem;
        if (game.isLevelPassed()) {
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
        logPlayData();
        // Recorded locally
        if (game.isLevelPassed()) {
            gameBase.recordStars(game.getChallengeName(), game.getChallengeLevel(), game.getNumStars());
        }
        int jewelCount = game.getJewelCount();
        game.resetJewelCount();
        gameBase.recordJewels(jewelCount);
        cacheGame();

        // Record Firebase Event
        Bundle bundle = new Bundle();
        bundle.putLong(FirebaseAnalytics.Param.SCORE, game.getScore(game.getCurrentPlayer()));
        bundle.putString(FirebaseAnalytics.Param.CHARACTER, game.getChallengeName() + " Level " + game.getChallengeLevel());
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.POST_SCORE, bundle);

        // Recorded if using Google Play Games
            if (mGoogleApiClient.isConnected()) {
                saveGameAsync();
                if (jewelCount > 0) {
                    Games.Achievements.increment(mGoogleApiClient, getString(R.string.achievement_collector), jewelCount);
                }
                if (game.isLevelPassed()) {
                    // Record level score
                    if (game.getLeaderboardId() != null)
                        Games.Leaderboards.submitScore(mGoogleApiClient, game.getLeaderboardId(), game.getScore(game.getCurrentPlayer()));
                    if (game.getNumStars() == 3) {
                        Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_level_master));
                        Games.Achievements.reveal(mGoogleApiClient, getString(R.string.achievement_challenge_master));
                        if (gameBase.isChallengeThreeStarred(game.getChallengeName()))
                            Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_challenge_master));
                    }
                    if (game.getGoalType() == ArithmosLevel.GOAL_301 && game.getRunCount() <= 3)
                        Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_301_in_3));
                    if (game.getNumTilesRemaining() == 0)
                        Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_blackout));
                    if (gameBase.getJewelCount() >= 1000)
                        Games.Achievements.unlock(mGoogleApiClient, getString(R.string.achievement_the_one_percent));
                }
            } else if (useGooglePlay && connectAttempts++ < maxAttempts){
                if (!mGoogleApiClient.isConnecting()) mGoogleApiClient.connect();
                recordAchievements();
            }
    }

    public void ExitButtonClick(View v){
            if (game.isLevelPassed())
                finishLevelComplete();
            else
                finishLevelIncomplete();
    }

    // Game events
    private int animStartDelay = 0;
    private boolean hasLevelPassedShown = false, isGoalViewPlaying = false;

    private void startTimer(){
        stopTimer = false;
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
                            timeText.setText("00:00");
                            ArithmosGame.GameResult result = new ArithmosGame.GameResult(ArithmosGame.GameResult.TIME_UP);
                            result.isLevelPassed = false;
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
                String value = String.valueOf(game.get301Total());
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                    String finalValue = "";
                    for (int i = 0; i < value.length(); i++) {
                        finalValue += value.charAt(i) + "\n";
                    }
                    three01.setText(finalValue.trim());
                } else
                    three01.setText(value);
            } else if (game.getGoalType() == ArithmosLevel.GOAL_SINGLE_NUM) {
                int x = game.getNumGoalsToWin() - game.getGoalsWon(game.PLAYER1).size();
                Log.d(LOG_TAG, "goals remaining = " + x);
                if (x == 1 || x == 2)
                    showQuickPopup(getResources().getQuantityString(R.plurals.x_more_to_pass, x, x));
            }
            else {
                int x = game.getNumGoalsToWin() - game.getGoalsWon(game.PLAYER1).size();
                Log.d(LOG_TAG, "goals remaining = " + x);
                if (x == 1 || x == 2)
                    showQuickPopup(getResources().getQuantityString(R.plurals.x_more_to_pass, x, x));
                // Update goal list if multi-number mode
                GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
                goalView.invalidate();
            }

            // If an evaluate left to right special was use, record and reset
            if (game.isEvalLeftToRight()) {
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_OP_ORDER);
                if (count < 1)
                    calc.setVisibility(View.GONE);
                else if (count < 2) {
                    calc.setText("");
                    calc.setCompoundDrawablePadding(0);
                }
                else
                    calc.setText(String.valueOf(count));
                cacheGame();
            }
        }
        if (game.isEvalLeftToRight()) {
            calc.setBackground(null);
            game.setEvalLeftToRight(false);
        }

        // Continue tutorial if appropriate
        if (isTutorialMode && generalPrefs.getString(MainActivity.TUTORIAL_LESSON_NAME, "blah").equals(Tutorial.SELECTION))
            nextLesson(Tutorial.AUTO_SELECTION);
    }

    @Override
    public void OnStarEarned(int numStars){
        // Record stars

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
        Animations.slideUp(layout, 200, gameBoard.getAnimDelay() + animStartDelay, rootLayout.getHeight() / 3).start();
        AnimatorSet set = Animations.explodeFade(layout, 200, gameBoard.getAnimDelay() + animStartDelay + 1200);
        animStartDelay += 1200;
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animStartDelay -= 1200;
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
        if (result.isLevelPassed && !result.noMorePossiblePlays && result.result != ArithmosGame.GameResult.FORFEIT) showLevelPassedPopup();

        if (result.noMorePossiblePlays || result.result == ArithmosGame.GameResult.FORFEIT
                || result.result == ArithmosGame.GameResult.TIME_UP) {
            stopTimer = true;
            GoalView goalView = (GoalView)rootLayout.findViewById(R.id.goal_view);
            goalView.stopGoalAnimation();
            recordActivityTurnFinished(result);
            recordAchievements();
            showGameOverPopup(result);
        }
    }

    private void showLevelPassedPopup(){
        if (hasLevelPassedShown) return;
        hasLevelPassedShown = true;
        final View layout = getLayoutInflater().inflate(R.layout.quick_popup, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        layout.setLayoutParams(params);
        layout.setVisibility(View.INVISIBLE);
        rootLayout.addView(layout);
        layout.post(new Runnable() {
            @Override
            public void run() {
                AnimatorSet set = Animations.slideInDownAndOutUp(layout, 3000, animStartDelay + gameBoard.getAnimDelay(), layout.getMeasuredHeight());
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

    }

    private void showGameOverPopup(ArithmosGame.GameResult result){
        if (rootLayout.findViewById(R.id.game_over_popup) != null) return;

        final View layout = getLayoutInflater().inflate(R.layout.game_over_popup, null);

        // Prepare info to display
        if (game.isLevelPassed()) {
            TextView titleText = (TextView)layout.findViewById(R.id.title_textview);
            titleText.setText(R.string.you_won);
            int numStars = gameBase.getNumStars(game.getChallengeName(), game.getChallengeLevel());
            int[] nameId = gameBase.unlockNextLevel(game.getChallengeName(), game.getChallengeLevel());
            if (nameId != null) {
                TextView levelView1 = (TextView) layout.findViewById(R.id.textview1);
                levelView1.setVisibility(View.VISIBLE);
                levelView1.setText(getString(R.string.unlock_level_x_x, getString(nameId[0]), getString(nameId[1])));
            }
            if (numStars > 1) {
                nameId = gameBase.unlockNextLevel(game.getChallengeName(), game.getChallengeLevel() + 1);
                if (nameId != null) {
                    TextView levelView2 = (TextView) layout.findViewById(R.id.textview2);
                    levelView2.setVisibility(View.VISIBLE);
                    levelView2.setText(getString(R.string.unlock_level_x_x, getString(nameId[0]), getString(nameId[1])));
                }
            }
        } else if (result.result == ArithmosGame.GameResult.TIME_UP){
            TextView textView1 = (TextView)layout.findViewById(R.id.textview1);
            textView1.setVisibility(View.VISIBLE);
            textView1.setText(R.string.times_up);
        } else if (result.noMorePossiblePlays){
            TextView textView1 = (TextView)layout.findViewById(R.id.textview1);
            textView1.setVisibility(View.VISIBLE);
            textView1.setText(R.string.no_more_moves);
        }

        if (gameBase.isNextLevelUnlocked(game.getChallengeName(), game.getChallengeLevel())) {
            Button nextLevelButton = (Button) layout.findViewById(R.id.next_level_button);
            nextLevelButton.setVisibility(View.VISIBLE);
            nextLevelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AnimatorSet set = Animations.slideOutDown(findViewById(R.id.game_over_popup), 200, 0, rootLayout.getHeight() / 3);
                    set.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            rootLayout.removeView(layout);
                            loadNextLevel();
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
        }

        Button retryButton = (Button)layout.findViewById(R.id.retry_button);
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AnimatorSet set = Animations.slideOutDown(findViewById(R.id.game_over_popup), 200, 0, rootLayout.getHeight() / 3);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rootLayout.removeView(layout);
                        retryLevel();
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

        if (game.getBonusCount(ArithmosLevel.BONUS_BALLOONS) > 0) {
            TextView bonusView = (TextView) layout.findViewById(R.id.balloon_count);
            bonusView.setVisibility(View.VISIBLE);
            bonusView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_BALLOONS)));
        }

        if (game.getBonusCount(ArithmosLevel.BONUS_OP_LOCK) > 0) {
            TextView bonusView = (TextView) layout.findViewById(R.id.lock_count);
            bonusView.setVisibility(View.VISIBLE);
            bonusView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_OP_LOCK)));
        }

        if (game.getBonusCount(ArithmosLevel.BONUS_APPLE) > 0) {
            TextView bonusView = (TextView) layout.findViewById(R.id.apple_count);
            bonusView.setVisibility(View.VISIBLE);
            bonusView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_APPLE)));
        }

        if (game.getBonusCount(ArithmosLevel.BONUS_BANANAS) > 0) {
            TextView bonusView = (TextView) layout.findViewById(R.id.banana_count);
            bonusView.setVisibility(View.VISIBLE);
            bonusView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_BANANAS)));
        }

        if (game.getBonusCount(ArithmosLevel.BONUS_CHERRIES) > 0) {
            TextView bonusView = (TextView) layout.findViewById(R.id.cherry_count);
            bonusView.setVisibility(View.VISIBLE);
            bonusView.setText(String.format(getString(R.string.number_after_x), game.getBonusCount(ArithmosLevel.BONUS_CHERRIES)));
        }

        // Prepare touch
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layout.setLayoutParams(params);
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        rootLayout.addView(layout);
        AnimatorSet set = Animations.slideUp(layout, 200, animStartDelay + gameBoard.getAnimDelay(), rootLayout.getHeight() / 3);
        // Continue tutorial if appropriate
        if (generalPrefs.getBoolean(GAME_PLAY_TUTORIAL_STARTED, true) &&
                !generalPrefs.getBoolean(GAME_PLAY_TUTORIAL_FINISHED, false))
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    nextLesson(Tutorial.GAME_OVER);
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
    public void OnAchievement(String label){
        if (mGoogleApiClient.isConnected()) Games.Achievements.unlock(mGoogleApiClient, label);
    }

    @Override
    public void OnJewel(){
        TextView jewelTextView = (TextView)rootLayout.findViewById(R.id.jewel_count);
        String text = String.valueOf(gameBase.getJewelCount() + game.getJewelCount());
        jewelTextView.setText(text);
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
        Animations.slideUp(layout, 200, animStartDelay + gameBoard.getAnimDelay(), rootLayout.getHeight() / 3).start();
        AnimatorSet set = Animations.explodeFade(layout, 200, animStartDelay + 1200 + gameBoard.getAnimDelay());
        animStartDelay += 1400;
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                animStartDelay -= 1400;
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
                    if (icon.getId() == R.id.slash_add)
                        icon.setVisibility(View.INVISIBLE);
                    else icon.setVisibility(View.GONE);
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
            gameBoard.useSkipSpecial();
            TextView skip = (TextView)rootLayout.findViewById(R.id.skip_button);
            if (count < 1)
                skip.setVisibility(View.GONE);
            else if (count < 2) {
                skip.setText("");
                skip.setCompoundDrawablePadding(0);
            }
            else
                skip.setText(String.valueOf(count));
            cacheGame();
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
                        else if (count < 2) {
                            bomb.setText("");
                        }
                        else
                            bomb.setText(String.valueOf(count));
                        cacheGame();
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
        numberList.setAdapter(new ArrayAdapter<>(context, R.layout.number_list_item, numbers));

        // Setup touch response
        numberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                gameBoard.changeNumber(x, y, String.valueOf(numbers.get(position)));
                TextView pencil = (TextView)rootLayout.findViewById(R.id.pencil_button);
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_CHANGE);
                if (count < 1)
                    pencil.setVisibility(View.GONE);
                else if (count < 2) {
                    pencil.setText("");
                    pencil.setCompoundDrawablePadding(0);
                }
                else
                    pencil.setText(String.valueOf(count));
                cacheGame();
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

    public void OperationButtonClick(View v){
        if (game.getGoalType() != ArithmosLevel.GOAL_301) {
            v.setSelected(!v.isSelected());
            if (v.isSelected()) {
                gameBoard.setOperationPickMode(GameBoard.MANUAL_PICK);
            } else {
                gameBoard.setOperationPickMode(GameBoard.AUTO_PICK);
            }
        }
    }

    public void ZeroButtonClick(View v){
        gameBoard.setOnDragListener(new ZeroDragEventListener());
        View.DragShadowBuilder myShadow = new DragShadowBuilder(v, ResourcesCompat.getDrawable(getResources(), R.drawable.ic_zero, null));
        v.startDrag(null, myShadow, null, 0);
    }

    protected class ZeroDragEventListener implements View.OnDragListener {
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
                        gameBoard.changeNumber(event.getX(), event.getY(), "0");
                        TextView zeroView = (TextView)rootLayout.findViewById(R.id.zero_button);
                        int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_ZERO);
                        if (count < 1)
                            zeroView.setVisibility(View.GONE);
                        else if (count < 2) {
                            zeroView.setText("");
                            zeroView.setCompoundDrawablePadding(0);
                        }
                        else
                            zeroView.setText(String.valueOf(count));
                        cacheGame();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    public void AutoRunButtonClick(View v){
        if (gameBase.getSpecialCount(ArithmosGameBase.SPECIAL_AUTO_RUN) > 0) {
            if (gameBoard.useAutoRunSpecial());
            {
                int count = gameBase.useSpecial(ArithmosGameBase.SPECIAL_AUTO_RUN);
                TextView skip = (TextView) rootLayout.findViewById(R.id.auto_run_button);
                if (count < -10)
                    skip.setVisibility(View.GONE);
                else if (count < 2) {
                    skip.setText("");
                    skip.setCompoundDrawablePadding(0);
                }
                else
                    skip.setText(String.valueOf(count));
                cacheGame();
            }
        }
    }


    // Google Play Games Services
    private GoogleApiClient mGoogleApiClient;

    private static int RC_SIGN_IN = 9001;

    private boolean mResolvingConnectionFailure = false;
    private boolean mAutoStartSignInFlow = true;
    private boolean mSignInClicked = false;
    private boolean useGooglePlay = true;
    private int connectAttempts = 0, maxAttempts = 10;

    @Override
    public void onConnected(Bundle connectionHint) {
        connectAttempts = 0;
        if (loadSavedGame) loadSavedLevel();
        loadGameState();
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
                        requestCode, resultCode, R.string.sign_in_failed);
                hideLoadingPopup();
            }
        }

    }


    // Ads
    private InterstitialAd mInterstitialAd;
    private boolean showAds = true;

    private void showAds(){
        // Setup Ads
        activityStartCount = gameBase.getInt(ArithmosGameBase.MAIN_START_COUNT, 1);
        int levelStartCount = gameBase.getInt(ArithmosGameBase.LEVEL_START_COUNT, 0);
        showAds = generalPrefs.getBoolean(MainActivity.SHOW_ADS, true) && activityStartCount + levelStartCount >= MainActivity.COUNT_TO_SHOW_ADS;
        if (showAds) {
            MobileAds.initialize(getApplicationContext(), "ca-app-pub-4640479150069852~3029191523");
            AdView mAdView = (AdView) rootLayout.findViewById(R.id.adView);
            mAdView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .build();
            mAdView.loadAd(adRequest);

            AdRequest intstAdRequest = new AdRequest.Builder()
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .build();
            mInterstitialAd = new InterstitialAd(this);
            mInterstitialAd.setAdUnitId(getString(R.string.interstitial_ad_unit_id));
            mInterstitialAd.loadAd(intstAdRequest);
        }
    }

    private void loadInterstitialAd(){
        if (showAds && !mInterstitialAd.isLoaded()) {
            AdRequest intstAdRequest = new AdRequest.Builder()
                    .addTestDevice("B351AB87B7184CD82FD0563D59D1E95B")
                    .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                    .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                    .build();
            mInterstitialAd.loadAd(intstAdRequest);
        }
    }


    // Tutorial
    private static final String GAME_PLAY_TUTORIAL_STARTED = "GAME_PLAY_TUTORIAL_STARTED";
    public static final String GAME_PLAY_TUTORIAL_FINISHED = "GAME_PLAY_TUTORIAL_FINISHED";
    private RelativeLayout tutorialPopup;
    private boolean isTutorialMode = false;
    private View.OnLayoutChangeListener layoutChangeListener;
    private Tutorial tutorial;

    public void nextLesson(final String lessonName){
        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                isTutorialMode = true;
                if (tutorial == null)
                    tutorial = new Tutorial();
                SharedPreferences.Editor editor = generalPrefs.edit();
                editor.putString(MainActivity.TUTORIAL_LESSON_NAME, lessonName);
                editor.apply();

                if (tutorialPopup == null){
                    // Prepare popup window
                    tutorialPopup = (RelativeLayout)getLayoutInflater().inflate(R.layout.tutorial_popup, null);
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    tutorialPopup.setLayoutParams(params);
                    rootLayout.addView(tutorialPopup);
                }

                final Tutorial.Lesson lesson = tutorial.getLesson(lessonName);
                // Setup tutorial message popup
                Log.d(LOG_TAG, "Lesson: " + lessonName);
                Log.d(LOG_TAG, "Next Lesson: " + lesson.nextLesson);

                // Set message
                TextView messageView = (TextView)tutorialPopup.findViewById(R.id.textview1);
                messageView.setText(lesson.textResId);

                // position popup and select background
                View popupForeground = tutorialPopup.findViewById(R.id.pop_up_foreground);
                final View anchorView = rootLayout.findViewById(lesson.anchorId);
                final Rect anchorViewRect = new Rect();
                anchorView.getGlobalVisibleRect(anchorViewRect);
                final Rect rootLayoutRect = new Rect();
                rootLayout.getGlobalVisibleRect(rootLayoutRect);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)popupForeground.getLayoutParams();
                switch (lesson.placementRelToAnchor){
                    case Tutorial.Lesson.BELOW_CENTER:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_top_center_anchor, null));
                        params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth() / 2, anchorViewRect.bottom - rootLayoutRect.top, 0, 0);
                        break;
                    case Tutorial.Lesson.ABOVE_CENTER:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_bottom_center_anchor, null));
                        params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth() / 2, anchorViewRect.top - rootLayoutRect.top - popupForeground.getMeasuredHeight(), 0, 0);
                        break;
                    case Tutorial.Lesson.RIGHT:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_left_center_anchor, null));
                        params.setMargins(anchorViewRect.right, anchorViewRect.centerY() - rootLayoutRect.top - popupForeground.getMeasuredHeight() / 2, 0, 0);
                        break;
                    case Tutorial.Lesson.ABOVE_RIGHT:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_bottom_left_anchor, null));
                        params.setMargins(anchorViewRect.centerX(), anchorViewRect.top - rootLayoutRect.top - popupForeground.getMeasuredHeight(), 0, 0);
                        break;
                    case Tutorial.Lesson.CENTER:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_no_anchor, null));
                        params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth() / 2, anchorViewRect.centerY() - rootLayoutRect.top - popupForeground.getMeasuredHeight() / 2, 0, 0);
                        break;
                    case Tutorial.Lesson.ABOVE_LEFT:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_bottom_right_anchor, null));
                        params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth(), anchorViewRect.top - rootLayoutRect.top - popupForeground.getMeasuredHeight(), 0, 0);
                        break;
                    default:
                        popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_top_left_anchor, null));
                        params.setMargins(anchorViewRect.centerX(), anchorViewRect.bottom - rootLayoutRect.top, 0, 0);
                        break;
                }
                popupForeground.setLayoutParams(params);


                layoutChangeListener = new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        if (tutorialPopup == null) {
                            v.removeOnLayoutChangeListener(this);
                            return;
                        }
                        Rect newViewRect = new Rect();
                        v.getGlobalVisibleRect(newViewRect);
                            Rect rootLayoutRect = new Rect();
                            rootLayout.getGlobalVisibleRect(rootLayoutRect);
                            View popupForeground = tutorialPopup.findViewById(R.id.pop_up_foreground);
                            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)popupForeground.getLayoutParams();
                            switch (lesson.placementRelToAnchor){
                                case Tutorial.Lesson.BELOW_CENTER:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_top_center_anchor, null));
                                    params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth() / 2, anchorViewRect.bottom - rootLayoutRect.top, 0, 0);
                                    break;
                                case Tutorial.Lesson.ABOVE_CENTER:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_bottom_center_anchor, null));
                                    params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth() / 2, anchorViewRect.top - rootLayoutRect.top - popupForeground.getMeasuredHeight(), 0, 0);
                                    break;
                                case Tutorial.Lesson.RIGHT:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_left_center_anchor, null));
                                    params.setMargins(anchorViewRect.right, anchorViewRect.centerY() - rootLayoutRect.top - popupForeground.getMeasuredHeight() / 2, 0, 0);
                                    break;
                                case Tutorial.Lesson.ABOVE_RIGHT:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_bottom_left_anchor, null));
                                    params.setMargins(anchorViewRect.centerX(), anchorViewRect.top - rootLayoutRect.top - popupForeground.getMeasuredHeight(), 0, 0);
                                    break;
                                case Tutorial.Lesson.CENTER:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_no_anchor, null));
                                    params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth() / 2, anchorViewRect.centerY() - rootLayoutRect.top - popupForeground.getMeasuredHeight() / 2, 0, 0);
                                    break;
                                case Tutorial.Lesson.ABOVE_LEFT:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_bottom_right_anchor, null));
                                    params.setMargins(anchorViewRect.centerX() - popupForeground.getMeasuredWidth(), anchorViewRect.top - rootLayoutRect.top - popupForeground.getMeasuredHeight(), 0, 0);
                                    break;
                                default:
                                    popupForeground.setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.popup_top_left_anchor, null));
                                    params.setMargins(anchorViewRect.centerX(), anchorViewRect.bottom - rootLayoutRect.top, 0, 0);
                                    break;
                            }
                            popupForeground.setLayoutParams(params);
                            anchorViewRect.set(newViewRect);
                            tutorialPopup.bringToFront();
                    }
                };
                anchorView.addOnLayoutChangeListener(layoutChangeListener);

                // Update what lesson shows next and what layoutChangeListener to remove
                TextView gotItView = (TextView)tutorialPopup.findViewById(R.id.right_button);
                if (lesson.showButton) {
                    gotItView.setText(lesson.buttonTextResId);
                    gotItView.setVisibility(View.VISIBLE);
                    gotItView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            anchorView.removeOnLayoutChangeListener(layoutChangeListener);
                            AnimatorSet set = Animations.fadeOut(tutorialPopup, 200, 0);
                            set.addListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (lessonName.equals(Tutorial.GAME_OVER)) {
                                        isTutorialMode = false;
                                        // Record prefs
                                        SharedPreferences.Editor editor = generalPrefs.edit();
                                        editor.putBoolean(GAME_PLAY_TUTORIAL_FINISHED, true);
                                        editor.apply();

                                        // Record Firebase Event
                                        Bundle bundle = new Bundle();
                                        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.TUTORIAL_BEGIN, bundle);
                                    }
                                    if (lesson.nextLesson.equals(Tutorial.WAIT_FOR_USER)) {
                                        rootLayout.removeView(tutorialPopup);
                                        tutorialPopup = null;
                                    } else
                                        nextLesson(lesson.nextLesson);
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
                } else
                    gotItView.setVisibility(View.GONE);

                // Update what layoutChangeListener to remove
                View exitView = tutorialPopup.findViewById(R.id.left_button);
                exitView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        isTutorialMode = false;
                        SharedPreferences.Editor editor = generalPrefs.edit();
                        editor.putBoolean(GAME_PLAY_TUTORIAL_FINISHED, true);
                        editor.apply();
                        Toast.makeText(context, R.string.quit_tutorial_message, Toast.LENGTH_LONG).show();
                        anchorView.removeOnLayoutChangeListener(layoutChangeListener);
                        AnimatorSet set = Animations.fadeOut(tutorialPopup, 200, 0);
                        set.addListener(new Animator.AnimatorListener() {
                            @Override
                            public void onAnimationStart(Animator animation) {

                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                rootLayout.removeView(tutorialPopup);
                                tutorialPopup = null;
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

                // Should the popup allow click events to the anchor view?
                if (lesson.allowTouch){
                    tutorialPopup.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            Rect targetViewRect = new Rect();
                            View targetView = rootLayout.findViewById(lesson.targetId);
                            targetView.getGlobalVisibleRect(targetViewRect);
                            if (targetViewRect.contains((int)event.getRawX(), (int)event.getRawY()))
                                return false;
                            else
                                return true;
                        }
                    });
                } else {
                    tutorialPopup.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            return true;
                        }
                    });
                }

                AnimatorSet set = Animations.fadeIn(tutorialPopup, 200, 0);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        tutorialPopup.bringToFront();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {

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
    }


    // Utility methods
    private void showLoadingPopup(){
        if (rootLayout.findViewById(R.id.loading_popup) == null) {
            View loadingPopup = Utils.progressPopup(context, R.string.saving_game_message);
            rootLayout.addView(loadingPopup);
            Animations.slideUp(loadingPopup, 200, 0, rootLayout.getHeight() / 3).start();
        }
    }

    private void hideLoadingPopup(){
        final View popup = rootLayout.findViewById(R.id.loading_popup);
        if (popup != null){
            AnimatorSet set = Animations.slideOutDown(popup, 150, 0, rootLayout.getHeight() / 3);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    rootLayout.removeView(popup);
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

    private void showQuickPopup(int stringResId){
        showQuickPopup(getString(stringResId));
    }
    private void showQuickPopup(String text){
        final View layout = getLayoutInflater().inflate(R.layout.quick_popup, null);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        layout.setLayoutParams(params);
        layout.setVisibility(View.INVISIBLE);

        TextView textView = (TextView)layout.findViewById(R.id.textView1);
        textView.setText(text);

        rootLayout.addView(layout);
        layout.post(new Runnable() {
            @Override
            public void run() {
                AnimatorSet set = Animations.slideInDownAndOutUp(layout, 3000, animStartDelay + gameBoard.getAnimDelay(), layout.getMeasuredHeight());
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
    }

    private void logPlayData(){
        String log_tag = "Gameover";
        Log.d(log_tag, getString(ArithmosGameBase.getChallengeDisplayNameResId(game.getChallengeName())) +
            " " + getString(ArithmosGameBase.getLevelDisplayNameResIds(game.getChallengeName())[game.getChallengeLevel()]));
        if (game.isLevelPassed()) {
            Log.d(log_tag, "Level passed at " + game.getScore(game.getCurrentPlayer()));
            Log.d(log_tag, "with " + game.getNumStars() + " stars");
        } else {
            Log.d(log_tag, "Level failed at " + game.getScore(game.getCurrentPlayer()));
        }
    }
}
