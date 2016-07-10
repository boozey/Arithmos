package com.nakedape.arithmos;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Nathan on 5/7/2016.
 */
public class ArithmosLevel {
    private static final String LOG_TAG = "ArithmosLevel";

    public static final int GOAL_SINGLE_NUM = 4002;
    public static final int GOAL_MULT_NUM = 4001;
    public static final int GOAL_MULTIPLES = 4003;
    public static final int GOAL_301 = 4004;
    public static final String BONUS_OP_LOCK = "bonus_lock";
    public static final String BONUS_LOCK_ADD = "bonus_lock+";
    public static final String BONUS_LOCK_SUB = "bonus_lock-";
    public static final String BONUS_LOCK_MULT = "bonus_lock*";
    public static final String BONUS_LOCK_DIV = "bonus_lock/";
    public static final String BONUS_BALLOONS = "bonus_balloons";
    public static final String BONUS_RED_JEWEL = "bonus_red_jewel";
    public static final String BONUS_BOMB ="bonus_bomb";
    public static final String BONUS_APPLE = "bonus_apple";
    public static final String BONUS_BANANAS = "bonus_bananas";
    public static final String BONUS_CHERRIES = "bonus_cherries";

    private int[] gridNumbers;
    public int[] getGridNumbers() { return gridNumbers; }

    private int[] gridSpecialNumbers;
    public int[] getGridSpecialNumbers() { return gridSpecialNumbers; }

    private int[] goalNumbers;
    public int[] getGoalNumbers() { return goalNumbers; }

    private int numGoalsToWin;
    public int getNumGoalsToWin() {return numGoalsToWin;}

    private int gridSize;
    public int getGridSize(){ return gridSize; }

    private int goalType = GOAL_MULT_NUM;
    public int getGoalType() {
        return goalType;
    }

    private int timeLimitMillis = -1;
    public int getTimeLimitMillis() { return timeLimitMillis; }
    public boolean hasTimeLimit() { return timeLimitMillis > -1 ;}

    private String challenge;
    public String getChallenge() { return challenge; }

    private int challengeLevel;
    public int getChallengeLevel() { return challengeLevel; }

    public int getChallengeDisplayNameId(){
        int id;
        try {
            id = ArithmosGameBase.getChallengeDisplayNameResId(challenge);
        } catch (Resources.NotFoundException e) {
            id = -1;
        }
        return id;
    }
    public int getLevelDisplayNameId(){
        int id;
        try {
            id = ArithmosGameBase.getLevelDisplayNameResIds(challenge)[challengeLevel];
        } catch (Resources.NotFoundException e){
            switch (gridSize){
                case 4:
                    return R.string.level_4x4;
                case 5:
                    return R.string.level_5x5;
                case 6:
                    return R.string.level_6x6;
                case 7:
                    return R.string.level_7x7;
                case 8:
                    return R.string.level_8x8;
                case 9:
                    return R.string.level_9x9;
                case 10:
                    return R.string.level_10x10;
                default:
                    return -1;
            }
        }
        return id;
    }

    private int[] starLevels;
    public int[] getStarLevels() {return starLevels;}

    private String leaderboardId;
    public String getLeaderboardId() { return leaderboardId; }

    private HashMap<String, Integer> bonuses;
    public HashMap<String, Integer> getBonuses(){
        return bonuses;
    }

    private ArrayList<String[]> runs;
    public ArrayList<String[]> getRuns() {return runs;}

    // Xml De-serialization

