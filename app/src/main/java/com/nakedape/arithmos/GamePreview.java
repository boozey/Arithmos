package com.nakedape.arithmos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.v4.content.res.ResourcesCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by Nathan on 7/8/2016.
 */
public class GamePreview extends View {
    private static final String LOG_TAG = "GamePreview";
    private static final String UNDEF = "?";
    private String[][] gameBoard;
    private Paint txtPaint, selectionPaint;
    private ArrayList<ArrayList<int[]>> predefinedRuns;
    private boolean showRuns;
    private Path selectionPath;

    public GamePreview(Context context, AttributeSet attrs){
        super(context, attrs);
        selectionPath = new Path();
        selectionPaint = new Paint();
        selectionPaint.setColor(ResourcesCompat.getColor(getResources(), R.color.run_color1, null));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(10);
        selectionPaint.setStrokeCap(Paint.Cap.ROUND);
        selectionPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setupBoard(int size, String[] numberList, HashMap<String, Integer> bonuses, ArrayList<String[]> runs){
        gameBoard = new String[2*size - 1][2*size - 1];
        class Helper {
            Random random = new Random();
            char[][] orders = {{'v', 'h', 'l', 'r'}, {'v', 'h', 'r', 'l'}, {'v', 'l', 'h', 'r'},
                    {'v', 'l', 'r', 'h'}, {'v', 'r', 'h', 'l'}, {'v', 'r', 'l', 'h'},
                    {'h', 'l', 'v', 'r'}, {'h', 'l', 'r', 'v'}, {'h', 'r', 'v', 'l'},
                    {'h', 'r', 'l', 'v'}, {'h', 'v', 'l', 'r'}, {'h', 'v', 'r', 'l'},
                    {'l', 'v', 'h', 'r'}, {'l', 'v', 'r', 'h'}, {'l', 'r', 'v', 'h'},
                    {'l', 'r', 'h', 'v'}, {'l', 'h', 'v', 'r'}, {'l', 'h', 'r', 'v'},
                    {'r', 'h', 'v', 'l'}, {'r', 'h', 'l', 'v'}, {'r', 'v', 'l', 'h'},
                    {'r', 'v', 'h', 'l'}, {'r', 'l', 'v', 'h'}, {'r', 'l', 'h', 'v'}};

            private void findPlaceForRun(String[] run){
                char[] order = orders[random.nextInt(orders.length)];

                boolean placed = false;
                for (int r = 0; r < gameBoard.length; r += 2)
                    for (int c = 0; c < gameBoard[r].length; c += 2) {
                        if (gameBoard[r][c] == null){
                            int i = 0;
                            do {
                                switch (order[i]){
                                    case 'h':
                                        placed = checkHoriz(r, c, run);
                                        break;
                                    case 'v':
                                        placed = checkVert(r, c, run);
                                        break;
                                    case 'r':
                                        placed = checkDiagRight(r, c, run);
                                        break;
                                    case 'l':
                                        placed = checkDiagLeft(r, c, run);
                                        break;
                                }
                                i++;
                            } while (i < order.length && !placed);
                            if (placed) return;
                        }
                    }
            }
            private boolean checkHoriz(int r, int c, String[] run){
                int i = 0;
                do {
                    i++;
                } while (c + i < gameBoard[0].length && gameBoard[r][c+i] == null && i < run.length);
                if (i == run.length) {
                    placeHoriz(run, r, c);
                    return true;
                }
                else return false;
            }
            private void placeHoriz(String[] run, int r, int c){
                ArrayList<int[]> newRun = new ArrayList<>(run.length);
                if (random.nextDouble() < 0.5)
                    for (int i = 0; i < run.length && c + i < gameBoard[r].length; i++) {
                        gameBoard[r][c + i] = run[i];
                        newRun.add(new int[]{r, c + i});
                    }
                else
                    for (int i = 0; i < run.length && c + i < gameBoard[r].length; i++) {
                        gameBoard[r][c + i] = run[run.length - 1 - i];
                        newRun.add(new int[]{r, c + i});
                    }
                predefinedRuns.add(newRun);
            }
            private boolean checkVert(int r, int c, String[] run){
                int i = 0;
                do {
                    i++;
                } while (r + i < gameBoard.length && gameBoard[r+i][c] == null && i < run.length);
                if (i == run.length) {
                    placeVert(run, r, c);
                    return true;
                }
                else return false;
            }
            private void placeVert(String[] run, int r, int c){
                ArrayList<int[]> newRun = new ArrayList<>(run.length);
                if (random.nextDouble() < 0.5)
                    for (int i = 0; i < run.length && r + i < gameBoard.length; i++) {
                        gameBoard[r + i][c] = run[i];
                        newRun.add(new int[]{r + i, c});
                    }
                else
                    for (int i = 0; i < run.length && r + i < gameBoard.length; i++){
                        gameBoard[r + i][c] = run[run.length - 1 - i];
                        newRun.add(new int[]{r + i, c});
                    }
                predefinedRuns.add(newRun);
            }
            private boolean checkDiagRight(int r, int c, String[] run){
                int i = 0;
                do {
                    i++;
                } while (r + i < gameBoard.length && c + i < gameBoard[0].length && gameBoard[r+i][c+i] == null && i < run.length);
                if (i == run.length) {
                    placeDiagRight(run, r, c);
                    return true;
                }
                else return false;
            }
            private void placeDiagRight(String[] run, int r, int c){
                ArrayList<int[]> newRun = new ArrayList<>(run.length);
                if (random.nextDouble() < 0.5)
                    for (int i = 0; i < run.length && r + i < gameBoard.length && c + i < gameBoard[0].length; i++){
                        gameBoard[r + i][c + i] = run[i];
                        newRun.add(new int[]{r + i, c + i});
                    }
                else
                    for (int i = 0; i < run.length && r + i < gameBoard.length && c + i < gameBoard[0].length; i++){
                        gameBoard[r + i][c + i] = run[run.length - 1 - i];
                        newRun.add(new int[]{r + i, c + i});
                    }
                predefinedRuns.add(newRun);
            }
            private boolean checkDiagLeft(int r, int c, String[] run){
                int i = 0;
                do {
                    i++;
                } while (r + i < gameBoard.length && c - i >= 0 && gameBoard[r+i][c-i] == null && i < run.length);
                if (i == run.length) {
                    placeDiagLeft(run, r, c);
                    return true;
                }
                else return false;
            }
            private void placeDiagLeft(String[] run, int r, int c){
                ArrayList<int[]> newRun = new ArrayList<>(run.length);
                if (random.nextDouble() < 0.5)
                    for (int i = 0; i < run.length && r + i < gameBoard.length && c - i >= 0; i++){
                        gameBoard[r + i][c - i] = run[i];
                        newRun.add(new int[]{r + i, c - i});
                    }
                else
                    for (int i = 0; i < run.length && r + i < gameBoard.length && c - i >= 0; i++){
                        gameBoard[r + i][c - i] = run[run.length - 1 - i];
                        newRun.add(new int[]{r + i, c - i});
                    }
                predefinedRuns.add(newRun);
            }
        }
        Helper mHelper = new Helper();
        Random random = new Random();
        String num;

        // Place pre-defined runs
        if (runs != null){
            predefinedRuns = new ArrayList<>(runs.size());
            for (String[] run : runs){
                mHelper.findPlaceForRun(run);
                String log = "Pre-defined run: ";
                for (String s : run)
                    log += s;
                Log.d(LOG_TAG, log);
            }
        }

        // Determine total bonus count and probability weights
        int bonusTotalWeight = 1;
        int[] bonusWeights;
        String[] bonusStrings;
        if (bonuses == null || bonuses.size() < 1) {
            bonuses = new HashMap<>();
            bonusWeights = new int[]{1};
            bonusStrings = new String[]{"?"};
        } else {
            bonusTotalWeight = 0;
            bonusWeights = new int[bonuses.size()];
            bonusStrings = new String[bonuses.size()];
            int index = 0;
            for (String s : bonuses.keySet()) {
                bonusStrings[index] = s;
                bonusTotalWeight += bonuses.get(s);
                bonusWeights[index] = bonusTotalWeight;
                index++;
            }
        }

        // Fill empty places on board with randomly selected numbers and bonuses
        for (int r = 0; r < gameBoard.length; r++) {
            for (int c = 0; c < gameBoard[0].length; c++) {
                if (r % 2 == 0 && c % 2 == 0) {
                    // Even row & col corresponds to a number
                    num = numberList[random.nextInt(numberList.length)];
                    if (gameBoard[r][c] == null) gameBoard[r][c] = num;
                } else if (gameBoard[r][c] == null || gameBoard[r][c].equals(UNDEF)) {
                    // Odd row or col corresponds to a bonus space
                    int p = random.nextInt(bonusTotalWeight);
                    for (int i = 0; i < bonusStrings.length; i++) {
                        if (p <= bonusWeights[i]) {
                            gameBoard[r][c] = bonusStrings[i];
                            break;
                        }
                    }
                }
            }
        }
        resetGameBoard(getWidth(), getHeight());
        invalidate();
    }

    public void setShowRuns(boolean showRuns){
        this.showRuns = showRuns;
        invalidate();
    }

    private RectF[][] tileDimensions;
    private float pieceW, pieceH;
    private float horzMargin = 16, vertMargin = 16;
    private Bitmap gameBoardBitmap;

    private void resetGameBoard(int w, int h){
        if (gameBoard != null && w > 0 && h > 0){
            tileDimensions = new RectF[gameBoard.length][gameBoard[0].length];

            // Determine margins

            // Determine tile sizes
            float width = (w - 2 * horzMargin) / tileDimensions[0].length, height = (h - 2 * vertMargin) / tileDimensions.length;
            pieceW = width;
            pieceH = height;
            for (int r = 0; r < tileDimensions.length; r++)
                for (int c = 0; c < tileDimensions[0].length; c++){
                    tileDimensions[r][c] = new RectF(horzMargin + c * width, vertMargin + r * height, horzMargin + c * width + width - 1, vertMargin + r * height + height - 1);
                }

            gameBoardBitmap = getGameBoardBitmap(w, h);

        }
    }
    private Bitmap getGameBoardBitmap(int width, int height){
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Set up text paint
        txtPaint = new Paint();
        txtPaint.setColor(Color.BLACK);
        RectF dimens = tileDimensions[0][0];
        float txtSize = Math.min(dimens.width(), dimens.height());
        txtPaint.setTextSize(txtSize);
        txtPaint.setTextAlign(Paint.Align.CENTER);
        selectionPaint.setStrokeWidth(txtSize * 1.333f);

        // Draw each of the number tiles
        String tileText;
        for (int r = 0; r < tileDimensions.length; r++)
            for (int c = 0; c < tileDimensions[0].length; c++){
                tileText = gameBoard[r][c];
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
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_LOCK_ADD, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_LOCK_SUB:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_LOCK_SUB, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_LOCK_MULT:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_LOCK_MULT, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_LOCK_DIV:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_LOCK_DIV, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_BALLOONS:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_BALLOONS, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_RED_JEWEL:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_RED_JEWEL, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_BOMB:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_BOMB, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_APPLE:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_APPLE, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_BANANAS:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_BANANAS, dimens), null, dimens, null);
                            break;
                        case ArithmosLevel.BONUS_CHERRIES:
                            canvas.drawBitmap(getBonusBitmap(ArithmosLevel.BONUS_CHERRIES, dimens), null, dimens, null);
                            break;
                        default:
                            canvas.drawText(tileText, dimens.centerX(), dimens.centerY() + txtSize / 2, txtPaint);
                    }
                }
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


    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        int reqWidth = MeasureSpec.getSize(widthMeasureSpec), reqHeight = MeasureSpec.getSize(heightMeasureSpec);
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
            if (showRuns){
                // Draw pre-defined runs
                for (ArrayList<int[]> run : predefinedRuns){
                    int[] start = run.get(0), end = run.get(run.size() - 1);
                    RectF startRect = tileDimensions[start[0]][start[1]], endRect = tileDimensions[end[0]][end[1]];
                    selectionPath.moveTo(startRect.centerX(), startRect.centerY());
                    selectionPath.lineTo(endRect.centerX(), endRect.centerY());
                    canvas.drawPath(selectionPath, selectionPaint);
                    selectionPath.rewind();
                }
            }
        }
    }
}
