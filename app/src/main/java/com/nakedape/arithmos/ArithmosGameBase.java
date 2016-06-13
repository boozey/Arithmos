package com.nakedape.arithmos;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Nathan on 5/8/2016.
 */
public class ArithmosGameBase {
    private static final String LOG_TAG = "ArithmosGameBaseData";

    public static final String CRAZY_EIGHTS = "CRAZY_EIGHTS";
    public static final String EASY_123 = "EASY_123";
    public static final String LUCKY_7 = "LUCKY_7";
    public static final String SPECIAL_BOMB = "SPECIAL_BOMB";
    public static final String SPECIAL_SKIP = "SPECIAL_SKIP";
    public static final String SPECIAL_OP_ORDER = "SPECIAL_OP_ORDER";
    public static final String SPECIAL_CHANGE = "SPECIAL_CHANGE";

    public static final String[] challenges = {CRAZY_EIGHTS, EASY_123, LUCKY_7};

    public static int getChallengeDisplayNameResId(String challenge){
        switch (challenge){
            case CRAZY_EIGHTS:
                return R.string.challenge_name_crazy_eights;
            case EASY_123:
                return R.string.challenge_name_easy_as_123;
            case LUCKY_7:
                return R.string.challenge_name_lucky_7;
            default:
                return -1;
        }
    }

    public static int[] getLevelXmlIds(String challenge){
        switch (challenge){
            case CRAZY_EIGHTS:
                return new int[] {R.xml.game_level_crazy_eights_6x6, R.xml.game_level_crazy_eights_8x8,
                        R.xml.game_level_crazy_eights_301, R.xml.game_level_crazy_eights_timed};
            case EASY_123:
                return new int[] {R.xml.game_level_easy_123_301, R.xml.game_level_easy_123_6x6, R.xml.game_level_easy_123_timed};
            default:
                return getLevelXmlIds(CRAZY_EIGHTS);
        }
    }

    public static int[] getLevelDisplayNameResIds(String challenge){
        switch (challenge){
            case CRAZY_EIGHTS:
                return new int[] {R.string.level_6x6, R.string.level_8x8, R.string.level_301, R.string.level_timed};
            case EASY_123:
                return new int[] {R.string.level_301, R.string.level_6x6, R.string.level_timed};
            default:
                return getLevelDisplayNameResIds(CRAZY_EIGHTS);
        }
    }

    private static final int serializationVersion = 3;
    private static final long weekInMillis = 604800000;
    private static final long dayInMillis = 86400000;
    private ArrayList<String> unlockedLevels;
    private int[] crazyEightsStars;
    private HashMap<String, Integer> stars;
    private int jewelCount;
    private HashMap<String, Integer> specials;
    private ArrayList<GameActivityItem> activityItems;
    private boolean needsSaving = false;

    public ArithmosGameBase(){
        unlockedLevels = new ArrayList<>();
        unlockedLevels.add(CRAZY_EIGHTS + "0");
        stars = new HashMap<>();
        jewelCount = 0;
        specials = new HashMap<>(4);
        specials.put(SPECIAL_BOMB, 1);
        specials.put(SPECIAL_SKIP, 3);
        specials.put(SPECIAL_CHANGE, 1);
        specials.put(SPECIAL_OP_ORDER, 2);
        activityItems = new ArrayList<>();
    }

    public boolean needsSaving(){return needsSaving;}

    public void resetGame(){
        unlockedLevels = new ArrayList<>(1);
        unlockedLevels.add(CRAZY_EIGHTS + "0");
        stars = new HashMap<>();
        activityItems = new ArrayList<>();
        needsSaving = true;
    }

    // Level unlocking
    public boolean isLevelUnlocked(String challengeName, int level){
        return unlockedLevels.contains(challengeName + String.valueOf(level));
    }

    public int[] unlockNextLevel(String challengeName, int levelPassed){
        int nextLevel = levelPassed + 1;
        if (nextLevel < getLevelXmlIds(challengeName).length) {
            if (!isLevelUnlocked(challengeName, nextLevel)) {
                unlockedLevels.add(challengeName + String.valueOf(nextLevel));
                needsSaving = true;
            }
            return new int[] {getChallengeDisplayNameResId(challengeName), getLevelDisplayNameResIds(challengeName)[nextLevel]};
        } else {
            switch (challengeName) {
                case CRAZY_EIGHTS:
                    return unlockNextLevel(EASY_123, -1);
                case EASY_123:
                    return unlockNextLevel(LUCKY_7, -1);
            }
        }
        return null;
    }


