package com.nakedape.arithmos;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Process;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by Nathan on 4/13/2016.
 */
public class GameBoard extends View {

    private static final String LOG_TAG = "GameBoard";

    public interface OnPlayCompleted {
        void OnPlayCompleted(ArithmosGame.GameResult result);
    }
    private OnPlayCompleted onPlayCompleted;
    public void setOnPlayCompleted(OnPlayCompleted listener){
        onPlayCompleted = listener;
    }

    public interface OnAchievementListener {
        void OnAchievement(String label);
    }
    private OnAchievementListener onAchievementListener;
    public void setOnAchievementListener(OnAchievementListener listener) { onAchievementListener = listener; }

    public interface OnGameOverListener {
        void OnGameOver(ArithmosGame.GameResult result);
    }
    private OnGameOverListener onGameOverListener;
    public void setOnGameOverListener(OnGameOverListener listener) { onGameOverListener = listener; }

    public interface OnStarEarnedListener {
        void OnStarEarned(int num);
    }
    private OnStarEarnedListener onStarEarnedListener;
    public void setOnStarEarnedListener(OnStarEarnedListener listener) {onStarEarnedListener = listener;}

    public interface OnJewelListener {
        void OnJewel();
    }
    private OnJewelListener onJewelListener;
    public void setOnJewelListener(OnJewelListener listener) { onJewelListener = listener; }

    public interface OnBombListener {
        void OnBomb(String operation);
    }
    private OnBombListener onBombListener;
    public void setOnBombListener(OnBombListener listener) { onBombListener = listener; }

    public interface OnCancelBombListener {
        void OnCancelBomb(String operation);
    }
    private OnCancelBombListener onCancelBombListener;
    public void setOnCancelBombListener(OnCancelBombListener listener) { onCancelBombListener = listener; }


    // Constructor
    private View thisView;
    private static final int SELECTION_TOUCH = 909;
    private static final int OP_POPUP_TOUCH = 910;
    private int TouchMode = SELECTION_TOUCH;
    private Context context;