    public ArithmosLevel(Context context, int levelResId){
        bonuses = new HashMap<>(9);
        runs = new ArrayList<>(5);
        XmlResourceParser parser = context.getResources().getXml(levelResId);
        // Process xml level data
        try {
            int eventType = -1;
            while (eventType != XmlResourceParser.END_DOCUMENT) {
                if (eventType == XmlResourceParser.START_TAG) {
                    switch (parser.getName()) {
                        case "Level":
                            parseLevelTag(parser);
                            break;
                        case "GridBaseNumbers":
                            parseGridBaseTag(parser);
                            break;
                        case "GridSpecialNumbers":
                            parseGridSpecialTag(parser);
                            break;
                        case "GoalNumbers":
                            parseGoalNumbersTag(parser);
                            break;
                        case "Bonus":
                            parseBonusTag(parser);
                            break;
                        case "StarLevels":
                            parseStarLevelsTag(parser);
                            break;
                        case "Run":
                            parseRunTag(parser);
                            break;
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e){
            e.printStackTrace();
        }
    }

    public ArithmosLevel(File levelXmlFile) throws XmlPullParserException, IOException{
        bonuses = new HashMap<>(9);
        runs = new ArrayList<>(5);
        XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
        XmlPullParser parser = xmlFactoryObject.newPullParser();
        parser.setInput(new FileReader(levelXmlFile));

        // Process xml level data
            int eventType = -1;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    switch (parser.getName()) {
                        case "Level":
                            parseLevelTag(parser);
                            break;
                        case "GridBaseNumbers":
                            parseGridBaseTag(parser);
                            break;
                        case "GridSpecialNumbers":
                            parseGridSpecialTag(parser);
                            break;
                        case "GoalNumbers":
                            parseGoalNumbersTag(parser);
                            break;
                        case "Bonus":
                            parseBonusTag(parser);
                            break;
                        case "StarLevels":
                            parseStarLevelsTag(parser);
                            break;
                        case "Run":
                            parseRunTag(parser);
                            break;
                    }
                }
                eventType = parser.next();
            }
    }

    private void parseLevelTag(XmlPullParser parser){
        for (int i = 0; i < parser.getAttributeCount(); i++){
            switch (parser.getAttributeName(i)){
                case "challenge_name":
                    challenge = parser.getAttributeValue(i);
                    break;
                case "challenge_level":
                    challengeLevel = Integer.valueOf(parser.getAttributeValue(i));
                    break;
                case "gridsize":
                    gridSize = Integer.valueOf(parser.getAttributeValue(i));
                    break;
                case "leaderboardid":
                    leaderboardId = parser.getAttributeValue(i);
                    break;
                case "goal_type":
                    String name = parser.getAttributeValue(i);
                    if (name.equals("301")) goalType = GOAL_301;
                    else if (name.equals("single")) goalType = GOAL_SINGLE_NUM;
                    else goalType = GOAL_MULT_NUM;
                    break;
                case "time_limit":
                    timeLimitMillis = 1000 * Integer.valueOf(parser.getAttributeValue(i));
                    break;
            }
        }
    }
    private void parseGridBaseTag(XmlPullParser parser) throws XmlPullParserException, IOException{
            parser.next();
            String text = parser.getText().replace(" ", "");
            String[] numbers = text.split(",");
            gridNumbers = new int[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                gridNumbers[i] = Integer.valueOf(numbers[i]);
            }
    }
    private void parseGridSpecialTag(XmlPullParser parser) throws XmlPullParserException, IOException{
            parser.next();
            String text = parser.getText().replace(" ", "");
            String[] numbers = text.split(",");
            gridSpecialNumbers = new int[numbers.length];
            for (int i = 0; i < numbers.length; i++) {
                gridSpecialNumbers[i] = Integer.valueOf(numbers[i]);
            }
    }
    private void parseGoalNumbersTag(XmlPullParser parser) throws XmlPullParserException, IOException{
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case "count":
                    numGoalsToWin = Integer.valueOf(parser.getAttributeValue(i));
                    break;
            }
        }
        parser.next();
        String text = parser.getText().replace(" ", "");
        String[] numbers = text.split(",");
        goalNumbers = new int[numbers.length];
        for (int i = 0; i < numbers.length; i++){
            goalNumbers[i] = Integer.valueOf(numbers[i]);
        }
        numGoalsToWin = numGoalsToWin > 0 ? numGoalsToWin : goalNumbers.length;
    }
    private void parseBonusTag(XmlPullParser parser) throws XmlPullParserException, IOException{
        String type = BONUS_BALLOONS;
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            switch (parser.getAttributeName(i)) {
                case "name":
                    type = parser.getAttributeValue(i);
                    if (!type.contains("bonus_"))
                        type = "bonus_" + type;
                    break;
            }
        }
        parser.next();
        int count = Integer.valueOf(parser.getText());
        bonuses.put(type, count);
    }
    private void parseStarLevelsTag(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.next();
        String text = parser.getText().replace(" ", "");
        String[] numStrings = text.split(",");
        starLevels = new int[numStrings.length];
        for (int i = 0; i < numStrings.length; i++)
            starLevels[i] = Integer.valueOf(numStrings[i]);
    }
    private void parseRunTag(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.next();
        String text = parser.getText().replace(" ", "");
        String[] entries = text.split(",");
        runs.add(entries);
    }

    // Xml serialization
    public ArithmosLevel(int size, String[] gridNumberList, String[] goalNumberList, HashMap<String, Integer> bonuses, ArrayList<String[]> runs){
        this.gridSize = size;
        this.gridNumbers = new int[gridNumberList.length];
        for (int i = 0; i < gridNumberList.length; i++)
            gridNumbers[i] = Integer.valueOf(gridNumberList[i]);
        this.goalNumbers = new int[goalNumberList.length];
        for (int i = 0; i < goalNumberList.length; i++)
            goalNumbers[i] = Integer.valueOf(goalNumberList[i]);
        this.bonuses = bonuses;
        this.runs = runs;
    }

    public void Serialize(File file) throws IOException, XmlPullParserException{
        FileWriter fileWriter = new FileWriter(file);
        XmlSerializer xmlSerializer = XmlPullParserFactory.newInstance().newSerializer();
        xmlSerializer.setOutput(fileWriter);

        //Start Document
        xmlSerializer.startDocument("UTF-8", true);
        xmlSerializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        //Open Tag <Level>
        xmlSerializer.startTag("", "Level");
        xmlSerializer.attribute("", "challenge_name", "My Level");
        xmlSerializer.attribute("", "challenge_level", String.valueOf(0));
        xmlSerializer.attribute("", "gridsize", String.valueOf(gridSize));

        // Add <GridBaseNumbers> tag
        xmlSerializer.startTag("", "GridBaseNumbers");
        String text = "";
        for (int i = 0; i < gridNumbers.length; i++){
            if (i != gridNumbers.length - 1)
                text += gridNumbers[i] + ", ";
            else
                text += gridNumbers[i];
        }
        xmlSerializer.text(text);
        xmlSerializer.endTag("", "GridBaseNumbers");

        // Add <GridSpecialNumbers> tag
        xmlSerializer.startTag("", "GridSpecialNumbers");
        text = "";
        for (int i = 0; i < gridNumbers.length; i++){
            if (i != gridNumbers.length - 1)
                text += gridNumbers[i] + ", ";
            else
                text += gridNumbers[i];
        }
        xmlSerializer.text(text);
        xmlSerializer.endTag("", "GridSpecialNumbers");

        // Add <GoalNumbers> tag
        xmlSerializer.startTag("", "GoalNumbers");
        xmlSerializer.attribute("", "count", String.valueOf(numGoalsToWin));
        text = "";
        for (int i = 0; i < goalNumbers.length; i++){
            if (i != goalNumbers.length - 1)
                text += goalNumbers[i] + ", ";
            else
                text += goalNumbers[i];
        }
        xmlSerializer.text(text);
        xmlSerializer.endTag("", "GoalNumbers");

        // Add <Run> tags
        for (String[] run : runs){
            xmlSerializer.startTag("", "Run");
            text = "";
            for (int i = 0; i < run.length; i++){
                if (i != run.length - 1)
                    text += run[i] + ", ";
                else
                    text += run[i];
            }
            xmlSerializer.text(text);
            xmlSerializer.endTag("", "Run");
        }

        // Add <StarLevels> tag
        xmlSerializer.startTag("", "StarLevels");
        text = String.valueOf((gridSize - 3)*1000) + ", " + String.valueOf((gridSize - 2)*1000) + ", " + String.valueOf((gridSize - 1)*1000);
        xmlSerializer.text(text);
        xmlSerializer.endTag("", "StarLevels");

        // Add <Bonus> tags
        for (String bonus : bonuses.keySet()){
            xmlSerializer.startTag("", "Bonus");
            xmlSerializer.attribute("", "name", bonus);
            xmlSerializer.text(String.valueOf(bonuses.get(bonus)));
            xmlSerializer.endTag("", "Bonus");
        }

        // End tag </Level>
        xmlSerializer.endTag("", "Level");
        xmlSerializer.flush();

        // End document
        xmlSerializer.endDocument();
        Log.d(LOG_TAG, "Xml written");
    }
}