    // Bonuses
    public void recordStars(String challengeName, int level, int numStars){
        if (numStars > 0) {
            if (stars.containsKey(challengeName + String.valueOf(level))) {
                int record = stars.get(challengeName + String.valueOf(level));
                if (numStars > record) {
                    stars.put(challengeName + String.valueOf(level), numStars);
                    Log.d(LOG_TAG, "Number stars updated to " + stars.get(challengeName + String.valueOf(level)));
                }
            } else
                stars.put(challengeName + String.valueOf(level), numStars);
            needsSaving = true;
        }
    }

    public int getNumStars(String challengeName, int level){
        if (stars.containsKey(challengeName + String.valueOf(level)))
            return stars.get(challengeName + String.valueOf(level));
        else
            return 0;
    }

    public void recordJewels(int num){
        jewelCount += num;
        needsSaving = true;
    }

    public void spendJewels(int num){
        jewelCount -= num;
        needsSaving = true;
    }

    public int getJewelCount(){
        return jewelCount;
    }


    // Specials
    public int getSpecialCount(String special){
        if (specials.containsKey(special))
            return specials.get(special);
        else
            return 0;
    }

    public void addSpecial(String special, int amount){
        amount += getSpecialCount(special);
        specials.put(special, amount);
        needsSaving = true;
    }

    public int useSpecial(String special){
        int count = 0;
        if (specials.containsKey(special)){
            count = specials.get(special);
            count = Math.max(--count, 0);
            specials.put(special, count);
            needsSaving = true;
        }
        return count;
    }


    // Activity
    public ArrayList<GameActivityItem> getActivityItems(){
        return activityItems;
    }

    public void addActivityItem(GameActivityItem item){
        ArrayList<GameActivityItem> temp = new ArrayList<>(activityItems.size() + 1);
        temp.add(item);
        temp.addAll(activityItems);
        activityItems = temp;
        needsSaving = true;
    }

    public boolean removeActivityItem(GameActivityItem item){
        if (activityItems.remove(item)){
            needsSaving = true;
            return true;
        } else {
            return false;
        }
    }

    public void removeSavedGameActivityItem(String uniqueName){
        int index = -1;
        for (int i = 0; i < activityItems.size(); i++){
            if (activityItems.get(i).uniqueName != null && activityItems.get(i).uniqueName.equals(uniqueName)) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            activityItems.remove(index);
            needsSaving = true;
        }
    }

    public void resetActivityList() {
        activityItems = new ArrayList<>();
        needsSaving = true;
    }


    // Serialization
    public byte[] getByteData(){
        ByteArrayOutputStream bos = null;
        try {
            bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            writeObject(oos);
            oos.close();
            bos.close();
        } catch (IOException e)
        {e.printStackTrace();}
        return bos.toByteArray();
    }
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(serializationVersion);
        out.writeObject(unlockedLevels);
        out.writeObject(stars);
        out.writeInt(jewelCount);
        out.writeObject(specials);
        out.writeObject(activityItems);
        needsSaving = false;
    }

    public void loadByteData(byte[] data){
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        try {
            ObjectInputStream ois = new ObjectInputStream(bis);
            readObject(ois);
        } catch (IOException | ClassNotFoundException e) {e.printStackTrace();}
    }
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        int version = in.readInt();
        unlockedLevels = (ArrayList<String>)in.readObject();
        stars = (HashMap<String, Integer>)in.readObject();
        jewelCount = in.readInt();
        if (version >= 2){
            specials = (HashMap<String, Integer>)in.readObject();
        }
        if (version >= 3){
            ArrayList<GameActivityItem> items = (ArrayList<GameActivityItem>)in.readObject();
            // Remove outdated entries
            activityItems = new ArrayList<>(items.size());
            for (GameActivityItem item : items){
                if (item.timeStamp > System.currentTimeMillis() - dayInMillis)
                    activityItems.add(item);
            }
        }

        needsSaving = false;
    }
}