    public GameBoard(Context context, AttributeSet attrs){
        super(context, attrs);
        this.context = context;
        thisView = this;

        selectionPath = new Path();
        selectionPaint = new Paint();
        selectionPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color1, null));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(10);
        selectionPaint.setStrokeCap(Paint.Cap.ROUND);
        selectionPaint.setStrokeJoin(Paint.Join.ROUND);

        txtPaint = new Paint();
        txtPaint.setColor(Color.BLACK);
        txtPaint.setTextAlign(Paint.Align.CENTER);

        scorePaint = new Paint();
        scorePaint.setColor(Color.RED);
        scorePaint.setAlpha(0);
        scorePaint.setTextAlign(Paint.Align.CENTER);

    }

    // Class overrides
    @Override
    public boolean onTouchEvent(MotionEvent event){
        switch (TouchMode){
            case SELECTION_TOUCH:
                return handleSelectionTouch(event);
            case OP_POPUP_TOUCH:
                return handleOpPopupTouch(event);
        }
        return false;
    }
    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int reqWidth = MeasureSpec.getSize(widthMeasureSpec), reqHeight = MeasureSpec.getSize(heightMeasureSpec);
        /*if (reqWidth >= reqHeight) {
            setMeasuredDimension(reqHeight * 3/2, reqHeight);
            return;
        }*/
        setMeasuredDimension(reqWidth, reqHeight);
    }
    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){
        resetGameBoard(w, h);
    }
    @Override
    protected void onDraw(Canvas canvas){
        if (gameBoardBitmap != null){
            canvas.drawBitmap(gameBoardBitmap, 0, 0, null);
            for (BonusTile b : bonuses){
                //canvas.drawBitmap(b.getIcon(), b.rectF.centerX() - b.getIcon().getWidth() / 2, b.rectF.centerY() - b.getIcon().getHeight() / 2, null);
                canvas.drawBitmap(b.getIcon(), null, b.getRectF(), null);
            }
            canvas.drawPath(selectionPath, selectionPaint);
            if (showComputerRun) {
                canvas.drawPath(computerRunPath, computerRunPaint);
            }
            if (operationAnimationPositions != null){
                for (int i = 0; i < operationAnimationPositions.size(); i++){
                    rectF = tileDimensions[operationAnimationPositions.get(i)[0]][operationAnimationPositions.get(i)[1]];
                    canvas.drawText(operationAnimationValues.get(i), rectF.centerX(), rectF.centerY() + txtPaint.getTextSize() / 2, txtPaint);
                }
            }
            if (playScoreAnimation){
                canvas.drawText(score, scorePos[0], scorePos[1], scorePaint);
            }
            if (showOpPopup){
                // Draw operation button bitmaps
                for (int i = 0; i < opButtonsRects.length; i++){
                    canvas.drawBitmap(opButtonBitmaps[i], null, opButtonsRects[i], null);
                }
                // Draw operations that have been selected
                for (int i = 1, j = 0; i < opIndex; i += 2) {
                    rectF = tileDimensions[selectedPieces.get(i)[0]][selectedPieces.get(i)[1]];
                    if (game.isOpLockBonus(selectedPieces.get(i)))
                        j++;
                    else
                        canvas.drawText(ArithmosGame.getOperationDisplayString(opList.get(j++)), rectF.centerX(), rectF.centerY() + txtPaint.getTextSize() / 2, txtPaint);
                }

            }
        }
    }


    // Game
    private ArithmosGame game;
    private boolean stopCheckBoardThread = false;
    private Thread checkBoardThread;

    public void setGame(ArithmosGame game){
        this.game = game;
        resetGameBoard(getWidth(), getHeight());
        invalidate();
    }
    public void startGame(){
        if (game != null)
            restartCheckBoardThread();
    }
    public void stopGame(){
        stopCheckBoardThread = true;
    }
    private void restartCheckBoardThread(){
        stopCheckBoardThread = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (checkBoardThread != null && checkBoardThread.isAlive()) {
                        Log.d(LOG_TAG, "Waiting for thread to finish");
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Log.e(LOG_TAG, "Thread interrupted exception");
                        }
                    }
                    stopCheckBoardThread = false;
                    checkBoardThread = new Thread(new CheckBoardThread());
                    checkBoardThread.start();
                }
            }).start();
    }
    private class CheckBoardThread implements Runnable {

        private String[][] boardClone;
        private boolean playFound = false;

        @Override
        public void run(){
            Log.d(LOG_TAG, "Check board thread started");
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boardClone = game.getGameBoard().clone();

            // Check horizontal runs
            for (int r = 0; r < boardClone.length && !playFound && !stopCheckBoardThread; r++) {
                for (int c = 0; c < boardClone[0].length; ) {
                    if (r % 2 == 0 && c % 2 == 0 && !isPiecePlayed(r, c)) {
                        ArrayList<int[]> run = new ArrayList<>(boardClone.length);
                        do {
                            run.add(new int[]{r, c});
                            c++;
                        } while (c < boardClone[0].length && !isPiecePlayed(r, c));
                        if (run.size() > 2) playFound = checkRunAndSubsets(run);
                        else playFound = false;
                    } else c++;
                }
            }

            if (!playFound) {
                // Check vertical runs
                for(int c = 0; c < boardClone[0].length && !playFound && !stopCheckBoardThread; c++)
                    for (int r = 0; r < boardClone.length && !playFound && !stopCheckBoardThread; ){
                        if (r % 2 == 0 && c % 2 == 0 && !isPiecePlayed(r, c)){
                            ArrayList<int[]> run = new ArrayList<>(boardClone.length);
                            do {
                                run.add(new int[]{r, c});
                                r++;
                            } while (r < boardClone.length && !isPiecePlayed(r, c));
                            if (run.size() > 2) playFound = checkRunAndSubsets(run);
                            else playFound = false;
                        } else r++;
                    }
            }

            if (!playFound) {
                // Check diagonals
                for (int c = 0; c < boardClone[0].length && !playFound && !stopCheckBoardThread; c++) {
                    for (int r = 0; r < boardClone.length && !playFound && !stopCheckBoardThread; r++) {
                        if (r % 2 == 0 && c % 2 == 0 && !isPiecePlayed(r, c)) {
                            ArrayList<int[]> run = new ArrayList<>(boardClone.length);
                            for (int i = 0; r + i < boardClone.length && c + i < boardClone[0].length && !isPiecePlayed(r + i, c + i); i++) {
                                run.add(new int[]{r + i, c + i});
                            }
                            if (run.size() > 2) playFound = checkRun(run);
                            else playFound = false;
                            if (!playFound) {
                                run = new ArrayList<>(boardClone.length);
                                for (int i = 0; r - i >= 0 && c + i < boardClone[0].length && !isPiecePlayed(r - i, c + i); i++) {
                                    run.add(new int[]{r - i, c + i});
                                }
                                if (run.size() > 2) playFound = checkRun(run);
                                else playFound = false;
                            }
                        }
                    }
                }
            }

            // If a play has not been found, notify
            if (stopCheckBoardThread) Log.d(LOG_TAG, "Checkboard thread stopped");
            else if (playFound) Log.d(LOG_TAG, "There is a possible expression");
            else {
                lastGameResult.noMorePossiblePlays = true;
                if (onGameOverListener != null) {
                    thisView.post(new Runnable() {
                        @Override
                        public void run() {
                            onGameOverListener.OnGameOver(lastGameResult);
                        }
                    });
                }
                Log.d(LOG_TAG, "There are no possible expressions");
            }
        }

        private boolean isPiecePlayed(int r, int c){
            String s = boardClone[r][c];
            String[] a = s.split(ArithmosGame.SEPARATOR);
            return !a[1].equals(ArithmosGame.UNDEF);
        }

        // Checks all subsets that start with index 0
        private boolean checkRun(ArrayList<int[]> run) {
            // Remove first and last entries if they are not number pieces
            int[] extra = run.get(run.size() - 1);
            if (extra[0] % 2 != 0 || extra[1] % 2 != 0) run.remove(run.size() - 1);

            // If there are less than three in the run, it will not work;
            if (run.size() < 3) return false;

            // Check from 1st to last that are at least three in length
            for (int i = 2; i < run.size(); i++ ) {
                ArrayList<int[]> subList = new ArrayList<>(i);
                subList.addAll(run.subList(0, i + 1));
                if (game.threadAutoCheckSelection(subList).result == ArithmosGame.GameResult.SUCCESS || stopCheckBoardThread)
                    return true;
            }

            // Check from last to first
            ArrayList<int[]> runReversed = new ArrayList<>(run.size());
            for (int i = run.size() - 1; i >= 0; i--)
                runReversed.add(run.get(i));

            for (int i = 2; i < run.size(); i++ ) {
                ArrayList<int[]> subList = new ArrayList<>(i);
                subList.addAll(runReversed.subList(0, i + 1));
                if (game.threadAutoCheckSelection(subList).result == ArithmosGame.GameResult.SUCCESS || stopCheckBoardThread)
                    return true;
            }

            // All subsets checked return false only if the thread hasn't been stopped
            return stopCheckBoardThread;
        }

        // Checks all subsets of run
        private boolean checkRunAndSubsets(ArrayList<int[]> run){
            // Remove first and last entries if they are not number pieces
            int [] extra = run.get(run.size() - 1);
            if (extra[0] % 2 != 0 || extra[1] % 2 != 0) run.remove(run.size() - 1);

            // If there are less than three in the run, it will not work;
            if (run.size() < 3) return false;

            // Check all subsets from 1st to last that are at least three in length
            for (int i = 0; i < run.size() - 2 && !stopCheckBoardThread; i++)
                for (int j = i + 2; j < run.size() && !stopCheckBoardThread; j++){
                    ArrayList<int[]> subList = new ArrayList<>(j - i);
                    subList.addAll(run.subList(i, j + 1));
                    if (game.threadAutoCheckSelection(subList).result == ArithmosGame.GameResult.SUCCESS || stopCheckBoardThread)
                        return true;
                }

            // Check all subsets from last to first
            ArrayList<int[]> runReversed = new ArrayList<>(run.size());
            for (int i = run.size() - 1; i >= 0; i--)
                runReversed.add(run.get(i));
            for (int i = 0; i < runReversed.size() - 2 && !stopCheckBoardThread; i++)
                for (int j = i + 2; j < runReversed.size() && !stopCheckBoardThread; j++){
                    ArrayList<int[]> subList = new ArrayList<>(j - i);
                    subList.addAll(run.subList(i, j + 1));
                    if (game.threadAutoCheckSelection(subList).result == ArithmosGame.GameResult.SUCCESS || stopCheckBoardThread)
                        return true;
                }
            // All subsets checked return false only if the thread hasn't been stopped
            return stopCheckBoardThread;
        }

    }


    // Gameboard
    // [row][col]
    private RectF[][] tileDimensions;
    private float pieceW, pieceH;
    private float horzMargin = 16, vertMargin = 16;
    private Bitmap gameBoardBitmap;
    private ArrayList<BonusTile> bonuses;
    private ArithmosGame.GameResult lastGameResult;
    private boolean showComputerRun = false;
    private Path computerRunPath;
    private Paint computerRunPaint;

    private void resetGameBoard(int w, int h){
        if (game != null && w > 0 && h > 0){
            String[][] board = game.getGameBoard();
            tileDimensions = new RectF[board.length][board[0].length];

            // Determine margins

            // Determine tile sizes
            float width = (w - 2 * horzMargin) / tileDimensions[0].length, height = (h - 2 * vertMargin) / tileDimensions.length;
            pieceW = width;
            pieceH = height;
            for (int r = 0; r < tileDimensions.length; r++)
                for (int c = 0; c < tileDimensions[0].length; c++){
                    tileDimensions[r][c] = new RectF(horzMargin + c * width, vertMargin + r * height, horzMargin + c * width + width - 1, vertMargin + r * height + height - 1);
                }

            setupOpButtons();

            gameBoardBitmap = getGameBoardBitmap(w, h);
            scorePaint.setTextSize((float) h / 10);

        }
    }
    private Bitmap getGameBoardBitmap(int width, int height){
        bonuses = new ArrayList<>(5);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Set up text paint
        txtPaint = new Paint();
        txtPaint.setColor(Color.BLACK);
        RectF dimens = tileDimensions[0][0];
        float txtSize = Math.min(dimens.width(), dimens.height());
        txtPaint.setTextSize(txtSize);
        selectionPaint.setStrokeWidth(txtSize * 1.333f);
        txtPaint.setTextAlign(Paint.Align.CENTER);

        // Draw each of the number tiles
        String tileText;
        for (int r = 0; r < tileDimensions.length; r++)
            for (int c = 0; c < tileDimensions[0].length; c++){
                tileText = game.getPiece(r, c);
                if (!tileText.equals(ArithmosGame.UNDEF)) {
                    dimens = tileDimensions[r][c];
                    switch (tileText) {
                        case ArithmosGame.MULTIPLY:
                            canvas.drawText("\u00D7", dimens.centerX(), dimens.centerY() + txtSize / 2, txtPaint);
                            break;
                        case ArithmosGame.DIVIDE:
                            canvas.drawText("\u00F7", dimens.centerX(), dimens.centerY() + txtSize / 2, txtPaint);
                            break;
                        case ArithmosGame.SUBTRACT:
                            canvas.drawText("\u2212", dimens.centerX(), dimens.centerY() + txtSize / 2, txtPaint);
                            break;
                        case ArithmosLevel.BONUS_LOCK_ADD:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_LOCK_ADD, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_LOCK_ADD, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_LOCK_SUB:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_LOCK_SUB, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_LOCK_SUB, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_LOCK_MULT:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_LOCK_MULT, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_LOCK_MULT, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_LOCK_DIV:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_LOCK_DIV, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_LOCK_DIV, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_BALLOONS:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_BALLOONS, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_BALLOONS, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_RED_JEWEL:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_RED_JEWEL, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_RED_JEWEL, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_BOMB:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_BOMB, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_BOMB, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_APPLE:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_APPLE, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_APPLE, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_BANANAS:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_BANANAS, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_BANANAS, dimens), new RectF(dimens)));
                            break;
                        case ArithmosLevel.BONUS_CHERRIES:
                            bonuses.add(new BonusTile(ArithmosLevel.BONUS_CHERRIES, new int[]{r, c},
                                    getBonusBitmap(ArithmosLevel.BONUS_CHERRIES, dimens), new RectF(dimens)));
                            break;
                        default:
                            canvas.drawText(tileText, dimens.centerX(), dimens.centerY() + txtSize / 2, txtPaint);
                    }
                }
            }

        // Draw runs that have already been played
        selectionPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color1, null));
        for (ArrayList<int[]> run : game.getP1Runs()){
            int[] start = run.get(0), end = run.get(run.size() - 1);
            RectF startRect = tileDimensions[start[0]][start[1]], endRect = tileDimensions[end[0]][end[1]];
            selectionPath.moveTo(startRect.centerX(), startRect.centerY());
            selectionPath.lineTo(endRect.centerX(), endRect.centerY());
            canvas.drawPath(selectionPath, selectionPaint);
            selectionPath.rewind();
        }

        selectionPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color2, null));
        for (ArrayList<int[]> run : game.getP2Runs()){
            int[] start = run.get(0), end = run.get(run.size() - 1);
            RectF startRect = tileDimensions[start[0]][start[1]], endRect = tileDimensions[end[0]][end[1]];
            selectionPath.moveTo(startRect.centerX(), startRect.centerY());
            selectionPath.lineTo(endRect.centerX(), endRect.centerY());
            canvas.drawPath(selectionPath, selectionPaint);
            selectionPath.rewind();
        }

        if (game.getCurrentPlayer().equals(game.PLAYER1)) {
            selectionPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color1, null));
        } else {
            selectionPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color2, null));
        }

        return bitmap;
    }
    private Bitmap getBonusBitmap(String name, RectF dimens){
        Bitmap bitmap;
        switch (name){
            case ArithmosLevel.BONUS_LOCK_ADD:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_addition_lock);
                break;
            case ArithmosLevel.BONUS_LOCK_SUB:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_subtraction_lock);
                break;
            case ArithmosLevel.BONUS_LOCK_MULT:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_multiplication_lock);
                break;
            case ArithmosLevel.BONUS_LOCK_DIV:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_division_lock);
                break;
            case ArithmosLevel.BONUS_BALLOONS:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_balloons);
                break;
            case ArithmosLevel.BONUS_RED_JEWEL:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_red_jewel);
                break;
            case ArithmosLevel.BONUS_BOMB:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bomb);
                break;
            case ArithmosLevel.BONUS_APPLE:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_apple);
                break;
            case ArithmosLevel.BONUS_BANANAS:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bananas);
                break;
            case ArithmosLevel.BONUS_CHERRIES:
                bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_cherries);
                break;
            default:
                bitmap = Bitmap.createBitmap((int)dimens.width(), (int)dimens.height(), Bitmap.Config.ARGB_8888);
        }

        int targetSize = (int)Math.min(dimens.width(), dimens.height());
        return Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, false);
    }
    private void playCompleted(){
        if (lastGameResult.result == ArithmosGame.GameResult.SUCCESS) {
            restartCheckBoardThread();
            updateGameboard();
            playScoreAnimation();

            if (onCancelBombListener != null)
                for (String op : lastGameResult.opsReplaced) {
                    onCancelBombListener.OnCancelBomb(op);
                    setupOpButtons();
                }

            for (int[] x : lastGameResult.bonusLocations)
                processBonus(x);

            if (lastGameResult.numStars > 0 && onStarEarnedListener != null) {
                onStarEarnedListener.OnStarEarned(lastGameResult.numStars);
            }

        }
        selectedPieces = new ArrayList<>();
        selectionPath.rewind();
        if (onPlayCompleted != null)
            onPlayCompleted.OnPlayCompleted(lastGameResult);
        if (lastGameResult.isLevelPassed && onGameOverListener != null)
            onGameOverListener.OnGameOver(lastGameResult);
    }


    // Bonuses
    int animDelay = 0;
    public int getAnimDelay(){ return animDelay;}
    private class BonusTile{
        private String type;
        public String getType() { return type; }

        private int[] location;
        public int[] getLocation() {return location;}
        public int getRow() {return location[0];}
        public int getCol() {return location[1];}

        private Bitmap icon;
        public Bitmap getIcon() {return icon;}

        private RectF rectF;
        public RectF getRectF() { return rectF; }
        public BonusTile(String type, int[] location, Bitmap icon, RectF rectF){
            this.type = type;
            this. location = location;
            this.icon = icon;
            float size = Math.min(rectF.width(), rectF.height());
            float left = rectF.left + (rectF.width() - size) / 2;
            float top = rectF.top + (rectF.height() - size) / 2;
            this.rectF = new RectF(left, top, left + size, top + size);
        }
        public BonusTile(int[] location){
            this.location = location;
        }

        @Override
        public boolean equals(Object o){
            if (o instanceof BonusTile){
                BonusTile b = (BonusTile)o;
                return b.location[0] == this.location[0] && b.location[1] == this.location[1];
            } else
                return false;
        }
    }
    private void processBonus(int[] location) {
        int index = bonuses.indexOf(new BonusTile(location));
        if (index >= 0){
            BonusTile bonusTile = bonuses.get(index);
            switch (bonusTile.getType()){
                case ArithmosLevel.BONUS_BALLOONS:
                    playBalloonAnimation(bonusTile);
                    break;
                case ArithmosLevel.BONUS_RED_JEWEL:
                    playJewelAnimation(bonusTile);
                    break;
                case ArithmosLevel.BONUS_BOMB:
                    playBombAnimation(bonusTile);
                    break;
                case ArithmosLevel.BONUS_LOCK_ADD:
                case ArithmosLevel.BONUS_LOCK_SUB:
                case ArithmosLevel.BONUS_LOCK_MULT:
                case ArithmosLevel.BONUS_LOCK_DIV:
                    bonuses.remove(bonusTile);
                    break;
                case ArithmosLevel.BONUS_APPLE:
                case ArithmosLevel.BONUS_BANANAS:
                case ArithmosLevel.BONUS_CHERRIES:
                    playFruitAnimation(bonusTile);
                    break;
            }
        }
    }
    private void playBalloonAnimation(final BonusTile balloonTile) {
        Log.d(LOG_TAG, balloonTile.getType() + " animation");
        thisView.post(new Runnable() {
            @Override
            public void run() {
                final RectF baseRect = new RectF(balloonTile.rectF);
                final Matrix m = new Matrix();
                ValueAnimator expand = ValueAnimator.ofFloat(1f, 3f);
                expand.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        m.setScale((float)animation.getAnimatedValue(), (float)animation.getAnimatedValue(), baseRect.centerX(), baseRect.centerY());
                    }
                });

                ValueAnimator floatUp = ValueAnimator.ofFloat(balloonTile.rectF.top, 0);
                floatUp.setInterpolator(new AnticipateInterpolator(1.75f));
                floatUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        m.postTranslate(0, (float) animation.getAnimatedValue() - baseRect.top);
                        m.mapRect(balloonTile.rectF, baseRect);
                        invalidate();
                    }
                });

                AnimatorSet set = new AnimatorSet();
                set.playTogether(floatUp, expand);
                set.setStartDelay(animDelay);
                animDelay += 75;
                set.setDuration(2000);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        bonuses.remove(balloonTile);
                        animDelay -= 75;
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
    private void playJewelAnimation(final BonusTile jewelTile){
        Log.d(LOG_TAG, jewelTile.getType() + " animation");
        thisView.post(new Runnable() {
            @Override
            public void run() {
                ValueAnimator accelUp = ValueAnimator.ofFloat(jewelTile.rectF.top, -jewelTile.rectF.height());
                accelUp.setInterpolator(new AccelerateInterpolator());
                accelUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        jewelTile.rectF.offsetTo(jewelTile.rectF.left, (float) animation.getAnimatedValue());
                    }
                });

                ValueAnimator flingRight = ValueAnimator.ofFloat(jewelTile.rectF.left, getWidth() - jewelTile.rectF.width());
                flingRight.setInterpolator(new AnticipateInterpolator(1.75f));
                flingRight.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        jewelTile.rectF.offsetTo((float)animation.getAnimatedValue(), jewelTile.rectF.top);
                        invalidate();
                    }
                });

                AnimatorSet set = new AnimatorSet();
                set.playTogether(accelUp, flingRight);
                set.setStartDelay(animDelay);
                animDelay += 75;
                set.setDuration(500);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animDelay -= 75;
                        bonuses.remove(jewelTile);
                        if (onJewelListener != null) onJewelListener.OnJewel();
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
    private void playBombAnimation(final BonusTile bombTile){
        final RectF baseRect = new RectF(bombTile.rectF);
        thisView.post(new Runnable() {
            @Override
            public void run() {
                ValueAnimator expand = ValueAnimator.ofFloat(2f, 0.1f);
                expand.setInterpolator(new DecelerateInterpolator());
                expand.setStartDelay(animDelay);
                animDelay += 75;
                expand.setDuration(750);
                expand.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        Matrix m = new Matrix();
                        m.setScale((float)animation.getAnimatedValue(), (float)animation.getAnimatedValue(), baseRect.centerX(), baseRect.centerY());
                        m.mapRect(bombTile.rectF, baseRect);
                        invalidate();
                    }
                });
                expand.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        bombTile.icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_explosion);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animDelay -= 75;
                            boolean hasOp = false;
                            String operation = game.getPiece(bombTile.getRow(), bombTile.getCol());
                            for (String s : game.availableOperations()){
                                hasOp = hasOp | s.equals(operation);
                            }
                            if (hasOp) {
                                game.removeOperation(operation);
                                setupOpButtons();
                                if (onBombListener != null) {
                                    onBombListener.OnBomb(operation);
                                }
                            }
                        bonuses.remove(bombTile);
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {

                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {

                    }
                });
                expand.start();
            }
        });
    }
    private void playFruitAnimation(final BonusTile fruitTile){
        Log.d(LOG_TAG, fruitTile.getType() + " animation");
        thisView.post(new Runnable() {
            @Override
            public void run() {
                ValueAnimator fallDown = ValueAnimator.ofFloat(fruitTile.rectF.top, getHeight());
                fallDown.setInterpolator(new AnticipateInterpolator());
                fallDown.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        fruitTile.rectF.offsetTo(fruitTile.rectF.left, (float) animation.getAnimatedValue());
                        invalidate();
                    }
                });

                AnimatorSet set = new AnimatorSet();
                set.play(fallDown);
                set.setStartDelay(animDelay);
                animDelay += 75;
                set.setDuration(500);
                set.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animDelay -= 75;
                        bonuses.remove(fruitTile);
                        if (onJewelListener != null) onJewelListener.OnJewel();
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


    // Specials
    public boolean placeBomb(float x, float y){
        int[] l = getTileLocation(x, y);
        if (l[0] % 2 == 0 && l[1] % 2 == 0) return false;
        else {
            int i = bonuses.indexOf(new BonusTile(l));
            if (i >= 0) bonuses.remove(i);
            game.setPiece(ArithmosLevel.BONUS_BOMB, l[0], l[1]);
            bonuses.add(new BonusTile(ArithmosLevel.BONUS_BOMB, l,
                    getBonusBitmap(ArithmosLevel.BONUS_BOMB, tileDimensions[l[0]][l[1]]), tileDimensions[l[0]][l[1]]));
            invalidate();
            return true;
        }
    }
    public void changeNumber(float x, float y, String value){
        int[] l = getTileLocation(x, y);
        game.setPiece(value, l[0], l[1]);
        Canvas canvas = new Canvas(gameBoardBitmap);
        RectF dimens = tileDimensions[l[0]][l[1]];
        Paint fill = new Paint();
        fill.setColor(Color.TRANSPARENT);
        fill.setStyle(Paint.Style.FILL);
        fill.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(dimens,fill);
        canvas.drawText(value, dimens.centerX(), dimens.centerY() + txtPaint.getTextSize() / 2, txtPaint);
        invalidate();
    }
    public boolean isChangeableNumber(float x, float y){
        int[] l = getTileLocation(x, y);
        return (l[0] % 2 == 0 && l[1] % 2 == 0) && !game.isPiecePlayed(l);
    }
    public void useSkipSpecial(){
        lastGameResult = game.skipGoalNumber();
        if (onCancelBombListener != null)
            for (String op : lastGameResult.opsReplaced) {
                onCancelBombListener.OnCancelBomb(op);
                setupOpButtons();
            }
        if (onPlayCompleted != null)
            onPlayCompleted.OnPlayCompleted(lastGameResult);
        if (lastGameResult.isLevelPassed && onGameOverListener != null)
            onGameOverListener.OnGameOver(lastGameResult);
    }
    public boolean useAutoRunSpecial(){
            if (game.getComputerFoundRun() != null){
                int[] start = game.getComputerFoundRun().get(0);
                int[] end = game.getComputerFoundRun().get(game.getComputerFoundRun().size() - 1);
                RectF startRect = tileDimensions[start[0]][start[1]];
                RectF endRect = tileDimensions[end[0]][end[1]];
                selectionPath.rewind();
                selectionPath.moveTo(startRect.centerX(), startRect.centerY());
                selectionPath.lineTo(endRect.centerX(), endRect.centerY());
                selectedPieces = new ArrayList<>(game.getComputerFoundRun().size());
                selectedPieces.addAll(game.getComputerFoundRun());
                startProcessingAnimation();
                processSelection();
                return true;
            } else
                return showComputerRun;
    }


    // Score popup
    private boolean playScoreAnimation = false;
    private Paint scorePaint;
    private String score = "";
    private float[] scorePos = {0, 0};

    private void playScoreAnimation(){
        playScoreAnimation = true;
        // Determine approx center row and col of selection
        int col = selectedPieces.get(selectedPieces.size() / 2)[1];
        int row = selectedPieces.get(selectedPieces.size() / 2)[0];

        // Update score
        score = String.valueOf(game.getLastScore());

        // Determine length of score text;
        float txtLength = 0;
        float[] lengths = new float[score.length()];
        scorePaint.getTextWidths(score, lengths);
        for (float x : lengths) txtLength += x;

        // Set initial position
        scorePos = new float[] {Math.max(txtLength / 2, Math.min(col * pieceW, getWidth() - txtLength / 2)), getHeight()};
        scorePaint.setAlpha(0);

        ValueAnimator fadeIn = ValueAnimator.ofInt(0, 255);
        fadeIn.setDuration(200);
        fadeIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scorePaint.setAlpha((int) animation.getAnimatedValue());
            }
        });

        ValueAnimator popUp = ValueAnimator.ofFloat(Math.max(scorePaint.getTextSize(), Math.min(row * pieceH, getHeight())) + 3 * scorePaint.getTextSize(), Math.max(scorePaint.getTextSize(), Math.min(row * pieceH, getHeight())));
        popUp.setDuration(200);
        popUp.setInterpolator(new OvershootInterpolator());
        popUp.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scorePos[1] = (float) animation.getAnimatedValue();
                invalidate();
            }
        });

        ValueAnimator fadeOut = ValueAnimator.ofInt(255, 0);
        fadeOut.setStartDelay(200);
        fadeOut.setDuration(150);
        fadeOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scorePaint.setAlpha((int) animation.getAnimatedValue());
                invalidate();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.play(fadeIn).with(popUp);
        set.play(fadeOut).after(popUp);
        set.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                playScoreAnimation = false;
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


    // Selection
    private Path selectionPath;
    private Paint selectionPaint;
    private Paint txtPaint;
    private ArrayList<int[]> selectedPieces;
    private boolean isProcessing = false;
    private ArrayList<int[]> operationAnimationPositions;
    private ArrayList<String> operationAnimationValues;
    private RectF rectF;
    public static int AUTO_PICK = 911, MANUAL_PICK = 912;
    private int opPickMode = AUTO_PICK;

    private boolean handleSelectionTouch(MotionEvent event){
        if (!isProcessing) {
            int[] location;
            final float x = event.getX(), y = event.getY();
            RectF coords;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    selectedPieces = new ArrayList<int[]>();
                    location = getTileLocation(x, y);
                    // Ensure path will start on a number tile
                    if (location[0] % 2 == 0 && location[1] % 2 == 0) {
                        selectedPieces.add(location);
                        coords = tileDimensions[location[0]][location[1]];
                        selectionPath.moveTo(coords.centerX(), coords.centerY());
                        invalidate();
                        return true;
                    } else {
                        return false;
                    }
                case MotionEvent.ACTION_MOVE:
                    selectionPath.rewind();
                    location = selectedPieces.get(0);
                    coords = tileDimensions[location[0]][location[1]];
                    selectionPath.moveTo(coords.centerX(), coords.centerY());
                    selectionPath.lineTo(x, y);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (game.availableOperations().length == 0){
                        Toast.makeText(thisView.getContext(), R.string.no_ops_available, Toast.LENGTH_SHORT).show();
                        selectionPath.rewind();
                        return true;
                    }
                    else if (fillSelection(getTileLocation(x, y))) {
                        // Over 7 tiles (4 numbers)
                        if (opPickMode == MANUAL_PICK || selectedPieces.size() > 7 || game.getGoalType() == ArithmosLevel.GOAL_301) {
                            opIndex = 1;
                            TouchMode = OP_POPUP_TOUCH;
                            showOpPopup = true;
                            opList = new ArrayList<>(3);
                            // Add locked operations at the start of the run
                            while (opIndex < selectedPieces.size() && game.isOpLockBonus(selectedPieces.get(opIndex))) {
                                opList.add(game.getLockedOperation(selectedPieces.get(opIndex)));
                                opIndex += 2;
                            }
                            // Show popup if there is room to choose more operations
                            if (opIndex < selectedPieces.size()) {
                                int[] l = selectedPieces.get(opIndex);
                                RectF r = tileDimensions[l[0]][l[1]];
                                positionOpPopup(r.centerX(), r.centerY());
                            }
                            // Otherwise check the selection
                            else {
                                processManualSelection();
                            }
                        } else {
                            // Play processing animation
                            startProcessingAnimation();
                            // Process selection (on a separate thread)
                            processSelection();
                        }
                    }
                    invalidate();
                    return false;
                default:
                    return false;
            }
        }
        return false;
    }
    private boolean fillSelection(int[] last){
        int[] first = selectedPieces.get(0);

        // Determine the change in rows and cols
        float dRow = last[0] - first[0];
        float dCol = last[1] - first[1];

        // Fill array list
        selectedPieces = new ArrayList<int[]>();
        if (dRow == 0){
            if (last[1] > first[1]) { // Left to right
                for (int i = first[1]; i <= last[1]; i++)
                    selectedPieces.add(new int[]{first[0], i});
            } else { // Right to left
                for (int i = first[1]; i >= last[1]; i--)
                    selectedPieces.add(new int[]{first[0], i});
            }
        } else if (dCol == 0){
            if (last[0] > first[0]) { // Up to down
                for (int i = first[0]; i <= last[0]; i++)
                    selectedPieces.add(new int[]{i, first[1]});
            } else { // Down to up
                for (int i = first[0]; i >= last[0]; i--)
                    selectedPieces.add(new int[]{i, first[1]});
            }
        } else if (dRow / dCol == 1){
            if (last[0] > first[0]) { // Upper left to lower right
                for (int i = 0; i + first[0] <= last[0] && i + first[1] <= last[1]; i++)
                    selectedPieces.add(new int[]{first[0] + i, first[1] + i});
            } else { // Lower right to upper left
                for (int i = 0; i + first[0] >= last[0] && i + first[1] >= last[1]; i--)
                    selectedPieces.add(new int[]{first[0] + i, first[1] + i});
            }
        } else if(dRow / dCol == -1){
            if (dRow < 0) { // Lower left to upper right
                for (int i = first[0], j = first[1]; i >= last[0] && j <= last[1]; i--, j++)
                    selectedPieces.add(new int[]{i, j});
            } else { // Upper right to lower left
                for (int i = first[0], j = first[1]; i <= last[0] && j >= last[1]; i++, j--)
                    selectedPieces.add(new int[]{i, j});
            }
        } else { // Selection is not horizontal, vertial or diagonal
            Log.d(LOG_TAG, "Selection not horz, vert, or diag");
            selectionPath.rewind();
            return false;
        }

        // Ensure path will end on a number tile
        last = selectedPieces.get(selectedPieces.size() - 1);
        if (last[0] % 2 != 0 || last[1] % 2 != 0)
            selectedPieces.remove(selectedPieces.size() - 1);

        // Check that no tiles in selection have been played
        for (int[] a : selectedPieces){
            if (game.isPiecePlayed(a)) {
                Log.d(LOG_TAG, "Tile in selection has been played");
                selectionPath.rewind();
                return false;
            }
        }

        selectionPath.rewind();
        first = selectedPieces.get(0);
        RectF coords = tileDimensions[first[0]][first[1]];
        selectionPath.moveTo(coords.centerX(), coords.centerY());
        last = selectedPieces.get(selectedPieces.size() - 1);
        coords = tileDimensions[last[0]][last[1]];
        selectionPath.lineTo(coords.centerX(), coords.centerY());
        return selectedPieces.size() > 2;
    }
    private void processSelection(){
        isProcessing = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                lastGameResult = game.checkAutoFillSelection(selectedPieces, true);
                isProcessing = false;
            }
        }).start();
    }
    private int[] getTileLocation(float x, float y){
        rectF = tileDimensions[0][0];
        float pieceW = rectF.width(), pieceH = rectF.height();
        int col = (int)((x - horzMargin) / pieceW), row = (int)((y - vertMargin) / pieceH);
        return new int[] {Math.max(0, Math.min(row, tileDimensions.length - 1)), Math.max(0, Math.min(col, tileDimensions[0].length - 1))};
    }
    private void startProcessingAnimation(){
        operationAnimationPositions = new ArrayList<>((selectedPieces.size() - 1) / 2);
        operationAnimationValues = new ArrayList<>((selectedPieces.size() - 1) / 2);
        for (int[] a : selectedPieces){
            if ((a[0] % 2 == 1 || a[1] % 2 == 1) && !game.isOpLockBonus(a)) {
                operationAnimationPositions.add(a);
                operationAnimationValues.add(ArithmosGame.UNDEF);
            }
        }

        ValueAnimator animator = ValueAnimator.ofInt(0, 20);
        animator.setDuration(2000);
        animator.setRepeatMode(ValueAnimator.INFINITE);

        // Callback that executes on animation steps.
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int n = (int) animation.getAnimatedValue();

                for (int i = 0; i < operationAnimationValues.size(); i++) {
                    operationAnimationValues.set(i, ArithmosGame.getOperationDisplayString(game.availableOperations()[(n + i) % game.availableOperations().length]));
                }
                invalidate();
                if (!isProcessing && animation.getCurrentPlayTime() > 1000) animation.cancel();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                operationAnimationPositions = null;
                operationAnimationValues = null;
                playCompleted();
                invalidate();
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });

        animator.start();
    }
    private void updateGameboard(){
        if (selectedPieces.size() > 0) {
            Canvas canvas = new Canvas(gameBoardBitmap);
            txtPaint.setColor(Color.BLACK);
            RectF dimens = tileDimensions[0][0];
            String tileText;
            for (int[] l : selectedPieces) {
                tileText = game.getPiece(l[0], l[1]);
                dimens = tileDimensions[l[0]][l[1]];
                switch (tileText) {
                    case ArithmosGame.MULTIPLY:
                        canvas.drawText("\u00D7", dimens.centerX(), dimens.centerY() + txtPaint.getTextSize() / 2, txtPaint);
                        break;
                    case ArithmosGame.DIVIDE:
                        canvas.drawText("\u00F7", dimens.centerX(), dimens.centerY() + txtPaint.getTextSize() / 2, txtPaint);
                        break;
                    case ArithmosGame.SUBTRACT:
                        canvas.drawText("\u2212", dimens.centerX(), dimens.centerY() + txtPaint.getTextSize() / 2, txtPaint);
                        break;
                    default:
                        canvas.drawText(tileText, dimens.centerX(), dimens.centerY() + txtPaint.getTextSize() / 2, txtPaint);
                }
            }
            canvas.drawPath(selectionPath, selectionPaint);
        }
    }
    public void setOperationPickMode(int mode){
        opPickMode = mode;
    }


    // Operation popup
    private boolean showOpPopup;
    private RectF[] opButtonsRects;
    private Bitmap[] opButtonBitmaps;
    private int opIndex = 1;
    private ArrayList<String> opList;

    private void setupOpButtons(){
        // Setup operation popup sizes
        float diam = Math.max(20, Math.min(Math.min(pieceW, pieceH), Math.min(getWidth(), getHeight()) / 10));
        opButtonBitmaps = new Bitmap[game.availableOperations().length];
        for (int i = 0; i < game.availableOperations().length; i++){
            opButtonBitmaps[i] = getOpButtonBitmap(ArithmosGame.getOperationDisplayString(game.availableOperations()[i]), (int)(1.5 * diam));
        }
        opButtonsRects = new RectF[opButtonBitmaps.length];
        for (int i = 0; i < opButtonsRects.length; i++){
            opButtonsRects[i] = new RectF(0, 0, 1.5f * diam, 1.5f * diam);
        }
    }
    private Bitmap getOpButtonBitmap(String symbol, int diam){
        Bitmap bitmap = Bitmap.createBitmap(diam, diam, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Color.argb(160, 0, 0, 0));
        paint.setStrokeWidth(diam);
        canvas.drawPoint((float) diam / 2, (float) diam / 2, paint);
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(diam);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(symbol, diam / 2, 5 * diam / 6, paint);
        return bitmap;
    }
    private void positionOpPopup(float x, float y){
        int[] firstTile = selectedPieces.get(0);
        int row = selectedPieces.get(opIndex)[0], col = selectedPieces.get(opIndex)[1];
        RectF tileRect = tileDimensions[row][col];
        final float w = opButtonsRects[0].width() + 4, h = opButtonsRects[0].height() + 4;
        final float x1 = x - w / 2, y1 = y - h / 2;
        ValueAnimator expandVert = ValueAnimator.ofFloat(0, 1);
        ValueAnimator expandHorz = ValueAnimator.ofFloat(0, 1);

        for (RectF r : opButtonsRects)
            r.offsetTo(x1, y1);

        if (row == firstTile[0]){ // Horizontal selection
            if (tileRect.top < h * opButtonsRects.length){ // Close to top, popup should go down
                expandVert.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float val = (float)animation.getAnimatedValue();
                        for (int i = 0; i < opButtonsRects.length; i++){
                            opButtonsRects[i].offsetTo(x1, y1 + val * h * i);
                        }
                        invalidate();
                    }
                });
            } else {
                expandVert.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float val = (float)animation.getAnimatedValue();
                        for (int i = 0; i < opButtonsRects.length; i++){
                            opButtonsRects[i].offsetTo(x1, y1 - val * h * i);
                        }
                        invalidate();
                    }
                });
            }
        } else { // Vertical or diagonal selection
            if (tileRect.right < w * opButtonsRects.length) { // Close to left, popup should go right
                expandHorz.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float val = (float)animation.getAnimatedValue();
                        for (int i = 0; i < opButtonsRects.length; i++){
                            opButtonsRects[i].offsetTo(x1 + val * w * i, y1);
                        }
                        invalidate();
                    }
                });
            } else {
                expandHorz.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        float val = (float)animation.getAnimatedValue();
                        for (int i = 0; i < opButtonsRects.length; i++){
                            opButtonsRects[i].offsetTo(x1 - val * w * i, y1);
                        }
                        invalidate();
                    }
                });
            }
        }

        // Animate movement to new position
        AnimatorSet set = new AnimatorSet();
        set.setDuration(150);
        set.playTogether(expandVert, expandHorz);
        set.start();
    }
    private boolean handleOpPopupTouch(MotionEvent event){
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_UP:
                if (addOpToList(x, y)) {
                    opIndex += 2;
                    if (opIndex < selectedPieces.size() && game.isOpLockBonus(selectedPieces.get(opIndex))) {
                        opList.add(game.getLockedOperation(selectedPieces.get(opIndex)));
                        opIndex += 2;
                    }
                    if (opIndex < selectedPieces.size()) {
                        int[] l = selectedPieces.get(opIndex);
                        RectF r = tileDimensions[l[0]][l[1]];
                        positionOpPopup(r.centerX(), r.centerY());
                    } else {
                        processManualSelection();
                        if (lastGameResult.result == ArithmosGame.GameResult.SUCCESS){
                            // Call achievement listener
                            if (onAchievementListener != null)
                                onAchievementListener.OnAchievement(getResources().getString(R.string.achievement_arithmetic_master));
                        }
                    }
                } else {
                    showOpPopup = false;
                    TouchMode = SELECTION_TOUCH;
                    selectionPath.rewind();
                }
                invalidate();
                return true;
        }
        return true;
    }
    private boolean addOpToList(float x, float y){
        for (int i = 0; i < opButtonsRects.length; i++){
            if (opButtonsRects[i].contains(x, y)){
                opList.add(game.availableOperations()[i]);
                return true;
            }
        }
        return false;
    }
    private void processManualSelection(){
        lastGameResult = game.checkSelection(selectedPieces, opList);
        if (lastGameResult.result == ArithmosGame.GameResult.SUCCESS){
            playCompleted();
        }
        showOpPopup = false;
        TouchMode = SELECTION_TOUCH;
        selectionPath.rewind();
    }
}
